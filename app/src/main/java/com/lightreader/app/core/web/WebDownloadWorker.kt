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
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.feature.download.DownloadWorkScheduler
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
        var generation = inputData.getLong(GENERATION, 0)
        val container = (applicationContext as ReaderApplication).container
        val dao = container.database.readerDao()
        if (dao.claimDownloadTask(taskId, generation, System.currentTimeMillis(), id.toString()) == 0) {
            // WorkManager retries the same request after a process/constraint interruption. Its
            // previous owner left DOWNLOADING behind, so fence that execution before resuming.
            if (runAttemptCount == 0 || dao.recoverInterruptedDownload(taskId, id.toString(), System.currentTimeMillis()) == 0) {
                return Result.success()
            }
            val recovered = dao.downloadTask(taskId) ?: return Result.success()
            if (recovered.executionOwner != id.toString() || recovered.status != "DOWNLOADING") return Result.success()
            generation = recovered.executionGeneration
        }
        var task = dao.downloadTask(taskId) ?: return Result.success()
        fun currentState(row: com.lightreader.app.core.data.DownloadTaskEntity?) =
            !isStopped && row?.executionGeneration == generation && row.executionOwner == id.toString() && row.status == "DOWNLOADING"
        if (!currentState(task)) return Result.success()
        val directory = File(applicationContext.filesDir, "downloads/$taskId/chapters").apply { mkdirs() }
        var completed = dao.downloadChapters(taskId).count { it.status == "COMPLETED" && it.contentPath?.let(::File)?.exists() == true }
        var activeChapter: com.lightreader.app.core.data.DownloadChapterEntity? = null
        try {
            val plan = ExtractionPlan(contentSelector = task.contentSelector,
                removeSelectors = container.json.decodeFromString(task.removeSelectorsJson))
            updateForeground(task, completed)
            var processedInBatch = 0
            val batchStartedAt = System.currentTimeMillis()
            for (chapter in dao.downloadChapters(taskId)) {
                if (!currentState(dao.downloadTask(taskId))) return Result.success()
                if (chapter.status == "COMPLETED" && chapter.contentPath?.let(::File)?.exists() == true) continue
                if (processedInBatch >= MAX_CHAPTERS_PER_BATCH || System.currentTimeMillis() - batchStartedAt >= MAX_BATCH_DURATION_MS) {
                    if (dao.yieldDownloadTask(taskId, generation, completed, 0, null, System.currentTimeMillis()) > 0) {
                        DownloadWorkScheduler.enqueue(applicationContext, taskId, generation + 1, append = true)
                    }
                    return Result.success()
                }
                activeChapter = chapter
                if (dao.updateDownloadChapterExecution(chapter.id, taskId, generation, "DOWNLOADING", chapter.contentPath, null) == 0) return Result.success()
                val text = container.sourceRegistry.chapterText(task.sourceId, task.sourceVersion, chapter.url, plan, chapter.title)
                if (!currentState(dao.downloadTask(taskId))) return Result.success()
                // Immutable per-execution file: a late old worker cannot overwrite a resumed result.
                val file = File(directory, "${chapter.orderIndex}-$generation.txt")
                val temporary = File(directory, "${chapter.orderIndex}-$generation-${id}.tmp")
                try {
                    java.io.FileOutputStream(temporary).use { output ->
                        output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync()
                    }
                    if (!currentState(dao.downloadTask(taskId))) return Result.success()
                    check(temporary.renameTo(file)) { "Cannot save downloaded chapter." }
                    if (dao.updateDownloadChapterExecution(chapter.id, taskId, generation, "COMPLETED", file.absolutePath, null) == 0) {
                        file.delete()
                        return Result.success()
                    }
                } finally { temporary.delete() }
                completed++
                if (dao.updateDownloadExecution(taskId, generation, "DOWNLOADING", completed, 0, null, System.currentTimeMillis()) == 0) return Result.success()
                updateForeground(task, completed)
                processedInBatch++
                delay(CHAPTER_REQUEST_DELAY_MS)
            }
            activeChapter = null
            if (!currentState(dao.downloadTask(taskId))) return Result.success()
            container.bookRepository.commitDownloadedTask(taskId, generation)
            return Result.success()
        } catch (error: CancellationException) {
            // Stop/restart is reconciled from persistent state. User pause/cancel already advanced generation.
            throw error
        } catch (error: Exception) {
            if (!currentState(dao.downloadTask(taskId))) return Result.success()
            activeChapter?.let { dao.updateDownloadChapterExecution(it.id, taskId, generation, "FAILED", it.contentPath, error.message?.take(240)) }
            val failed = dao.downloadChapters(taskId).count { it.status == "FAILED" }
            val webError = error as? WebImportException
            val retryable = webError?.retryable ?: (error is java.io.IOException)
            val attempt = maxOf(inputData.getInt(RETRY_ATTEMPT, 0), task.automaticRetryCount)
            val retry = retryable && attempt < MAX_RETRIES
            val waitMillis = maxOf(15_000L * (1L shl attempt.coerceIn(0, MAX_RETRIES)), webError?.retryAfterMillis ?: 0L)
            val now = System.currentTimeMillis()
            val notBefore = if (waitMillis > Long.MAX_VALUE - now) Long.MAX_VALUE else now + waitMillis
            val published = if (retry) {
                dao.yieldDownloadTask(taskId, generation, completed, failed, error.message?.take(300), now, notBefore, attempt + 1)
            } else {
                dao.updateDownloadExecution(taskId, generation, "FAILED", completed, failed, error.message?.take(300), System.currentTimeMillis())
            }
            if (published > 0 && retry) {
                DownloadWorkScheduler.enqueue(applicationContext, taskId, generation + 1, append = true,
                    initialDelayMillis = waitMillis, retryAttempt = attempt + 1)
            }
            return Result.success()
        }
    }

    companion object {
        const val TASK_ID = "task_id"
        const val GENERATION = "execution_generation"
        const val RETRY_ATTEMPT = "retry_attempt"
        private const val MAX_RETRIES = 3
        private const val NOTIFICATION_CHANNEL_ID = "web_book_downloads"
        private const val NOTIFICATION_CHANNEL_NAME = "Novel downloads"
        const val MAX_CHAPTERS_PER_BATCH = 8
        private const val MAX_BATCH_DURATION_MS = 7 * 60 * 1_000L
        private const val CHAPTER_REQUEST_DELAY_MS = 400L
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
                        applicationContext.getString(R.string.action_pause),
                        com.lightreader.app.feature.download.DownloadActionReceiver.pauseIntent(applicationContext, task.id),
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
