package com.lightreader.app.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastReadAt, addedAt) DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun book(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE contentFingerprint = :fingerprint ORDER BY addedAt DESC LIMIT 1")
    suspend fun bookByContentFingerprint(fingerprint: String): BookEntity?

    @Query("SELECT rootPath FROM books")
    suspend fun bookRootPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun updateBookMetadata(id: String, title: String, author: String?)

    @Query("UPDATE books SET lastReadAt = :lastReadAt WHERE id = :id")
    suspend fun updateLastReadAt(id: String, lastReadAt: Long)

    @Query("UPDATE books SET chapterCount = (SELECT COUNT(*) FROM chapters WHERE bookId = :id), totalChars = (SELECT COALESCE(SUM(charCount), 0) FROM chapters WHERE bookId = :id) WHERE id = :id")
    suspend fun updateBookStatistics(id: String)

    @Query("SELECT contentPath FROM chapters")
    suspend fun referencedChapterPaths(): List<String>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookRow(id: String)

    @Insert
    suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex")
    suspend fun chapters(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun chapter(id: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun progress(bookId: String): ReadingProgressEntity?

    @Query("""
        SELECT reading_progress.bookId AS bookId,
               reading_progress.chapterTitle AS chapterTitle,
               reading_progress.chapterIndex AS chapterIndex,
               reading_progress.charOffset AS charOffset,
               reading_progress.updatedAt AS updatedAt,
               COALESCE((
                   SELECT SUM(chapters.charCount)
                   FROM chapters
                   WHERE chapters.bookId = reading_progress.bookId
                     AND chapters.orderIndex < reading_progress.chapterIndex
               ), 0) + reading_progress.charOffset AS readChars
        FROM reading_progress
    """)
    fun observeShelfProgress(): Flow<List<ShelfProgressRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun bookmark(id: String): BookmarkEntity?

    @Transaction
    suspend fun restoreBookmarkIfValid(mark: BookmarkEntity): Boolean {
        val chapter = chapter(mark.chapterId) ?: return false
        if (book(mark.bookId) == null || chapter.bookId != mark.bookId || mark.charOffset !in 0..chapter.charCount) return false
        val existing = bookmark(mark.id)
        if (existing != null && existing != mark) return false
        insertBookmark(mark)
        return true
    }

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insertSearchChunks(chunks: List<SearchChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchIndexState(state: SearchIndexStateEntity)

    @Query("DELETE FROM search_chunks WHERE bookId = :bookId")
    suspend fun deleteSearchChunks(bookId: String)

    @Query("DELETE FROM search_index_state WHERE bookId = :bookId")
    suspend fun deleteSearchIndexState(bookId: String)

    @Query("SELECT * FROM search_index_state WHERE bookId = :bookId")
    suspend fun searchIndexState(bookId: String): SearchIndexStateEntity?

    @Query("SELECT COALESCE((SELECT indexedUtf16Chars FROM search_index_state WHERE bookId = :bookId), 0)")
    suspend fun indexedCharacterCount(bookId: String): Long

    @Query("""
        SELECT search_chunks.rowid AS rowId, search_chunks.chapterId AS chapterId,
               chapters.title AS chapterTitle,
               search_chunks.chunkStart AS chunkStart,
               search_chunks.content AS content
        FROM search_chunks
        JOIN chapters ON chapters.id = CAST(search_chunks.chapterId AS INTEGER)
        WHERE search_chunks MATCH :ftsQuery AND search_chunks.bookId = :bookId
        ORDER BY search_chunks.rowid
        LIMIT 100
    """)
    suspend fun search(bookId: String, ftsQuery: String): List<SearchRow>

    @Query("""
        SELECT search_chunks.rowid AS rowId, search_chunks.chapterId AS chapterId,
               chapters.title AS chapterTitle, search_chunks.chunkStart AS chunkStart,
               search_chunks.content AS content
        FROM search_chunks JOIN chapters ON chapters.id = CAST(search_chunks.chapterId AS INTEGER)
        WHERE search_chunks MATCH :ftsQuery AND search_chunks.bookId = :bookId AND search_chunks.rowid > :afterRowId
        ORDER BY search_chunks.rowid LIMIT :limit
    """)
    suspend fun searchCandidates(bookId: String, ftsQuery: String, afterRowId: Int, limit: Int): List<SearchRow>

    @Transaction
    suspend fun replaceSearchIndex(
        bookId: String,
        chunks: List<SearchChunkEntity>,
        state: SearchIndexStateEntity,
    ) {
        deleteSearchChunks(bookId)
        deleteSearchIndexState(bookId)
        chunks.chunked(50).forEach { insertSearchChunks(it) }
        insertSearchIndexState(state)
    }

    @Transaction
    suspend fun clearSearchIndex(bookId: String) {
        deleteSearchChunks(bookId)
        deleteSearchIndexState(bookId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadTask(task: DownloadTaskEntity)

    @Update
    suspend fun updateDownloadTask(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeDownloadTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun downloadTask(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    suspend fun downloadTasks(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE id = :bookId OR importedBookId = :bookId")
    suspend fun tasksForBook(bookId: String): List<DownloadTaskEntity>

    @Transaction
    suspend fun createDownloadTask(task: DownloadTaskEntity, pending: List<DownloadChapterEntity>): DownloadTaskEntity {
        require(task.importedBookId == null || book(task.importedBookId) != null) { "Book no longer exists" }
        val existing = downloadTasks().firstOrNull {
            if (task.importedBookId != null) {
                it.importedBookId == task.importedBookId && it.status !in listOf("COMPLETED", "CANCELED")
            } else {
                (it.importedBookId == null || it.importedBookId == it.id) && it.status != "CANCELED" &&
                    WebChapterIdentity.url(it.sourceUrl) == WebChapterIdentity.url(task.sourceUrl)
            }
        }
        if (existing != null) return existing
        insertDownloadTask(task)
        insertDownloadChapters(pending)
        return task
    }

    @Query("UPDATE download_tasks SET status = :status, executionGeneration = executionGeneration + 1, executionOwner = NULL, updatedAt = :now, error = NULL WHERE id = :id AND status IN (:allowed)")
    suspend fun transitionDownloadTask(id: String, status: String, allowed: List<String>, now: Long): Int

    @Transaction
    suspend fun resumeDownloadTask(id: String, now: Long): DownloadTaskEntity? {
        if (transitionDownloadTask(id, "QUEUED", listOf("PAUSED", "FAILED", "CANCELED"), now) == 0) return downloadTask(id)
        resetFailedDownloadChapters(id)
        val task = downloadTask(id) ?: return null
        updateDownloadTask(task.copy(failedChapters = 0, automaticRetryCount = 0))
        return downloadTask(id)
    }

    @Query("UPDATE download_tasks SET status = 'DOWNLOADING', executionOwner = :owner, updatedAt = :now, error = NULL WHERE id = :id AND executionGeneration = :generation AND status = 'QUEUED' AND notBeforeMillis <= :now")
    suspend fun claimDownloadTask(id: String, generation: Long, now: Long, owner: String = ""): Int

    @Query("UPDATE download_tasks SET executionGeneration = executionGeneration + 1, updatedAt = :now WHERE id = :id AND executionOwner = :owner AND status = 'DOWNLOADING'")
    suspend fun recoverInterruptedDownload(id: String, owner: String, now: Long): Int

    @Query("UPDATE download_tasks SET status = 'QUEUED', executionGeneration = executionGeneration + 1, executionOwner = NULL, completedChapters = :completed, failedChapters = :failed, error = :error, updatedAt = :now, notBeforeMillis = :notBeforeMillis, automaticRetryCount = :retryCount WHERE id = :id AND executionGeneration = :generation AND status = 'DOWNLOADING'")
    suspend fun yieldDownloadTask(id: String, generation: Long, completed: Int, failed: Int, error: String?, now: Long, notBeforeMillis: Long = 0, retryCount: Int = 0): Int

    @Query("UPDATE download_tasks SET status = :status, completedChapters = :completed, failedChapters = :failed, error = :error, updatedAt = :now WHERE id = :id AND executionGeneration = :generation AND status = 'DOWNLOADING'")
    suspend fun updateDownloadExecution(id: String, generation: Long, status: String, completed: Int, failed: Int, error: String?, now: Long): Int

    @Query("UPDATE download_chapters SET status = :status, contentPath = :path, error = :error WHERE id = :id AND taskId = :taskId AND EXISTS (SELECT 1 FROM download_tasks WHERE id = :taskId AND executionGeneration = :generation AND status = 'DOWNLOADING')")
    suspend fun updateDownloadChapterExecution(id: String, taskId: String, generation: Long, status: String, path: String?, error: String?): Int

    /** Files are already durably prepared. The task guard, append identity, ordering, statistics,
     * search invalidation and completion are committed together, never around network calls. */
    @Transaction
    suspend fun commitDownloadedTask(taskId: String, generation: Long, newBook: BookEntity, prepared: List<ChapterEntity>): Boolean {
        val task = downloadTask(taskId) ?: return false
        if (task.executionGeneration != generation || task.status != "DOWNLOADING") return false
        val append = task.importedBookId?.takeIf { it != task.id }
        val target = append ?: task.id
        if (append != null && book(target) == null) return false
        if (book(target) == null) insertBook(newBook)
        appendChaptersAtomically(target, prepared)
        updateDownloadTask(task.copy(status = "COMPLETED", completedChapters = task.totalChapters,
            failedChapters = 0, importedBookId = target, error = null, updatedAt = System.currentTimeMillis()))
        return true
    }

    @Transaction
    suspend fun appendChaptersAtomically(bookId: String, prepared: List<ChapterEntity>): Int {
        requireNotNull(book(bookId)) { "Book was not found." }
        val existing = chapters(bookId)
        val identities = existing.mapTo(mutableSetOf()) { WebChapterIdentity.key(it.sourceUrl, it.title) }
        val start = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val rows = prepared.filter { identities.add(WebChapterIdentity.key(it.sourceUrl, it.title)) }
            .mapIndexed { index, row -> row.copy(id = 0, bookId = bookId, orderIndex = start + index) }
        if (rows.isNotEmpty()) {
            insertChapters(rows)
            updateBookStatistics(bookId)
            clearSearchIndex(bookId)
        }
        return rows.size
    }

    @Transaction
    suspend fun deleteBookAndTasks(id: String) {
        tasksForBook(id).forEach { deleteDownloadTask(it.id) }
        clearSearchIndex(id)
        deleteBookRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadChapters(chapters: List<DownloadChapterEntity>)

    @Update
    suspend fun updateDownloadChapter(chapter: DownloadChapterEntity)

    @Query("SELECT * FROM download_chapters WHERE taskId = :taskId ORDER BY orderIndex")
    suspend fun downloadChapters(taskId: String): List<DownloadChapterEntity>

    @Query("UPDATE download_chapters SET status = 'PENDING', error = NULL WHERE taskId = :taskId AND status = 'FAILED'")
    suspend fun resetFailedDownloadChapters(taskId: String)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteDownloadTask(id: String)

    @Transaction
    suspend fun insertBookWithChapters(book: BookEntity, chapters: List<ChapterEntity>): List<Long> {
        insertBook(book)
        return insertChapters(chapters)
    }
}

data class ShelfProgressRow(
    val bookId: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val charOffset: Int,
    val updatedAt: Long,
    val readChars: Long,
)
