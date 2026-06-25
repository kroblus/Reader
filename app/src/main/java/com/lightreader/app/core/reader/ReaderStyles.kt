package com.lightreader.app.core.reader

import com.lightreader.app.core.model.ReaderPalette
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderStyle
import com.lightreader.app.core.model.ReaderTheme

fun ReaderPreferences.toReaderStyle(): ReaderStyle = ReaderStyle(
    fontSizeSp = fontSizeSp,
    fontWeight = fontWeight,
    lineHeightMultiplier = lineSpacingMultiplier,
    paragraphSpacingDp = paragraphSpacingDp,
    firstLineIndentEm = if (firstLineIndent) firstLineIndentEm else 0f,
    horizontalPaddingDp = horizontalPaddingDp,
    verticalPaddingTopDp = verticalPaddingTopDp,
    verticalPaddingBottomDp = verticalPaddingBottomDp,
    justified = justified,
    fontFamily = fontFamily,
    palette = palette(),
    showHeader = showHeader && !minimalMode,
    showFooter = showStatus && !minimalMode,
    showRightProgressBar = showRightProgressBar && !minimalMode,
)

fun ReaderPreferences.palette(): ReaderPalette = when (theme) {
    ReaderTheme.EYE_CARE -> ReaderPalette(0xFFB8C9A7, 0xFF26301F, 0xFF6F8063, 0xE6C6D4B8)
    ReaderTheme.SEPIA -> ReaderPalette(0xFFF5ECD8, 0xFF2B2B2B, 0xFF8A7A5C, 0xE6EDE1C9)
    ReaderTheme.LIGHT_GRAY -> ReaderPalette(0xFFECEDE7, 0xFF2B2B2B, 0xFF777777, 0xE6E2E4DD)
    ReaderTheme.WARM_BROWN -> ReaderPalette(0xFFE5BE8D, 0xFF332719, 0xFF7C654A, 0xE6D7AC78)
    ReaderTheme.FROST_BLUE -> ReaderPalette(0xFFE8F3FA, 0xFF1F2E36, 0xFF6F8794, 0xE6D7E9F2)
    ReaderTheme.SAKURA_PINK -> ReaderPalette(0xFFF7E8EA, 0xFF342629, 0xFF9A757B, 0xE6F0D8DC)
    ReaderTheme.NIGHT -> ReaderPalette(0xFF111713, 0xFF7F8A78, 0xFF586356, 0xE61B251E)
    ReaderTheme.CUSTOM -> ReaderPalette(
        customBackground,
        customForeground,
        customSecondary,
        (customBackground and 0x00FFFFFFFF) or 0xE600000000,
    )
}
