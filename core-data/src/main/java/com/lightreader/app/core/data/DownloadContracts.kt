package com.lightreader.app.core.data

import com.lightreader.app.core.model.WebBookPreview

/** A preview provider implemented by site adapters outside the persistence module. */
interface WebBookPreviewSource {
    suspend fun preview(url: String): WebBookPreview
}

/** Work scheduling boundary: Room state never depends on a concrete WorkManager worker. */
interface DownloadTaskScheduler {
    fun enqueue(taskId: String, append: Boolean = false)
    fun cancel(taskId: String)
}

internal object NoopDownloadTaskScheduler : DownloadTaskScheduler {
    override fun enqueue(taskId: String, append: Boolean) = Unit
    override fun cancel(taskId: String) = Unit
}
