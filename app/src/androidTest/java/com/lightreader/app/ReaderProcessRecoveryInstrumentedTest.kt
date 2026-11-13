package com.lightreader.app

import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Two independent instrumentation invocations separated by an external QA force-stop.
 * Normal suite excludes this class: its two phases require durable data between invocations. */
@RunWith(AndroidJUnit4::class)
class ReaderProcessRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as ReaderApplication
    private val marker get() = File(app.filesDir, "process-recovery-expected.json")

    @Test fun prepareSavedAnchor() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        runBlocking {
            app.container.database.clearAllTables()
            app.container.settingsRepository.save(ReaderPreferences())
            val root = File(app.filesDir, "books/process-restore").apply { mkdirs() }
            val chapters = (0..2).map { index ->
                val body = "第${index + 1}章\n" + "进程恢复必须保留真实 UTF-16 锚点😀。This is a durable reading position.\n".repeat(160)
                val file = File(root, "$index.txt").apply { writeText(body) }
                ChapterEntity(bookId = "process-restore", orderIndex = index, title = "Chapter $index",
                    contentPath = file.absolutePath, charCount = body.length, sourceUrl = null)
            }
            app.container.database.readerDao().insertBookWithChapters(BookEntity("process-restore", "Process recovery QA", null,
                "TXT", root.absolutePath, 1, null, chapters.sumOf { it.charCount.toLong() }, chapters.size, null), chapters)
        }
        val vm = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
        compose.runOnIdle { vm.openBook("process-restore") }
        compose.waitUntil(20_000) { vm.readerState.value.pages.isNotEmpty() && !vm.readerState.value.loading }
        compose.runOnIdle { vm.selectChapter(2) }
        compose.waitUntil(20_000) { vm.readerState.value.chapterIndex == 2 && vm.readerState.value.pages.size > 4 && !vm.readerState.value.loading }
        compose.runOnIdle { vm.pageSelected(3) }
        val origin = vm.currentSearchOrigin()!!
        compose.runOnIdle { vm.goBack() }
        compose.waitUntil(10_000) { runBlocking { app.container.bookRepository.progress(origin.bookId)?.charOffset == origin.charOffset } }
        marker.writeText(JSONObject().put("pid", Process.myPid()).put("book", origin.bookId)
            .put("chapter", origin.chapterId).put("offset", origin.charOffset).toString())
    }

    @Test fun verifySavedAnchorInNewProcess() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        check(marker.exists()) { "Run prepareSavedAnchor, externally force-stop QA, then invoke this phase separately." }
        val expected = JSONObject(marker.readText())
        assertNotEquals("Verification must execute in a different process", expected.getInt("pid"), Process.myPid())
        val progress = runBlocking { app.container.bookRepository.progress(expected.getString("book"))!! }
        assertEquals(expected.getLong("chapter"), progress.chapterId)
        assertEquals(expected.getInt("offset"), progress.charOffset)
        val vm = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
        compose.runOnIdle { vm.openBook(progress.bookId) }
        compose.waitUntil(20_000) { vm.readerState.value.pages.isNotEmpty() && !vm.readerState.value.loading }
        val state = vm.readerState.value
        assertEquals(progress.chapterId, state.chapters[state.chapterIndex].id)
        val page = state.pages[state.pageIndex]
        assertTrue(progress.charOffset >= page.startOffset && progress.charOffset <= page.endOffset)
        marker.delete()
    }
}
