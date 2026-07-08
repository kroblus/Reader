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
    version = 4,
    exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao

    companion object {
        fun create(context: Context): ReaderDatabase = Room.databaseBuilder(
            context.applicationContext,
            ReaderDatabase::class.java,
            "reader.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN chapterIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN pageIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN chapterTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN styleHash INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN author TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN failedChapters INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN importedBookId TEXT")
                db.execSQL("UPDATE download_tasks SET updatedAt = createdAt WHERE updatedAt = 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN sourceUrl TEXT")
            }
        }
    }
}
