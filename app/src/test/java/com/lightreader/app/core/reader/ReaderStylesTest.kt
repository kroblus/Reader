package com.lightreader.app.core.reader

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
            base.copy(theme = ReaderTheme.NIGHT, showHeader = false, showStatus = false, showRightProgressBar = false, minimalMode = true)
                .toReaderStyle().layoutFingerprint(),
        )
    }

    @Test
    fun typographyAndContentBoundsChangeLayoutFingerprint() {
        val base = ReaderPreferences().toReaderStyle().layoutFingerprint()
        assertNotEquals(base, ReaderPreferences(fontSizeSp = 19f).toReaderStyle().layoutFingerprint())
        assertNotEquals(base, ReaderPreferences(horizontalPaddingDp = 34f).toReaderStyle().layoutFingerprint())
        assertNotEquals(base, ReaderPreferences(lineSpacingMultiplier = 1.4f).toReaderStyle().layoutFingerprint())
    }
}
