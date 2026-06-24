package com.lightreader.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.model.BookFormat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderUiInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val application: ReaderApplication
        get() = composeRule.activity.application as ReaderApplication

    @Before
    fun resetAppData() = runBlocking {
        application.container.database.clearAllTables()
        application.container.keyStore.clear()
    }

    @Test
    fun emptyLibraryAndApiSettingsAreReachable() {
        composeRule.onNodeWithText("轻阅").assertIsDisplayed()
        composeRule.onNodeWithText("书架还是空的").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("DeepSeek 设置").performClick()
        composeRule.onNodeWithText("DeepSeek 设置").assertIsDisplayed()
        composeRule.onNodeWithText("当前未配置 API Key").assertIsDisplayed()
    }

    @Test
    fun bookCardDeleteRequiresConfirmation() {
        seedBook("待删除小说")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("待删除小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.onNodeWithText("删除书籍").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("待删除小说").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("待删除小说").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun readerControlsOpenAndAutoHideAfterThreeSeconds() {
        seedBook("按钮测试小说")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("按钮测试小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("按钮测试小说").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("目录").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("阅读设置").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("阅读设置").fetchSemanticsNode()
        composeRule.mainClock.advanceTimeBy(4_000)
        composeRule.onNodeWithContentDescription("目录").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(3_500)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("目录").assertCountEquals(0)
        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("目录").assertIsDisplayed()
    }

    private fun seedBook(title: String) = runBlocking {
        val id = "ui-${System.nanoTime()}"
        val directory = java.io.File(application.filesDir, "books/$id/chapters").apply { mkdirs() }
        val content = "第一章 入山\n" + "山中修行，自此开始。".repeat(200)
        val chapterFile = java.io.File(directory, "00000.txt").apply { writeText(content) }
        val now = System.currentTimeMillis()
        application.container.database.readerDao().insertBookWithChapters(
            BookEntity(id, title, null, BookFormat.TXT.name, directory.parentFile!!.absolutePath, now, null, content.length.toLong(), 1, null),
            listOf(ChapterEntity(bookId = id, orderIndex = 0, title = "第一章 入山", contentPath = chapterFile.absolutePath, charCount = content.length)),
        )
    }
}
