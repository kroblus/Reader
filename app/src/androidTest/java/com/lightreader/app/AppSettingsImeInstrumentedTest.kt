package com.lightreader.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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

/** Observes real vsyncs, including the transient frames before the final page settles. */
@RunWith(AndroidJUnit4::class)
class AppSettingsImeInstrumentedTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var activity: MainActivity
    private lateinit var viewModel: ReaderViewModel

    @Test fun untouchedSearchDoesNotShowImeDuringThreeSettingsRoundTrips() =
        exercise(Preparation.UNTOUCHED, 3)

    @Test fun searchDismissedWithBackDoesNotReshowImeDuringThreeSettingsRoundTrips() =
        exercise(Preparation.BACK_DISMISSED, 3)

    @Test fun visibleSearchImeClosesWithoutReappearingWhenOpeningSettings() =
        exercise(Preparation.VISIBLE, 1)

    private fun exercise(preparation: Preparation, repetitions: Int) {
        activityRule.scenario.onActivity {
            activity = it
            viewModel = ViewModelProvider(it)[ReaderViewModel::class.java]
        }
        val app = activity.application as ReaderApplication
        check(app.packageName == "com.lightreader.app.qa") { "IME fixtures require the isolated QA package" }
        val recorder = ImeRecorder()
        val transitions = mutableListOf<Transition>()
        val violations = mutableListOf<String>()
        var originalHardwareIme: String? = null
        var failure: Throwable? = null
        var inputVerified = false
        try {
            // This only controls keyboard visibility with emulator hardware keyboards;
            // it does not grant permissions or alter any security/install setting.
            originalHardwareIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
            check(originalHardwareIme == "null" || originalHardwareIme == "0" || originalHardwareIme == "1") {
                "Cannot preserve show_ime_with_hard_keyboard=$originalHardwareIme"
            }
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            check(device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim() == "1") {
                "Could not enable the software keyboard for this headless/emulator run"
            }
            runBlocking {
                app.container.database.clearAllTables()
                app.container.settingsRepository.save(ReaderPreferences())
                val fixture = FixtureCatalog.materialize(activity, FixtureId.UTF8_TXT)
                app.container.bookRepository.import(Uri.fromFile(fixture))
            }
            await("initial library") { viewModel.uiState.value.screen is AppScreen.Library }
            searchEditor() // Presence check only: UNTOUCHED must never focus the field.
            activityRule.scenario.onActivity { recorder.start() }
            recorder.awaitStable("library", imePresent = false, durationMs = 200)

            repeat(repetitions) { iteration ->
                recorder.iteration = iteration
                recorder.phase = "prepare-${preparation.name.lowercase()}"
                if (preparation != Preparation.UNTOUCHED) {
                    searchEditor().click()
                    recorder.awaitStable("library", imePresent = true, durationMs = 200)
                    if (preparation == Preparation.BACK_DISMISSED) {
                        // A real IME Back intentionally leaves any Compose input focus intact.
                        assertTrue("Back was not injected", device.pressBack())
                        recorder.awaitStable("library", imePresent = false, durationMs = 200)
                    }
                }
                val editorFocusedBefore = searchEditor().isFocused
                val more = requireNotNull(device.wait(Until.findObject(By.desc(activity.getString(R.string.action_more))), 5_000)) {
                    "Library More button was not accessible"
                }
                recorder.phase = "menu-click"
                val start = SystemClock.elapsedRealtimeNanos()
                more.click()
                val settings = requireNotNull(device.wait(Until.findObject(By.text(activity.getString(R.string.library_app_settings))), 5_000)) {
                    "App settings menu item was not accessible"
                }
                recorder.phase = "settings-click"
                val settingsClick = SystemClock.elapsedRealtimeNanos()
                settings.click()
                await("app settings route") { viewModel.uiState.value.screen is AppScreen.AppSettings }
                assertTrue("App settings title must actually be visible", device.wait(
                    Until.hasObject(By.text(activity.getString(R.string.app_settings_title))), 5_000))
                recorder.phase = "settings-stable-window"
                val end = recorder.awaitStable("app_settings", imePresent = false, durationMs = 800)
                val measured = recorder.snapshot().filter { it.observedNs in start..end }
                assertTrue("No frames were sampled for settings transition $iteration", measured.size >= 10)
                val forbidden = if (preparation == Preparation.VISIBLE) {
                    // Initial visibility is expected; once hidden, it must stay hidden.
                    measured.dropWhile { it.imePresent }.filter { it.imePresent }
                } else measured.filter { it.imePresent }
                if (forbidden.isNotEmpty()) {
                    val first = forbidden.first()
                    violations += "iteration=$iteration: ${forbidden.size} IME frames; first at " +
                        "${(first.observedNs - start) / 1_000_000.0}ms, phase=${first.phase}, " +
                        "screen=${first.screen}, visible=${first.imeVisible}, height=${first.imeHeight}"
                }
                transitions += Transition(iteration, start, settingsClick, end, editorFocusedBefore, measured.size, forbidden.size)
                recorder.phase = "return-to-library"
                val back = requireNotNull(device.wait(Until.findObject(By.desc(activity.getString(R.string.action_back))), 5_000))
                back.click()
                await("returned library") { viewModel.uiState.value.screen is AppScreen.Library }
                searchEditor()
                // Do not clear focus, hide the keyboard or alter the route via the ViewModel.
            }

            recorder.phase = "verify-active-search-input"
            if (recorder.snapshot().lastOrNull()?.imePresent == true) {
                assertTrue(device.pressBack())
                recorder.awaitStable("library", imePresent = false, durationMs = 200)
            }
            val editor = searchEditor()
            val before = editor.text.orEmpty()
            editor.click()
            recorder.awaitStable("library", imePresent = true, durationMs = 200)
            assertTrue("Q key was not injected", device.pressKeyCode(KeyEvent.KEYCODE_Q))
            assertTrue("A key was not injected", device.pressKeyCode(KeyEvent.KEYCODE_A))
            await("search accepts real key input") {
                val current = device.findObject(searchSelector())?.text.orEmpty()
                current.isNotBlank() && current != before
            }
            inputVerified = true
            assertTrue("Transient IME appearance: ${violations.joinToString("; ")}", violations.isEmpty())
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            activityRule.scenario.onActivity { recorder.stop() }
            var cleanupFailure: Throwable? = null
            try {
                when (originalHardwareIme) {
                    "null" -> device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
                    "0", "1" -> device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $originalHardwareIme")
                }
                if (originalHardwareIme in listOf("null", "0", "1")) {
                    check(device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim() == originalHardwareIme) {
                        "show_ime_with_hard_keyboard was not restored"
                    }
                }
            } catch (error: Throwable) { cleanupFailure = error }
            try {
                val args = InstrumentationRegistry.getArguments()
                val frames = recorder.snapshot()
                val evidence = JSONObject()
                    .put("run_id", args.getString("runId", "direct-local"))
                    .put("source_id", args.getString("sourceId", "direct-local"))
                    .put("scenario", preparation.name).put("requested_round_trips", repetitions)
                    .put("passed", failure == null && cleanupFailure == null)
                    .put("failure", failure?.toString() ?: JSONObject.NULL)
                    .put("cleanup_failure", cleanupFailure?.toString() ?: JSONObject.NULL)
                    .put("clock", "Choreographer vsync + SystemClock.elapsedRealtimeNanos; no Compose test clock")
                    .put("observation", "read-only root WindowInsetsCompat.Type.ime; no insets listener/callback installed")
                    .put("window", "before real More click through 800ms of stable app-settings frames")
                    .put("sdk", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT)
                    .put("window_animation_scale", animationScale(Settings.Global.WINDOW_ANIMATION_SCALE))
                    .put("transition_animation_scale", animationScale(Settings.Global.TRANSITION_ANIMATION_SCALE))
                    .put("animator_duration_scale", animationScale(Settings.Global.ANIMATOR_DURATION_SCALE))
                    .put("show_ime_with_hard_keyboard_before", originalHardwareIme ?: JSONObject.NULL)
                    .put("show_ime_with_hard_keyboard_during", 1)
                    .put("search_accepts_key_input_after_round_trips", inputVerified)
                    .put("violations", JSONArray(violations))
                    .put("transitions", JSONArray(transitions.map { it.json() }))
                    .put("frame_count", frames.size)
                    .put("frames", JSONArray(frames.map { it.json() }))
                TestEvidenceOutput.writeText("interaction/app-settings-ime-${preparation.name.lowercase()}.json", evidence.toString(2))
            } catch (error: Throwable) {
                if (cleanupFailure == null) cleanupFailure = error else cleanupFailure.addSuppressed(error)
            }
            cleanupFailure?.let { if (failure == null) throw it else failure.addSuppressed(it) }
        }
    }

    private fun searchSelector() = By.pkg(activity.packageName).clazz("android.widget.EditText")
    private fun searchEditor(): UiObject2 = requireNotNull(device.wait(Until.findObject(searchSelector()), 5_000)) {
        "The seeded library search field was not accessible"
    }

    private fun animationScale(key: String): Float = Settings.Global.getFloat(activity.contentResolver, key, 1f)

    private fun await(description: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertTrue("Timed out waiting for $description", condition())
    }

    private inner class ImeRecorder : Choreographer.FrameCallback {
        private val frames = mutableListOf<ImeFrame>()
        private var running = false
        @Volatile var phase = "initial-library"
        @Volatile var iteration = -1
        private val inputMethod by lazy { activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }

        fun start() { running = true; Choreographer.getInstance().postFrameCallback(this) }
        fun stop() { running = false; Choreographer.getInstance().removeFrameCallback(this) }
        fun snapshot(): List<ImeFrame> = synchronized(frames) { frames.toList() }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
            val focused = decor.findFocus()
            val screen = when (viewModel.uiState.value.screen) {
                is AppScreen.Library -> "library"
                is AppScreen.AppSettings -> "app_settings"
                else -> viewModel.uiState.value.screen.javaClass.simpleName
            }
            val frame = ImeFrame(frameTimeNanos, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(),
                phase, iteration, screen, insets != null, insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false,
                insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: -1, decor.width, decor.height,
                activity.hasWindowFocus(), focused?.javaClass?.name, focused?.id,
                focused?.onCheckIsTextEditor() ?: false, inputMethod.isAcceptingText,
                focused?.let { inputMethod.isActive(it) } ?: false)
            synchronized(frames) { frames += frame }
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun awaitStable(screen: String, imePresent: Boolean, durationMs: Long): Long {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var cursor = snapshot().size
            var stableSince = 0L
            var geometry: List<Int>? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                val current = snapshot()
                for (frame in current.drop(cursor)) {
                    val nextGeometry = listOf(frame.width, frame.height, frame.imeHeight)
                    if (frame.insetsKnown && frame.screen == screen && frame.imePresent == imePresent && frame.windowFocused) {
                        if (stableSince == 0L || geometry != nextGeometry) stableSince = frame.observedNs
                        geometry = nextGeometry
                        if (frame.observedNs - stableSince >= durationMs * 1_000_000) return frame.observedNs
                    } else { stableSince = 0L; geometry = null }
                }
                cursor = current.size
                SystemClock.sleep(10)
            }
            throw AssertionError("No ${durationMs}ms stable $screen IME=$imePresent window; latest=${snapshot().lastOrNull()}")
        }
    }

    private enum class Preparation { UNTOUCHED, BACK_DISMISSED, VISIBLE }

    private data class Transition(val iteration: Int, val start: Long, val settingsClick: Long, val end: Long,
        val editorFocusedBefore: Boolean, val frameCount: Int, val forbiddenFrameCount: Int) {
        fun json() = JSONObject().put("iteration", iteration).put("menu_click_elapsed_ns", start)
            .put("settings_click_elapsed_ns", settingsClick).put("stable_window_end_elapsed_ns", end)
            .put("editor_focused_before_menu", editorFocusedBefore).put("frame_count", frameCount)
            .put("forbidden_ime_frame_count", forbiddenFrameCount)
    }

    private data class ImeFrame(val vsyncNs: Long, val observedNs: Long, val wallMs: Long, val phase: String,
        val iteration: Int, val screen: String, val insetsKnown: Boolean, val imeVisible: Boolean, val imeHeight: Int,
        val width: Int, val height: Int, val windowFocused: Boolean, val focusedViewClass: String?, val focusedViewId: Int?,
        val focusedViewIsEditor: Boolean, val acceptingText: Boolean, val inputConnectionActive: Boolean) {
        val imePresent get() = imeVisible || imeHeight > 0
        fun json() = JSONObject().put("vsync_ns", vsyncNs).put("observed_elapsed_ns", observedNs).put("wall_time_ms", wallMs)
            .put("phase", phase).put("iteration", iteration).put("screen", screen).put("insets_known", insetsKnown)
            .put("ime_visible", imeVisible).put("ime_height_px", imeHeight).put("root_width", width).put("root_height", height)
            .put("window_focused", windowFocused).put("focused_view_class", focusedViewClass ?: JSONObject.NULL)
            .put("focused_view_id", focusedViewId ?: JSONObject.NULL).put("focused_view_is_text_editor", focusedViewIsEditor)
            .put("ime_accepting_text", acceptingText).put("input_connection_active", inputConnectionActive)
    }
}
