package com.lightreader.app.core.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lightreader.app.core.web.WebDownloadWorker
import java.util.concurrent.TimeUnit

/** Schedules small, resumable download batches instead of one book-long worker. */
object DownloadWorkScheduler {
    fun enqueue(context: Context, taskId: String, append: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<WebDownloadWorker>()
            .setInputData(Data.Builder().putString(WebDownloadWorker.TASK_ID, taskId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        val manager = WorkManager.getInstance(context)
        if (append) {
            manager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        } else {
            manager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.KEEP, request)
        }
    }

    fun cancel(context: Context, taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
    }

    private fun workName(id: String) = "web-book-$id"
}
