package com.lightreader.app.core.reader

import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderLayoutSnapshot

fun ReaderPreferences.layoutSnapshot(): ReaderLayoutSnapshot {
    val effective = effectiveLayout()
    return ReaderLayoutSnapshot(
        fontSizeSp = effective.fontSizeSp,
        fontWeight = this.fontWeight,
        lineSpacingMultiplier = effective.lineSpacingMultiplier,
        paragraphSpacingDp = effective.paragraphSpacingDp,
        firstLineIndent = this.firstLineIndent,
        firstLineIndentEm = this.firstLineIndentEm,
        horizontalPaddingDp = effective.horizontalPaddingDp,
        verticalPaddingTopDp = effective.verticalPaddingTopDp,
        verticalPaddingBottomDp = effective.verticalPaddingBottomDp,
        justified = this.justified,
        fontFamily = this.fontFamily,
    )
}

fun ReaderPreferences.applyLayout(snapshot: ReaderLayoutSnapshot): ReaderPreferences = copy(
    fontSizeSp = snapshot.fontSizeSp,
    fontWeight = snapshot.fontWeight,
    lineSpacingMultiplier = snapshot.lineSpacingMultiplier,
    paragraphSpacingDp = snapshot.paragraphSpacingDp,
    firstLineIndent = snapshot.firstLineIndent,
    firstLineIndentEm = snapshot.firstLineIndentEm,
    horizontalPaddingDp = snapshot.horizontalPaddingDp,
    verticalPaddingTopDp = snapshot.verticalPaddingTopDp,
    verticalPaddingBottomDp = snapshot.verticalPaddingBottomDp,
    justified = snapshot.justified,
    fontFamily = snapshot.fontFamily,
    layoutPreset = ReaderLayoutPreset.CUSTOM,
)

fun ReaderPreferences.selectLayoutPreset(preset: ReaderLayoutPreset): ReaderPreferences {
    val saved = if (layoutPreset == ReaderLayoutPreset.CUSTOM) layoutSnapshot() else lastCustomLayout
    return if (preset == ReaderLayoutPreset.CUSTOM) {
        applyLayout(saved ?: layoutSnapshot()).copy(lastCustomLayout = saved ?: layoutSnapshot())
    } else copy(layoutPreset = preset, lastCustomLayout = saved)
}

