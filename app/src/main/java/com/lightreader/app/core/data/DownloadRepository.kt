package com.lightreader.app.core.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.web.WebDownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    private val dao: ReaderDao,
    private val json: Json,
) {
    val tasks: Flow<List<DownloadTaskEntity>> = dao.observeDownloadTasks()

    suspend fun start(preview: WebBookPreview): String {
        val id = UUID.randomUUID().toString()
        dao.insertDownloadTask(
            DownloadTaskEntity(
                id, preview.title, preview.sourceUrl, "QUEUED", preview.chapters.size, 0,
                preview.extractionPlan.contentSelector,
                json.encodeToString(preview.extractionPlan.removeSelectors),
                System.currentTimeMillis(), null,
            ),
        )
        dao.insertDownloadChapters(preview.chapters.mapIndexed { index, chapter ->
            DownloadChapterEntity(
                id = "$id:$index",
                taskId = id,
                orderIndex = index,
                title = chapter.title,
                url = chapter.url,
                status = "PENDING",
                contentPath = null,
                error = null,
            )
        })
        enqueue(id)
        return id
    }

    suspend fun pause(id: String) {
        val task = dao.downloadTask(id) ?: return
        dao.updateDownloadTask(task.copy(status = "PAUSED", error = null))
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    suspend fun resume(id: String) {
        val task = dao.downloadTask(id) ?: return
        dao.resetFailedDownloadChapters(id)
        dao.updateDownloadTask(task.copy(status = "QUEUED", error = null))
        enqueue(id)
    }

    private fun enqueue(id: String) {
        val request = OneTimeWorkRequestBuilder<WebDownloadWorker>()
            .setInputData(Data.Builder().putString(WebDownloadWorker.TASK_ID, id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "web-book-$id"
}
