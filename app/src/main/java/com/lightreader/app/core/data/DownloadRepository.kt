package com.lightreader.app.core.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.web.WebDownloadWorker
import com.lightreader.app.core.web.WebSourceParser
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    private val dao: ReaderDao,
    private val json: Json,
    private val webSourceParser: WebSourceParser,
    private val enqueueWork: Boolean = true,
) {
    val tasks: Flow<List<DownloadTaskEntity>> = dao.observeDownloadTasks()

    suspend fun start(preview: WebBookPreview): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertDownloadTask(
            DownloadTaskEntity(
                id = id,
                title = preview.title,
                author = preview.author,
                description = preview.description,
                sourceUrl = preview.sourceUrl,
                status = "QUEUED",
                totalChapters = preview.chapters.size,
                completedChapters = 0,
                failedChapters = 0,
                contentSelector = preview.extractionPlan.contentSelector,
                removeSelectorsJson = json.encodeToString(preview.extractionPlan.removeSelectors),
                createdAt = now,
                updatedAt = now,
                importedBookId = null,
                error = null,
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

    suspend fun refreshBook(bookId: String): Int {
        val book = dao.book(bookId) ?: error("Book was not found.")
        require(BookFormat.valueOf(book.format) == BookFormat.WEB && !book.sourceUrl.isNullOrBlank()) {
            "Only downloaded web books can be refreshed."
        }
        val existing = dao.chapters(bookId)
        val existingUrls = existing.mapNotNull { it.sourceUrl?.normalizeUrlKey() }.toSet()
        val existingTitles = existing.map { it.title.normalizeTitleKey() }.toSet()
        val preview = webSourceParser.preview(book.sourceUrl)
        val newChapters = preview.chapters
            .filter { chapter ->
                val urlKey = chapter.url.normalizeUrlKey()
                val titleKey = chapter.title.normalizeTitleKey()
                urlKey !in existingUrls && titleKey !in existingTitles
            }
        if (newChapters.isEmpty()) return 0

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insertDownloadTask(
            DownloadTaskEntity(
                id = id,
                title = book.title,
                author = book.author,
                description = preview.description,
                sourceUrl = book.sourceUrl,
                status = "QUEUED",
                totalChapters = newChapters.size,
                completedChapters = 0,
                failedChapters = 0,
                contentSelector = preview.extractionPlan.contentSelector,
                removeSelectorsJson = json.encodeToString(preview.extractionPlan.removeSelectors),
                createdAt = now,
                updatedAt = now,
                importedBookId = bookId,
                error = null,
            ),
        )
        dao.insertDownloadChapters(newChapters.mapIndexed { index, chapter ->
            DownloadChapterEntity(
                id = "$id:$index",
                taskId = id,
                orderIndex = existing.size + index,
                title = chapter.title,
                url = chapter.url,
                status = "PENDING",
                contentPath = null,
                error = null,
            )
        })
        enqueue(id)
        return newChapters.size
    }

    suspend fun pause(id: String) {
        val task = dao.downloadTask(id) ?: return
        dao.updateDownloadTask(task.copy(status = "PAUSED", updatedAt = System.currentTimeMillis(), error = null))
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    suspend fun resume(id: String) {
        val task = dao.downloadTask(id) ?: return
        dao.resetFailedDownloadChapters(id)
        dao.updateDownloadTask(task.copy(status = "QUEUED", failedChapters = 0, updatedAt = System.currentTimeMillis(), error = null))
        enqueue(id)
    }

    suspend fun cancel(id: String) {
        val task = dao.downloadTask(id) ?: return
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        dao.updateDownloadTask(
            task.copy(
                status = "CANCELED",
                updatedAt = System.currentTimeMillis(),
                error = null,
            ),
        )
    }

    suspend fun delete(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        val importedBookExists = dao.book(id) != null
        dao.deleteDownloadTask(id)
        if (!importedBookExists) {
            java.io.File(context.filesDir, "downloads/$id").deleteRecursively()
        }
    }

    private fun enqueue(id: String) {
        if (!enqueueWork) return
        val request = OneTimeWorkRequestBuilder<WebDownloadWorker>()
            .setInputData(Data.Builder().putString(WebDownloadWorker.TASK_ID, id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "web-book-$id"

    private fun String.normalizeUrlKey(): String =
        trim().substringBefore('#').removeSuffix("/").lowercase()

    private fun String.normalizeTitleKey(): String =
        trim().replace(Regex("""\s+"""), "")
}
