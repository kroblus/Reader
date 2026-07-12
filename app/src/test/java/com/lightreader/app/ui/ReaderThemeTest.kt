package com.lightreader.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.lightreader.app.core.model.AppSkin
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeTest {
    @Test
    fun skinTextAndPrimaryActionsMeetContrastTarget() {
        AppSkin.entries.forEach { skin ->
            val colors = appColorScheme(skin)
            assertContrast(colors.onBackground, colors.background, skin, "background")
            assertContrast(colors.onSurface, colors.surface, skin, "surface")
            assertContrast(colors.onSurfaceVariant, colors.surfaceVariant, skin, "surfaceVariant")
            assertContrast(colors.onPrimary, colors.primary, skin, "primary")
        }
    }

    private fun assertContrast(foreground: Color, background: Color, skin: AppSkin, role: String) {
        val opaqueForeground = foreground.compositeOver(background)
        val lighter = maxOf(opaqueForeground.luminance(), background.luminance())
        val darker = minOf(opaqueForeground.luminance(), background.luminance())
        val ratio = (lighter + .05f) / (darker + .05f)
        assertTrue("$skin $role contrast was $ratio", ratio >= 4.5f)
    }
}
