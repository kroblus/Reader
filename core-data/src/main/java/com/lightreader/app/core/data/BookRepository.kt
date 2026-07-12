package com.lightreader.app.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lightreader.app.core.formats.BookImportException
import com.lightreader.app.core.formats.BookImportFailure
import com.lightreader.app.core.formats.BookImportOptions
import com.lightreader.app.core.formats.BookFormatPlugin
import com.lightreader.app.core.formats.EpubBookFormatPlugin
import com.lightreader.app.core.formats.ImportedChapter
import com.lightreader.app.core.formats.ImportProgress
import com.lightreader.app.core.formats.ImportProgressCallback
import com.lightreader.app.core.formats.ImportStage
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.ReadingProgress
import com.lightreader.app.core.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import com.lightreader.app.core.reader.UnicodeTextBoundary

data class BookImportCandidate(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val fingerprint: String,
    val existingBook: Book?,
)

class BookRepository(
    private val context: Context,
    private val dao: ReaderDao,
    private val plugins: List<BookFormatPlugin> = listOf(TxtBookFormatPlugin(), EpubBookFormatPlugin()),
) {
    private val searchIndexMutex = Mutex()
    val books: Flow<List<Book>> = dao.observeBooks().map { rows -> rows.map(BookEntity::toModel) }
    val shelfProgress: Flow<List<ShelfBookProgress>> = dao.observeShelfProgress().map { rows ->
        rows.map {
            ShelfBookProgress(
                bookId = it.bookId,
                chapterTitle = it.chapterTitle,
                chapterIndex = it.chapterIndex,
                charOffset = it.charOffset,
                updatedAt = it.updatedAt,
                readChars = it.readChars,
            )
        }
    }

    suspend fun inspectImport(
        uri: Uri,
        onProgress: ImportProgressCallback = {},
    ): BookImportCandidate = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri)
        if (plugins.none { it.supports(name, mime) }) {
            throw BookImportException(BookImportFailure.UNSUPPORTED_FORMAT, "仅支持 TXT 和无 DRM EPUB")
        }
        val fingerprint = sourceFingerprint(uri, onProgress)
        BookImportCandidate(
            uri = uri,
            displayName = name,
            mimeType = mime,
            fingerprint = fingerprint,
            existingBook = dao.bookByContentFingerprint(fingerprint)?.toModel(),
        )
    }

    suspend fun import(
        uri: Uri,
        options: BookImportOptions = BookImportOptions(),
        onProgress: ImportProgressCallback = {},
    ): Book {
        val candidate = inspectImport(uri, onProgress)
        if (candidate.existingBook != null) throw DuplicateBookImportException(candidate.existingBook)
        return import(candidate, options, onProgress)
    }

    suspend fun import(
        candidate: BookImportCandidate,
        options: BookImportOptions = BookImportOptions(),
        onProgress: ImportProgressCallback = {},
    ): Book = withContext(Dispatchers.IO) {
        val plugin = plugins.firstOrNull { it.supports(candidate.displayName, candidate.mimeType) }
            ?: throw BookImportException(BookImportFailure.UNSUPPORTED_FORMAT, "仅支持 TXT 和无 DRM EPUB")
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "books/$id").apply { mkdirs() }
        try {
            val result = plugin.import(context, candidate.uri, candidate.displayName, directory, options, onProgress)
            onProgress(ImportProgress(ImportStage.SAVING, .94f, result.chapters.size.toLong(), result.chapters.size.toLong()))
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
                contentFingerprint = candidate.fingerprint,
            )
            val chapterRows = result.chapters.mapIndexed { index, chapter ->
                ChapterEntity(
                    bookId = id,
                    orderIndex = index,
                    title = chapter.title,
                    contentPath = chapter.file.absolutePath,
                    charCount = chapter.charCount,
                    sourceUrl = chapter.sourceUrl,
                )
            }
            dao.insertBookWithChapters(book, chapterRows)
            onProgress(ImportProgress(ImportStage.SAVING, 1f, chapterRows.size.toLong(), chapterRows.size.toLong()))
            book.toModel()
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    suspend fun cleanupOrphanedBookDirectories() = withContext(Dispatchers.IO) {
        val referenced = dao.bookRootPaths().map { File(it).canonicalPath }.toSet()
        val booksRoot = File(context.filesDir, "books")
        booksRoot.listFiles()?.filter(File::isDirectory)?.forEach { directory ->
            if (directory.canonicalPath !in referenced) directory.deleteRecursively()
        }
    }

    suspend fun book(id: String): Book? = dao.book(id)?.toModel()
    suspend fun chapters(bookId: String): List<Chapter> = dao.chapters(bookId).map(ChapterEntity::toModel)
    suspend fun chapter(id: Long): Chapter? = dao.chapter(id)?.toModel()

    suspend fun updateBookMetadata(bookId: String, title: String, author: String?): Book = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotBlank()) { "Book title is required." }
        val normalizedAuthor = author?.trim()?.takeIf { it.isNotBlank() }
        dao.updateBookMetadata(bookId, normalizedTitle, normalizedAuthor)
        dao.book(bookId)?.toModel() ?: error("Book was not found.")
    }

    suspend fun importDownloadedWebBook(
        id: String,
        title: String,
        author: String?,
        sourceUrl: String,
        rootDirectory: File,
        chapters: List<ImportedChapter>,
    ): Book = withContext(Dispatchers.IO) {
        dao.book(id)?.let { return@withContext it.toModel() }
        require(chapters.isNotEmpty()) { "Downloaded book has no readable chapters" }
        val now = System.currentTimeMillis()
        val book = BookEntity(
            id = id,
            title = title.ifBlank { "Web book" },
            author = author?.takeIf { it.isNotBlank() },
            format = BookFormat.WEB.name,
            rootPath = rootDirectory.absolutePath,
            addedAt = now,
            lastReadAt = null,
            totalChars = chapters.sumOf { it.charCount.toLong() },
            chapterCount = chapters.size,
            sourceUrl = sourceUrl,
        )
        val chapterRows = chapters.mapIndexed { index, chapter ->
            ChapterEntity(
                bookId = id,
                orderIndex = index,
                title = chapter.title.ifBlank { "Chapter ${index + 1}" },
                contentPath = chapter.file.absolutePath,
                charCount = chapter.charCount,
                sourceUrl = chapter.sourceUrl,
            )
        }
        dao.insertBookWithChapters(book, chapterRows)
        book.toModel()
    }

    suspend fun appendDownloadedWebChapters(
        bookId: String,
        chapters: List<ImportedChapter>,
    ): Int = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) return@withContext 0
        val book = dao.book(bookId) ?: error("Book was not found.")
        require(BookFormat.valueOf(book.format) == BookFormat.WEB) { "Only web books can be refreshed." }
        val existing = dao.chapters(bookId)
        val existingSourceUrls = existing.mapNotNull { it.sourceUrl?.normalizeSourceUrl() }.toSet()
        val existingTitleKeys = existing.map { it.title.normalizeChapterTitle() }.toSet()
        // A worker can be retried after its files were committed but before its task row was marked
        // complete. Filtering here makes the final append idempotent instead of duplicating chapters.
        val newChapters = chapters.filter { chapter ->
            chapter.sourceUrl?.normalizeSourceUrl()?.let { it !in existingSourceUrls }
                ?: (chapter.title.normalizeChapterTitle() !in existingTitleKeys)
        }
        if (newChapters.isEmpty()) return@withContext 0
        val startIndex = existing.size
        val rows = newChapters.mapIndexed { index, chapter ->
            ChapterEntity(
                bookId = bookId,
                orderIndex = startIndex + index,
                title = chapter.title.ifBlank { "Chapter ${startIndex + index + 1}" },
                contentPath = chapter.file.absolutePath,
                charCount = chapter.charCount,
                sourceUrl = chapter.sourceUrl,
            )
        }
        dao.insertChapters(rows)
        dao.updateBook(
            book.copy(
                chapterCount = book.chapterCount + rows.size,
                totalChars = book.totalChars + newChapters.sumOf { it.charCount.toLong() },
            ),
        )
        dao.clearSearchIndex(bookId)
        rows.size
    }

    suspend fun readChapter(chapter: Chapter): String = withContext(Dispatchers.IO) {
        File(chapter.contentPath).readText()
    }

    private fun String.normalizeSourceUrl(): String =
        trim().substringBefore('#').removeSuffix("/").lowercase()

    private fun String.normalizeChapterTitle(): String = trim().replace(Regex("""\s+"""), "")

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
        val normalizedQuery = query.trim()
        val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        ensureSearchIndex(bookId)
        if (normalizedQuery.length > SEARCH_CHUNK_OVERLAP || normalizedQuery.any(::isCjkCharacter)) {
            return exactSearch(bookId, normalizedQuery)
        }
        val fts = tokens.joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
        val candidateChapterIds = dao.search(bookId, fts).mapTo(linkedSetOf()) { it.chapterId.toLong() }
        if (candidateChapterIds.isEmpty()) return exactSearch(bookId, normalizedQuery)
        val indexedResults = exactSearch(bookId, normalizedQuery, candidateChapterIds)
        return indexedResults.ifEmpty { exactSearch(bookId, normalizedQuery) }
    }

    private suspend fun exactSearch(
        bookId: String,
        query: String,
        candidateChapterIds: Set<Long>? = null,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = ArrayList<SearchResult>()
        dao.chapters(bookId).forEach { chapter ->
            if (results.size >= 100) return@forEach
            if (candidateChapterIds != null && chapter.id !in candidateChapterIds) return@forEach
            currentCoroutineContext().ensureActive()
            val text = File(chapter.contentPath).readText()
            var fromIndex = 0
            while (fromIndex < text.length && results.size < 100) {
                currentCoroutineContext().ensureActive()
                val match = text.indexOf(query, fromIndex, ignoreCase = true)
                if (match < 0) break
                val excerptStart = UnicodeTextBoundary.previousBoundary(text, (match - 28).coerceAtLeast(0))
                val excerptEnd = if (match + query.length + 42 >= text.length) {
                    text.length
                } else {
                    UnicodeTextBoundary.safeEnd(text, match + query.length, match + query.length + 42)
                }
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
        dao.clearSearchIndex(id)
        dao.deleteBookRow(id)
        dao.deleteDownloadTask(id)
        root?.let { File(it).deleteRecursively() }
    }

    private suspend fun ensureSearchIndex(bookId: String) = searchIndexMutex.withLock {
        withContext(Dispatchers.IO) {
            val book = dao.book(bookId) ?: error("书籍不存在")
            val existingState = dao.searchIndexState(bookId)
            if (existingState?.indexedUtf16Chars == book.totalChars && existingState.indexVersion == SEARCH_INDEX_VERSION) {
                return@withContext
            }
            try {
                val rows = ArrayList<SearchChunkEntity>()
                var indexedUtf16Chars = 0L
                dao.chapters(bookId).forEach { chapter ->
                    currentCoroutineContext().ensureActive()
                    val text = File(chapter.contentPath).readText()
                    indexedUtf16Chars += text.length
                    var start = 0
                    while (start < text.length) {
                        val end = UnicodeTextBoundary.safeEnd(text, start, (start + SEARCH_CHUNK_SIZE).coerceAtMost(text.length))
                        rows += SearchChunkEntity(
                            bookId = bookId,
                            chapterId = chapter.id.toString(),
                            chunkStart = start,
                            content = text.substring(start, end),
                        )
                        if (end == text.length) break
                        val overlappedStart = UnicodeTextBoundary.previousBoundary(
                            text,
                            (end - SEARCH_CHUNK_OVERLAP).coerceAtLeast(start + 1),
                            start,
                        )
                        start = overlappedStart.takeIf { it > start } ?: end
                    }
                }
                check(indexedUtf16Chars == book.totalChars) { "全文索引不完整，请重试" }
                dao.replaceSearchIndex(
                    bookId,
                    rows,
                    SearchIndexStateEntity(bookId, indexedUtf16Chars, SEARCH_INDEX_VERSION),
                )
            } catch (error: Throwable) {
                dao.clearSearchIndex(bookId)
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

    private fun sourceFingerprint(uri: Uri, onProgress: ImportProgressCallback): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val total = sourceSize(uri)
        var processed = 0L
        onProgress(ImportProgress(ImportStage.INSPECTING, 0f, 0, total))
        context.contentResolver.openInputStream(uri)?.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                processed += read
                val fraction = if (total != null && total > 0L) processed.toFloat() / total else 0f
                onProgress(ImportProgress(ImportStage.INSPECTING, fraction.coerceIn(0f, 1f) * .2f, processed, total))
            }
        } ?: throw BookImportException(BookImportFailure.FILE_UNREADABLE, "无法读取文件")
        onProgress(ImportProgress(ImportStage.INSPECTING, .2f, processed, total))
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun sourceSize(uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0).takeIf { it >= 0 }
        }
        return null
    }

    private fun isCjkCharacter(character: Char): Boolean =
        character.code in 0x3400..0x9FFF || character.code in 0xF900..0xFAFF

    private companion object {
        const val SEARCH_CHUNK_SIZE = 4_000
        const val SEARCH_CHUNK_OVERLAP = 256
        const val SEARCH_INDEX_VERSION = 1
    }
}

class DuplicateBookImportException(val existingBook: Book) : IllegalStateException("书籍已导入")

private fun BookEntity.toModel() = Book(
    id, title, author, BookFormat.valueOf(format), chapterCount, totalChars, addedAt, lastReadAt, sourceUrl,
)

private fun ChapterEntity.toModel() = Chapter(id, bookId, orderIndex, title, contentPath, charCount, sourceUrl)

data class ShelfBookProgress(
    val bookId: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
    val readChars: Long,
)
