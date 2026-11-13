package com.lightreader.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.data.*
import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.model.WebChapter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadPersistenceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: ReaderDatabase
    private lateinit var dao: ReaderDao
    private lateinit var root: File
    private lateinit var books: BookRepository
    private var catalog = preview()

    @Before fun before() {
        check(context.packageName.endsWith(".qa"))
        database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        dao = database.readerDao()
        root = File(context.cacheDir, "download-persistence-${UUID.randomUUID()}").apply { mkdirs() }
        books = BookRepository(context, dao)
    }

    @After fun after() { database.close(); root.deleteRecursively() }

    @Test fun concurrentConfirmIsOneAtomicTaskAndChapters() = runBlocking {
        val repository = repository()
        val ids = coroutineScope { List(12) { async { repository.start(catalog) } }.awaitAll() }
        assertEquals(1, ids.distinct().size)
        assertEquals(1, dao.downloadTasks().size)
        assertEquals(catalog.chapters.size, dao.downloadChapters(ids.first()).size)
    }

    @Test fun refreshIdentityAllowsSameTitleDistinctPathsAndReusesActiveTask() = runBlocking {
        val book = originalBook()
        catalog = preview().copy(chapters = listOf(
            WebChapter("Renamed", "https://example.com/chapter/Original"),
            WebChapter("Same title", "https://example.com/chapter/A"),
            WebChapter("Same title", "https://example.com/chapter/a"),
            WebChapter("Repeated URL", "https://EXAMPLE.com/chapter/A"),
        ))
        val repository = repository()
        val counts = coroutineScope { List(8) { async { repository.refreshBook(book.id) } }.awaitAll() }
        assertTrue(counts.all { it == 2 })
        assertEquals(1, dao.tasksForBook(book.id).size)
        assertEquals(2, dao.downloadChapters(dao.tasksForBook(book.id).single().id).size)
    }

    @Test fun staleExecutionCannotChangePausedCanceledOrDeletedState() = runBlocking {
        val repository = repository()
        val id = repository.start(catalog)
        assertEquals(1, dao.claimDownloadTask(id, 0, 1))
        val chapter = dao.downloadChapters(id).first()
        repository.pause(id)
        assertEquals(0, dao.updateDownloadExecution(id, 0, "DOWNLOADING", 1, 0, null, 2))
        assertEquals(0, dao.updateDownloadChapterExecution(chapter.id, id, 0, "COMPLETED", "/late", null))
        repository.resume(id)
        val generation = dao.downloadTask(id)!!.executionGeneration
        assertEquals(0, dao.claimDownloadTask(id, 0, 3))
        assertEquals(1, dao.claimDownloadTask(id, generation, 3))
        repository.cancel(id)
        assertEquals(0, dao.updateDownloadExecution(id, generation, "DOWNLOADING", 1, 0, null, 4))
        repository.delete(id)
        assertNull(dao.downloadTask(id))
        assertEquals(0, dao.updateDownloadChapterExecution(chapter.id, id, generation, "COMPLETED", "/late", null))
    }

    @Test fun preparedFilesSurviveTransactionFailureAndRetryWithoutChangingOriginalIdentity() = runBlocking {
        val book = originalBook()
        val original = dao.chapters(book.id).single()
        dao.saveProgress(ReadingProgressEntity(book.id, original.id, 4, 0, 0, original.title, 0, 1))
        dao.insertBookmark(BookmarkEntity("bookmark", book.id, original.id, 4, "anchor", 1))
        catalog = preview().copy(chapters = listOf(WebChapter("Same title", "https://example.com/new")))
        val repository = repository()
        repository.refreshBook(book.id)
        val task = dao.tasksForBook(book.id).single()
        assertEquals(1, dao.claimDownloadTask(task.id, task.executionGeneration, 1))
        val pending = dao.downloadChapters(task.id).single()
        val stage = File(root, "new.txt").apply { writeText("新增正文😀\n第二行") }
        dao.updateDownloadChapterExecution(pending.id, task.id, task.executionGeneration, "COMPLETED", stage.absolutePath, null)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_statistics BEFORE UPDATE OF chapterCount ON books BEGIN SELECT RAISE(ABORT, 'injected statistics failure'); END")
        try {
            books.commitDownloadedTask(task.id, task.executionGeneration)
            fail("Injected DB failure must propagate")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(listOf(original.id), dao.chapters(book.id).map { it.id })
        assertEquals(original.charCount.toLong(), dao.book(book.id)!!.totalChars)
        assertEquals("DOWNLOADING", dao.downloadTask(task.id)!!.status)
        assertEquals("原有正文😀原有正文", File(original.contentPath).readText())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_statistics")
        assertTrue(books.commitDownloadedTask(task.id, task.executionGeneration))
        assertFalse(books.commitDownloadedTask(task.id, task.executionGeneration))
        val chapters = dao.chapters(book.id)
        assertEquals(2, chapters.size)
        assertEquals(original.id, chapters.first().id)
        assertEquals(chapters.sumOf { it.charCount.toLong() }, dao.book(book.id)!!.totalChars)
        assertEquals(4, dao.progress(book.id)!!.charOffset)
        repository.delete(task.id)
        assertTrue(chapters.all { File(it.contentPath).exists() })
        assertEquals(2, dao.chapters(book.id).size)
    }

    @Test fun progressUpdateDuringAppendDoesNotOverwriteStatisticsAndDeletingBookRemovesAllTasks() = runBlocking {
        val book = originalBook()
        val original = dao.chapters(book.id).single()
        val extra = File(root, "append.txt").apply { writeText("extra body") }
        coroutineScope {
            val saving = async { repeat(20) { books.saveProgress(book.id, original.id, 4, 0, 0, original.title, 0) } }
            val adding = async {
                dao.appendChaptersAtomically(book.id, listOf(ChapterEntity(bookId = book.id, orderIndex = 0,
                    title = "Same title", contentPath = extra.absolutePath, charCount = 10, sourceUrl = "https://example.com/extra")))
            }
            saving.await(); adding.await()
        }
        assertEquals(2, dao.book(book.id)!!.chapterCount)
        assertEquals(original.charCount.toLong() + 10, dao.book(book.id)!!.totalChars)
        repository().refreshBook(book.id)
        assertTrue(dao.tasksForBook(book.id).isNotEmpty())
        books.deleteBook(book.id)
        assertTrue(dao.tasksForBook(book.id).isEmpty())
        assertNull(dao.book(book.id))
    }

    @Test fun reconcileSchedulesCommittedButUnscheduledTaskAndInterruptedGeneration() = runBlocking {
        val scheduled = mutableListOf<Pair<String, Long>>()
        val scheduler = object : DownloadTaskScheduler {
            override fun enqueue(taskId: String, generation: Long, append: Boolean, initialDelayMillis: Long, retryAttempt: Int) { scheduled += taskId to generation }
            override suspend fun cancel(taskId: String) = Unit
        }
        val repository = repository(scheduler, enqueue = true)
        val id = repository.start(catalog)
        scheduled.clear() // Simulate scheduling state lost after database commit.
        repository.reconcile()
        assertEquals(listOf(id to 0L), scheduled)
        dao.claimDownloadTask(id, 0, 1)
        scheduled.clear()
        repository.reconcile()
        assertEquals("QUEUED", dao.downloadTask(id)!!.status)
        assertEquals(listOf(id to 1L), scheduled)
        assertEquals(0, dao.claimDownloadTask(id, 0, 2))
    }

    @Test fun deferredRetrySurvivesLostEnqueueAndCannotStartBeforeRetryAfter() = runBlocking {
        val scheduled = mutableListOf<Triple<Long, Long, Int>>()
        val inspected = mutableListOf<Pair<Long?, String?>>()
        val scheduler = object : DownloadTaskScheduler {
            override fun enqueue(taskId: String, generation: Long, append: Boolean, initialDelayMillis: Long, retryAttempt: Int) {
                scheduled += Triple(generation, initialDelayMillis, retryAttempt)
            }
            override suspend fun cancel(taskId: String) = Unit
            override suspend fun hasUnfinishedWork(taskId: String, generation: Long?, owner: String?): Boolean {
                inspected += generation to owner
                // The superseded request is still alive in WorkManager. It is not a schedule
                // for the queued generation whose enqueue was interrupted.
                return generation == 0L || owner == "old-owner"
            }
        }
        val repository = repository(scheduler, enqueue = true)
        val id = repository.start(catalog)
        val now = System.currentTimeMillis()
        val notBefore = now + 120_000
        assertEquals(1, dao.claimDownloadTask(id, 0, now, "old-owner"))
        assertEquals(1, dao.yieldDownloadTask(id, 0, 0, 1, "HTTP 429", now, notBefore, 2))
        scheduled.clear() // Simulate process death after the transaction but before WorkManager enqueue.
        repository.reconcile()
        val queued = dao.downloadTask(id)!!
        assertEquals(1L, queued.executionGeneration)
        assertEquals(notBefore, queued.notBeforeMillis)
        assertEquals(2, queued.automaticRetryCount)
        assertNull(queued.executionOwner)
        assertEquals(1, scheduled.size)
        assertEquals(listOf(1L to null), inspected)
        assertEquals(1L, scheduled.single().first)
        assertTrue(scheduled.single().second in 1..120_000)
        assertEquals(2, scheduled.single().third)
        assertEquals(0, dao.claimDownloadTask(id, 1, notBefore - 1, "too-early"))
        assertEquals(0, dao.recoverInterruptedDownload(id, "old-owner", now))
        assertEquals(1, dao.claimDownloadTask(id, 1, notBefore, "scheduled-owner"))
    }

    @Test fun resumeWaitsForNotificationPauseCancellationToFinish() = runBlocking {
        val cancellationEntered = CompletableDeferred<Unit>()
        val releaseCancellation = CompletableDeferred<Unit>()
        val resumeEntered = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val scheduler = object : DownloadTaskScheduler {
            override fun enqueue(taskId: String, generation: Long, append: Boolean, initialDelayMillis: Long, retryAttempt: Int) {
                calls += "enqueue-$generation"
            }
            override suspend fun cancel(taskId: String) {
                calls += "cancel-start"
                cancellationEntered.complete(Unit)
                releaseCancellation.await() // WorkManager cancellation Operation is still pending.
                calls += "cancel-finished"
            }
        }
        val repository = repository(scheduler, enqueue = true)
        val id = repository.start(catalog)
        calls.clear()
        val pausing = async { repository.pause(id) }
        cancellationEntered.await()
        val resuming = async { resumeEntered.complete(Unit); repository.resume(id) }
        resumeEntered.await()
        assertEquals("PAUSED", dao.downloadTask(id)!!.status)
        assertFalse(resuming.isCompleted)
        assertEquals(listOf("cancel-start"), calls)
        releaseCancellation.complete(Unit)
        pausing.await(); resuming.await()
        assertEquals(listOf("cancel-start", "cancel-finished", "enqueue-2"), calls)
        assertEquals("QUEUED", dao.downloadTask(id)!!.status)
    }

    private fun repository(scheduler: DownloadTaskScheduler? = null, enqueue: Boolean = false): DownloadRepository {
        val source = object : WebBookPreviewSource { override suspend fun preview(url: String) = catalog }
        return if (scheduler == null) DownloadRepository(context, dao, Json, source, enqueueWork = enqueue)
        else DownloadRepository(context, dao, Json, source, scheduler, enqueue)
    }

    private suspend fun originalBook(): BookEntity {
        val id = UUID.randomUUID().toString()
        val directory = File(root, id).apply { mkdirs() }
        val file = File(directory, "original.txt").apply { writeText("原有正文😀原有正文") }
        val book = BookEntity(id, "Web book", null, "WEB", directory.absolutePath, 1, null, file.readText().length.toLong(), 1, "https://example.com/catalog")
        dao.insertBookWithChapters(book, listOf(ChapterEntity(bookId = id, orderIndex = 0, title = "Same title", contentPath = file.absolutePath,
            charCount = file.readText().length, sourceUrl = "https://example.com/chapter/Original")))
        return book
    }

    companion object {
        private fun preview() = WebBookPreview("Test", null, null, "https://example.com/catalog", "https://example.com/catalog",
            listOf(WebChapter("One", "https://example.com/1"), WebChapter("Two", "https://example.com/2")), "Body", ExtractionPlan())
    }
}
