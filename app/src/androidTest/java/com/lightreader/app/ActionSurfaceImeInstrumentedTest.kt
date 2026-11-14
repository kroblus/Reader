package com.lightreader.app

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.journey.FixtureCatalog
import com.lightreader.app.journey.FixtureId
import com.lightreader.app.journey.TestEvidenceOutput
import com.lightreader.app.ui.AppScreen
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Real-frame regression across Activity, popup and Dialog roots, including opening/closing. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ActionSurfaceImeInstrumentedTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var activity: MainActivity
    private lateinit var viewModel: ReaderViewModel
    private val recorder = Recorder()
    private val checks = mutableListOf<JSONObject>()
    private val violations = mutableListOf<String>()

    @Test fun libraryThemeSelectionAndDismissalNeverStartAnInputSession() = exercise("library-theme") {
        for (preparation in Preparation.entries) {
            prepareSearch(preparation)
            measure("theme-${preparation.name}", allowInitialIme = preparation == Preparation.VISIBLE) {
                clickDescription(R.string.action_more)
                clickText(R.string.library_change_skin)
                sheetOpened(R.string.library_skin_title)
                clickText(R.string.skin_ocean)
                settle()
                clickText(R.string.skin_sakura)
                settle()
                assertTrue(device.pressBack())
                awaitLibraryFocus()
            }
        }
        measure("theme-outside-dismissal") {
            clickDescription(R.string.action_more)
            clickText(R.string.library_change_skin)
            sheetOpened(R.string.library_skin_title)
            // The top part of the screen is the sheet scrim, outside all sheet actions.
            device.click(device.displayWidth / 2, device.displayHeight / 8)
            awaitLibraryFocus()
        }
        verifySearchInput()
    }

    @Test fun sortingBookActionsAndImportSheetDoNotRestoreSearchIme() = exercise("shelf-actions") {
        for (preparation in Preparation.entries) {
            prepareSearch(preparation)
            measure("sort-${preparation.name}", allowInitialIme = preparation == Preparation.VISIBLE) {
                clickDescription(R.string.library_sort)
                clickText(R.string.library_sort_title)
                awaitLibraryFocus()
            }
            prepareSearch(preparation)
            measure("book-menu-${preparation.name}", allowInitialIme = preparation == Preparation.VISIBLE) {
                clickDescription(R.string.library_book_actions)
                visibleText(R.string.library_edit_book_info)
                settle()
                assertTrue(device.pressBack())
                awaitLibraryFocus()
            }
            // The bottom FAB is covered by the software keyboard; test only reachable taps.
            if (preparation != Preparation.VISIBLE) {
                prepareSearch(preparation)
                measure("import-sheet-${preparation.name}") {
                    clickDescription(R.string.library_import_novel)
                    sheetOpened(R.string.library_import_choose)
                    assertTrue(device.pressBack())
                    awaitLibraryFocus()
                }
            }
        }
        measure("delete-confirmation-cancel") {
            clickDescription(R.string.library_book_actions)
            clickText(R.string.action_delete)
            visibleText(R.string.library_delete_book_title)
            settle()
            clickText(R.string.action_cancel)
            awaitLibraryFocus()
        }
        verifySearchInput()
    }

    @Test fun settingsThemeLanguageAndLicenseWindowsDoNotShowIme() = exercise("settings-actions") {
        prepareSearch(Preparation.BACK_DISMISSED)
        measure("enter-settings") {
            clickDescription(R.string.action_more)
            clickText(R.string.library_app_settings)
            await("settings route") { viewModel.uiState.value.screen is AppScreen.AppSettings }
            settle()
        }
        measure("settings-theme") {
            clickText(R.string.library_change_skin, scroll = true)
            sheetOpened(R.string.library_skin_title)
            clickText(R.string.skin_apricot)
            settle()
            assertTrue(device.pressBack())
            await("activity focus after theme") { activity.hasWindowFocus() }
        }
        // Return to the first group without using a ViewModel shortcut.
        repeat(3) {
            device.swipe(device.displayWidth / 2, device.displayHeight / 3,
                device.displayWidth / 2, device.displayHeight * 4 / 5, 30)
        }
        settle()
        measure("language-sheet") {
            clickText(R.string.app_settings_language, scroll = true)
            sheetOpened(R.string.language_system_caption)
            clickText(R.string.language_system)
            await("activity focus after language") { activity.hasWindowFocus() }
        }
        measure("licenses-dialog") {
            clickText(R.string.app_settings_licenses, scroll = true)
            visibleText(R.string.app_settings_licenses_body)
            settle()
            clickText(R.string.action_done)
            await("activity focus after licenses") { activity.hasWindowFocus() }
        }
        measure("settings-back") {
            clickDescription(R.string.action_back)
            awaitLibraryFocus()
        }
        verifySearchInput()
    }

    @Test fun bookMetadataDialogStillAcceptsKeyboardInput() = exercise("editable-dialog") {
        clickDescription(R.string.library_book_actions)
        clickText(R.string.library_edit_book_info)
        val field = editor()
        val before = field.text.orEmpty()
        field.click()
        recorder.awaitIme(true)
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_Q))
        await("metadata keyboard input") { editor().text.orEmpty() != before }
        assertTrue(device.pressBack())
        recorder.awaitIme(false)
        settle()
        clickText(R.string.action_cancel)
        awaitLibraryFocus()
        verifySearchInput()
    }

    private fun exercise(name: String, body: () -> Unit) {
        activityRule.scenario.onActivity {
            activity = it
            viewModel = ViewModelProvider(it)[ReaderViewModel::class.java]
        }
        val app = activity.application as ReaderApplication
        check(app.packageName == "com.lightreader.app.qa")
        val originalHardwareIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        val originalIdleTimeout = Configurator.getInstance().waitForIdleTimeout
        check(originalHardwareIme in listOf("null", "0", "1"))
        var failure: Throwable? = null
        try {
            // Observe consecutive taps while popup/sheet animations are still running.
            if (name == "library-theme") Configurator.getInstance().waitForIdleTimeout = 0
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            runBlocking {
                app.container.database.clearAllTables()
                app.container.settingsRepository.save(ReaderPreferences())
                app.container.bookRepository.import(Uri.fromFile(FixtureCatalog.materialize(activity, FixtureId.UTF8_TXT)))
            }
            awaitLibraryFocus()
            editor()
            activityRule.scenario.onActivity { recorder.start() }
            recorder.awaitIme(false)
            body()
            assertTrue("Unexpected IME frames: ${violations.joinToString()}", violations.isEmpty())
        } catch (error: Throwable) {
            failure = error
            val hierarchy = ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            TestEvidenceOutput.writeText("interaction/action-surface-failure-$name.xml", hierarchy.toString("UTF-8"))
            throw error
        } finally {
            Configurator.getInstance().waitForIdleTimeout = originalIdleTimeout
            activityRule.scenario.onActivity { recorder.stop() }
            if (originalHardwareIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $originalHardwareIme")
            check(device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim() == originalHardwareIme)
            val frames = recorder.snapshot()
            TestEvidenceOutput.writeText("interaction/action-surface-ime-$name.json", JSONObject()
                .put("passed", failure == null).put("failure", failure?.toString() ?: JSONObject.NULL)
                .put("sdk", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT)
                .put("clock", "real Choreographer; no Compose test clock")
                .put("observation", "read-only WindowInsets on every WindowInspector root; no listeners replaced")
                .put("hardwareImeRestored", true).put("checks", JSONArray(checks))
                .put("violations", JSONArray(violations)).put("frameCount", frames.size)
                .put("frames", JSONArray(frames.map { it.json })).toString())
        }
    }

    private fun prepareSearch(preparation: Preparation) {
        recorder.phase = "prepare-${preparation.name}"
        if (preparation != Preparation.UNTOUCHED) {
            editor().click()
            recorder.awaitIme(true)
            if (preparation == Preparation.BACK_DISMISSED) {
                assertTrue(device.pressBack())
                recorder.awaitIme(false)
            }
        } else recorder.awaitIme(false)
    }

    private fun measure(name: String, allowInitialIme: Boolean = false, action: () -> Unit) {
        recorder.phase = name
        val start = SystemClock.elapsedRealtimeNanos()
        action()
        recorder.awaitIme(false, durationMs = 700)
        val frames = recorder.snapshot().filter { it.ns >= start }
        assertTrue("No real frames recorded for $name", frames.size >= 10)
        val forbidden = (if (allowInitialIme) frames.dropWhile { it.ime } else frames).filter { it.ime }
        checks += JSONObject().put("name", name).put("frames", frames.size).put("forbiddenImeFrames", forbidden.size)
        if (forbidden.isNotEmpty()) violations += "$name: ${forbidden.size} frames; first=${forbidden.first().json}"
    }

    private fun verifySearchInput() {
        recorder.phase = "verify-editor"
        val field = editor()
        val before = field.text.orEmpty()
        field.click()
        recorder.awaitIme(true)
        assertTrue(device.pressKeyCode(KeyEvent.KEYCODE_Q))
        await("real keyboard input") { editor().text.orEmpty() != before }
    }

    private fun editor(): UiObject2 = requireNotNull(device.wait(Until.findObject(By.pkg(activity.packageName).clazz("android.widget.EditText")), 5_000))
    private fun visibleText(@StringRes id: Int) = requireNotNull(device.wait(Until.findObject(By.textContains(activity.getString(id))), 5_000)) { "Missing ${activity.getString(id)}" }
    private fun clickText(@StringRes id: Int, scroll: Boolean = false) {
        if (scroll) repeat(6) {
            if (device.findObject(By.textContains(activity.getString(id))) != null) return@repeat
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                device.displayWidth / 2, device.displayHeight / 3, 25)
            settle()
        }
        visibleText(id).click()
    }
    private fun clickDescription(@StringRes id: Int) = requireNotNull(device.wait(Until.findObject(By.desc(activity.getString(id))), 5_000)) { "Missing ${activity.getString(id)}" }.click()
    private fun sheetOpened(@StringRes title: Int) {
        visibleText(title)
        await("independent sheet window") { recorder.snapshot().lastOrNull()?.rootCount?.let { it >= 2 } == true }
        settle()
    }
    private fun settle() = SystemClock.sleep(600)
    private fun awaitLibraryFocus() = await("library focus") { viewModel.uiState.value.screen is AppScreen.Library && activity.hasWindowFocus() }
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertTrue("Timed out: $description", condition())
    }

    private enum class Preparation { UNTOUCHED, BACK_DISMISSED, VISIBLE }
    private data class Frame(val ns: Long, val ime: Boolean, val rootCount: Int, val json: JSONObject)

    private inner class Recorder : Choreographer.FrameCallback {
        @Volatile var phase = "setup"
        private var running = false
        private val frames = mutableListOf<Frame>()
        fun start() { running = true; Choreographer.getInstance().postFrameCallback(this) }
        fun stop() { running = false; Choreographer.getInstance().removeFrameCallback(this) }
        fun snapshot() = synchronized(frames) { frames.toList() }
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            var ime = false
            val roots = WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow && it.isShown }
            val windows = roots.map { root ->
                val insets = ViewCompat.getRootWindowInsets(root)
                val visible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
                val height = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                ime = ime || visible || height > 0
                JSONObject().put("type", root.javaClass.name).put("focus", root.hasWindowFocus())
                    .put("flags", (root.layoutParams as? WindowManager.LayoutParams)?.flags ?: 0)
                    .put("imeVisible", visible).put("imeHeight", height)
                    .put("width", root.width).put("height", root.height)
            }
            val ns = SystemClock.elapsedRealtimeNanos()
            val json = JSONObject().put("ns", ns).put("phase", phase).put("ime", ime).put("roots", JSONArray(windows))
            synchronized(frames) { frames += Frame(ns, ime, roots.size, json) }
            Choreographer.getInstance().postFrameCallback(this)
        }
        fun awaitIme(expected: Boolean, durationMs: Long = 200) {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var cursor = snapshot().size
            var since = 0L
            while (SystemClock.elapsedRealtime() < deadline) {
                val current = snapshot()
                for (frame in current.drop(cursor)) {
                    if (frame.rootCount > 0 && frame.ime == expected) {
                        if (since == 0L) since = frame.ns
                        if (frame.ns - since >= durationMs * 1_000_000) return
                    } else since = 0L
                }
                cursor = current.size
                SystemClock.sleep(10)
            }
            throw AssertionError("IME did not settle to $expected; ${snapshot().lastOrNull()?.json}")
        }
    }
}
