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
        val firstVisibleDurationsMs = List(10) {
            measureNanoTime {
                engine.paginate(0, "性能基准", previewParagraphs, viewport, style).pages.first()
            } / 1_000_000.0
        }
        val durationsMs = List(10) {
            measureNanoTime { engine.paginate(0, "性能基准", paragraphs, viewport, style) } / 1_000_000.0
        }
        val firstPages = engine.paginate(0, "性能基准", paragraphs, viewport, style).pages
        val crossChapterMs = measureNanoTime {
            engine.paginate(0, "第一章", paragraphs, viewport, style)
            engine.paginate(1, "第二章", paragraphs, viewport, style)
        } / 1_000_000.0

        val controller = ReaderSessionController(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        val cacheKey = ReaderLayoutCacheKey("benchmark", 1, style.layoutFingerprint(), viewport)
        controller.putPages(cacheKey, firstPages)
        val cacheSamplesMs = List(20) {
            measureNanoTime { repeat(1_000) { check(controller.pages(cacheKey) === firstPages) } } / 1_000_000.0 / 1_000.0
        }
        val paginationP95 = percentile95(durationsMs)
        val firstVisibleP95 = percentile95(firstVisibleDurationsMs)
        val cacheP95 = percentile95(cacheSamplesMs)
        val isEmulator = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("getprop ro.kernel.qemu").trim() == "1"
        if (!isEmulator) {
            assertTrue("First visible page p95 was $firstVisibleP95 ms", firstVisibleP95 <= 300.0)
            assertTrue("Cached page lookup p95 was $cacheP95 ms", cacheP95 <= 50.0)
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val evidence = JSONObject()
            .put("manufacturer", android.os.Build.MANUFACTURER)
            .put("model", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("emulator", isEmulator)
            .put("chapter_chars", raw.length)
            .put("pages", firstPages.size)
            .put("pagination_ms", JSONArray(durationsMs))
            .put("first_visible_ms", JSONArray(firstVisibleDurationsMs))
            .put("first_visible_p95_ms", firstVisibleP95)
            .put("pagination_p95_ms", paginationP95)
            .put("cache_lookup_p95_ms", cacheP95)
            .put("cross_chapter_ms", crossChapterMs)
            .put("pss_delta_kb", Debug.getPss() - beforePssKb)
            .put("package", context.packageName)
        TestEvidenceOutput.writeText("qa-evidence/performance/reader-engine-benchmark.json", evidence.toString(2))
    }

    private fun percentile95(values: List<Double>): Double {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * .95).toInt() - 1).coerceIn(sorted.indices)]
    }
}
