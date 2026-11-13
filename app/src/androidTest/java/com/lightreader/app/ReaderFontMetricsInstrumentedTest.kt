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
    @Test fun firstBodyLineUsesCompactHeaderSpaceAndRespectsLargeFontsAndCustomMargins() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val paragraphs = BookTextNormalizer().normalize("给阅读留一点时间。".repeat(80))
        for (scale in listOf(1f, 2f)) {
            val metrics = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = scale })
                .resources.displayMetrics
            val density = metrics.density
            val fonts = readerFontMetrics(18f, metrics)
            val viewport = ReaderViewport((392 * density).toInt(), (800 * density).toInt(), density, metrics.scaledDensity,
                safeTopPx = (32 * density).toInt(), safeBottomPx = (24 * density).toInt(), fontMetrics = fonts)
            val headerPaint = TextPaint().apply { textSize = fonts.auxiliaryPx; typeface = Typeface.DEFAULT }
            val headerBottom = viewport.safeTopPx + maxOf(14f * density, -headerPaint.fontMetrics.ascent + 2f * density) + headerPaint.fontMetrics.descent
            for (topMargin in listOf(24f, 90f)) {
                val style = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 18f,
                    verticalPaddingTopDp = topMargin, fontFamily = FontFamilyOption.SERIF).toReaderStyle()
                val page = PaintReaderLayoutEngine().paginate(0, "", paragraphs, viewport, style).pages.first()
                val bodyPaint = TextPaint().apply { textSize = fonts.bodyPx; typeface = Typeface.SERIF }
                val bodyTop = page.lines.first().baselinePx + bodyPaint.fontMetrics.ascent
                assertTrue("Body must leave visible space below the scaled header", bodyTop >= headerBottom + 4f * density)
                assertTrue("Explicit user margin must still be respected", bodyTop >= topMargin * density)
                if (scale == 1f && topMargin == 24f) {
                    assertTrue("Hidden toolbar must not leave a 52dp hole above text", bodyTop <= viewport.safeTopPx + 40f * density)
                }
                val hiddenChrome = PaintReaderLayoutEngine().paginate(0, "", paragraphs, viewport,
                    style.copy(showHeader = false, showFooter = false)).pages
                assertEquals("Header visibility must not repage the book", page.lines, hiddenChrome.first().lines)
                val titled = PaintReaderLayoutEngine().paginate(0, "第一章 风起", paragraphs, viewport, style).pages.first()
                val titlePaint = TextPaint().apply { textSize = fonts.titlePx; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
                val lastTitleBottom = titled.lines.last { it.isChapterTitle }.baselinePx + titlePaint.fontMetrics.descent
                val firstBodyTop = titled.lines.first { !it.isChapterTitle }.baselinePx + bodyPaint.fontMetrics.ascent
                val titleGapDp = (firstBodyTop - lastTitleBottom) / density
                assertTrue("Chapter heading must remain distinct from body text", titleGapDp >= 8f)
                assertTrue("Chapter opening must not add an oversized blank band", titleGapDp <= if (scale == 1f) 28f else 40f)
            }
        }
    }

    @Test fun nonlinearFontScaleUsesOneMeasuredSnapshotForEveryPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = "中文 English 😀组合 é：长段落，测试分页完整性。".repeat(160)
        val paragraphs = BookTextNormalizer().normalize(raw)
        for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            val config = Configuration(context.resources.configuration).apply { fontScale = scale }
            val metrics = context.createConfigurationContext(config).resources.displayMetrics
            for ((width, height) in listOf(360 to 720, 720 to 360, 1000 to 700)) {
                val style = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 22f, fontFamily = FontFamilyOption.SERIF).toReaderStyle()
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
