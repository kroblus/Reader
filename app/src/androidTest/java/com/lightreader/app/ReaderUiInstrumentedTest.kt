package com.lightreader.app

import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.data.ReadingProgressEntity
import com.lightreader.app.core.model.AppLanguage
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ReaderPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

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
    fun stringResourcesResolveForEnglishAndChinese() {
        assertEquals("LightReader", localizedText(Locale.ENGLISH, R.string.app_name))
        assertEquals("轻阅", localizedText(Locale.SIMPLIFIED_CHINESE, R.string.app_name))
        assertEquals("My shelf", localizedText(Locale.ENGLISH, R.string.library_my_shelf))
        assertEquals("我的书架", localizedText(Locale.SIMPLIFIED_CHINESE, R.string.library_my_shelf))
        assertEquals("Settings", localizedText(Locale.ENGLISH, R.string.reader_settings))
        assertEquals("设置", localizedText(Locale.SIMPLIFIED_CHINESE, R.string.reader_settings))
        assertEquals("No bookmarks yet", localizedText(Locale.ENGLISH, R.string.reader_no_bookmarks))
        assertEquals("还没有书签", localizedText(Locale.SIMPLIFIED_CHINESE, R.string.reader_no_bookmarks))
        assertEquals("Failed", localizedText(Locale.ENGLISH, R.string.download_status_failed))
        assertEquals("失败", localizedText(Locale.SIMPLIFIED_CHINESE, R.string.download_status_failed))
    }

    @Test
    fun globalSkinCanBeChangedFromLibrary() {
        composeRule.onNodeWithContentDescription(text(R.string.library_change_skin)).performClick()
        composeRule.onNodeWithText(text(R.string.library_skin_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.skin_ocean)).performClick()
        composeRule.onNodeWithText(text(R.string.skin_ocean)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_selected)).assertIsDisplayed()
    }

    @Test
    fun emptyLibraryAndApiSettingsAreReachable() {
        composeRule.onNodeWithText(text(R.string.brand_name)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_import_file)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.library_web_import)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithText(text(R.string.app_settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_web_import)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.web_import_flow_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(R.string.app_settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_deepseek_settings)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.api_settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.api_settings_not_configured)).assertIsDisplayed()
    }

    @Test
    fun apiKeyDeleteRequiresConfirmation() {
        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithText(text(R.string.library_deepseek_settings)).performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("test-key")
        composeRule.onNodeWithText(text(R.string.api_settings_save_key)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.api_settings_configured)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(R.string.api_settings_delete_key)).performClick()
        composeRule.onNodeWithText(text(R.string.api_settings_delete_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_keep)).performClick()
        composeRule.onNodeWithText(text(R.string.api_settings_configured)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.api_settings_delete_key)).performClick()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.api_settings_not_configured)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun bookCardDeleteRequiresConfirmation() {
        seedBook("待删除小说")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("待删除小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.library_book_actions)).performClick()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeRule.onNodeWithText(text(R.string.library_delete_book_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        composeRule.onNodeWithText("待删除小说").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.library_book_actions)).performClick()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("待删除小说").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun bookMetadataCanBeEditedWithoutChangingChapterFiles() {
        val bookId = seedBook("Original Title", author = "Original Author")
        val beforeChapter = runBlocking { application.container.database.readerDao().chapters(bookId).single() }
        val beforeText = File(beforeChapter.contentPath).readText()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Original Title").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(text(R.string.library_book_actions)).performClick()
        composeRule.onNodeWithText(text(R.string.library_edit_book_info)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Original Title")).performTextReplacement("Edited Title")
        composeRule.onNode(hasSetTextAction() and hasText("Original Author")).performTextReplacement("Edited Author")
        composeRule.onNodeWithText(text(R.string.action_save)).performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edited Title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edited Author").assertIsDisplayed()
        composeRule.onAllNodesWithText("Original Title").assertCountEquals(0)
        val updatedBook = runBlocking { application.container.database.readerDao().book(bookId)!! }
        val afterChapter = runBlocking { application.container.database.readerDao().chapters(bookId).single() }
        assertEquals("Edited Title", updatedBook.title)
        assertEquals("Edited Author", updatedBook.author)
        assertEquals(beforeChapter.contentPath, afterChapter.contentPath)
        assertEquals(beforeText, File(afterChapter.contentPath).readText())
    }

    @Test
    fun bookAuthorCanBeCleared() {
        val bookId = seedBook("Authorless Title", author = "Temporary Author")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Authorless Title").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(text(R.string.library_book_actions)).performClick()
        composeRule.onNodeWithText(text(R.string.library_edit_book_info)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Temporary Author")).performTextReplacement("")
        composeRule.onNodeWithText(text(R.string.action_save)).performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.library_unknown_author)).fetchSemanticsNodes().isNotEmpty()
        }
        val updatedBook = runBlocking { application.container.database.readerDao().book(bookId)!! }
        assertEquals(null, updatedBook.author)
    }

    @Test
    fun appSettingsSwitchesLanguageImmediately() {
        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithText(text(R.string.app_settings_title)).assertIsDisplayed()

        composeRule.onNodeWithText(localizedText(Locale.ENGLISH, R.string.language_en)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(localizedText(Locale.ENGLISH, R.string.app_settings_title)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(localizedText(Locale.ENGLISH, R.string.app_settings_language)).assertIsDisplayed()
        composeRule.onNodeWithText(localizedText(Locale.ENGLISH, R.string.language_zh_cn)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(localizedText(Locale.SIMPLIFIED_CHINESE, R.string.app_settings_title)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(localizedText(Locale.SIMPLIFIED_CHINESE, R.string.app_settings_language)).assertIsDisplayed()
    }

    @Test
    fun appLanguagePreferenceDefaultsAndPersists() = runBlocking {
        val repository = application.container.settingsRepository
        assertEquals(AppLanguage.SYSTEM, repository.preferences.first().appLanguage)
        repository.save(ReaderPreferences(appLanguage = AppLanguage.EN))
        assertEquals(AppLanguage.EN, repository.preferences.first().appLanguage)
    }

    @Test
    fun localizedTaglineArraysHaveAtLeastThirtyItems() {
        assertTrue(localizedStringArray(Locale.ENGLISH, R.array.brand_taglines).size >= 30)
        assertTrue(localizedStringArray(Locale.SIMPLIFIED_CHINESE, R.array.brand_taglines).size >= 30)
    }

    @Test
    fun readerControlsOpenAndAutoHideAfterFiveSeconds() {
        seedBook("按钮测试小说")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("按钮测试小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("按钮测试小说").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription(text(R.string.reader_blank_page)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(text(R.string.reader_toc)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(text(R.string.reader_settings_content_description)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.settings_brightness)).fetchSemanticsNodes().isNotEmpty()
        }
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.height
        val dockHeight = composeRule.onNodeWithTag("reader_settings_dock").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Reader settings panel should be compressed to roughly three tenths of the screen", dockHeight <= rootHeight * 0.32f)
        composeRule.onNodeWithText(text(R.string.settings_brightness)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_layout)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.layout_comfort)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_font_size)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_background)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_more)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.settings_show_progress)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.settings_page_turn)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(text(R.string.settings_reader_background, ""), substring = true).assertCountEquals(6)
        composeRule.onAllNodesWithText(text(R.string.action_close)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.settings_all_backgrounds)).assertCountEquals(0)

        composeRule.onRoot().performTouchInput { click(Offset(center.x * 0.35f, center.y * 0.72f)) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.settings_brightness)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText(text(R.string.settings_brightness)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).assertCountEquals(0)
        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.reader_toc)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.settings_brightness)).assertCountEquals(0)

        composeRule.onNodeWithContentDescription(text(R.string.reader_settings_content_description)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.settings_brightness)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(R.string.settings_more)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.settings_page_turn)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(R.string.settings_more)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_layout)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_page_turn)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_show_progress)).performScrollTo()
        composeRule.onNodeWithText(text(R.string.settings_show_progress)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_show_right_progress)).performScrollTo()
        composeRule.onNodeWithText(text(R.string.settings_show_right_progress)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_fullscreen_tap_next)).performScrollTo()
        composeRule.onNodeWithText(text(R.string.settings_fullscreen_tap_next)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(text(R.string.reader_settings_content_description)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.settings_brightness)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.reader_auto)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).assertCountEquals(0)

        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.reader_pause)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(R.string.reader_pause)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.reader_pause)).performClick()

        composeRule.waitUntil(7_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).assertCountEquals(0)
        composeRule.onRoot().performTouchInput { click(Offset(center.x, center.y)) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.reader_toc)).assertIsDisplayed()
    }

    @Test
    fun readerDirectoryAndBookmarksUseUnifiedOverlay() {
        seedBook("浮层测试小说", chapterCount = 260, progressChapterIndex = 234)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("浮层测试小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("浮层测试小说").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_toc)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(text(R.string.reader_toc)).performClick()
        composeRule.onNodeWithText(text(R.string.reader_toc_ascending)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.reader_read_percent, 0).substringBefore("0"), substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Chapter 235").assertCountEquals(1)
        composeRule.onAllNodesWithText("Chapter 001").assertCountEquals(0)
        composeRule.onNodeWithContentDescription(text(R.string.reader_close_overlay)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.reader_toc_ascending)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(text(R.string.reader_bookmarks)).performClick()
        composeRule.onNodeWithText(text(R.string.reader_no_bookmarks)).assertIsDisplayed()
    }

    @Test
    fun webBookReaderShowsRefreshButton() {
        seedBook(
            "网页追更测试",
            format = BookFormat.WEB,
            sourceUrl = "https://example.test/book",
            chapterSourceUrl = "https://example.test/book/1",
        )
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("网页追更测试").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("网页追更测试").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.reader_refresh)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.reader_refresh)).assertIsDisplayed()
    }

    @Test
    fun searchScreenShowsEmptyNoResultAndJumpsToReader() {
        seedBook("搜索测试小说")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("搜索测试小说").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("搜索测试小说").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.action_search)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.action_search)).performClick()
        composeRule.onNodeWithText(text(R.string.search_empty_title)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("不存在")
        composeRule.onNodeWithText(text(R.string.action_search)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.search_no_result_title)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.action_clear_search)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("山中")
        composeRule.onNodeWithText(text(R.string.action_search)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(quantityPrefix(R.plurals.search_results_found, 1), substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(searchChapterPrefix(1), substring = true)[0].performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.message_jumped_to_search_result)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun webImportDownloadTaskActionsRequireConfirmation() {
        seedDownloadTask()
        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithText(text(R.string.library_web_import)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.web_import_flow_title)).assertIsDisplayed()
        composeRule.onNodeWithText("失败任务").assertIsDisplayed()
        composeRule.onNodeWithText("3/10 · ${text(R.string.download_status_failed)}${text(R.string.web_import_failed_suffix, 1)}").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.web_import_show_error)).performClick()
        composeRule.onNodeWithText("章节下载失败").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        composeRule.onNodeWithText(text(R.string.web_import_cancel_task_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_keep)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.action_delete_task)).performClick()
        composeRule.onNodeWithText(text(R.string.web_import_delete_task_title)).assertIsDisplayed()
    }

    private fun text(@StringRes id: Int, vararg args: Any): String =
        composeRule.activity.getString(id, *args)

    private fun quantityText(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        composeRule.activity.resources.getQuantityString(id, quantity, *args)

    private fun quantityPrefix(@PluralsRes id: Int, quantity: Int): String =
        quantityText(id, quantity, quantity).substringBefore(quantity.toString())

    private fun searchChapterPrefix(chapterNumber: Int): String =
        text(R.string.search_chapter_position, chapterNumber, 0).substringBefore("0")

    private fun localizedText(locale: Locale, @StringRes id: Int, vararg args: Any): String {
        val config = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(locale)
        }
        return composeRule.activity.createConfigurationContext(config).getString(id, *args)
    }

    private fun localizedStringArray(locale: Locale, id: Int): Array<String> {
        val config = Configuration(composeRule.activity.resources.configuration).apply {
            setLocale(locale)
        }
        return composeRule.activity.createConfigurationContext(config).resources.getStringArray(id)
    }

    private fun seedBook(
        title: String,
        author: String? = null,
        chapterCount: Int = 1,
        progressChapterIndex: Int = 0,
        format: BookFormat = BookFormat.TXT,
        sourceUrl: String? = null,
        chapterSourceUrl: String? = null,
    ): String = runBlocking {
        val id = "ui-${System.nanoTime()}"
        val directory = java.io.File(application.filesDir, "books/$id/chapters").apply { mkdirs() }
        val chapters = (0 until chapterCount).map { index ->
            val chapterTitle = if (chapterCount == 1) "第一章 入山" else "Chapter %03d".format(index + 1)
            val content = "$chapterTitle\n" + "山中修行，自此开始。".repeat(200)
            val chapterFile = java.io.File(directory, "%05d.txt".format(index)).apply { writeText(content) }
            ChapterEntity(
                bookId = id,
                orderIndex = index,
                title = chapterTitle,
                contentPath = chapterFile.absolutePath,
                charCount = content.length,
                sourceUrl = chapterSourceUrl?.let { "$it-$index" },
            )
        }
        val now = System.currentTimeMillis()
        val chapterIds = application.container.database.readerDao().insertBookWithChapters(
            BookEntity(id, title, author, format.name, directory.parentFile!!.absolutePath, now, null, chapters.sumOf { it.charCount.toLong() }, chapterCount, sourceUrl),
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
        id
    }

    private fun seedDownloadTask() = runBlocking {
        val now = System.currentTimeMillis()
        application.container.database.readerDao().insertDownloadTask(
            DownloadTaskEntity(
                id = "download-${System.nanoTime()}",
                title = "失败任务",
                author = "测试作者",
                description = null,
                sourceUrl = "https://example.test/book",
                status = "FAILED",
                totalChapters = 10,
                completedChapters = 3,
                failedChapters = 1,
                contentSelector = "main",
                removeSelectorsJson = "[]",
                createdAt = now,
                updatedAt = now,
                importedBookId = null,
                error = "章节下载失败",
            ),
        )
    }
}
