package com.lightreader.app.journey

import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderViewport
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.PaintReaderLayoutEngine
import com.lightreader.app.core.reader.UnicodeTextBoundary
import com.lightreader.app.core.reader.toReaderStyle
import com.lightreader.app.feature.reader.ReaderLayoutCacheKey
import com.lightreader.app.feature.reader.ReaderSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class ReaderEnginePerformanceInstrumentedTest {
    @Test
    fun recordsFullChapterPaginationCacheAndCrossChapterEvidence() {
        val targetChars = 256 * 1024
        val unit = "繁體中文排版，English wrapping，e\u0301，👩🏽‍💻。"
        val generated = buildString(targetChars + unit.length) {
            while (length < targetChars) append(unit)
        }
        val raw = generated.substring(0, UnicodeTextBoundary.previousBoundary(generated, targetChars))
        val paragraphs = BookTextNormalizer().normalize(raw)
        val viewport = ReaderViewport(1080, 2400, 3f, 3f, 96, 96)
        val style = ReaderPreferences().toReaderStyle()
        val engine = PaintReaderLayoutEngine()
        engine.paginate(0, "性能基准", paragraphs, viewport, style)

        val beforePssKb = Debug.getPss()
        val previewEnd = UnicodeTextBoundary.safeEnd(raw, 0, 8_192)
        val previewParagraphs = BookTextNormalizer().normalize(raw.substring(0, previewEnd))
        val previewPaginationDurationsMs = List(10) {
            measureNanoTime {
                engine.paginate(0, "性能基准", previewParagraphs, viewport, style).pages.first()
            } / 1_000_000.0
        }
        val durationsMs = List(10) {
            measureNanoTime { engine.paginate(0, "性能基准", paragraphs, viewport, style) } / 1_000_000.0
        }
        val firstPages = engine.paginate(0, "性能基准", paragraphs, viewport, style).pages
        val twoChapterDurationsMs = List(10) {
            measureNanoTime {
                engine.paginate(0, "第一章", paragraphs, viewport, style)
                engine.paginate(1, "第二章", paragraphs, viewport, style)
            } / 1_000_000.0
        }

        val controller = ReaderSessionController(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        val cacheKey = ReaderLayoutCacheKey("benchmark", 1, style.layoutFingerprint(), viewport)
        controller.putPages(cacheKey, firstPages)
        val cacheSamplesMs = List(20) {
            measureNanoTime { repeat(1_000) { check(controller.pages(cacheKey) === firstPages) } } / 1_000_000.0 / 1_000.0
        }
        val paginationP95 = percentile95(durationsMs)
        val previewPaginationP95 = percentile95(previewPaginationDurationsMs)
        val cacheP95 = percentile95(cacheSamplesMs)
        val isEmulator = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("getprop ro.kernel.qemu").trim() == "1"

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val displayManager = context.getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val evidence = JSONObject()
            .put("font_scale", context.resources.configuration.fontScale)
            .put("density_dpi", context.resources.displayMetrics.densityDpi)
            .put("viewport_width_px", context.resources.displayMetrics.widthPixels)
            .put("viewport_height_px", context.resources.displayMetrics.heightPixels)
            .put("measurement_scope", "prepared_text_engine")
            .put("source_id", InstrumentationRegistry.getArguments().getString("sourceId", "direct-local"))
            .put("run_id", InstrumentationRegistry.getArguments().getString("runId", "direct-local"))
            .put("fingerprint", android.os.Build.FINGERPRINT)
            .put("refresh_rate_hz", displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f)
            .put("power_state", battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
            .put("corpus_sha256", java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) })
            .put("manufacturer", android.os.Build.MANUFACTURER)
            .put("model", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("emulator", isEmulator)
            .put("chapter_chars", raw.length)
            .put("pages", firstPages.size)
            .put("pagination_ms", JSONArray(durationsMs))
            .put("preview_pagination_ms", JSONArray(previewPaginationDurationsMs))
            .put("preview_pagination_p95_ms", previewPaginationP95)
            .put("pagination_p95_ms", paginationP95)
            .put("cache_lookup_ms", JSONArray(cacheSamplesMs))
            .put("cache_lookup_p95_ms", cacheP95)
            .put("two_chapter_pagination_ms", JSONArray(twoChapterDurationsMs))
            .put("two_chapter_pagination_p95_ms", percentile95(twoChapterDurationsMs))
            .put("pss_delta_kb", Debug.getPss() - beforePssKb)
            .put("package", context.packageName)
        TestEvidenceOutput.writeText("qa-evidence/performance/reader-engine-benchmark.json", evidence.toString(2))
    }

    private fun percentile95(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * .95).toInt() - 1).coerceIn(sorted.indices)]
    }
}
