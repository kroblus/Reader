package com.lightreader.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText(BOOK_TITLE).performScrollTo().assertIsDisplayed().performClick()
        val openingReader = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        try {
            // Presence of an icon is not the completion signal: first-page preview keeps
            // loading=true, while the reader's five-second chrome timer runs independently.
            // Wait for the book's actual ready state, then reveal chrome as a reader would.
            composeRule.waitUntil(10_000) {
                val value = openingReader.readerState.value
                value.book?.id == BOOK_ID && !value.loading && value.pages.isNotEmpty() &&
                    composeRule.onAllNodesWithTag(ReaderTestTags.READER).fetchSemanticsNodes().isNotEmpty()
            }
            if (!openingReader.readerState.value.toolbarVisible) {
                composeRule.onNodeWithTag(ReaderTestTags.READER).performTouchInput { click(center) }
            }
            composeRule.onNodeWithContentDescription(text(R.string.action_search)).assertIsDisplayed().assertIsEnabled()
        } catch (failure: Throwable) {
            val value = openingReader.readerState.value
            val tree = runCatching { composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 8) }
                .getOrElse { "UI tree unavailable: ${it.javaClass.simpleName}" }
            throw AssertionError("Before search: screen=${openingReader.uiState.value.screen}, book=${value.book?.id}, loading=${value.loading}, pages=${value.pages.size}, toolbar=${value.toolbarVisible}, error=${value.error}\n$tree", failure)
        }
        composeRule.onNodeWithContentDescription(text(R.string.action_search)).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(ReaderTestTags.SEARCH_INPUT).fetchSemanticsNodes().isNotEmpty() }
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

    @Test fun pagedResultsReturnToListAndOriginalSavedAnchor() {
        seedBook(matches = 131)
        val reader = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        val search = ViewModelProvider(composeRule.activity)[BookSearchViewModel::class.java]
        composeRule.runOnIdle { reader.openBook(BOOK_ID) }
        composeRule.waitUntil(10_000) { !reader.readerState.value.loading && reader.readerState.value.pages.size > 2 }
        composeRule.runOnIdle { reader.pageSelected(2) }
        val origin = reader.currentSearchOrigin()!!
        composeRule.runOnIdle { reader.openSearch(BOOK_ID) }
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_INPUT).performTextReplacement(QUERY)
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH_SUBMIT).performClick()
        composeRule.waitUntil(15_000) { !search.state.value.searching && search.state.value.results.size == 100 }
        assertTrue(search.state.value.hasMore)
        composeRule.onNodeWithText(text(R.string.search_continue)).performClick()
        composeRule.waitUntil(15_000) { search.state.value.complete }
        assertEquals(131, search.state.value.results.size)
        composeRule.onNodeWithTag("search_results_list").performScrollToIndex(80)
        composeRule.waitUntil(5_000) { search.state.value.listIndex >= 79 }
        val selectedNode = composeRule.onAllNodesWithTag(ReaderTestTags.SEARCH_RESULT)[0]
        selectedNode.performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()
        val savedIndex = search.state.value.listIndex
        selectedNode.performClick()
        try {
            composeRule.waitUntil(10_000) { !reader.readerState.value.loading && reader.readerState.value.chapterIndex == 1 }
        } catch (timeout: Throwable) {
            val value = reader.readerState.value
            throw AssertionError("Result navigation: screen=${reader.uiState.value.screen}, book=${value.book?.id}, chapter=${value.chapterIndex}, loading=${value.loading}, pages=${value.pages.size}, error=${value.error}", timeout)
        }
        composeRule.runOnIdle { reader.setToolbarVisible(true) }
        composeRule.onNodeWithText(text(R.string.reader_return_results)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH).assertIsDisplayed()
        assertEquals(savedIndex, search.state.value.listIndex)
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag(ReaderTestTags.SEARCH).assertIsDisplayed()
        assertEquals(savedIndex, search.state.value.listIndex)
        composeRule.onNodeWithText(text(R.string.search_return_origin)).performClick()
        composeRule.waitUntil(10_000) {
            val current = reader.readerState.value
            !current.loading && current.chapters.getOrNull(current.chapterIndex)?.id == origin.chapterId
        }
        composeRule.waitUntil(10_000) { runBlocking { application.container.bookRepository.progress(BOOK_ID)?.charOffset == origin.charOffset } }
        assertEquals(origin, reader.currentSearchOrigin())
    }

    private fun seedBook(matches: Int = 1): Long = runBlocking {
        val directory = File(application.filesDir, "books/$BOOK_ID/chapters").apply { mkdirs() }
        val chapters = (0..1).map { index ->
            val title = if (index == 0) "First Chapter" else "Second Chapter"
            val content = "$title\n" + "Morning light falls across the quiet river. ".repeat(40) +
                if (index == 1) "\nThe lighthouse opens the way home.\n".repeat(matches) else "\nThe journey begins.\n"
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
