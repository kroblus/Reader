package com.lightreader.app.core.data

import com.lightreader.app.core.model.SearchResult
import com.lightreader.app.core.reader.UnicodeTextBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class SearchCursor(
    val bookId: String,
    val query: String,
    val contentVersion: String,
    val chapterIndex: Int,
    val charOffset: Int,
)

data class SearchPage(
    val results: List<SearchResult>,
    val hasMore: Boolean,
    val cursor: SearchCursor?,
    val complete: Boolean,
) {
    init { require(hasMore == (cursor != null) && complete == !hasMore) }
}

class SearchCursorExpiredException : IllegalStateException("Search content changed; restart the query")

/** Paged exact UTF-16 search. FTS is a candidate accelerator, never a completeness filter. */
class BookSearchRepository(private val dao: ReaderDao) {
    suspend fun page(bookId: String, query: String, cursor: SearchCursor? = null, limit: Int = 100): SearchPage =
        withContext(Dispatchers.IO) {
            require(limit in 1..200)
            val normalized = query.trim()
            if (normalized.isEmpty()) return@withContext SearchPage(emptyList(), false, null, true)
            val chapters = dao.chapters(bookId)
            check(dao.book(bookId) != null) { "Book no longer exists" }
            val version = contentVersion(chapters)
            if (cursor != null && (cursor.bookId != bookId || cursor.query != normalized || cursor.contentVersion != version)) {
                throw SearchCursorExpiredException()
            }
            // Existing complete indexes are optional. Drain candidates in bounded SQL batches and
            // deduplicate overlapping chunks. Exact fallback includes every non-candidate chapter,
            // because FTS word-prefix semantics cannot prove absence of an arbitrary substring.
            val candidates = linkedSetOf<Long>()
            if (dao.searchIndexState(bookId) != null && normalized.length <= 128) {
                val fts = normalized.split(Regex("\\s+")).joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
                var after = 0
                do {
                    currentCoroutineContext().ensureActive()
                    val batch = dao.searchCandidates(bookId, fts, after, 128)
                    batch.forEach { candidates += it.chapterId.toLong() }
                    if (batch.isEmpty()) break
                    after = batch.last().rowId
                } while (batch.size == 128)
            }
            // Preserve chapter order even if an optional index is rebuilt between pages.
            // Every candidate is verified below, together with the exact-scan fallback chapters.
            val ordered = chapters
            val results = ArrayList<SearchResult>(limit)
            var chapterIndex = cursor?.chapterIndex ?: 0
            var offset = cursor?.charOffset ?: 0
            var scanned = 0
            while (chapterIndex < ordered.size && results.size < limit && scanned < 12) {
                currentCoroutineContext().ensureActive()
                val chapter = ordered[chapterIndex]
                val text = File(chapter.contentPath).readText()
                while (offset < text.length && results.size < limit) {
                    currentCoroutineContext().ensureActive()
                    val match = text.indexOf(normalized, offset, ignoreCase = true)
                    if (match < 0) { offset = text.length; break }
                    val start = UnicodeTextBoundary.previousBoundary(text, (match - 28).coerceAtLeast(0))
                    val end = UnicodeTextBoundary.safeEnd(text, match + normalized.length, (match + normalized.length + 42).coerceAtMost(text.length))
                    val excerpt = buildString {
                        if (start > 0) append('…')
                        append(text.substring(start, match))
                        append("<b>").append(text.substring(match, match + normalized.length)).append("</b>")
                        append(text.substring(match + normalized.length, end))
                        if (end < text.length) append('…')
                    }
                    results += SearchResult(chapter.id, chapter.title, excerpt, match)
                    offset = match + normalized.length
                }
                if (offset >= text.length) { chapterIndex++; offset = 0 }
                scanned++
            }
            // A concurrent append/delete invalidates this result before it reaches the screen.
            if (contentVersion(dao.chapters(bookId)) != version) throw SearchCursorExpiredException()
            val next = if (chapterIndex < ordered.size) SearchCursor(bookId, normalized, version, chapterIndex, offset) else null
            SearchPage(results, next != null, next, next == null)
        }

    suspend fun all(bookId: String, query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        var cursor: SearchCursor? = null
        do {
            val page = page(bookId, query, cursor)
            results += page.results
            cursor = page.cursor
        } while (cursor != null)
        return results
    }

    private fun contentVersion(chapters: List<ChapterEntity>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chapters.forEach {
            val file = File(it.contentPath)
            digest.update("${it.id}:${it.orderIndex}:${it.charCount}:${it.title}:${it.contentPath}:${file.length()}:${file.lastModified()}\n".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
