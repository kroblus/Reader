package com.lightreader.app.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lightreader.app.core.formats.BookFormatPlugin
import com.lightreader.app.core.formats.EpubBookFormatPlugin
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.ReadingProgress
import com.lightreader.app.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val dao: ReaderDao,
    private val plugins: List<BookFormatPlugin> = listOf(TxtBookFormatPlugin(), EpubBookFormatPlugin()),
) {
    private val searchIndexMutex = Mutex()
    val books: Flow<List<Book>> = dao.observeBooks().map { rows -> rows.map(BookEntity::toModel) }

    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri)
        val plugin = plugins.firstOrNull { it.supports(name, mime) }
            ?: error("仅支持 TXT 和无 DRM EPUB")
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "books/$id").apply { mkdirs() }
        try {
            val result = plugin.import(context, uri, name, directory)
            val now = System.currentTimeMillis()
            val book = BookEntity(
                id = id,
                title = result.title,
                author = result.author,
                format = result.format.name,
                rootPath = directory.absolutePath,
                addedAt = now,
                lastReadAt = null,
                totalChars = result.chapters.sumOf { it.charCount.toLong() },
                chapterCount = result.chapters.size,
                sourceUrl = null,
            )
            val chapterRows = result.chapters.mapIndexed { index, chapter ->
                ChapterEntity(
                    bookId = id,
                    orderIndex = index,
                    title = chapter.title,
                    contentPath = chapter.file.absolutePath,
                    charCount = chapter.charCount,
                )
            }
            dao.insertBookWithChapters(book, chapterRows)
            book.toModel()
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    suspend fun book(id: String): Book? = dao.book(id)?.toModel()
    suspend fun chapters(bookId: String): List<Chapter> = dao.chapters(bookId).map(ChapterEntity::toModel)
    suspend fun chapter(id: Long): Chapter? = dao.chapter(id)?.toModel()

    suspend fun readChapter(chapter: Chapter): String = withContext(Dispatchers.IO) {
        File(chapter.contentPath).readText()
    }

    suspend fun progress(bookId: String): ReadingProgress? = dao.progress(bookId)?.let {
        ReadingProgress(
            it.bookId, it.chapterId, it.charOffset, it.chapterIndex,
            it.pageIndex, it.chapterTitle, it.styleHash, it.updatedAt,
        )
    }

    suspend fun saveProgress(
        bookId: String,
        chapterId: Long,
        charOffset: Int,
        chapterIndex: Int,
        pageIndex: Int,
        chapterTitle: String,
        styleHash: Int,
    ) {
        val now = System.currentTimeMillis()
        dao.saveProgress(
            ReadingProgressEntity(
                bookId, chapterId, charOffset, chapterIndex, pageIndex,
                chapterTitle, styleHash, now,
            ),
        )
        dao.book(bookId)?.let { dao.updateBook(it.copy(lastReadAt = now)) }
    }

    fun bookmarks(bookId: String): Flow<List<Bookmark>> = dao.observeBookmarks(bookId).map { rows ->
        rows.map { Bookmark(it.id, it.bookId, it.chapterId, it.charOffset, it.excerpt, it.createdAt) }
    }

    suspend fun addBookmark(bookId: String, chapterId: Long, offset: Int, excerpt: String) {
        dao.insertBookmark(
            BookmarkEntity(UUID.randomUUID().toString(), bookId, chapterId, offset, excerpt.take(120), System.currentTimeMillis()),
        )
    }

    suspend fun deleteBookmark(id: String) = dao.deleteBookmark(id)

    suspend fun search(bookId: String, query: String): List<SearchResult> {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        ensureSearchIndex(bookId)
        val fts = tokens.joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
        val nextSearchOffset = mutableMapOf<Long, Int>()
        val chapterText = mutableMapOf<Long, String>()
        val primaryToken = tokens.first()
        val indexedResults = dao.search(bookId, fts).map {
            val chapterId = it.chapterId.toLong()
            val text = chapterText.getOrPut(chapterId) {
                dao.chapter(chapterId)?.let { chapter -> File(chapter.contentPath).readText() }.orEmpty()
            }
            val fromIndex = nextSearchOffset[chapterId] ?: 0
            val matchIndex = text.indexOf(primaryToken, fromIndex, ignoreCase = true)
                .takeIf { index -> index >= 0 }
                ?: text.indexOf(primaryToken, ignoreCase = true).coerceAtLeast(0)
            nextSearchOffset[chapterId] = (matchIndex + primaryToken.length).coerceAtMost(text.length)
            SearchResult(chapterId, it.chapterTitle, it.excerpt, matchIndex)
        }
        return indexedResults.ifEmpty { fallbackSearch(bookId, query.trim()) }
    }

    private suspend fun fallbackSearch(bookId: String, query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = ArrayList<SearchResult>()
        dao.chapters(bookId).forEach { chapter ->
            if (results.size >= 100) return@forEach
            val text = File(chapter.contentPath).readText()
            var fromIndex = 0
            while (fromIndex < text.length && results.size < 100) {
                val match = text.indexOf(query, fromIndex, ignoreCase = true)
                if (match < 0) break
                val excerptStart = (match - 28).coerceAtLeast(0)
                val excerptEnd = (match + query.length + 42).coerceAtMost(text.length)
                val excerpt = buildString {
                    if (excerptStart > 0) append('…')
                    append(text.substring(excerptStart, match))
                    append("<b>").append(text.substring(match, match + query.length)).append("</b>")
                    append(text.substring(match + query.length, excerptEnd))
                    if (excerptEnd < text.length) append('…')
                }
                results += SearchResult(chapter.id, chapter.title, excerpt, match)
                fromIndex = (match + query.length).coerceAtLeast(match + 1)
            }
        }
        results
    }

    suspend fun deleteBook(id: String) = withContext(Dispatchers.IO) {
        val root = dao.book(id)?.rootPath
        dao.deleteSearchChunks(id)
        dao.deleteBookRow(id)
        dao.deleteDownloadTask(id)
        root?.let { File(it).deleteRecursively() }
    }

    private suspend fun ensureSearchIndex(bookId: String) = searchIndexMutex.withLock {
        withContext(Dispatchers.IO) {
            val book = dao.book(bookId) ?: error("书籍不存在")
            if (dao.indexedCharacterCount(bookId) == book.totalChars) return@withContext
            dao.deleteSearchChunks(bookId)
            try {
                dao.chapters(bookId).forEach { chapter ->
                    val rows = File(chapter.contentPath).readText().chunked(4_000).map { chunk ->
                        SearchChunkEntity(bookId = bookId, chapterId = chapter.id.toString(), content = chunk)
                    }
                    rows.chunked(50).forEach { dao.insertSearchChunks(it) }
                }
                check(dao.indexedCharacterCount(bookId) == book.totalChars) { "全文索引不完整，请重试" }
            } catch (error: Throwable) {
                dao.deleteSearchChunks(bookId)
                throw error
            }
        }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment ?: "未命名小说.txt"
    }
}

private fun BookEntity.toModel() = Book(
    id, title, author, BookFormat.valueOf(format), chapterCount, totalChars, addedAt, lastReadAt, sourceUrl,
)

private fun ChapterEntity.toModel() = Chapter(id, bookId, orderIndex, title, contentPath, charCount)
