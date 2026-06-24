package com.lightreader.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.data.ReaderDatabase
import org.junit.Assert.assertEquals
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
}
