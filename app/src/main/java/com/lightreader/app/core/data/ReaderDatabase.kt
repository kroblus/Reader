package com.lightreader.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao

    companion object {
        fun create(context: Context): ReaderDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReaderDatabase::class.java,
            "reader.db",
        ).addMigrations(MIGRATION_1_2).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reading_progress ADD COLUMN chapterIndex INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reading_progress ADD COLUMN pageIndex INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reading_progress ADD COLUMN chapterTitle TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE reading_progress ADD COLUMN styleHash INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
