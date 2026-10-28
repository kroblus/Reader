package com.lightreader.app.journey

import android.view.KeyEvent
import android.content.ContentValues
import android.content.Intent
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
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
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
        awaitAppWindow(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))
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
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        awaitSystemPickerWindow(uiDevice)

        evidence.step("Cancel with the Android system back action")
        assertTrue("UI Automator should inject Back", uiDevice.pressBack())
        awaitAppWindow(uiDevice)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(ReaderTestTags.LIBRARY_EMPTY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ReaderTestTags.LIBRARY_EMPTY).assertIsDisplayed()
        assertTrue(runBlocking { application.container.database.readerDao().observeBooks().first().isEmpty() })
    }

    @Test
    fun systemFilePickerImportsQaFixtureWithoutTouchingProductionPackage() {
        assumeTrue("MediaStore Downloads staging requires Android 10+", Build.VERSION.SDK_INT >= 29)
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val fileName = "reader_qa_system.txt"
        stageDownloadFixture(uiDevice, fileName)

        evidence.step("Open DocumentsUI and select the staged UTF-8 fixture")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.library_import_file)).performClick()
        awaitSystemPickerWindow(uiDevice)
        val file = uiDevice.wait(Until.findObject(By.textContains("reader_qa_system")), 10_000)
        assumeTrue("The active system picker did not expose the staged fixture", file != null)
        file.click()
        awaitAppWindow(uiDevice)

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
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        awaitAppWindow(uiDevice)
        repeat(2) {
            val expectedPage = readerViewModel.readerState.value.pageIndex + 1
            assertTrue("UI Automator should inject volume-down", uiDevice.pressKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN))
            composeRule.waitUntil(5_000) {
                readerViewModel.readerState.value.pageIndex >= expectedPage
            }
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
        val pageBeforeHome = readerViewModel.readerState.value.pageIndex
        uiDevice.pressHome()
        assertTrue(uiDevice.wait(Until.gone(By.pkg(BuildConfig.APPLICATION_ID)), 5_000))
        // Use launcher semantics and reuse the existing top Activity, rather than creating a
        // second MainActivity while ActivityScenario still tracks the original instance.
        val launcherFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        uiDevice.executeShellCommand(
            "am start -W -a ${Intent.ACTION_MAIN} -c ${Intent.CATEGORY_LAUNCHER} " +
                "-f 0x${launcherFlags.toString(16)} -n ${BuildConfig.APPLICATION_ID}/com.lightreader.app.MainActivity",
        )
        awaitAppWindow(uiDevice)
        composeRule.onNodeWithTag(ReaderTestTags.READER).assertIsDisplayed()
        assertEquals(book.id, readerViewModel.readerState.value.book?.id)
        assertEquals(pageBeforeHome, readerViewModel.readerState.value.pageIndex)
    }

    private fun awaitAppWindow(uiDevice: UiDevice) {
        val activity = composeRule.activity
        composeRule.waitUntil(5_000) {
            dismissKnownSystemOverlays(uiDevice)
            var ready = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ready = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    activity.hasWindowFocus()
            }
            ready && uiDevice.hasObject(By.pkg(BuildConfig.APPLICATION_ID))
        }
    }

    private fun awaitSystemPickerWindow(uiDevice: UiDevice) {
        composeRule.waitUntil(5_000) {
            dismissKnownSystemOverlays(uiDevice)
            uiDevice.currentPackageName in setOf("com.android.documentsui", "com.google.android.documentsui")
        }
    }

    private fun dismissKnownSystemOverlays(uiDevice: UiDevice) {
        // A launcher ANR can outlive an orchestrated test. Never dismiss a Reader ANR.
        val launcherAnr = By.pkg("android").res("android", "alertTitle").text("Quickstep isn't responding")
        if (uiDevice.hasObject(launcherAnr)) {
            uiDevice.findObject(By.pkg("android").res("android", "aerr_close"))?.click()
            assertTrue("The Quickstep system dialog should close", uiDevice.wait(Until.gone(launcherAnr), 5_000))
        }
        val immersiveHelp = By.pkg("android").res("android", "immersive_cling_title")
        if (uiDevice.hasObject(immersiveHelp)) {
            uiDevice.findObject(By.pkg("android").res("android", "ok"))?.click()
            assertTrue("The system full-screen introduction should close", uiDevice.wait(Until.gone(immersiveHelp), 5_000))
        }
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
