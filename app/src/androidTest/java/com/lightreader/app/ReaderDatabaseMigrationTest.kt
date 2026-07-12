package com.lightreader.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.data.ReaderDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migrationOneToTwoPreservesOffsetAndAddsDiagnosticFields() {
        val name = "reader-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE reading_progress (bookId TEXT NOT NULL PRIMARY KEY, chapterId INTEGER NOT NULL, charOffset INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                        )
                        db.execSQL("INSERT INTO reading_progress VALUES ('book', 7, 1234, 99)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ReaderDatabase.MIGRATION_1_2.migrate(db)
            db.query("SELECT * FROM reading_progress WHERE bookId = 'book'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1234, cursor.getInt(cursor.getColumnIndexOrThrow("charOffset")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("pageIndex")))
                assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("chapterTitle")))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationTwoToThreeAddsDownloadMetadataFields() {
        val name = "reader-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE download_tasks (" +
                                "id TEXT NOT NULL PRIMARY KEY, " +
                                "title TEXT NOT NULL, " +
                                "sourceUrl TEXT NOT NULL, " +
                                "status TEXT NOT NULL, " +
                                "totalChapters INTEGER NOT NULL, " +
                                "completedChapters INTEGER NOT NULL, " +
                                "contentSelector TEXT NOT NULL, " +
                                "removeSelectorsJson TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL, " +
                                "error TEXT)",
                        )
                        db.execSQL(
                            "INSERT INTO download_tasks VALUES (" +
                                "'task', 'Title', 'https://example.com/book', 'QUEUED', 3, 0, '#content', '[]', 12345, NULL)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ReaderDatabase.MIGRATION_2_3.migrate(db)
            db.query("SELECT * FROM download_tasks WHERE id = 'task'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Title", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("author")))
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("description")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("failedChapters")))
                assertEquals(12345L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("importedBookId")))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationThreeToFourAddsNullableChapterSourceUrl() {
        val name = "reader-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE chapters (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "bookId TEXT NOT NULL, " +
                                "orderIndex INTEGER NOT NULL, " +
                                "title TEXT NOT NULL, " +
                                "contentPath TEXT NOT NULL, " +
                                "charCount INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "INSERT INTO chapters (bookId, orderIndex, title, contentPath, charCount) " +
                                "VALUES ('book', 0, '第一章', '/tmp/00000.txt', 42)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ReaderDatabase.MIGRATION_3_4.migrate(db)
            db.query("SELECT * FROM chapters WHERE bookId = 'book'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("第一章", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("sourceUrl")))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFourToFiveAddsBookFingerprintAndIndex() {
        val name = "reader-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT, format TEXT NOT NULL, rootPath TEXT NOT NULL, addedAt INTEGER NOT NULL, lastReadAt INTEGER, totalChars INTEGER NOT NULL, chapterCount INTEGER NOT NULL, sourceUrl TEXT)",
                        )
                        db.execSQL("INSERT INTO books VALUES ('book', '测试书', NULL, 'TXT', '/tmp/book', 1, NULL, 100, 1, NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ReaderDatabase.MIGRATION_4_5.migrate(db)
            db.query("SELECT contentFingerprint FROM books WHERE id = 'book'").use { cursor ->
                cursor.moveToFirst()
                assertNull(cursor.getString(0))
            }
            db.execSQL("UPDATE books SET contentFingerprint = 'abc' WHERE id = 'book'")
            db.query("SELECT id FROM books WHERE contentFingerprint = 'abc'").use { cursor -> assertEquals(1, cursor.count) }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationFiveToSixAddsGenericSourceAdapterId() {
        val name = "reader-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE download_tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT, description TEXT, sourceUrl TEXT NOT NULL, status TEXT NOT NULL, totalChapters INTEGER NOT NULL, completedChapters INTEGER NOT NULL, failedChapters INTEGER NOT NULL, contentSelector TEXT NOT NULL, removeSelectorsJson TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, importedBookId TEXT, error TEXT)",
                        )
                        db.execSQL("INSERT INTO download_tasks VALUES ('task', '测试', NULL, NULL, 'https://example.com', 'QUEUED', 1, 0, 0, '#content', '[]', 1, 1, NULL, NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ReaderDatabase.MIGRATION_5_6.migrate(db)
            db.query("SELECT sourceId FROM download_tasks WHERE id = 'task'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("generic-html", cursor.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationSixToSevenAddsSourceAdapterVersion() {
        val name = "reader-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE download_tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT, description TEXT, sourceUrl TEXT NOT NULL, status TEXT NOT NULL, totalChapters INTEGER NOT NULL, completedChapters INTEGER NOT NULL, failedChapters INTEGER NOT NULL, contentSelector TEXT NOT NULL, removeSelectorsJson TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, importedBookId TEXT, error TEXT, sourceId TEXT NOT NULL)",
                        )
                        db.execSQL("INSERT INTO download_tasks VALUES ('task', '测试', NULL, NULL, 'https://example.com', 'QUEUED', 1, 0, 0, '#content', '[]', 1, 1, NULL, NULL, 'generic-html')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ReaderDatabase.MIGRATION_6_7.migrate(db)
            db.query("SELECT sourceVersion FROM download_tasks WHERE id = 'task'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("1", cursor.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migrationSevenToEightRebuildsOnlyDisposableSearchState() {
        val name = "reader-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, author TEXT, format TEXT NOT NULL, rootPath TEXT NOT NULL, addedAt INTEGER NOT NULL, lastReadAt INTEGER, totalChars INTEGER NOT NULL, chapterCount INTEGER NOT NULL, sourceUrl TEXT, contentFingerprint TEXT)")
                        db.execSQL("CREATE VIRTUAL TABLE search_chunks USING FTS4(bookId TEXT NOT NULL, chapterId TEXT NOT NULL, content TEXT NOT NULL)")
                        db.execSQL("INSERT INTO books VALUES ('book', '测试书', NULL, 'TXT', '/tmp/book', 1, NULL, 5, 1, NULL, NULL)")
                        db.execSQL("INSERT INTO search_chunks(bookId, chapterId, content) VALUES ('book', '1', '旧索引')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            ReaderDatabase.MIGRATION_7_8.migrate(db)
            db.query("SELECT title FROM books WHERE id = 'book'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("测试书", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM search_chunks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.execSQL("INSERT INTO search_chunks(bookId, chapterId, chunkStart, content) VALUES ('book', '1', 0, '新索引')")
            db.execSQL("INSERT INTO search_index_state VALUES ('book', 5, 1)")
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
