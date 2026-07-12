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
    ReaderTheme.EYE_CARE -> ReaderPalette(0xFFDDEBD2, 0xFF1F2A23, 0xFF5F8068, 0xFAF4FAF0)
    ReaderTheme.SEPIA -> ReaderPalette(0xFFF5ECD8, 0xFF2B2B2B, 0xFF8A7A5C, 0xFAEDE1C9)
    ReaderTheme.LIGHT_GRAY -> ReaderPalette(0xFFECEDE7, 0xFF2B2B2B, 0xFF777777, 0xFAE2E4DD)
    ReaderTheme.WARM_BROWN -> ReaderPalette(0xFFE5BE8D, 0xFF332719, 0xFF7C654A, 0xFAD7AC78)
    ReaderTheme.FROST_BLUE -> ReaderPalette(0xFFE8F3FA, 0xFF1F2E36, 0xFF6F8794, 0xFAD7E9F2)
    ReaderTheme.SAKURA_PINK -> ReaderPalette(0xFFF7E8EA, 0xFF342629, 0xFF9A757B, 0xFAF0D8DC)
    ReaderTheme.NIGHT -> ReaderPalette(0xFF111713, 0xFFB7C2B0, 0xFF7C8979, 0xFA1B251E)
    ReaderTheme.CUSTOM -> ReaderPalette(
        customBackground,
        customForeground,
        customSecondary,
        (customBackground and 0x00FFFFFFFF) or 0xFA000000,
    )
}
