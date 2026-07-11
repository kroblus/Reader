package com.lightreader.app.journey

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.lightreader.app.MainActivity
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.ui.ReaderTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ReaderUserJourneyInstrumentedTest {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val evidence = UserJourneyEvidenceRule(TestPersona.LOCAL_LIBRARY_READER)

    private val application: ReaderApplication
        get() = composeRule.activity.application as ReaderApplication

    @Before
    fun resetQaState() = runBlocking {
        application.container.database.clearAllTables()
        application.container.keyStore.clear()
        application.container.settingsRepository.save(ReaderPreferences())
    }

    @Test
    fun firstTimeReaderNavigatesEveryEntryAndDeveloperToolsPersist() {
        evidence.step("Verify the first-launch empty shelf")
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_EMPTY).assertIsDisplayed()

        evidence.step("Open application settings from the overflow menu")
        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.library_dom_bridge)).assertCountEquals(0)

        evidence.step("Enable developer tools with the documented long-press gesture")
        composeRule.onNodeWithText(text(R.string.app_settings_title)).performTouchInput { longClick() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.library_dom_bridge)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithText(text(R.string.message_developer_tools_enabled)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS_DOM).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.dom_title)).assertIsDisplayed()

        evidence.step("Return and recreate the activity; the developer entry remains enabled")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag(ReaderTestTags.APP_SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_dom_bridge)).performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking { application.container.settingsRepository.preferences.first().developerToolsEnabled })
    }

    @Test
    fun bookmarkAndReadingPositionSurviveActivityRecreation() {
        val fixture = FixtureCatalog.materialize(composeRule.activity, FixtureId.UTF8_TXT)
        val book = runBlocking { application.container.bookRepository.import(android.net.Uri.fromFile(fixture)) }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(book.title).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(book.title).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()

        evidence.step("Bookmark the current page and verify persistence")
        composeRule.onNodeWithContentDescription(text(R.string.reader_bookmark_add)).performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { application.container.bookRepository.bookmarks(book.id).first().size == 1 }
        }
        val saved = runBlocking { application.container.bookRepository.bookmarks(book.id).first().single() }
        assertTrue(saved.excerpt.isNotBlank())

        evidence.step("Recreate the activity; the retained ViewModel keeps the active reader and bookmark")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.READER).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text(R.string.reader_bookmarks)).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER_BOOKMARKS).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.reader_no_bookmarks)).assertCountEquals(0)
        assertEquals(saved.id, runBlocking { application.container.bookRepository.bookmarks(book.id).first().single().id })
    }

    @Test
    fun readerWindowPreferencesApplyAndAreRestoredOnExit() {
        val fixture = FixtureCatalog.materialize(composeRule.activity, FixtureId.UTF8_TXT)
        val book = runBlocking { application.container.bookRepository.import(android.net.Uri.fromFile(fixture)) }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(book.title).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(book.title).performClick()

        evidence.step("Enable brightness override, keep-screen-on and portrait lock")
        runBlocking {
            application.container.settingsRepository.save(
                ReaderPreferences(brightness = .42f, keepScreenOn = true, lockPortrait = true),
            )
        }
        composeRule.waitUntil(5_000) {
            composeRule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT &&
                composeRule.activity.window.attributes.screenBrightness in .41f..43f &&
                composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        }

        evidence.step("Leave the reader and verify transient window state is restored")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0
        }
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, composeRule.activity.window.attributes.screenBrightness)
    }

    private fun text(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)
}
