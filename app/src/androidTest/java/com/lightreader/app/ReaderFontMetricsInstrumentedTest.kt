package com.lightreader.app

import android.content.res.Configuration
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.model.*
import com.lightreader.app.core.reader.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderFontMetricsInstrumentedTest {
    @Test fun nonlinearFontScaleUsesOneMeasuredSnapshotForEveryPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = "中文 English 😀组合 é：长段落，测试分页完整性。".repeat(160)
        val paragraphs = BookTextNormalizer().normalize(raw)
        for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            val config = Configuration(context.resources.configuration).apply { fontScale = scale }
            val metrics = context.createConfigurationContext(config).resources.displayMetrics
            for ((width, height) in listOf(360 to 720, 720 to 360, 1000 to 700)) {
                val style = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 22f).toReaderStyle()
                val fonts = readerFontMetrics(style.fontSizeSp, metrics)
                assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f, metrics), fonts.bodyPx)
                assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f * READER_TITLE_SP_SCALE, metrics), fonts.titlePx)
                val viewport = ReaderViewport((width * metrics.density).toInt(), (height * metrics.density).toInt(), metrics.density, metrics.scaledDensity, fontMetrics = fonts)
                val result = PaintReaderLayoutEngine().paginate(0, "很长的章节标题 A long chapter title".repeat(3), paragraphs, viewport, style)
                assertTrue(result.pages.isNotEmpty())
                assertTrue(result.pages.all { it.fontMetrics == fonts })
                val body = result.pages.flatMap { it.lines }.filterNot { it.isChapterTitle }
                assertEquals(paragraphs.joinToString("") { it.text }, body.joinToString("") { it.text })
                val paint = TextPaint().apply { textSize = fonts.bodyPx; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
                for (line in body) {
                    assertEquals(paint.measureText(line.text), line.widthPx, .01f)
                    assertTrue(line.baselinePx + paint.fontMetrics.descent <= viewport.heightPx + .5f)
                }
                for (page in result.pages) {
                    assertTrue(page.startOffset <= page.endOffset)
                    assertTrue(page.startOffset in 0..raw.length)
                }
            }
        }
    }
}
