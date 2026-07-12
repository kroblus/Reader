package com.lightreader.app.ui

import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun nightModeReturnsToEveryDayTheme() {
        ReaderTheme.entries.filter { it != ReaderTheme.NIGHT }.forEach { theme ->
            val original = ReaderPreferences(theme = theme, lastNonNightTheme = theme)
            val night = toggleNightTheme(original)
            assertEquals(ReaderTheme.NIGHT, night.theme)
            assertEquals(theme, night.lastNonNightTheme)
            assertEquals(theme, toggleNightTheme(night).theme)
        }
    }

    @Test
    fun invalidNightFallbackReturnsToEyeCare() {
        val corrupted = ReaderPreferences(
            theme = ReaderTheme.NIGHT,
            lastNonNightTheme = ReaderTheme.NIGHT,
        )
        assertEquals(ReaderTheme.EYE_CARE, toggleNightTheme(corrupted).theme)
    }
}
