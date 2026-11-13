package com.lightreader.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderViewport
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.PaintReaderLayoutEngine
import com.lightreader.app.core.reader.ReaderTypefaces
import com.lightreader.app.core.reader.readerFontMetrics
import com.lightreader.app.core.reader.toReaderStyle
import com.lightreader.app.core.settings.ReaderSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderBundledFontsInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val bundled = listOf(FontFamilyOption.LXGW_WENKAI, FontFamilyOption.ZHUQUE_FANGSONG)

    @Test fun bundledChineseFacesAreDistinctAndReuseLoadedTypefaces() = runBlocking {
        ReaderTypefaces.preload(context)
        assertSame(Typeface.DEFAULT, ReaderTypefaces.awaitTypeface(FontFamilyOption.SYSTEM))
        val faces = bundled.map { family ->
            val regular = ReaderTypefaces.awaitTypeface(family)
            assertSame(regular, ReaderTypefaces.cachedTypeface(family))
            assertSame(regular, ReaderTypefaces.awaitTypeface(family))
            assertSame(ReaderTypefaces.awaitTypeface(family, 700), ReaderTypefaces.cachedTypeface(family, 700))
            val paint = TextPaint().apply { typeface = regular }
            for (character in "轻阅山川春风繁體中文") assertTrue("$family must contain Chinese glyphs", paint.hasGlyph(character.toString()))
            regular
        }
        // Native raster output detects the old problem where differently named
        // families fell back to the same Chinese face despite distinct Typeface IDs.
        val images = (listOf(Typeface.DEFAULT) + faces).map(::chineseRaster)
        for (left in images.indices) for (right in 0 until left) {
            assertTrue("Each reading option must render visibly different Chinese glyphs", !images[left].contentEquals(images[right]))
        }
    }

    @Test fun bundledFontsMeasureExactlyAsDrawnAcrossScaleWeightAndOrientation() = runBlocking {
        ReaderTypefaces.preload(context)
        val raw = "春风吹过江南，书页留下微光。繁體中文 English é 😀，阅读与分页保持原字符位置。".repeat(50)
        val paragraphs = BookTextNormalizer().normalize(raw)
        for (family in bundled) for (weight in listOf(400, 700)) {
            val bodyFace = ReaderTypefaces.awaitTypeface(family, weight)
            val titleFace = ReaderTypefaces.awaitTypeface(family, 700)
            for (scale in listOf(1f, 2f)) {
                val configuration = Configuration(context.resources.configuration).apply { fontScale = scale }
                val metrics = context.createConfigurationContext(configuration).resources.displayMetrics
                val style = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontFamily = family,
                    fontSizeSp = 22f, fontWeight = weight).toReaderStyle()
                val fonts = readerFontMetrics(style.fontSizeSp, metrics)
                val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bodyFace; textSize = fonts.bodyPx }
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleFace; textSize = fonts.titlePx }
                for ((width, height) in listOf(360 to 720, 720 to 360)) {
                    val viewport = ReaderViewport((width * metrics.density).toInt(), (height * metrics.density).toInt(),
                        metrics.density, metrics.scaledDensity, fontMetrics = fonts)
                    val pages = PaintReaderLayoutEngine().paginate(0, "第一章 风起江南", paragraphs, viewport, style).pages
                    val body = pages.flatMap { it.lines }.filterNot { it.isChapterTitle }
                    assertEquals(paragraphs.joinToString("") { it.text }, body.joinToString("") { it.text })
                    for (page in pages) {
                        assertEquals(fonts, page.fontMetrics)
                        assertTrue(page.startOffset in 0..raw.length && page.endOffset in page.startOffset..raw.length)
                        for (line in page.lines) {
                            val paint = if (line.isChapterTitle) titlePaint else bodyPaint
                            assertEquals("$family scale=$scale weight=$weight", paint.measureText(line.text), line.widthPx, .01f)
                            assertTrue(line.baselinePx + paint.fontMetrics.descent <= viewport.heightPx + .5f)
                        }
                    }
                }
            }
        }
    }

    @Test fun existingAndBundledSelectionsSurvivePreferenceReload() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        val repository = ReaderSettingsRepository(context)
        val original = repository.preferences.first()
        try {
            assertEquals(FontFamilyOption.SYSTEM, ReaderPreferences().fontFamily)
            for (family in FontFamilyOption.entries) {
                repository.save(original.copy(fontFamily = family))
                assertEquals(family, ReaderSettingsRepository(context).preferences.first().fontFamily)
            }
        } finally {
            repository.save(original)
        }
    }

    private fun chineseRaster(typeface: Typeface): IntArray {
        val bitmap = Bitmap.createBitmap(640, 128, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 56f; color = Color.BLACK }
            canvas.drawText("轻阅山川春风繁體中文", 8f, 80f, paint)
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        } finally {
            bitmap.recycle()
        }
    }
}
