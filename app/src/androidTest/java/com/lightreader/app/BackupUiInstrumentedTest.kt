package com.lightreader.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.data.ReadingProgressEntity
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.journey.TestEvidenceOutput
import com.lightreader.app.ui.BackupRestoreViewModel
import com.lightreader.app.ui.ReaderTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real navigation, both SAF launch/cancel contracts and a production-repository duplicate preview. */
@RunWith(AndroidJUnit4::class)
class BackupUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as ReaderApplication
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun libraryBackupEntryPickerCancellationAndDuplicatePreviewKeepExistingProgress() {
        check(BuildConfig.APPLICATION_ID == "com.lightreader.app.qa" && app.packageName == BuildConfig.APPLICATION_ID)
        val container = app.container
        val dao = container.database.readerDao()
        val body = "序章😀正文 preserve this reading position.\n".repeat(12)
        val bookId = "backup-ui-fixture"
        val archive = File(app.cacheDir, "backup-ui-fixture.zip")
        val currentOffset = body.indexOf("preserve") + 3
        runBlocking {
            container.backupRepository.awaitReady()
            container.database.clearAllTables()
            container.settingsRepository.save(ReaderPreferences())
            val directory = File(app.filesDir, "books/$bookId").apply { mkdirs() }
            val file = File(directory, "chapter.txt").apply { writeText(body) }
            val chapterId = dao.insertBookWithChapters(BookEntity(bookId, "Backup UI fixture", null, "TXT", directory.absolutePath,
                1, 2, body.length.toLong(), 1, null), listOf(ChapterEntity(bookId = bookId, orderIndex = 0,
                title = "Chapter", contentPath = file.absolutePath, charCount = body.length, sourceUrl = null))).single()
            dao.saveProgress(ReadingProgressEntity(bookId, chapterId, 4, 0, 0, "Chapter", 0, 10))
            assertEquals(1, container.backupRepository.exportTo(archive))
            // This newer local progress must survive restoring the older archive as a duplicate.
            dao.saveProgress(ReadingProgressEntity(bookId, chapterId, currentOffset, 0, 0, "Chapter", 0, 20))
        }
        try {
            awaitAppWindow()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Backup UI fixture").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(text(R.string.action_more)).performClick()
            compose.onNodeWithText(text(R.string.library_app_settings)).performClick()
            compose.onNodeWithTag(ReaderTestTags.APP_SETTINGS).assertIsDisplayed()
            compose.onNode(hasText(text(R.string.backup_title)) and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.backup_export)).assertIsDisplayed()

            compose.onNodeWithText(text(R.string.backup_export)).performClick()
            awaitDocumentsUi()
            cancelSystemPicker()
            assertUnchanged(bookId, currentOffset, body)
            compose.onNodeWithText(text(R.string.backup_import)).performClick()
            awaitDocumentsUi()
            cancelSystemPicker()
            assertUnchanged(bookId, currentOffset, body)

            val vm = ViewModelProvider(compose.activity)[BackupRestoreViewModel::class.java]
            assertNull(vm.state.value.preview)
            assertNull(vm.state.value.result)
            assertNull(vm.state.value.exportedBooks)
            assertFalse(vm.state.value.busy)
            // No fake state is injected: inspect invokes the same validator/repository as SAF selection.
            compose.runOnIdle { vm.inspect(Uri.fromFile(archive)) }
            compose.waitUntil(10_000) { !vm.state.value.busy && (vm.state.value.preview != null || vm.state.value.error != null) }
            assertNull(vm.state.value.error)
            val preview = requireNotNull(vm.state.value.preview)
            assertTrue(preview.restorableBooks.isEmpty())
            assertEquals(setOf(bookId), preview.duplicateBookIds)
            val summary = compose.activity.getString(R.string.backup_preview, 0, 1, 0)
            compose.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Backup UI fixture · ${text(R.string.backup_duplicate)}").assertExists()
            capture("duplicate-preview")
            compose.onNodeWithText(text(R.string.backup_confirm)).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText(text(R.string.backup_settings)).assertExists()
            compose.onNode(isToggleable()).assertIsOff()
            compose.onNode(isToggleable()).performScrollTo().performClick().assertIsOn()
            compose.onNodeWithText(text(R.string.backup_confirm)).performScrollTo().assertIsEnabled()
            capture("settings-restore-enabled")
            compose.onNodeWithText(text(R.string.backup_confirm)).performClick()
            compose.waitUntil(10_000) { !vm.state.value.busy && (vm.state.value.result != null || vm.state.value.error != null) }
            assertNull(vm.state.value.error)
            val result = requireNotNull(vm.state.value.result)
            assertEquals(0, result.restored)
            assertEquals(1, result.skipped)
            assertTrue(result.settingsRestored)
            assertUnchanged(bookId, currentOffset, body)
            compose.onNodeWithText(compose.activity.getString(R.string.backup_result, 0, 1, 0)).performScrollTo().assertIsDisplayed()
            capture("duplicate-result")
            TestEvidenceOutput.writeText("backup-ui/result.json", JSONObject().put("passed", true)
                .put("systemPickersCanceled", 2).put("duplicateBooks", 1).put("restoredBooks", 0)
                .put("originalProgressRetained", true).put("optionalSettingsRestored", true).toString())
        } finally { archive.delete() }
    }

    private fun assertUnchanged(bookId: String, offset: Int, body: String) = runBlocking {
        val dao = app.container.database.readerDao()
        assertEquals(listOf(bookId), dao.observeBooks().first().map { it.id })
        assertEquals(offset, dao.progress(bookId)!!.charOffset)
        assertEquals(body, File(dao.chapters(bookId).single().contentPath).readText())
    }

    private fun awaitDocumentsUi() {
        compose.waitUntil(5_000) { device.currentPackageName in DOCUMENT_PACKAGES }
        assertTrue("The actual Android DocumentsUI must own the window", device.currentPackageName in DOCUMENT_PACKAGES)
    }

    private fun cancelSystemPicker() {
        // CreateDocument may first dismiss its filename IME; a second Back closes DocumentsUI.
        repeat(3) {
            if (device.currentPackageName in DOCUMENT_PACKAGES) {
                // The event wait can report false after DocumentsUI already closed.
                // Accept that only when Reader actually owns the window; the resumed
                // activity, backup screen and original data are still asserted below.
                val reportedSuccess = device.pressBack()
                device.waitForIdle(1_000)
                assertTrue("Back must report success or return to Reader",
                    reportedSuccess || device.currentPackageName == BuildConfig.APPLICATION_ID)
            }
        }
        awaitAppWindow()
        compose.onNodeWithText(text(R.string.backup_export)).assertIsDisplayed()
    }

    private fun awaitAppWindow() {
        val activity = compose.activity
        compose.waitUntil(5_000) {
            var resumed = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus()
            }
            resumed && device.hasObject(By.pkg(BuildConfig.APPLICATION_ID))
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val runId = InstrumentationRegistry.getArguments().getString("runId")
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_") ?: "direct-${android.os.Process.myPid()}"
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            PlatformTestStorageRegistry.getInstance().openOutputFile("$runId/backup-ui/$name.png").use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
    }

    private companion object {
        val DOCUMENT_PACKAGES = setOf("com.android.documentsui", "com.google.android.documentsui")
    }
}
