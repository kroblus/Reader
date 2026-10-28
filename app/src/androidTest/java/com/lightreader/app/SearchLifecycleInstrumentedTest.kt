package com.lightreader.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.ui.ReaderTestTags
import com.lightreader.app.ui.ReaderViewModel
import com.lightreader.app.ui.BookSearchViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SearchLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val application: ReaderApplication
        get() = composeRule.activity.application as ReaderApplication

    @Before
    fun resetQaState() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa")) { "This test requires the isolated QA package." }
        application.container.database.clearAllTables()
        application.container.settingsRepository.save(ReaderPreferences())
    }

    @Test
    fun searchResultsSurviveActivityRecreationAndStillOpenTheirChapter() {
        val expectedChapterId = seedBook()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(BOOK_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(BOOK_TITLE).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription(text(R.string.action_search)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.action_search)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_INPUT).performTextReplacement(QUERY)
        composeRule.waitUntil(5_000) {
            !composeRule.onNodeWithTag(ReaderTestTags.SEARCH_SUBMIT).fetchSemanticsNode()
                .config.contains(SemanticsProperties.Disabled)
        }
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_SUBMIT).performClick()
        val searchViewModel = ViewModelProvider(composeRule.activity)[BookSearchViewModel::class.java]
        composeRule.waitUntil(10_000) { !searchViewModel.state.value.searching }
        assertEquals("The search session must retain the submitted query", QUERY, searchViewModel.state.value.query)
        assertEquals("Search state: ${searchViewModel.state.value}", 1, searchViewModel.state.value.results.size)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.SEARCH_RESULT).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_RESULT).performScrollTo().assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.SEARCH).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_INPUT).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(QUERY)),
        )
        composeRule.onAllNodesWithTag(ReaderTestTags.SEARCH_RESULT).assertCountEquals(1)
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_RESULT).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()

        // The result remains actionable and resolves to its actual second chapter after recreation.
        lateinit var readerViewModel: ReaderViewModel
        composeRule.runOnIdle {
            readerViewModel = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        }
        composeRule.waitUntil(10_000) {
            val reader = readerViewModel.readerState.value
            reader.chapterIndex == 1 && reader.pages.isNotEmpty() && !reader.loading
        }
        val reader = readerViewModel.readerState.value
        assertEquals(BOOK_ID, reader.book?.id)
        assertEquals(expectedChapterId, reader.chapters[reader.chapterIndex].id)
    }

    private fun seedBook(): Long = runBlocking {
        val directory = File(application.filesDir, "books/$BOOK_ID/chapters").apply { mkdirs() }
        val chapters = (0..1).map { index ->
            val title = if (index == 0) "First Chapter" else "Second Chapter"
            val content = "$title\n" + "Morning light falls across the quiet river. ".repeat(40) +
                if (index == 1) "\nThe lighthouse opens the way home.\n" else "\nThe journey begins.\n"
            val file = File(directory, "$index.txt").apply { writeText(content) }
            ChapterEntity(
                bookId = BOOK_ID,
                orderIndex = index,
                title = title,
                contentPath = file.absolutePath,
                charCount = content.length,
                sourceUrl = null,
            )
        }
        application.container.database.readerDao().insertBookWithChapters(
            BookEntity(
                id = BOOK_ID,
                title = BOOK_TITLE,
                author = "QA reader",
                format = BookFormat.TXT.name,
                rootPath = directory.parentFile!!.absolutePath,
                addedAt = System.currentTimeMillis(),
                lastReadAt = null,
                totalChars = chapters.sumOf { it.charCount.toLong() },
                chapterCount = chapters.size,
                sourceUrl = null,
            ),
            chapters,
        )[1]
    }

    private fun text(id: Int): String = composeRule.activity.getString(id)

    private companion object {
        const val BOOK_ID = "search-lifecycle-fixture"
        const val BOOK_TITLE = "A Quiet Journey"
        const val QUERY = "lighthouse"
    }
}
