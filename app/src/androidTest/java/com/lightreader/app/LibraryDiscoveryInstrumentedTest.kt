package com.lightreader.app

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.data.ReadingProgressEntity
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.ui.ReaderTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises discovery against real Room rows and the app's navigation/saveable state. */
@RunWith(AndroidJUnit4::class)
class LibraryDiscoveryInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val application: ReaderApplication
        get() = composeRule.activity.application as ReaderApplication
    private val device by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }

    @Before
    fun resetQaState() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa")) { "This test requires the isolated QA package." }
        application.container.database.clearAllTables()
        application.container.settingsRepository.save(ReaderPreferences())
    }

    @Test
    fun continueReadingAndDiscoverySurviveNavigation() {
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_EMPTY).assertIsDisplayed()
        seedBook("reading", "Zulu River", "Mira", addedAt = 1_000, lastReadAt = 2_000)
        seedBook("imported", "Alpha Trail", "Ada", addedAt = 3_000, lastReadAt = null)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text(R.string.library_continue_button)).fetchSemanticsNodes().isNotEmpty()
        }

        // The newer unread import must not take ownership of the continue-reading card.
        composeRule.onNode(hasText("Zulu River") and hasText(text(R.string.library_continue_button)))
            .assertIsDisplayed()
        composeRule.onAllNodes(hasText("Alpha Trail") and hasText(text(R.string.library_continue_button)))
            .assertCountEquals(0)
        capture("01_populated")

        searchFor("rIvEr")
        composeRule.onNodeWithText("Zulu River").assertIsDisplayed()
        composeRule.onAllNodesWithText("Alpha Trail").assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.library_continue_button)).assertCountEquals(0)

        searchFor("  ADA  ")
        composeRule.onNodeWithText("Alpha Trail").assertIsDisplayed()
        composeRule.onAllNodesWithText("Zulu River").assertCountEquals(0)
        capture("02_filtered")

        searchFor("missing novel")
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_NO_RESULTS).assertIsDisplayed()
        capture("03_no_results")
        composeRule.onNodeWithContentDescription(text(R.string.action_clear_search)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_SEARCH).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
        composeRule.onNode(hasText("Zulu River") and hasText(text(R.string.library_continue_button)))
            .assertIsDisplayed()

        // Both authors match. Verify the selected sort and access both books, including off-screen
        // lazy items on narrow screens. The projection unit tests verify their exact ordering.
        searchFor("a")
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_SORT).performClick()
        composeRule.onNodeWithText(text(R.string.library_sort_title)).performClick()
        assertTitleSortSelected()
        composeRule.onNode(hasText("Alpha Trail") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Zulu River") and hasClickAction())
        composeRule.onNode(hasText("Zulu River") and hasClickAction()).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.LIBRARY_SEARCH).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_SEARCH).assertTextEquals("a")
        assertTitleSortSelected()
        composeRule.onAllNodesWithText(text(R.string.library_continue_button)).assertCountEquals(0)
    }

    private fun searchFor(query: String) {
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_SEARCH).performTextReplacement(query)
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_SEARCH).performImeAction()
    }

    private fun assertTitleSortSelected() {
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_SORT).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.library_sort_title)),
        )
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        device.waitForIdle(1_000)
        // IME insets animate outside Compose's idle synchronization after submitting the query.
        android.os.SystemClock.sleep(500)
        composeRule.waitForIdle()
        // Public QA evidence survives Orchestrator clearing the isolated app's data.
        val configuration = composeRule.activity.resources.configuration
        val scale = (configuration.fontScale * 100).toInt()
        val directory = "/sdcard/Download/LightReader-LibraryDiscovery/${configuration.screenWidthDp}dp-${scale}font"
        device.executeShellCommand("mkdir -p $directory")
        device.executeShellCommand("screencap -p $directory/$name.png")
    }

    private fun seedBook(id: String, title: String, author: String, addedAt: Long, lastReadAt: Long?) = runBlocking {
        val directory = File(application.filesDir, "books/discovery-$id/chapters").apply { mkdirs() }
        val chapterTitle = "Chapter One"
        val content = "$chapterTitle\n" + "A quiet path leads beyond the river. ".repeat(80)
        val chapterFile = File(directory, "00000.txt").apply { writeText(content) }
        val dao = application.container.database.readerDao()
        val chapterIds = dao.insertBookWithChapters(
            BookEntity(
                id = id,
                title = title,
                author = author,
                format = BookFormat.TXT.name,
                rootPath = directory.parentFile!!.absolutePath,
                addedAt = addedAt,
                lastReadAt = lastReadAt,
                totalChars = content.length.toLong(),
                chapterCount = 1,
                sourceUrl = null,
            ),
            listOf(ChapterEntity(bookId = id, orderIndex = 0, title = chapterTitle, contentPath = chapterFile.absolutePath, charCount = content.length, sourceUrl = null)),
        )
        if (lastReadAt != null) {
            dao.saveProgress(
                ReadingProgressEntity(
                    bookId = id,
                    chapterId = chapterIds.single(),
                    charOffset = 100,
                    chapterIndex = 0,
                    pageIndex = 0,
                    chapterTitle = chapterTitle,
                    styleHash = 0,
                    updatedAt = lastReadAt,
                ),
            )
        }
    }

    private fun text(@StringRes id: Int): String = composeRule.activity.getString(id)
}
