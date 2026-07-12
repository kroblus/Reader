package com.lightreader.app.journey

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.data.DownloadRepository
import com.lightreader.app.core.model.WebChapter
import com.lightreader.app.core.web.WebDownloadWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebDownloadWorkerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: ReaderApplication = context as ReaderApplication
    private lateinit var server: QaHttpsFixtureServer

    @Before
    fun setUp() = runBlocking {
        application.container.database.clearAllTables()
        server = QaHttpsFixtureServer()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun userStartedWebDownloadCompletesAndCreatesExactlyOneReadableBook() = runBlocking {
        val preview = application.container.webSourceParser.preview(server.url("/catalog"))
        val repository = unscheduledRepository()
        val taskId = repository.start(preview)

        val result = worker(taskId).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val task = application.container.database.readerDao().downloadTask(taskId)
        assertEquals("COMPLETED", task?.status)
        assertEquals(4, task?.completedChapters)
        val book = application.container.database.readerDao().book(taskId)
        assertNotNull(book)
        assertEquals(4, application.container.database.readerDao().chapters(taskId).size)
        assertTrue(application.container.bookRepository.readChapter(application.container.bookRepository.chapters(taskId).first()).contains("QA正文可读"))
    }

    @Test
    fun partialFailureIsRecordedAndResumeOnlyResetsFailedChapters() = runBlocking {
        val preview = application.container.webSourceParser.preview(server.url("/catalog")).copy(
            chapters = listOf(
                WebChapter("第一章", server.url("/chapter/1")),
                WebChapter("失败章", server.url("/status/500")),
            ),
        )
        val repository = unscheduledRepository()
        val taskId = repository.start(preview)

        val result = worker(taskId).doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
        val failed = application.container.database.readerDao().downloadTask(taskId)
        assertEquals("FAILED", failed?.status)
        assertEquals(1, failed?.completedChapters)
        assertEquals(1, failed?.failedChapters)

        repository.resume(taskId)
        val resumed = application.container.database.readerDao().downloadTask(taskId)
        assertEquals("QUEUED", resumed?.status)
        assertEquals(0, resumed?.failedChapters)
        val chapters = application.container.database.readerDao().downloadChapters(taskId)
        assertEquals("COMPLETED", chapters.first().status)
        assertEquals("PENDING", chapters.last().status)
    }

    @Test
    fun controlledTwoHundredChapterDownloadCreatesOneOrderedBook() = runBlocking {
        server.close()
        server = QaHttpsFixtureServer(largeChapterCount = 200)
        val preview = application.container.webSourceParser.preview(server.url("/large-catalog"))
        assertEquals(200, preview.chapters.size)
        val repository = unscheduledRepository()
        val taskId = repository.start(preview)

        val result = worker(taskId, runToCompletion = true).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val task = application.container.database.readerDao().downloadTask(taskId)
        val chapters = application.container.database.readerDao().chapters(taskId)
        assertEquals("COMPLETED", task?.status)
        assertEquals(200, task?.completedChapters)
        assertEquals(200, chapters.size)
        assertEquals((0 until 200).toList(), chapters.map { it.orderIndex })
        assertEquals(1, application.container.database.readerDao().bookRootPaths().size)
        assertEquals(
            200,
            server.requests.mapNotNull { it.path }.filter { it.startsWith("/large-chapter/") }.distinct().size,
        )
    }

    private fun unscheduledRepository() = DownloadRepository(
        context = context,
        dao = application.container.database.readerDao(),
        json = application.container.json,
        webSourceParser = application.container.webSourceParser,
        enqueueWork = false,
    )

    private fun worker(taskId: String, runToCompletion: Boolean = false): WebDownloadWorker =
        TestListenableWorkerBuilder<WebDownloadWorker>(context)
        .setInputData(
            workDataOf(
                WebDownloadWorker.TASK_ID to taskId,
                WebDownloadWorker.RUN_TO_COMPLETION_FOR_TEST to runToCompletion,
            ),
        )
        .build()
}
