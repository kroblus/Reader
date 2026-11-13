package com.lightreader.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.core.data.ReaderDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderSchemaEightMigrationInstrumentedTest {
    @Test fun actualExportedSchemaEightOpensThroughRoomNineWithAllReferences() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "schema8-${System.nanoTime()}.db"
        val content = File(context.cacheDir, "$name.txt").apply { writeText("序章😀正文保持原样") }
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.lightreader.app.core.data.ReaderDatabase/8.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indexes = entity.optJSONArray("indices")
                        if (indexes != null) for (j in 0 until indexes.length()) {
                            db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    db.execSQL("INSERT INTO books VALUES ('old', 'Title', 'Author', 'WEB', ?, 1, 2, ?, 1, 'https://example.org/book', 'fingerprint')", arrayOf(content.parent, content.readText().length))
                    db.execSQL("INSERT INTO chapters VALUES (37, 'old', 0, '序章', ?, ?, 'https://example.org/Chapter?ID=A')", arrayOf(content.absolutePath, content.readText().length))
                    db.execSQL("INSERT INTO reading_progress VALUES ('old',37,4,0,0,'序章',123,99)")
                    db.execSQL("INSERT INTO bookmarks VALUES ('mark','old',37,4,'😀',88)")
                    db.execSQL("INSERT INTO download_tasks VALUES ('task','Title',NULL,NULL,'https://example.org/book','PAUSED',1,0,0,'#content','[]',1,2,'old',NULL,'generic-html','1')")
                    db.execSQL("INSERT INTO download_chapters VALUES ('pending','task',0,'Next','https://example.org/next','PENDING',NULL,NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        var migrated: ReaderDatabase? = null
        try {
            helper.writableDatabase
            helper.close()
            migrated = Room.databaseBuilder(context, ReaderDatabase::class.java, name)
                .addMigrations(ReaderDatabase.MIGRATION_8_9).build()
            val dao = migrated.readerDao()
            assertEquals(37L, dao.chapters("old").single().id)
            assertEquals(4, dao.progress("old")!!.charOffset)
            assertEquals(37L, dao.bookmark("mark")!!.chapterId)
            assertEquals(content.absolutePath, dao.chapter(37)!!.contentPath)
            assertEquals("序章😀正文保持原样", File(dao.chapter(37)!!.contentPath).readText())
            assertEquals(0L, dao.downloadTask("task")!!.executionGeneration)
            assertNull(dao.downloadTask("task")!!.executionOwner)
            assertEquals(0L, dao.downloadTask("task")!!.notBeforeMillis)
            assertEquals(0, dao.downloadTask("task")!!.automaticRetryCount)
            assertEquals("PAUSED", dao.downloadTask("task")!!.status)
            assertEquals("pending", dao.downloadChapters("task").single().id)
            assertEquals(9, migrated.openHelper.readableDatabase.version)
        } finally { helper.close(); migrated?.close(); context.deleteDatabase(name); content.delete() }
    }
}
