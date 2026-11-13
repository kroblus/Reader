package com.lightreader.app.journey

import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.MainActivity
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.ui.ReaderOverlay
import com.lightreader.app.ui.ReaderTestTags
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Action-to-committed-frame samples include instrumentation overhead; no absolute product target is inferred. */
@RunWith(AndroidJUnit4::class)
class ReaderUiPerformanceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private data class PhaseSample(
        val startWallTimeMs: Long,
        val actionMs: Double,
        val readyWaitMs: Double,
        val idleWaitMs: Double,
        val frameWaitMs: Double,
    )

    @Test fun recordsWarmBookOpenActualTurnsLayoutAndTools() {
        val app = compose.activity.application as ReaderApplication
        assertTrue(app.packageName.endsWith(".qa"))
        runBlocking {
            app.container.database.clearAllTables()
            app.container.settingsRepository.save(ReaderPreferences(pageTurnMode = PageTurnMode.NONE))
        }
        val corpus = buildString {
            repeat(32) { chapter ->
                append("第${chapter + 1}章 帧测量$chapter\n")
                repeat(80) { append("逐帧测量正文。English words，中文标点和长段落，保持同一语料用于比较。\n") }
            }
        }
        val source = File(app.cacheDir, "ui-frame-corpus.txt").apply { writeText(corpus) }
        val book = runBlocking { app.container.bookRepository.import(Uri.fromFile(source)) }
        val vm = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
        val samples = linkedMapOf<String, MutableList<Double>>()
        val phaseSamples = linkedMapOf<String, MutableList<PhaseSample>>()
        fun measure(name: String, ready: () -> Boolean, action: () -> Unit) {
            val wallStart = System.currentTimeMillis()
            val start = SystemClock.elapsedRealtimeNanos()
            action()
            val actionEnd = SystemClock.elapsedRealtimeNanos()
            compose.waitUntil(30_000, ready)
            val readyObserved = SystemClock.elapsedRealtimeNanos()
            compose.waitForIdle()
            val idleReturned = SystemClock.elapsedRealtimeNanos()
            // API 29+ callback runs when this view's frame is submitted to the renderer.
            val frame = CountDownLatch(1)
            compose.activity.runOnUiThread {
                val view = compose.activity.window.decorView
                if (android.os.Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
                    view.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
                    view.invalidate()
                } else {
                    view.postOnAnimation { view.postOnAnimation { frame.countDown() } }
                }
            }
            assertTrue("No readable frame was submitted", frame.await(5, TimeUnit.SECONDS))
            val end = SystemClock.elapsedRealtimeNanos()
            // Keep the original action-to-committed-frame total. Only retain phase
            // timestamps here; JSON construction and output happen after all samples.
            samples.getOrPut(name) { mutableListOf() } += (end - start) / 1_000_000.0
            phaseSamples.getOrPut(name) { mutableListOf() } += PhaseSample(
                wallStart,
                (actionEnd - start) / 1_000_000.0,
                (readyObserved - actionEnd) / 1_000_000.0,
                (idleReturned - readyObserved) / 1_000_000.0,
                (end - idleReturned) / 1_000_000.0,
            )
        }
        repeat(30) { iteration ->
            compose.waitUntil(10_000) { compose.onAllNodesWithText(book.title).fetchSemanticsNodes().isNotEmpty() }
            measure("book_open", { vm.readerState.value.book?.id == book.id && vm.readerState.value.pages.isNotEmpty() }) {
                compose.onAllNodesWithText(book.title).onFirst().performClick()
            }
            compose.waitUntil(30_000) { !vm.readerState.value.loading && vm.readerState.value.pages.size >= 3 }
            compose.runOnIdle { vm.pageSelected(0); vm.hideToolbar() }
            measure("cached_turn", { vm.readerState.value.pageIndex == 1 }) {
                compose.onNodeWithTag(ReaderTestTags.READER).performTouchInput { click(Offset(width * .88f, height * .5f)) }
            }
            // A non-adjacent chapter has not been prefetched in this newly opened session.
            val target = 3 + (iteration % 10) * 2
            measure("uncached_chapter", { !vm.readerState.value.loading && vm.readerState.value.chapterIndex == target }) {
                compose.runOnIdle { vm.selectChapter(target) }
            }
            val version = vm.readerState.value.layoutVersion
            measure("layout_change", { !vm.readerState.value.loading && vm.readerState.value.layoutVersion > version }) {
                runBlocking { app.container.settingsRepository.save(app.container.settingsRepository.preferences.first().copy(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = if (iteration % 2 == 0) 22f else 20f)) }
            }
            measure("tools_list", { vm.readerState.value.overlay == ReaderOverlay.TOC }) {
                compose.runOnIdle { vm.showTableOfContents() }
            }
            compose.onNodeWithTag(ReaderTestTags.READER_TOC).assertIsDisplayed()
            compose.runOnIdle { vm.hideReaderOverlay(); vm.goBack() }
        }
        val display = (app.getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val battery = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val args = InstrumentationRegistry.getArguments()
        val evidence = JSONObject()
            .put("font_scale", app.resources.configuration.fontScale)
            .put("density_dpi", app.resources.displayMetrics.densityDpi)
            .put("viewport_width_px", app.resources.displayMetrics.widthPixels)
            .put("viewport_height_px", app.resources.displayMetrics.heightPixels)
            .put("measurement_scope", "warm_ui_readable_frame")
            .put("source_id", args.getString("sourceId", "direct-local"))
            .put("run_id", args.getString("runId", "direct-local"))
            .put("manufacturer", android.os.Build.MANUFACTURER).put("model", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT).put("fingerprint", android.os.Build.FINGERPRINT)
            .put("emulator", androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
            .put("refresh_rate_hz", display?.refreshRate ?: 0f)
            .put("power_state", battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
            .put("corpus_sha256", java.security.MessageDigest.getInstance("SHA-256").digest(corpus.toByteArray()).joinToString("") { "%02x".format(it) })
        samples.forEach { (name, values) ->
            evidence.put("${name}_ms", JSONArray(values))
            evidence.put("${name}_p95_ms", values.sorted()[ceil(values.size * .95).toInt() - 1])
        }
        val phases = JSONObject()
        phaseSamples.forEach { (name, values) ->
            phases.put(name, JSONObject()
                .put("start_wall_time_ms", JSONArray(values.map { it.startWallTimeMs }))
                .put("action_ms", JSONArray(values.map { it.actionMs }))
                .put("ready_wait_ms", JSONArray(values.map { it.readyWaitMs }))
                .put("idle_wait_ms", JSONArray(values.map { it.idleWaitMs }))
                .put("frame_wait_ms", JSONArray(values.map { it.frameWaitMs })))
        }
        evidence.put("diagnostic_phases", JSONObject()
            .put("version", 1)
            .put("definition", "action; waitUntil ready observed; waitForIdle; request and await committed frame (includes instrumentation scheduling)")
            .put("samples_ms", phases))
        // Existing QA metrics contain operation/count/timing fields, never book text
        // or URLs. The writer is asynchronous: this is an unmodified file snapshot,
        // not a promise that pending writes were flushed. Host logcat supplements it.
        val engineSnapshotStarted = System.currentTimeMillis()
        val engineSnapshot = runCatching {
            File(app.filesDir, "qa-evidence/performance/reader-engine.jsonl").readText(Charsets.UTF_8)
        }
        evidence.put("diagnostic_engine_snapshot", JSONObject()
            .put("captured_after_measurements", true)
            .put("started_wall_time_ms", engineSnapshotStarted)
            .put("finished_wall_time_ms", System.currentTimeMillis())
            .put("flush_guaranteed", false)
            .put("available", engineSnapshot.isSuccess)
            .put("jsonl", engineSnapshot.getOrNull() ?: JSONObject.NULL))
        val serialized = evidence.toString(2)
        val encoded = android.util.Base64.encodeToString(serialized.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(serialized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val identity = "${evidence.getString("run_id")}|${evidence.getString("source_id")}|$digest"
        // Emit only after every timed action. Status 2 is in-progress, never a test completion.
        InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
            putString("stream", "\nREADER_UI_JSON|$identity|$encoded\n")
        })
        val chunks = encoded.chunked(2800)
        chunks.forEachIndexed { index, chunk ->
            android.util.Log.i("ReaderUiEvidence", "READER_UI_CHUNK|$identity|$index|${chunks.size}|$chunk")
        }
        runCatching { TestEvidenceOutput.writeText("qa-evidence/performance/reader-ui-benchmark.json", serialized) }
            .onFailure { android.util.Log.w("ReaderUiEvidence", "Optional file output failed; canonical status/logcat payload emitted", it) }
        source.delete()
    }
}
