package com.lightreader.app.core.data

import android.content.Context
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.WebBookPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class DownloadRepository(
    private val context: Context,
    private val dao: ReaderDao,
    private val json: Json,
    private val webSourceParser: WebBookPreviewSource,
    private val scheduler: DownloadTaskScheduler = NoopDownloadTaskScheduler,
    private val enqueueWork: Boolean = true,
) {
    private val actionMutex = Mutex()
    val tasks: Flow<List<DownloadTaskEntity>> = dao.observeDownloadTasks()

    suspend fun start(preview: WebBookPreview): String {
        val task = create(preview, null)
        if (task.status in listOf("QUEUED", "DOWNLOADING")) enqueue(task)
        return task.id
    }

    suspend fun refreshBook(bookId: String): Int {
        dao.tasksForBook(bookId).firstOrNull { it.status !in listOf("COMPLETED", "CANCELED") }?.let {
            if (it.status == "QUEUED") enqueue(it)
            return it.totalChapters
        }
        val book = dao.book(bookId) ?: error("Book was not found.")
        require(BookFormat.valueOf(book.format) == BookFormat.WEB && !book.sourceUrl.isNullOrBlank()) {
            "Only downloaded web books can be refreshed."
        }
        val preview = webSourceParser.preview(book.sourceUrl)
        val existingKeys = dao.chapters(bookId).mapTo(mutableSetOf()) { WebChapterIdentity.key(it.sourceUrl, it.title) }
        val newChapters = preview.chapters.filter { existingKeys.add(WebChapterIdentity.key(it.url, it.title)) }
        if (newChapters.isEmpty()) return 0
        val task = create(preview.copy(title = book.title, author = book.author, chapters = newChapters), bookId)
        if (task.status == "QUEUED") enqueue(task)
        return task.totalChapters
    }

    private suspend fun create(preview: WebBookPreview, appendBookId: String?): DownloadTaskEntity {
        require(preview.chapters.isNotEmpty()) { "No chapters to download." }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val chapters = preview.chapters.distinctBy { WebChapterIdentity.key(it.url, it.title) }
        val task = DownloadTaskEntity(
            id = id, title = preview.title, author = preview.author, description = preview.description,
            sourceUrl = preview.sourceUrl, status = "QUEUED", totalChapters = chapters.size,
            completedChapters = 0, failedChapters = 0, contentSelector = preview.extractionPlan.contentSelector,
            removeSelectorsJson = json.encodeToString(preview.extractionPlan.removeSelectors),
            createdAt = now, updatedAt = now, importedBookId = appendBookId, error = null,
            sourceId = preview.sourceId, sourceVersion = preview.sourceVersion,
        )
        return dao.createDownloadTask(task, chapters.mapIndexed { index, chapter ->
            DownloadChapterEntity("$id:$index", id, index, chapter.title, chapter.url, "PENDING", null, null)
        })
    }

    suspend fun pause(id: String) = actionMutex.withLock {
        if (dao.transitionDownloadTask(id, "PAUSED", listOf("QUEUED", "DOWNLOADING", "FAILED"), System.currentTimeMillis()) > 0) {
            scheduler.cancel(id)
        }
    }

    suspend fun resume(id: String) = actionMutex.withLock {
        val task = dao.resumeDownloadTask(id, System.currentTimeMillis()) ?: return@withLock
        if (task.status == "QUEUED") enqueue(task)
    }

    suspend fun cancel(id: String) = actionMutex.withLock {
        if (dao.transitionDownloadTask(id, "CANCELED", listOf("QUEUED", "DOWNLOADING", "PAUSED", "FAILED"), System.currentTimeMillis()) > 0) {
            scheduler.cancel(id)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        actionMutex.withLock {
            val task = dao.downloadTask(id) ?: return@withLock
            // Delete the persistent authority first. A late Worker can no longer publish content.
            dao.deleteDownloadTask(id)
            scheduler.cancel(id)
            cleanupTaskFiles(task)
        }
    }

    /** Repairs the database-commit / WorkManager-enqueue window and interrupted executions. */
    suspend fun reconcile() = actionMutex.withLock {
        if (!enqueueWork) return@withLock
        for (task in dao.downloadTasks()) {
            if (task.status in listOf("PAUSED", "CANCELED")) {
                scheduler.cancel(task.id)
                continue
            }
            if (task.status !in listOf("QUEUED", "DOWNLOADING")) continue
            if (scheduler.hasUnfinishedWork(task.id, task.executionGeneration,
                    task.executionOwner.takeIf { task.status == "DOWNLOADING" })) continue
            if (task.status == "DOWNLOADING") {
                dao.transitionDownloadTask(task.id, "QUEUED", listOf("DOWNLOADING"), System.currentTimeMillis())
            }
            dao.downloadTask(task.id)?.takeIf { it.status == "QUEUED" }?.let { enqueue(it) }
        }
    }

    private fun enqueue(task: DownloadTaskEntity) {
        if (enqueueWork) scheduler.enqueue(task.id, task.executionGeneration,
            initialDelayMillis = (task.notBeforeMillis - System.currentTimeMillis()).coerceAtLeast(0),
            retryAttempt = task.automaticRetryCount)
    }

    private suspend fun cleanupTaskFiles(task: DownloadTaskEntity) {
        val referenced = dao.referencedChapterPaths().mapTo(hashSetOf()) { File(it).canonicalPath }
        val stage = File(context.filesDir, "downloads/${task.id}")
        // Includes old task storage layouts: referenced content is never removed.
        if (stage.isDirectory) stage.walkBottomUp().forEach { file ->
            if (file.isFile && file.canonicalPath !in referenced) file.delete()
            if (file.isDirectory && file.listFiles()?.isEmpty() == true) file.delete()
        }
        val target = task.importedBookId ?: task.id
        val root = dao.book(target)?.rootPath?.let(::File) ?: File(context.filesDir, "books/$target")
        File(root, "chapters").listFiles()?.filter { it.name.startsWith("web-${task.id}-") }?.forEach {
            if (it.canonicalPath !in referenced) it.delete()
        }
    }
}
