package com.lightreader.app.core.web

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.model.BookFormat
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
        task = task.copy(status = "DOWNLOADING", error = null)
        dao.updateDownloadTask(task)
        val plan = ExtractionPlan(
            contentSelector = task.contentSelector,
            removeSelectors = container.json.decodeFromString(task.removeSelectorsJson),
        )
        val directory = File(applicationContext.filesDir, "downloads/$taskId/chapters").apply { mkdirs() }
        val chapters = dao.downloadChapters(taskId)
        var completed = chapters.count { it.status == "COMPLETED" }

        for (chapter in chapters) {
            if (isStopped || dao.downloadTask(taskId)?.status == "PAUSED") return Result.success()
            if (chapter.status == "COMPLETED" && chapter.contentPath?.let(::File)?.exists() == true) continue
            try {
                val text = container.webSourceParser.chapterText(chapter.url, plan)
                val file = File(directory, "%05d.txt".format(chapter.orderIndex))
                file.writeText(text)
                dao.updateDownloadChapter(chapter.copy(status = "COMPLETED", contentPath = file.absolutePath, error = null))
                completed++
                task = task.copy(completedChapters = completed, status = "DOWNLOADING")
                dao.updateDownloadTask(task)
                delay(800)
            } catch (error: Throwable) {
                dao.updateDownloadChapter(chapter.copy(status = "FAILED", error = error.message?.take(240)))
                dao.updateDownloadTask(task.copy(status = "FAILED", error = "${chapter.title}：${error.message}".take(300)))
                return if (runAttemptCount < 3) Result.retry() else Result.success()
            }
        }

        val finished = dao.downloadChapters(taskId)
        if (finished.any { it.status != "COMPLETED" || it.contentPath == null }) {
            dao.updateDownloadTask(task.copy(status = "FAILED", error = "部分章节下载失败，可点击重试"))
            return Result.success()
        }
        if (dao.book(taskId) == null) {
            val chapterRows = finished.map { chapter ->
                val file = File(requireNotNull(chapter.contentPath))
                ChapterEntity(
                    bookId = taskId,
                    orderIndex = chapter.orderIndex,
                    title = chapter.title,
                    contentPath = file.absolutePath,
                    charCount = file.readText().length,
                )
            }
            val now = System.currentTimeMillis()
            val book = BookEntity(
                id = taskId,
                title = task.title,
                author = null,
                format = BookFormat.WEB.name,
                rootPath = directory.parentFile!!.absolutePath,
                addedAt = now,
                lastReadAt = null,
                totalChars = chapterRows.sumOf { it.charCount.toLong() },
                chapterCount = chapterRows.size,
                sourceUrl = task.sourceUrl,
            )
            dao.insertBookWithChapters(book, chapterRows)
        }
        dao.updateDownloadTask(task.copy(status = "COMPLETED", completedChapters = chapters.size, error = null))
        return Result.success()
    }

    companion object { const val TASK_ID = "task_id" }
}
