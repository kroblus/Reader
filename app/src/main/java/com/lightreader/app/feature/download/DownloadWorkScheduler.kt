package com.lightreader.app.feature.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lightreader.app.core.data.DownloadTaskScheduler
import com.lightreader.app.core.web.WebDownloadWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Schedules small resumable batches. A generation-specific chain cannot be held by an old cancel. */
class DownloadWorkScheduler(private val context: Context) : DownloadTaskScheduler {
    override fun enqueue(taskId: String, generation: Long, append: Boolean, initialDelayMillis: Long, retryAttempt: Int) {
        enqueue(context, taskId, generation, append, initialDelayMillis, retryAttempt)
    }

    override suspend fun cancel(taskId: String) { cancel(context, taskId) }

    override suspend fun hasUnfinishedWork(taskId: String, generation: Long?, owner: String?): Boolean = withContext(Dispatchers.IO) {
        val manager = WorkManager.getInstance(context)
        if (owner != null) {
            val ownerId = runCatching { java.util.UUID.fromString(owner) }.getOrNull() ?: return@withContext false
            return@withContext manager.getWorkInfoById(ownerId).get()?.state?.isFinished == false
        }
        val work = if (generation != null) manager.getWorkInfosForUniqueWork("${workTag(taskId)}-$generation")
            else manager.getWorkInfosByTag(workTag(taskId))
        work.get().any { !it.state.isFinished }
    }

    companion object {
        fun enqueue(context: Context, taskId: String, generation: Long = 0, append: Boolean = false,
                    initialDelayMillis: Long = 0, retryAttempt: Int = 0) {
            val request = OneTimeWorkRequestBuilder<WebDownloadWorker>()
                .setInputData(Data.Builder().putString(WebDownloadWorker.TASK_ID, taskId)
                    .putLong(WebDownloadWorker.GENERATION, generation)
                    .putInt(WebDownloadWorker.RETRY_ATTEMPT, retryAttempt).build())
                .addTag(workTag(taskId))
                .setInitialDelay(initialDelayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("${workTag(taskId)}-$generation",
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request)
        }

        suspend fun cancel(context: Context, taskId: String) = withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).cancelAllWorkByTag(workTag(taskId)).result.get()
            // Also stops pre-schema-9 work whose unique name had no generation/tag.
            WorkManager.getInstance(context).cancelUniqueWork(workTag(taskId)).result.get()
        }

        private fun workTag(id: String) = "web-book-$id"
    }
}
