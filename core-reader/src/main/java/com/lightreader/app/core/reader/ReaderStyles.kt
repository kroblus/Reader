package com.lightreader.app.core.reader

import com.lightreader.app.core.model.ReaderPalette
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderStyle
import com.lightreader.app.core.model.ReaderTheme

fun ReaderPreferences.toReaderStyle(): ReaderStyle = effectiveLayout().let { layout ->
    val immersive = layoutPreset == ReaderLayoutPreset.IMMERSIVE || minimalMode
    ReaderStyle(
        fontSizeSp = layout.fontSizeSp,
        fontWeight = fontWeight,
        lineHeightMultiplier = layout.lineSpacingMultiplier,
        paragraphSpacingDp = layout.paragraphSpacingDp,
        firstLineIndentEm = if (firstLineIndent) firstLineIndentEm else 0f,
        horizontalPaddingDp = layout.horizontalPaddingDp,
        verticalPaddingTopDp = layout.verticalPaddingTopDp,
        verticalPaddingBottomDp = layout.verticalPaddingBottomDp,
        justified = justified,
        fontFamily = fontFamily,
        palette = palette(),
        showHeader = showHeader && !immersive,
        showFooter = showStatus && !immersive,
        showRightProgressBar = showRightProgressBar && !immersive,
        maxContentWidthDp = layout.maxContentWidthDp,
    )
}

fun ReaderPreferences.effectiveLayout(): ReaderLayoutValues = when (layoutPreset) {
    ReaderLayoutPreset.COMFORT -> ReaderLayoutValues(
        fontSizeSp = 18f,
        lineSpacingMultiplier = 1.78f,
        paragraphSpacingDp = 10f,
        horizontalPaddingDp = 30f,
        verticalPaddingTopDp = 58f,
        verticalPaddingBottomDp = 52f,
        maxContentWidthDp = 620f,
    )
    ReaderLayoutPreset.COMPACT -> ReaderLayoutValues(
        fontSizeSp = 16f,
        lineSpacingMultiplier = 1.58f,
        paragraphSpacingDp = 7f,
        horizontalPaddingDp = 18f,
        verticalPaddingTopDp = 42f,
        verticalPaddingBottomDp = 40f,
        maxContentWidthDp = 700f,
    )
    ReaderLayoutPreset.IMMERSIVE -> ReaderLayoutValues(
        fontSizeSp = 18f,
        lineSpacingMultiplier = 1.74f,
        paragraphSpacingDp = 8f,
        horizontalPaddingDp = 28f,
        verticalPaddingTopDp = 34f,
        verticalPaddingBottomDp = 30f,
        maxContentWidthDp = 680f,
    )
    ReaderLayoutPreset.CUSTOM -> ReaderLayoutValues(
        fontSizeSp = fontSizeSp,
        lineSpacingMultiplier = lineSpacingMultiplier,
        paragraphSpacingDp = paragraphSpacingDp,
        horizontalPaddingDp = horizontalPaddingDp,
        verticalPaddingTopDp = verticalPaddingTopDp,
        verticalPaddingBottomDp = verticalPaddingBottomDp,
        maxContentWidthDp = 640f,
    )
}

data class ReaderLayoutValues(
    val fontSizeSp: Float,
    val lineSpacingMultiplier: Float,
    val paragraphSpacingDp: Float,
    val horizontalPaddingDp: Float,
    val verticalPaddingTopDp: Float,
    val verticalPaddingBottomDp: Float,
    val maxContentWidthDp: Float,
)

fun ReaderPreferences.palette(): ReaderPalette = when (theme) {
    ReaderTheme.EYE_CARE -> ReaderPalette(0xFFDDEBD2, 0xFF1F2A23, 0xFF445D4B, 0xFFF4FAF0)
    ReaderTheme.SEPIA -> ReaderPalette(0xFFF5ECD8, 0xFF2B2B2B, 0xFF65573F, 0xFFEDE1C9)
    ReaderTheme.LIGHT_GRAY -> ReaderPalette(0xFFECEDE7, 0xFF2B2B2B, 0xFF565656, 0xFFE2E4DD)
    ReaderTheme.WARM_BROWN -> ReaderPalette(0xFFE5BE8D, 0xFF332719, 0xFF51402C, 0xFFD7AC78)
    ReaderTheme.FROST_BLUE -> ReaderPalette(0xFFE8F3FA, 0xFF1F2E36, 0xFF465D69, 0xFFD7E9F2)
    ReaderTheme.SAKURA_PINK -> ReaderPalette(0xFFF7E8EA, 0xFF342629, 0xFF6C4E53, 0xFFF0D8DC)
    ReaderTheme.NIGHT -> ReaderPalette(0xFF111713, 0xFFB7C2B0, 0xFF879582, 0xFF1B251E)
    ReaderTheme.CUSTOM -> ReaderPalette(
        customBackground or 0xFF000000,
        readableCustomText(customForeground, customBackground),
        readableCustomText(customSecondary, customBackground),
        (customBackground and 0x00FFFFFFFF) or 0xFF000000,
    )
}

/** Keep a custom hue when possible, increasing its luminance contrast for small text. */
private fun readableCustomText(requested: Long, background: Long): Long {
    fun luminance(color: Long): Double {
        val components = listOf(16, 8, 0).map { shift ->
            val c = ((color shr shift) and 255).toDouble() / 255
            if (c <= .04045) c / 12.92 else Math.pow((c + .055) / 1.055, 2.4)
        }
        return components[0] * .2126 + components[1] * .7152 + components[2] * .0722
    }
    val backgroundLuminance = luminance(background)
    fun contrast(color: Long): Double {
        val text = luminance(color)
        return (maxOf(text, backgroundLuminance) + .05) / (minOf(text, backgroundLuminance) + .05)
    }
    val opaque = requested or 0xFF000000
    if (contrast(opaque) >= 4.5) return opaque
    val target = if (contrast(0xFFFFFFFF) > contrast(0xFF000000)) 255 else 0
    for (step in 1..255) {
        var candidate = 0xFF000000L
        for (shift in listOf(16, 8, 0)) {
            val original = ((opaque shr shift) and 255).toInt()
            val value = original + (target - original) * step / 255
            candidate = candidate or (value.toLong() shl shift)
        }
        if (contrast(candidate) >= 4.5) return candidate
    }
    return if (target == 255) 0xFFFFFFFF else 0xFF000000
}
