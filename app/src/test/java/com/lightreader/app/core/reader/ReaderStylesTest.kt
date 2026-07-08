package com.lightreader.app.core.reader

import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderStylesTest {
    @Test
    fun colorsAndChromeVisibilityDoNotChangeLayoutFingerprint() {
        val base = ReaderPreferences()
        assertEquals(
            base.toReaderStyle().layoutFingerprint(),
            base.copy(
                theme = ReaderTheme.NIGHT,
                showHeader = false,
                showStatus = false,
                showRightProgressBar = false,
                minimalMode = true,
                fullScreenTapNext = true,
            )
                .toReaderStyle().layoutFingerprint(),
        )
    }

    @Test
    fun typographyAndContentBoundsChangeLayoutFingerprint() {
        val base = ReaderPreferences().toReaderStyle().layoutFingerprint()
        assertNotEquals(base, ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 19f).toReaderStyle().layoutFingerprint())
        assertNotEquals(base, ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, horizontalPaddingDp = 34f).toReaderStyle().layoutFingerprint())
        assertNotEquals(base, ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, lineSpacingMultiplier = 1.4f).toReaderStyle().layoutFingerprint())
    }

    @Test
    fun layoutPresetsProduceDistinctReaderStyles() {
        val comfort = ReaderPreferences(layoutPreset = ReaderLayoutPreset.COMFORT).toReaderStyle()
        val compact = ReaderPreferences(layoutPreset = ReaderLayoutPreset.COMPACT).toReaderStyle()
        val immersive = ReaderPreferences(layoutPreset = ReaderLayoutPreset.IMMERSIVE).toReaderStyle()

        assertEquals(18f, comfort.fontSizeSp)
        assertEquals(16f, compact.fontSizeSp)
        assertNotEquals(comfort.layoutFingerprint(), compact.layoutFingerprint())
        assertNotEquals(comfort.layoutFingerprint(), immersive.layoutFingerprint())
        assertEquals(false, immersive.showHeader)
        assertEquals(false, immersive.showFooter)
        assertEquals(false, immersive.showRightProgressBar)
    }
}
