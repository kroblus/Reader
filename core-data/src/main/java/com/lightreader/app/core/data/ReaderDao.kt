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
        SELECT search_chunks.chapterId AS chapterId,
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
