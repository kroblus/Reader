package com.lightreader.app.core.web

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.formats.ImportedChapter
import com.lightreader.app.core.model.ExtractionPlan
import kotlinx.coroutines.delay
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

        for (chapter in chapters) {
            if (isStopped || dao.downloadTask(taskId)?.status in TERMINAL_STOP_STATUSES) return Result.success()
            if (chapter.status == "COMPLETED" && chapter.contentPath?.let(::File)?.exists() == true) continue
            try {
                dao.updateDownloadChapter(chapter.copy(status = "DOWNLOADING", error = null))
                val text = container.webSourceParser.chapterText(chapter.url, plan, chapter.title)
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
                delay(800)
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
        private val TERMINAL_STOP_STATUSES = setOf("PAUSED", "CANCELED")
    }
}
