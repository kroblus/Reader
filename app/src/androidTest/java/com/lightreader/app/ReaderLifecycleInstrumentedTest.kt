package com.lightreader.app

import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.journey.FixtureCatalog
import com.lightreader.app.journey.FixtureId
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var application: ReaderApplication

    @Before
    fun resetIsolatedQaData() = runBlocking {
        assertTrue("Lifecycle tests must use the isolated QA package", BuildConfig.APPLICATION_ID.endsWith(".qa"))
        application = composeRule.activity.application as ReaderApplication
        application.container.database.clearAllTables()
        application.container.settingsRepository.save(
            ReaderPreferences(pageTurnMode = PageTurnMode.NONE, autoReadIntervalSeconds = 3),
        )
    }

    @Test
    fun automaticReadingPausesWhileInactiveAndResumesFromSavedPosition() {
        val fixture = FixtureCatalog.materialize(composeRule.activity, FixtureId.MULTI_PAGE_TXT)
        val book = runBlocking { application.container.bookRepository.import(Uri.fromFile(fixture)) }
        val viewModel = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        composeRule.runOnIdle { viewModel.openBook(book.id) }
        composeRule.waitUntil(20_000) {
            val state = viewModel.readerState.value
            !state.loading && state.pages.size >= 4 && viewModel.uiState.value.preferences.autoReadIntervalSeconds == 3
        }
        composeRule.runOnIdle {
            viewModel.pageSelected(1)
            viewModel.setAutoReading(true)
        }
        val scenario = composeRule.activityRule.scenario
        try {
            // STARTED sends ON_PAUSE while the activity is still visible, such as under another activity.
            scenario.moveToState(Lifecycle.State.STARTED)
            val paused = viewModel.readerState.value
            SystemClock.sleep(6_500)
            assertEquals("Automatic reading must not turn pages while paused", paused.pageIndex, viewModel.readerState.value.pageIndex)
            assertEquals(paused.chapterIndex, viewModel.readerState.value.chapterIndex)

            // CREATED sends ON_STOP without disposing this activity's composition.
            scenario.moveToState(Lifecycle.State.CREATED)
            val stopped = viewModel.readerState.value
            val expectedOffset = stopped.pages[stopped.pageIndex].startOffset
            SystemClock.sleep(6_500)
            assertEquals("Background time must not consume unread pages", stopped.pageIndex, viewModel.readerState.value.pageIndex)
            assertEquals(stopped.chapterIndex, viewModel.readerState.value.chapterIndex)
            val saved = runBlocking { application.container.bookRepository.progress(book.id) }
            assertEquals("Leaving the foreground must persist the visible position", expectedOffset, saved?.charOffset)

            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil(8_000) {
                viewModel.readerState.value.pageIndex > stopped.pageIndex ||
                    viewModel.readerState.value.chapterIndex > stopped.chapterIndex
            }
        } finally {
            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.runOnIdle { viewModel.setAutoReading(false) }
        }
    }

    @Test
    fun immediatelyLeavingAndReopeningRestoresTheLastVisiblePage() {
        val fixture = FixtureCatalog.materialize(composeRule.activity, FixtureId.MULTI_PAGE_TXT)
        val book = runBlocking { application.container.bookRepository.import(Uri.fromFile(fixture)) }
        val viewModel = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        composeRule.runOnIdle { viewModel.openBook(book.id) }
        composeRule.waitUntil(20_000) {
            !viewModel.readerState.value.loading && viewModel.readerState.value.pages.size >= 4
        }
        var expectedOffset = -1
        composeRule.runOnIdle {
            viewModel.pageSelected(2)
            expectedOffset = viewModel.readerState.value.pages[2].startOffset
            // Keep all three actions in one main-thread turn: the debounced save cannot finish first.
            viewModel.goBack()
            viewModel.openBook(book.id)
        }
        composeRule.waitUntil(20_000) {
            val state = viewModel.readerState.value
            state.book?.id == book.id && !state.loading && state.pages.isNotEmpty()
        }
        val restored = viewModel.readerState.value
        assertEquals(expectedOffset, restored.pages[restored.pageIndex].startOffset)
        assertEquals(expectedOffset, runBlocking { application.container.bookRepository.progress(book.id)?.charOffset })
    }
}
