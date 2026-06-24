package com.lightreader.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        SearchChunkEntity::class,
        DownloadTaskEntity::class,
        DownloadChapterEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao

    companion object {
        fun create(context: Context): ReaderDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReaderDatabase::class.java,
            "reader.db",
        ).build()
    }
}
