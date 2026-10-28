package com.lightreader.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.data.ReadingProgressEntity
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.journey.QaHttpsFixtureServer
import com.lightreader.app.ui.ReaderTestTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** QA-only runtime gallery. Every frame is captured from the running app on the connected AVD. */
@RunWith(AndroidJUnit4::class)
class UiOptimizationScreenshotInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val application: ReaderApplication
        get() = composeRule.activity.application as ReaderApplication
    private val device by lazy { UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()) }
    private lateinit var server: QaHttpsFixtureServer

    @Before
    fun setUp() {
        runBlocking {
            application.container.database.clearAllTables()
            application.container.keyStore.clear()
            application.container.settingsRepository.save(ReaderPreferences())
        }
        server = QaHttpsFixtureServer()
        device.executeShellCommand("mkdir -p $SCREENSHOT_ROOT")
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun captureUiOptimizationGallery() {
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_EMPTY).assertIsDisplayed()
        capture("001_bookshelf_empty")

        val bookTitle = "雾桥长路"
        seedBook(bookTitle)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(bookTitle).fetchSemanticsNodes().isNotEmpty()
        }
        capture("002_bookshelf_populated")

        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_IMPORT).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_IMPORT_LOCAL).assertIsDisplayed()
        capture("003_import_sheet")
        device.pressBack()

        composeRule.onAllNodesWithText(bookTitle)[0].performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.reader_toc)).assertIsDisplayed()
        SystemClock.sleep(1_500)
        composeRule.waitForIdle()
        capture("005_reader_controls")
        tapReaderCenter()
        capture("004_reader_clean")
        tapReaderCenter()

        composeRule.onNodeWithContentDescription(text(R.string.reader_toc)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_TOC).assertIsDisplayed()
        capture("006_reader_toc")
        composeRule.onNodeWithContentDescription(text(R.string.reader_close_overlay)).performClick()

        composeRule.onNodeWithContentDescription(text(R.string.action_search)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_INPUT).performTextReplacement("修行")
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_SUBMIT).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.SEARCH_RESULT).fetchSemanticsNodes().isNotEmpty()
        }
        capture("007_reader_search")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        composeRule.onNodeWithContentDescription(text(R.string.reader_bookmark_add)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.reader_more_actions)).performClick()
        composeRule.onNodeWithText(text(R.string.reader_bookmark_list)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_BOOKMARKS).assertIsDisplayed()
        capture("008_reader_bookmark")
        composeRule.onNodeWithContentDescription(text(R.string.reader_close_overlay)).performClick()
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_TOGGLE).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_PANEL).assertIsDisplayed()
        capture("009_reader_quick_settings")
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_MORE).performClick()
        composeRule.onNodeWithText(text(R.string.settings_page_turn)).assertIsDisplayed()
        capture("reader_settings_after")
        composeRule.onNodeWithTag(ReaderTestTags.READER_ADVANCED_SETTINGS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderTestTags.READER_ADVANCED_SETTINGS).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_ADVANCED_CONTENT).performScrollTo().assertIsDisplayed()
        capture("010_reader_advanced_settings")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        showReaderChrome()
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_TOGGLE).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_PANEL).assertIsDisplayed()

        selectPresetAndCapture(R.string.layout_compact, "011_reader_compact")
        selectPresetAndCapture(R.string.layout_comfort, "012_reader_comfort")
        selectPresetAndCapture(R.string.layout_immersive, "013_reader_immersive")
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_TOGGLE).performClick()

        showReaderChrome()
        composeRule.onNodeWithContentDescription(text(R.string.reader_more_actions)).performClick()
        composeRule.onNodeWithText(text(R.string.reader_night)).performClick()
        tapReaderCenter()
        capture("014_reader_night")

        showReaderChrome()
        composeRule.onNodeWithContentDescription(text(R.string.reader_more_actions)).performClick()
        composeRule.onNodeWithText(text(R.string.reader_auto_start)).performClick()
        composeRule.onNodeWithText(text(R.string.reader_pause_auto)).assertIsDisplayed()
        capture("015_reader_auto")
        composeRule.onNodeWithText(text(R.string.reader_pause_auto)).performClick()
        showReaderChrome()
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS).assertIsDisplayed()
        capture("016_app_settings")

        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS_SKIN).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.library_skin_title)).assertIsDisplayed()
        capture("017_skin_picker")
        device.pressBack()

        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS_WEB_IMPORT).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderTestTags.WEB_IMPORT).assertIsDisplayed()
        capture("018_web_import")
        composeRule.onNodeWithTag(ReaderTestTags.WEB_IMPORT_URL).performTextReplacement(server.url("/catalog"))
        composeRule.onNodeWithTag(ReaderTestTags.WEB_IMPORT_PARSE).performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("LightReader QA Novel").fetchSemanticsNodes().isNotEmpty()
        }
        capture("web_import_preview_after")

        composeRule.onNodeWithTag(ReaderTestTags.WEB_IMPORT_URL).performTextReplacement(server.url("/status/403"))
        composeRule.onNodeWithTag(ReaderTestTags.WEB_IMPORT_PARSE).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.WEB_IMPORT_ERROR).fetchSemanticsNodes().isNotEmpty()
        }
        capture("019_web_error")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS_API).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderTestTags.API_SETTINGS).assertIsDisplayed()
        capture("020_ai_settings")
    }

    @Test
    fun captureLandscapeReaderWhenPortraitLockIsOff() {
        val bookTitle = "横屏阅读验收"
        runBlocking {
            application.container.settingsRepository.save(ReaderPreferences(lockPortrait = false))
        }
        seedBook(bookTitle)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(bookTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(bookTitle)[0].performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            composeRule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        try {
            device.setOrientationLeft()
            composeRule.waitUntil(10_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()
            SystemClock.sleep(1_500)
            capture("021_reader_landscape_unlocked")
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun selectPresetAndCapture(@StringRes preset: Int, outputName: String) {
        composeRule.onNodeWithText(text(preset)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_TOGGLE).performClick()
        tapReaderCenter()
        capture(outputName)
        showReaderChrome()
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_TOGGLE).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_SETTINGS_PANEL).assertIsDisplayed()
    }

    private fun showReaderChrome() {
        if (composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isEmpty()) {
            tapReaderCenter()
        }
    }

    private fun tapReaderCenter() {
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.waitForIdle()
        SystemClock.sleep(220)
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        SystemClock.sleep(420)
        device.executeShellCommand("screencap -p $SCREENSHOT_ROOT/$name.png")
    }

    private fun seedBook(title: String) = runBlocking {
        val id = "uiux-${System.nanoTime()}"
        val directory = java.io.File(application.filesDir, "books/$id/chapters").apply { mkdirs() }
        val chapters = (0 until 36).map { index ->
            val chapterTitle = "第${index + 1}章 山路"
            val content = "$chapterTitle\n" +
                "山中修行，自此开始。晨钟响过三遍，薄雾漫过石桥，少年沿着长路继续前行。".repeat(160)
            val chapterFile = java.io.File(directory, "%05d.txt".format(index)).apply { writeText(content) }
            ChapterEntity(
                bookId = id,
                orderIndex = index,
                title = chapterTitle,
                contentPath = chapterFile.absolutePath,
                charCount = content.length,
                sourceUrl = null,
            )
        }
        val now = System.currentTimeMillis()
        val chapterIds = application.container.database.readerDao().insertBookWithChapters(
            BookEntity(
                id = id,
                title = title,
                author = "林舟",
                format = BookFormat.TXT.name,
                rootPath = directory.parentFile!!.absolutePath,
                addedAt = now,
                lastReadAt = now,
                totalChars = chapters.sumOf { it.charCount.toLong() },
                chapterCount = chapters.size,
                sourceUrl = null,
            ),
            chapters,
        )
        application.container.database.readerDao().saveProgress(
            ReadingProgressEntity(
                bookId = id,
                chapterId = chapterIds[12],
                charOffset = 0,
                chapterIndex = 12,
                pageIndex = 0,
                chapterTitle = chapters[12].title,
                styleHash = 0,
                updatedAt = now,
            ),
        )
    }

    private fun text(@StringRes id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)

    private companion object {
        const val SCREENSHOT_ROOT = "/sdcard/Download/LightReader-UIUX"
    }
}
