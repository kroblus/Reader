package com.lightreader.app.core.web

import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.feature.download.DownloadWorkScheduler
import com.lightreader.app.core.formats.ImportedChapter
import com.lightreader.app.core.model.ExtractionPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import java.io.File

class WebDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(TASK_ID) ?: return Result.failure()
        val container = (applicationContext as ReaderApplication).container
        val dao = container.database.readerDao()
        var task = dao.downloadTask(taskId) ?: return Result.failure()
        if (task.status == "PAUSED" || task.status == "COMPLETED") return Result.success()
        task = task.copy(status = "DOWNLOADING", updatedAt = System.currentTimeMillis(), error = null)
        dao.updateDownloadTask(task)
        val plan = ExtractionPlan(
            contentSelector = task.contentSelector,
            removeSelectors = container.json.decodeFromString(task.removeSelectorsJson),
        )
        val appendTargetBookId = task.importedBookId?.takeIf { it != taskId }
        val rootDirectory = appendTargetBookId
            ?.let { dao.book(it)?.rootPath?.let(::File) ?: error("Target web book was not found.") }
            ?: File(applicationContext.filesDir, "downloads/$taskId").apply { mkdirs() }
        val directory = File(rootDirectory, "chapters").apply { mkdirs() }
        val chapters = dao.downloadChapters(taskId)
        var completed = chapters.count { it.status == "COMPLETED" && it.contentPath?.let(::File)?.exists() == true }
        updateForeground(task, completed)
        var processedInBatch = 0
        val batchStartedAt = System.currentTimeMillis()

        for (chapter in chapters) {
            if (isStopped || dao.downloadTask(taskId)?.status in TERMINAL_STOP_STATUSES) return Result.success()
            if (chapter.status == "COMPLETED" && chapter.contentPath?.let(::File)?.exists() == true) continue
            if (processedInBatch >= MAX_CHAPTERS_PER_BATCH || System.currentTimeMillis() - batchStartedAt >= MAX_BATCH_DURATION_MS) {
                dao.updateDownloadTask(task.copy(status = "QUEUED", updatedAt = System.currentTimeMillis()))
                DownloadWorkScheduler.enqueue(applicationContext, taskId, append = true)
                return Result.success()
            }
            try {
                dao.updateDownloadChapter(chapter.copy(status = "DOWNLOADING", error = null))
                val text = container.sourceRegistry.chapterText(task.sourceId, task.sourceVersion, chapter.url, plan, chapter.title)
                val file = File(directory, "%05d.txt".format(chapter.orderIndex))
                file.writeText(text)
                dao.updateDownloadChapter(chapter.copy(status = "COMPLETED", contentPath = file.absolutePath, error = null))
                completed++
                task = task.copy(
                    completedChapters = completed,
                    failedChapters = dao.downloadChapters(taskId).count { it.status == "FAILED" },
                    status = "DOWNLOADING",
                    updatedAt = System.currentTimeMillis(),
                )
                dao.updateDownloadTask(task)
                updateForeground(task, completed)
                processedInBatch++
                delay(CHAPTER_REQUEST_DELAY_MS)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                dao.updateDownloadChapter(chapter.copy(status = "FAILED", error = error.message?.take(240)))
                val failed = dao.downloadChapters(taskId).count { it.status == "FAILED" }
                dao.updateDownloadTask(
                    task.copy(
                        status = "FAILED",
                        failedChapters = failed,
                        updatedAt = System.currentTimeMillis(),
                        error = "${chapter.title}: ${error.message}".take(300),
                    ),
                )
                return if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        }

        val finished = dao.downloadChapters(taskId)
        if (finished.any { it.status != "COMPLETED" || it.contentPath == null }) {
            dao.updateDownloadTask(
                task.copy(
                    status = "FAILED",
                    failedChapters = finished.count { it.status == "FAILED" },
                    updatedAt = System.currentTimeMillis(),
                    error = "Some chapters failed to download. Retry the failed chapters.",
                ),
            )
            return Result.success()
        }
        val importedChapters = finished.map { chapter ->
            val file = File(requireNotNull(chapter.contentPath))
            ImportedChapter(
                title = chapter.title,
                file = file,
                charCount = file.readText().length,
                sourceUrl = chapter.url,
            )
        }
        if (appendTargetBookId != null) {
            container.bookRepository.appendDownloadedWebChapters(
                bookId = appendTargetBookId,
                chapters = importedChapters,
            )
        } else {
            container.bookRepository.importDownloadedWebBook(
                id = taskId,
                title = task.title,
                author = task.author,
                sourceUrl = task.sourceUrl,
                rootDirectory = rootDirectory,
                chapters = importedChapters,
            )
        }
        dao.updateDownloadTask(
            task.copy(
                status = "COMPLETED",
                completedChapters = finished.size,
                failedChapters = 0,
                updatedAt = System.currentTimeMillis(),
                importedBookId = appendTargetBookId ?: taskId,
                error = null,
            ),
        )
        return Result.success()
    }

    companion object {
        const val TASK_ID = "task_id"
        private const val NOTIFICATION_CHANNEL_ID = "web_book_downloads"
        private const val NOTIFICATION_CHANNEL_NAME = "Novel downloads"
        private const val MAX_CHAPTERS_PER_BATCH = 8
        private const val MAX_BATCH_DURATION_MS = 7 * 60 * 1_000L
        private const val CHAPTER_REQUEST_DELAY_MS = 400L
        private val TERMINAL_STOP_STATUSES = setOf("PAUSED", "CANCELED")
        private const val LOG_TAG = "WebDownloadWorker"
    }

    private suspend fun updateForeground(task: com.lightreader.app.core.data.DownloadTaskEntity, completed: Int) {
        try {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val notification = Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(task.title.take(80))
                .setContentText(applicationContext.getString(R.string.notification_download_progress, completed, task.totalChapters))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(task.totalChapters.coerceAtLeast(1), completed.coerceAtMost(task.totalChapters), false)
                .addAction(
                    Notification.Action.Builder(
                        null,
                        applicationContext.getString(R.string.action_cancel),
                        WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
                    ).build(),
                )
                .build()
            val notificationId = id.hashCode() and Int.MAX_VALUE
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(notificationId, notification)
            }
            setForeground(foregroundInfo)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A notification denial must not corrupt a resumable local download task.
            Log.w(LOG_TAG, "Could not show web download notification", error)
        }
    }
}
