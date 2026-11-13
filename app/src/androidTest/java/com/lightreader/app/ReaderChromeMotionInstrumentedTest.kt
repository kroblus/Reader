package com.lightreader.app

import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.journey.FixtureCatalog
import com.lightreader.app.journey.FixtureId
import com.lightreader.app.journey.TestEvidenceOutput
import com.lightreader.app.ui.AppScreen
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Uses the real Android frame clock: no Compose test clock advances these animations. */
@RunWith(AndroidJUnit4::class)
class ReaderChromeMotionInstrumentedTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var activity: MainActivity
    private lateinit var viewModel: ReaderViewModel
    private val insetsEvidence = mutableListOf<BarsSnapshot>()

    @Test fun closingLayoutOnBodyKeepsAnchorAndRecordsActualWindowFrames() {
        openFixture()
        val initial = position()
        val viewportHeight = rootHeight()
        val frameThread = HandlerThread("reader-close-frame-evidence").apply { start() }
        val handler = Handler(frameThread.looper)
        val frames = mutableListOf<RenderedFrame>()
        val ranges = mutableListOf<LongRange>()
        val dismissalObservedNs = mutableListOf<Long>()
        val listener = Window.OnFrameMetricsAvailableListener { _, metric, dropped ->
            // Copy immediately: Android reuses the FrameMetrics object on the next callback.
            synchronized(frames) {
                frames += RenderedFrame(metric.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                    metric.getMetric(FrameMetrics.TOTAL_DURATION), metric.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    metric.getMetric(FrameMetrics.ANIMATION_DURATION),
                    metric.getMetric(FrameMetrics.DRAW_DURATION), metric.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
                    metric.getMetric(FrameMetrics.SYNC_DURATION), metric.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                    metric.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION), dropped)
            }
        }
        activityRule.scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, handler) }
        try {
            repeat(10) {
                activityRule.scenario.onActivity { viewModel.setToolbarVisible(true) }
                val settings = requireNotNull(device.wait(Until.findObject(By.desc(activity.getString(R.string.reader_settings_content_description))), 5_000))
                settings.click()
                await { viewModel.readerState.value.settingsVisible }
                assertTrue("Layout controls must actually be displayed", device.wait(
                    Until.hasObject(By.desc(activity.getString(R.string.reader_font_increase))), 5_000))
                awaitRealFrames(400)
                assertEquals(initial, position())
                val start = System.nanoTime()
                // Same unobscured body position for every iteration; this exercises tap dismissal.
                assertTrue(device.click(device.displayWidth / 2, device.displayHeight / 4))
                await { !viewModel.readerState.value.toolbarVisible && !viewModel.readerState.value.settingsVisible }
                dismissalObservedNs += System.nanoTime()
                awaitRealFrames(400)
                ranges += start..System.nanoTime()
                assertEquals("Tool visibility must not repaginate or change the UTF-16 anchor", initial, position())
                assertEquals("System bars must not resize the reader viewport", viewportHeight, rootHeight())
                assertTrue("The closed layout must leave no accessible settings action", device.wait(
                    Until.gone(By.desc(activity.getString(R.string.reader_font_increase))), 5_000))
            }
        } finally {
            activityRule.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            val flushed = CountDownLatch(1)
            handler.post { flushed.countDown() }
            try {
                assertTrue("Frame callbacks did not drain", flushed.await(5, TimeUnit.SECONDS))
            } finally { frameThread.quitSafely() }
        }
        val measured = synchronized(frames) { frames.toList() }
        val samples = ranges.mapIndexed { index, range ->
            val sample = measured.filter { it.vsyncNs in range }
            assertTrue("No real rendered frame for layout dismissal $index", sample.isNotEmpty())
            JSONObject().put("iteration", index).put("start_monotonic_ns", range.first).put("end_monotonic_ns", range.last)
                .put("logical_dismissal_observed_offset_ms", (dismissalObservedNs[index] - range.first) / 1_000_000.0)
                .put("vsync_offset_ms", JSONArray(sample.map { (it.vsyncNs - range.first) / 1_000_000.0 }))
                .put("total_render_ms", JSONArray(sample.map { it.totalNs / 1_000_000.0 }))
                .put("layout_measure_ms", JSONArray(sample.map { it.layoutNs / 1_000_000.0 }))
                .put("animation_callback_ms", JSONArray(sample.map { it.animationNs / 1_000_000.0 }))
                .put("draw_ms", JSONArray(sample.map { it.drawNs / 1_000_000.0 }))
                .put("input_handling_ms", JSONArray(sample.map { it.inputNs / 1_000_000.0 }))
                .put("sync_ms", JSONArray(sample.map { it.syncNs / 1_000_000.0 }))
                .put("command_issue_ms", JSONArray(sample.map { it.commandNs / 1_000_000.0 }))
                .put("swap_buffers_ms", JSONArray(sample.map { it.swapNs / 1_000_000.0 }))
                .put("callbacks_dropped", sample.sumOf { it.dropped })
        }
        val totals = measured.filter { frame -> ranges.any { frame.vsyncNs in it } }.map { it.totalNs / 1_000_000.0 }.sorted()
        val args = InstrumentationRegistry.getArguments()
        val scale = Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val evidence = JSONObject().put("run_id", args.getString("runId", "direct-local"))
            .put("source_id", args.getString("sourceId", "direct-local"))
            .put("scope", "actual_window_render_frames_during_body_tap_layout_dismissal")
            .put("clock", "Android Choreographer; no Compose test clock")
            .put("window_definition", "body tap through 400ms after logical dismissal; only rendered frames")
            .put("animator_duration_scale", scale).put("motion_enabled", scale > 0f)
            .put("sdk", android.os.Build.VERSION.SDK_INT).put("fingerprint", android.os.Build.FINGERPRINT)
            .put("density_dpi", activity.resources.displayMetrics.densityDpi)
            .put("font_scale", activity.resources.configuration.fontScale)
            .put("refresh_rate_hz", activity.windowManager.defaultDisplay.refreshRate)
            .put("iterations", JSONArray(samples)).put("render_frame_count", totals.size)
            .put("render_frame_p95_ms", totals[ceil(totals.size * .95).toInt() - 1])
        TestEvidenceOutput.writeText("performance/reader-chrome-close-frames.json", evidence.toString(2))
    }

    @Test fun searchAndDetailedSettingsKeepViewportAndLibraryRestoresBars() {
        val libraryBars = openFixture()
        val initial = position()
        val height = rootHeight()
        val bookId = requireNotNull(viewModel.readerState.value.book).id
        try {
            repeat(2) {
                activityRule.scenario.onActivity { viewModel.openSearch(bookId) }
                await { viewModel.uiState.value.screen is AppScreen.Search }
                awaitRealFrames(400)
                assertRestoredSystemBars("search-$it", libraryBars)
                assertEquals(height, rootHeight())
                activityRule.scenario.onActivity { viewModel.goBack() }
                await { viewModel.uiState.value.screen is AppScreen.Reader }
                awaitRealFrames(400)
                assertRestoredSystemBars("reader-from-search-$it", libraryBars)
                assertEquals(initial, position())
                assertEquals(height, rootHeight())

                activityRule.scenario.onActivity { viewModel.openReaderSettingsDetail() }
                await { viewModel.uiState.value.screen is AppScreen.ReaderSettingsDetail }
                awaitRealFrames(400)
                assertRestoredSystemBars("settings-detail-$it", libraryBars)
                activityRule.scenario.onActivity { viewModel.goBack() }
                await { viewModel.uiState.value.screen is AppScreen.Reader }
                awaitRealFrames(400)
                assertRestoredSystemBars("reader-from-detail-$it", libraryBars)
                assertEquals(initial, position())
                assertEquals(height, rootHeight())
            }
            activityRule.scenario.onActivity { viewModel.goBack() }
            await { viewModel.uiState.value.screen is AppScreen.Library }
            awaitRealFrames(400)
            assertRestoredSystemBars("library-return", libraryBars)
        } finally {
            val args = InstrumentationRegistry.getArguments()
            TestEvidenceOutput.writeText("performance/reader-chrome-system-bars.json", JSONObject()
                .put("run_id", args.getString("runId", "direct-local"))
                .put("source_id", args.getString("sourceId", "direct-local"))
                .put("sdk", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT)
                .put("observations", JSONArray(insetsEvidence.map { it.json() })).toString(2))
        }
    }

    private fun openFixture(): BarsSnapshot {
        activityRule.scenario.onActivity {
            activity = it
            viewModel = ViewModelProvider(it)[ReaderViewModel::class.java]
        }
        val app = activity.application as ReaderApplication
        check(app.packageName == "com.lightreader.app.qa")
        val preferences = ReaderPreferences(pageTurnMode = PageTurnMode.NONE, lockPortrait = false)
        runBlocking {
            app.container.database.clearAllTables()
            app.container.settingsRepository.save(preferences)
        }
        await { viewModel.uiState.value.preferences == preferences }
        awaitRealFrames(400)
        val libraryBars = observeBars("library-baseline")
        assertTrue("The initial library must show its status bar: $libraryBars", libraryBars.status.visible)
        val fixture = FixtureCatalog.materialize(activity, FixtureId.MULTI_PAGE_TXT)
        val book = runBlocking { app.container.bookRepository.import(Uri.fromFile(fixture)) }
        activityRule.scenario.onActivity { viewModel.openBook(book.id) }
        await(20_000) { !viewModel.readerState.value.loading && viewModel.readerState.value.pages.size >= 3 }
        activityRule.scenario.onActivity { viewModel.pageSelected(1) }
        awaitRealFrames(400)
        return libraryBars
    }

    private fun position(): List<Any> = viewModel.readerState.value.let {
        listOf(it.book!!.id, it.chapterIndex, it.pages[it.pageIndex].startOffset, it.layoutVersion)
    }

    private fun rootHeight(): Int {
        var height = 0
        activityRule.scenario.onActivity { height = it.window.decorView.height }
        return height
    }

    private fun assertRestoredSystemBars(phase: String, baseline: BarsSnapshot) {
        val current = observeBars(phase)
        val message = "$phase must restore the device's available bars; baseline=$baseline; current=$current"
        assertTrue("Status: $message", current.status.visible)
        if (baseline.navigation.visible && baseline.navigation.present) {
            assertTrue("Navigation: $message", current.navigation.visible)
        }
        if (baseline.caption.visible && baseline.caption.present) {
            assertTrue("Caption: $message", current.caption.visible)
        }
        // systemOverlays is included by systemBars(), but clients cannot control it. Its state
        // is still recorded so an absent OEM decoration cannot masquerade as a Reader failure.
    }

    private fun observeBars(phase: String): BarsSnapshot {
        lateinit var observation: BarsSnapshot
        activityRule.scenario.onActivity {
            val decor = it.window.decorView
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor)) { "$phase has no root WindowInsets" }
            fun bar(type: Int): BarVisibility {
                val current = insets.getInsets(type)
                val maximum = insets.getInsetsIgnoringVisibility(type)
                return BarVisibility(insets.isVisible(type),
                    listOf(current.left, current.top, current.right, current.bottom),
                    listOf(maximum.left, maximum.top, maximum.right, maximum.bottom))
            }
            val overlays = if (Build.VERSION.SDK_INT >= 34) {
                val native = requireNotNull(decor.rootWindowInsets)
                val type = android.view.WindowInsets.Type.systemOverlays()
                val current = native.getInsets(type)
                val maximum = native.getInsetsIgnoringVisibility(type)
                BarVisibility(native.isVisible(type),
                    listOf(current.left, current.top, current.right, current.bottom),
                    listOf(maximum.left, maximum.top, maximum.right, maximum.bottom))
            } else null
            observation = BarsSnapshot(phase, bar(WindowInsetsCompat.Type.statusBars()),
                bar(WindowInsetsCompat.Type.navigationBars()), bar(WindowInsetsCompat.Type.captionBar()),
                overlays, decor.width, decor.height, it.hasWindowFocus())
        }
        insetsEvidence += observation
        Log.i("ReaderChromeInsets", observation.json().toString())
        return observation
    }

    private data class BarVisibility(val visible: Boolean, val insetsLtrb: List<Int>, val maximumInsetsLtrb: List<Int>) {
        val present: Boolean get() = insetsLtrb.any { it > 0 } || maximumInsetsLtrb.any { it > 0 }
        fun json() = JSONObject().put("visible", visible).put("present", present)
            .put("insets_ltrb", JSONArray(insetsLtrb)).put("maximum_insets_ltrb", JSONArray(maximumInsetsLtrb))
    }

    private data class BarsSnapshot(
        val phase: String,
        val status: BarVisibility,
        val navigation: BarVisibility,
        val caption: BarVisibility,
        val systemOverlays: BarVisibility?,
        val rootWidth: Int,
        val rootHeight: Int,
        val focused: Boolean,
    ) {
        fun json() = JSONObject().put("phase", phase).put("status", status.json())
            .put("navigation", navigation.json()).put("caption", caption.json())
            .put("system_overlays", systemOverlays?.json() ?: JSONObject.NULL)
            .put("root_width", rootWidth).put("root_height", rootHeight).put("focused", focused)
    }

    private fun await(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < until) SystemClock.sleep(20)
        assertTrue("Reader UI state did not settle within ${timeoutMs}ms", condition())
    }

    private fun awaitRealFrames(durationMs: Long) {
        val done = CountDownLatch(1)
        val until = System.nanoTime() + durationMs * 1_000_000
        activityRule.scenario.onActivity {
            Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (frameTimeNanos >= until) done.countDown()
                    else Choreographer.getInstance().postFrameCallback(this)
                }
            })
        }
        assertTrue("Real display frames stalled", done.await(5, TimeUnit.SECONDS))
    }

    private data class RenderedFrame(
        val vsyncNs: Long, val totalNs: Long, val layoutNs: Long, val animationNs: Long,
        val drawNs: Long, val inputNs: Long, val syncNs: Long, val commandNs: Long, val swapNs: Long,
        val dropped: Int,
    )
}
