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
import com.lightreader.app.core.data.ReadingProgressEntity
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ReaderPreferences
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
        application.container.settingsRepository.save(ReaderPreferences())
    }

    @Test
    fun globalSkinCanBeChangedFromLibrary() {
        composeRule.onNodeWithContentDescription("更换皮肤").performClick()
        composeRule.onNodeWithText("换一种阅读心情").assertIsDisplayed()
        composeRule.onNodeWithText("海盐蓝白").performClick()
        composeRule.onNodeWithText("海盐蓝白").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("已选择").assertIsDisplayed()
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
    fun readerControlsOpenAndAutoHideAfterFiveSeconds() {
        seedBook("按钮测试小说")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("按钮测试小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("按钮测试小说").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("空白页面").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("目录").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("阅读设置").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("亮度").assertIsDisplayed()
        composeRule.onNodeWithText("字号").assertIsDisplayed()
        composeRule.onNodeWithText("背景").assertIsDisplayed()
        composeRule.onNodeWithText("翻页").assertIsDisplayed()
        composeRule.onNodeWithText("全屏点击").assertIsDisplayed()
        composeRule.onNodeWithText("更多设置").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("阅读背景", substring = true).assertCountEquals(6)
        composeRule.onAllNodesWithText("关闭").assertCountEquals(0)
        composeRule.onAllNodesWithText("更多背景").assertCountEquals(0)
        composeRule.onAllNodesWithText("极简").assertCountEquals(0)

        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("亮度").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("目录").assertCountEquals(0)
        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("目录").assertIsDisplayed()
        composeRule.onAllNodesWithText("亮度").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("阅读设置").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("更多设置").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("更多设置").assertIsDisplayed()
        composeRule.onNodeWithText("排版").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("阅读设置").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("自动阅读").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("目录").assertCountEquals(0)

        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("暂停").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("暂停").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        composeRule.mainClock.advanceTimeBy(5_500)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("目录").assertCountEquals(0)
        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("目录").assertIsDisplayed()
    }

    @Test
    fun readerDirectoryAndBookmarksUseUnifiedOverlay() {
        seedBook("浮层测试小说", chapterCount = 260, progressChapterIndex = 234)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("浮层测试小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("浮层测试小说").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("目录").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("目录").performClick()
        composeRule.onNodeWithText("正序").assertIsDisplayed()
        composeRule.onNodeWithText("已读", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Chapter 235").assertCountEquals(2)
        composeRule.onAllNodesWithText("Chapter 001").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("关闭浮层").performTouchInput { click(Offset(center.x * 1.9f, center.y)) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("正序").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("书签").performClick()
        composeRule.onNodeWithText("还没有书签").assertIsDisplayed()
    }

    private fun seedBook(title: String, chapterCount: Int = 1, progressChapterIndex: Int = 0) = runBlocking {
        val id = "ui-${System.nanoTime()}"
        val directory = java.io.File(application.filesDir, "books/$id/chapters").apply { mkdirs() }
        val chapters = (0 until chapterCount).map { index ->
            val chapterTitle = if (chapterCount == 1) "第一章 入山" else "Chapter %03d".format(index + 1)
            val content = "$chapterTitle\n" + "山中修行，自此开始。".repeat(200)
            val chapterFile = java.io.File(directory, "%05d.txt".format(index)).apply { writeText(content) }
            ChapterEntity(bookId = id, orderIndex = index, title = chapterTitle, contentPath = chapterFile.absolutePath, charCount = content.length)
        }
        val now = System.currentTimeMillis()
        val chapterIds = application.container.database.readerDao().insertBookWithChapters(
            BookEntity(id, title, null, BookFormat.TXT.name, directory.parentFile!!.absolutePath, now, null, chapters.sumOf { it.charCount.toLong() }, chapterCount, null),
            chapters,
        )
        val progressIndex = progressChapterIndex.coerceIn(0, chapterCount - 1)
        if (progressIndex > 0) {
            application.container.database.readerDao().saveProgress(
                ReadingProgressEntity(
                    bookId = id,
                    chapterId = chapterIds[progressIndex],
                    charOffset = 0,
                    chapterIndex = progressIndex,
                    pageIndex = 0,
                    chapterTitle = chapters[progressIndex].title,
                    styleHash = 0,
                    updatedAt = now,
                ),
            )
        }
    }
}
