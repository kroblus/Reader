package com.lightreader.app.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val format: String,
    val rootPath: String,
    val addedAt: Long,
    val lastReadAt: Long?,
    val totalChars: Long,
    val chapterCount: Int,
    val sourceUrl: String?,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("bookId"), Index(value = ["bookId", "orderIndex"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val orderIndex: Int,
    val title: String,
    val contentPath: String,
    val charCount: Int,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterId: Long,
    val charOffset: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("bookId"), Index("chapterId")],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: Long,
    val charOffset: Int,
    val excerpt: String,
    val createdAt: Long,
)

@Fts4
@Entity(tableName = "search_chunks")
data class SearchChunkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val bookId: String,
    val chapterId: String,
    val content: String,
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUrl: String,
    val status: String,
    val totalChapters: Int,
    val completedChapters: Int,
    val contentSelector: String,
    val removeSelectorsJson: String,
    val createdAt: Long,
    val error: String?,
)

@Entity(
    tableName = "download_chapters",
    foreignKeys = [ForeignKey(
        entity = DownloadTaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId"), Index(value = ["taskId", "orderIndex"], unique = true)],
)
data class DownloadChapterEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val orderIndex: Int,
    val title: String,
    val url: String,
    val status: String,
    val contentPath: String?,
    val error: String?,
)

data class SearchRow(
    val chapterId: String,
    val chapterTitle: String,
    val excerpt: String,
)
