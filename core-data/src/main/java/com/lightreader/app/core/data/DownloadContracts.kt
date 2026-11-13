package com.lightreader.app.core.data

import com.lightreader.app.core.model.WebBookPreview

/** A preview provider implemented by site adapters outside the persistence module. */
interface WebBookPreviewSource {
    suspend fun preview(url: String): WebBookPreview
}

/** Work scheduling boundary: Room state never depends on a concrete WorkManager worker. */
interface DownloadTaskScheduler {
    fun enqueue(taskId: String, generation: Long = 0, append: Boolean = false, initialDelayMillis: Long = 0, retryAttempt: Int = 0)
    suspend fun cancel(taskId: String)
    suspend fun hasUnfinishedWork(taskId: String, generation: Long? = null, owner: String? = null): Boolean = false
}

internal object NoopDownloadTaskScheduler : DownloadTaskScheduler {
    override fun enqueue(taskId: String, generation: Long, append: Boolean, initialDelayMillis: Long, retryAttempt: Int) = Unit
    override suspend fun cancel(taskId: String) = Unit
}
