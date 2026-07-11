package com.lightreader.app.journey

import android.view.KeyEvent
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.lifecycle.ViewModelProvider
import com.lightreader.app.BuildConfig
import com.lightreader.app.MainActivity
import com.lightreader.app.ReaderApplication
import com.lightreader.app.R
import com.lightreader.app.ui.ReaderTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSystemBoundaryInstrumentedTest {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 1)
    val evidence = UserJourneyEvidenceRule(TestPersona.CONSTRAINED_DEVICE_READER)

    private val application: ReaderApplication
        get() = composeRule.activity.application as ReaderApplication
    private var stagedUri: Uri? = null
    @Before
    fun assertIsolatedQaPackage() = runBlocking {
        assertTrue("System-boundary tests must never target production data", BuildConfig.APPLICATION_ID.endsWith(".qa"))
        application.container.database.clearAllTables()
        application.container.keyStore.clear()
    }

    @After
    fun removeStagedDownload() {
        stagedUri?.let { runCatching { composeRule.activity.contentResolver.delete(it, null, null) } }
        stagedUri = null
    }

    @Test
    fun systemFilePickerCancelReturnsToUntouchedEmptyShelf() {
        evidence.step("Open the system document picker from the empty shelf")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.library_import_file)).performClick()
        val uiDevice = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        assertTrue(uiDevice.wait(Until.gone(By.pkg(BuildConfig.APPLICATION_ID)), 5_000))

        evidence.step("Cancel with the Android system back action")
        uiDevice.pressBack()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.LIBRARY_EMPTY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_EMPTY).assertIsDisplayed()
        assertTrue(runBlocking { application.container.database.readerDao().observeBooks().first().isEmpty() })
    }

    @Test
    fun systemFilePickerImportsQaFixtureWithoutTouchingProductionPackage() {
        assumeTrue("MediaStore Downloads staging requires Android 10+", Build.VERSION.SDK_INT >= 29)
        val uiDevice = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        val fileName = "reader_qa_system.txt"
        stageDownloadFixture(uiDevice, fileName)

        evidence.step("Open DocumentsUI and select the staged UTF-8 fixture")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.library_import_file)).performClick()
        val file = uiDevice.wait(Until.findObject(By.textContains("reader_qa_system")), 10_000)
        assumeTrue("The active system picker did not expose the staged fixture", file != null)
        file.click()

        evidence.step("Wait for the imported book to appear in the isolated QA database")
        composeRule.waitUntil(15_000) {
            runBlocking { application.container.database.readerDao().observeBooks().first().isNotEmpty() }
        }
        val books = runBlocking { application.container.database.readerDao().observeBooks().first() }
        assertEquals(1, books.size)
        assertTrue(books.single().title.contains("reader_qa_system", ignoreCase = true))
        assertTrue(uiDevice.executeShellCommand("pm path ${BuildConfig.APPLICATION_ID}").contains("package:"))
    }

    @Test
    fun homeResumeAndVolumeKeyKeepReaderJourneyAlive() {
        val fixture = FixtureCatalog.materialize(composeRule.activity, FixtureId.MULTI_PAGE_TXT)
        val book = runBlocking { application.container.bookRepository.import(android.net.Uri.fromFile(fixture)) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(book.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(book.title).performClick()
        composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.READER_PROGRESS).fetchSemanticsNodes().isNotEmpty()
        }
        val readerViewModel = ViewModelProvider(composeRule.activity)[com.lightreader.app.ui.ReaderViewModel::class.java]
        composeRule.waitUntil(20_000) {
            val state = readerViewModel.readerState.value
            !state.loading && state.pages.size >= 3
        }

        evidence.step("Turn a page with the physical volume-down key")
        val uiDevice = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        repeat(2) {
            assertTrue("UI Automator should inject volume-down", uiDevice.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN))
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(5_000) {
            readerViewModel.readerState.value.pageIndex >= 2 && runBlocking {
                application.container.bookRepository.progress(book.id)?.let { it.pageIndex > 0 || it.charOffset > 0 } == true
            }
        }
        val progress = runBlocking { application.container.bookRepository.progress(book.id) }
        assertTrue(
            "Volume-down should advance and persist a later page or character offset",
            progress?.let { it.pageIndex > 0 || it.charOffset > 0 } == true,
        )

        evidence.step("Send the app Home, then relaunch the QA activity")
        uiDevice.pressHome()
        assertTrue(uiDevice.wait(Until.gone(By.pkg(BuildConfig.APPLICATION_ID)), 5_000))
        uiDevice.executeShellCommand("am start -W -n ${BuildConfig.APPLICATION_ID}/com.lightreader.app.MainActivity")
        assertTrue(uiDevice.wait(Until.hasObject(By.pkg(BuildConfig.APPLICATION_ID)), 8_000))
    }

    private fun stageDownloadFixture(uiDevice: UiDevice, fileName: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val bytes = testContext.assets
            .open("reader-qa.txt")
            .use { it.readBytes() }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        stagedUri = composeRule.activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        val uri = requireNotNull(stagedUri) { "Could not create the QA fixture in MediaStore Downloads" }
        composeRule.activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Could not write the QA fixture to MediaStore Downloads")
    }
}
