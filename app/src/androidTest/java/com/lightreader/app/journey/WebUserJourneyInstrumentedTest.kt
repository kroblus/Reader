package com.lightreader.app.journey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.lightreader.app.MainActivity
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.ui.ReaderTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WebUserJourneyInstrumentedTest {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val evidence = UserJourneyEvidenceRule(TestPersona.WEB_NOVEL_READER)

    private val application: ReaderApplication
        get() = composeRule.activity.application as ReaderApplication
    private lateinit var server: QaHttpsFixtureServer

    @Before
    fun setUp() = runBlocking {
        application.container.database.clearAllTables()
        server = QaHttpsFixtureServer()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun webReaderPreviewsDownloadsAndOpensAControlledHttpsNovel() {
        openWebImport()
        evidence.step("Parse a controlled HTTPS catalog through the production parser")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(server.url("/catalog"))
        composeRule.onNodeWithText(text(R.string.web_import_parse)).performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("LightReader QA Novel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("QA Author", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("QA正文可读", substring = true).assertIsDisplayed()

        evidence.step("Start the WorkManager download and wait for a completed task")
        composeRule.onNodeWithText(text(R.string.web_import_download_book)).performClick()
        composeRule.waitUntil(30_000) {
            runBlocking {
                application.container.database.readerDao().observeDownloadTasks().first().singleOrNull()?.status == "COMPLETED"
            }
        }
        val task = runBlocking { application.container.database.readerDao().observeDownloadTasks().first().single() }
        assertEquals(4, task.completedChapters)

        evidence.step("Open the downloaded web book from the completed task")
        composeRule.onNodeWithContentDescription(text(R.string.action_open_book)).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.reader_refresh)).assertIsDisplayed()
    }

    @Test
    fun webReaderReceivesActionableHttpAndAccessRestrictionErrors() {
        openWebImport()
        evidence.step("Map HTTP failure to the localized user message")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(server.url("/status/403"))
        composeRule.onNodeWithText(text(R.string.web_import_parse)).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text(R.string.message_web_http)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(R.string.message_web_http)).assertIsDisplayed()

        evidence.step("Map browser verification pages to access-restricted guidance")
        composeRule.onNode(hasSetTextAction()).performTextReplacement(server.url("/verification"))
        composeRule.onNodeWithText(text(R.string.web_import_parse)).performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text(R.string.message_web_access_restricted)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(R.string.message_web_access_restricted)).assertIsDisplayed()
    }

    private fun openWebImport() {
        composeRule.onNodeWithContentDescription(text(R.string.action_more)).performClick()
        composeRule.onNodeWithText(text(R.string.library_app_settings)).performClick()
        composeRule.onNodeWithText(text(R.string.library_web_import)).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderTestTags.WEB_IMPORT).assertIsDisplayed()
    }

    private fun text(id: Int, vararg args: Any): String = composeRule.activity.getString(id, *args)
}
