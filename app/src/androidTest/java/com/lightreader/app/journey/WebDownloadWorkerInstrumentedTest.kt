package com.lightreader.app.journey

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.SynchronousExecutor
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.data.DownloadRepository
import com.lightreader.app.core.model.WebChapter
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.web.WebDownloadWorker
import com.lightreader.app.feature.download.DownloadActionReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class WebDownloadWorkerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: ReaderApplication = context as ReaderApplication
    private lateinit var server: QaHttpsFixtureServer

    @Before
    fun setUp() = runBlocking {
        check(context.packageName.endsWith(".qa")) { "Worker tests require the isolated QA package." }
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        application.container.database.clearAllTables()
        server = QaHttpsFixtureServer()
    }

    @After
    fun tearDown() {
        server.close()
        WorkManager.getInstance(context).cancelAllWork().result.get()
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

        val result = worker(taskId, retryAttempt = 3).doWork()
        assertTrue(result is ListenableWorker.Result.Success)
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

        // Execute the actual production batch limit. Scheduled continuations remain constrained
        // in TestWorkManager; each explicit invocation represents a fresh process/worker batch.
        var batches = 0
        do {
            val before = application.container.database.readerDao().downloadTask(taskId)!!.completedChapters
            val result = worker(taskId).doWork()
            assertTrue(result is ListenableWorker.Result.Success)
            val current = application.container.database.readerDao().downloadTask(taskId)!!
            assertTrue(current.completedChapters - before <= WebDownloadWorker.MAX_CHAPTERS_PER_BATCH)
            batches++
            assertTrue("Batch count is bounded", batches <= 25)
        } while (application.container.database.readerDao().downloadTask(taskId)?.status == "QUEUED")
        assertEquals(25, batches)
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

    @Test
    fun notificationPauseRejectsInflightResultAndResumeKeepsSameTask() = runBlocking {
        server.close()
        val gate = CountDownLatch(1)
        server = QaHttpsFixtureServer(chapterResponseGate = gate)
        val repository = unscheduledRepository()
        val taskId = repository.start(singleDelayedPreview("pause"))
        val execution = worker(taskId)
        val running = async(Dispatchers.IO) { execution.doWork() }
        awaitChapterRequest()
        DownloadActionReceiver.pauseIntent(context, taskId).send()
        withTimeout(5_000) {
            while (application.container.database.readerDao().downloadTask(taskId)?.status != "PAUSED") delay(10)
        }
        gate.countDown()
        running.await()
        val dao = application.container.database.readerDao()
        assertEquals(0, dao.downloadTask(taskId)?.completedChapters)
        assertNull(dao.book(taskId))
        repository.resume(taskId)
        worker(taskId).doWork()
        assertEquals("COMPLETED", dao.downloadTask(taskId)?.status)
        assertEquals(1, dao.chapters(taskId).size)
    }

    @Test
    fun workManagerRestartFencesInterruptedDownloadingExecution() = runBlocking {
        val taskId = unscheduledRepository().start(singleDelayedPreview("restart"))
        val dao = application.container.database.readerDao()
        val restarted = worker(taskId, workerAttempt = 1)
        assertEquals(1, dao.claimDownloadTask(taskId, 0, System.currentTimeMillis(), restarted.id.toString()))
        // Two process interruptions with the same WorkRequest UUID retain an attributable owner.
        assertEquals(1, dao.recoverInterruptedDownload(taskId, restarted.id.toString(), System.currentTimeMillis()))
        restarted.doWork()
        assertEquals("COMPLETED", dao.downloadTask(taskId)?.status)
        assertEquals(2L, dao.downloadTask(taskId)?.executionGeneration)
        assertEquals(0, dao.updateDownloadExecution(taskId, 0, "DOWNLOADING", 0, 0, null, 1))
    }

    @Test
    fun cancelAndDeleteDuringHttpCannotPublishChapterOrBook() = runBlocking {
        server.close()
        val repository = unscheduledRepository()
        val dao = application.container.database.readerDao()
        for (delete in listOf(false, true)) {
            val gate = CountDownLatch(1)
            server = QaHttpsFixtureServer(chapterResponseGate = gate)
            val taskId = repository.start(singleDelayedPreview(if (delete) "delete" else "cancel"))
            val execution = worker(taskId)
            val running = async(Dispatchers.IO) { execution.doWork() }
            awaitChapterRequest()
            if (delete) repository.delete(taskId) else repository.cancel(taskId)
            gate.countDown()
            running.await()
            assertNull(dao.book(taskId))
            assertTrue(dao.chapters(taskId).isEmpty())
            if (delete) assertNull(dao.downloadTask(taskId)) else assertEquals("CANCELED", dao.downloadTask(taskId)?.status)
            if (!delete) server.close()
        }
    }

    private fun singleDelayedPreview(scenario: String) = WebBookPreview("Delayed", null, null,
        server.url("/catalog?case=$scenario"), server.url("/catalog?case=$scenario"),
        listOf(WebChapter("第1章", server.url("/large-chapter/1"))), "Sample", ExtractionPlan(contentSelector = ".content"))

    private suspend fun awaitChapterRequest() = withTimeout(5_000) {
        while (server.requests.none { it.path?.startsWith("/large-chapter/") == true }) delay(10)
    }

    private fun unscheduledRepository() = DownloadRepository(
        context = context,
        dao = application.container.database.readerDao(),
        json = application.container.json,
        webSourceParser = application.container.webSourceParser,
        enqueueWork = false,
    )

    private suspend fun worker(taskId: String, retryAttempt: Int = 0, workerAttempt: Int = 0): WebDownloadWorker =
        TestListenableWorkerBuilder<WebDownloadWorker>(context)
        .setRunAttemptCount(workerAttempt)
        .setInputData(
            workDataOf(
                WebDownloadWorker.TASK_ID to taskId,
                WebDownloadWorker.GENERATION to application.container.database.readerDao().downloadTask(taskId)!!.executionGeneration,
                WebDownloadWorker.RETRY_ATTEMPT to retryAttempt,
            ),
        )
        .build()
}