/** Apply only fields edited against the UI's baseline to the latest stored value. */
fun mergeReaderPreferenceEdit(base: ReaderPreferences, edit: ReaderPreferences, current: ReaderPreferences): ReaderPreferences {
    var merged = current.copy(
        appSkin = if (edit.appSkin != base.appSkin) edit.appSkin else current.appSkin,
        appLanguage = if (edit.appLanguage != base.appLanguage) edit.appLanguage else current.appLanguage,
        libraryTaglineIndex = if (edit.libraryTaglineIndex != base.libraryTaglineIndex) edit.libraryTaglineIndex else current.libraryTaglineIndex,
        developerToolsEnabled = if (edit.developerToolsEnabled != base.developerToolsEnabled) edit.developerToolsEnabled else current.developerToolsEnabled,
        cleanTxtNoise = if (edit.cleanTxtNoise != base.cleanTxtNoise) edit.cleanTxtNoise else current.cleanTxtNoise,
        layoutPreset = if (edit.layoutPreset != base.layoutPreset) edit.layoutPreset else current.layoutPreset,
        lastCustomLayout = if (edit.lastCustomLayout != base.lastCustomLayout) edit.lastCustomLayout else current.lastCustomLayout,
        fontSizeSp = if (edit.fontSizeSp != base.fontSizeSp) edit.fontSizeSp else current.fontSizeSp,
        fontWeight = if (edit.fontWeight != base.fontWeight) edit.fontWeight else current.fontWeight,
        lineSpacingMultiplier = if (edit.lineSpacingMultiplier != base.lineSpacingMultiplier) edit.lineSpacingMultiplier else current.lineSpacingMultiplier,
        paragraphSpacingDp = if (edit.paragraphSpacingDp != base.paragraphSpacingDp) edit.paragraphSpacingDp else current.paragraphSpacingDp,
        firstLineIndent = if (edit.firstLineIndent != base.firstLineIndent) edit.firstLineIndent else current.firstLineIndent,
        firstLineIndentEm = if (edit.firstLineIndentEm != base.firstLineIndentEm) edit.firstLineIndentEm else current.firstLineIndentEm,
        horizontalPaddingDp = if (edit.horizontalPaddingDp != base.horizontalPaddingDp) edit.horizontalPaddingDp else current.horizontalPaddingDp,
        verticalPaddingTopDp = if (edit.verticalPaddingTopDp != base.verticalPaddingTopDp) edit.verticalPaddingTopDp else current.verticalPaddingTopDp,
        verticalPaddingBottomDp = if (edit.verticalPaddingBottomDp != base.verticalPaddingBottomDp) edit.verticalPaddingBottomDp else current.verticalPaddingBottomDp,
        justified = if (edit.justified != base.justified) edit.justified else current.justified,
        fontFamily = if (edit.fontFamily != base.fontFamily) edit.fontFamily else current.fontFamily,
        theme = if (edit.theme != base.theme) edit.theme else current.theme,
        lastNonNightTheme = if (edit.lastNonNightTheme != base.lastNonNightTheme) edit.lastNonNightTheme else current.lastNonNightTheme,
        customBackground = if (edit.customBackground != base.customBackground) edit.customBackground else current.customBackground,
        customForeground = if (edit.customForeground != base.customForeground) edit.customForeground else current.customForeground,
        customSecondary = if (edit.customSecondary != base.customSecondary) edit.customSecondary else current.customSecondary,
        brightness = if (edit.brightness != base.brightness) edit.brightness else current.brightness,
        keepScreenOn = if (edit.keepScreenOn != base.keepScreenOn) edit.keepScreenOn else current.keepScreenOn,
        lockPortrait = if (edit.lockPortrait != base.lockPortrait) edit.lockPortrait else current.lockPortrait,
        showStatus = if (edit.showStatus != base.showStatus) edit.showStatus else current.showStatus,
        showHeader = if (edit.showHeader != base.showHeader) edit.showHeader else current.showHeader,
        showRightProgressBar = if (edit.showRightProgressBar != base.showRightProgressBar) edit.showRightProgressBar else current.showRightProgressBar,
        minimalMode = if (edit.minimalMode != base.minimalMode) edit.minimalMode else current.minimalMode,
        autoReadIntervalSeconds = if (edit.autoReadIntervalSeconds != base.autoReadIntervalSeconds) edit.autoReadIntervalSeconds else current.autoReadIntervalSeconds,
        volumeKeys = if (edit.volumeKeys != base.volumeKeys) edit.volumeKeys else current.volumeKeys,
        pageTurnMode = if (edit.pageTurnMode != base.pageTurnMode) edit.pageTurnMode else current.pageTurnMode,
        fullScreenTapNext = if (edit.fullScreenTapNext != base.fullScreenTapNext) edit.fullScreenTapNext else current.fullScreenTapNext,
    )
    val layoutChanged = edit.fontSizeSp != base.fontSizeSp ||
        edit.fontWeight != base.fontWeight ||
        edit.lineSpacingMultiplier != base.lineSpacingMultiplier ||
        edit.paragraphSpacingDp != base.paragraphSpacingDp ||
        edit.firstLineIndent != base.firstLineIndent ||
        edit.firstLineIndentEm != base.firstLineIndentEm ||
        edit.horizontalPaddingDp != base.horizontalPaddingDp ||
        edit.verticalPaddingTopDp != base.verticalPaddingTopDp ||
        edit.verticalPaddingBottomDp != base.verticalPaddingBottomDp ||
        edit.justified != base.justified ||
        edit.fontFamily != base.fontFamily
    if (edit.layoutPreset != base.layoutPreset) {
        if (edit.layoutPreset == ReaderLayoutPreset.CUSTOM) {
            merged = merged.copy(lastCustomLayout = merged.layoutSnapshot())
        }
    } else if (layoutChanged) {
        val effectiveBase = base.applyLayout(base.layoutSnapshot())
        var customized = merged.applyLayout(merged.layoutSnapshot())
        customized = customized.copy(
            fontSizeSp = if (edit.fontSizeSp != base.fontSizeSp) edit.fontSizeSp else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.fontSizeSp else effectiveBase.fontSizeSp,
            fontWeight = if (edit.fontWeight != base.fontWeight) edit.fontWeight else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.fontWeight else effectiveBase.fontWeight,
            lineSpacingMultiplier = if (edit.lineSpacingMultiplier != base.lineSpacingMultiplier) edit.lineSpacingMultiplier else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.lineSpacingMultiplier else effectiveBase.lineSpacingMultiplier,
            paragraphSpacingDp = if (edit.paragraphSpacingDp != base.paragraphSpacingDp) edit.paragraphSpacingDp else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.paragraphSpacingDp else effectiveBase.paragraphSpacingDp,
            firstLineIndent = if (edit.firstLineIndent != base.firstLineIndent) edit.firstLineIndent else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.firstLineIndent else effectiveBase.firstLineIndent,
            firstLineIndentEm = if (edit.firstLineIndentEm != base.firstLineIndentEm) edit.firstLineIndentEm else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.firstLineIndentEm else effectiveBase.firstLineIndentEm,
            horizontalPaddingDp = if (edit.horizontalPaddingDp != base.horizontalPaddingDp) edit.horizontalPaddingDp else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.horizontalPaddingDp else effectiveBase.horizontalPaddingDp,
            verticalPaddingTopDp = if (edit.verticalPaddingTopDp != base.verticalPaddingTopDp) edit.verticalPaddingTopDp else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.verticalPaddingTopDp else effectiveBase.verticalPaddingTopDp,
            verticalPaddingBottomDp = if (edit.verticalPaddingBottomDp != base.verticalPaddingBottomDp) edit.verticalPaddingBottomDp else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.verticalPaddingBottomDp else effectiveBase.verticalPaddingBottomDp,
            justified = if (edit.justified != base.justified) edit.justified else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.justified else effectiveBase.justified,
            fontFamily = if (edit.fontFamily != base.fontFamily) edit.fontFamily else if (current.layoutPreset == ReaderLayoutPreset.CUSTOM) current.fontFamily else effectiveBase.fontFamily,
        )
        merged = customized.copy(lastCustomLayout = customized.layoutSnapshot())
    }
    return merged
}
