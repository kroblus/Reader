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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insertSearchChunks(chunks: List<SearchChunkEntity>)

    @Query("DELETE FROM search_chunks WHERE bookId = :bookId")
    suspend fun deleteSearchChunks(bookId: String)

    @Query("SELECT COALESCE(SUM(LENGTH(content)), 0) FROM search_chunks WHERE bookId = :bookId")
    suspend fun indexedCharacterCount(bookId: String): Long

    @Query("""
        SELECT search_chunks.chapterId AS chapterId,
               chapters.title AS chapterTitle,
               snippet(search_chunks, '<b>', '</b>', '…', -1, 18) AS excerpt
        FROM search_chunks
        JOIN chapters ON chapters.id = CAST(search_chunks.chapterId AS INTEGER)
        WHERE search_chunks MATCH :ftsQuery AND search_chunks.bookId = :bookId
        ORDER BY search_chunks.rowid
        LIMIT 100
    """)
    suspend fun search(bookId: String, ftsQuery: String): List<SearchRow>

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
