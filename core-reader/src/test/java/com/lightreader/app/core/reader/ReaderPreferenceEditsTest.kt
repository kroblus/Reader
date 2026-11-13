package com.lightreader.app.core.reader

import com.lightreader.app.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReaderPreferenceEditsTest {
    @Test fun customSurvivesPresetSwitchAndSerialization() {
        val a = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 25f, lineSpacingMultiplier = 1.91f)
        val preset = a.selectLayoutPreset(ReaderLayoutPreset.COMPACT)
        assertEquals(16f, preset.effectiveLayout().fontSizeSp)
        val reopened = Json.decodeFromString<ReaderPreferences>(Json.encodeToString(preset))
        val restored = reopened.selectLayoutPreset(ReaderLayoutPreset.CUSTOM)
        assertEquals(a.layoutSnapshot(), restored.layoutSnapshot())
    }

    @Test fun editOfPresetCreatesCustomFromEffectiveValues() {
        val base = ReaderPreferences(layoutPreset = ReaderLayoutPreset.COMPACT, fontSizeSp = 29f)
        val edited = mergeReaderPreferenceEdit(base, base.copy(fontWeight = 600), base)
        assertEquals(ReaderLayoutPreset.CUSTOM, edited.layoutPreset)
        assertEquals(16f, edited.fontSizeSp)
        assertEquals(600, edited.fontWeight)
        assertEquals(edited.layoutSnapshot(), edited.lastCustomLayout)
    }

    @Test fun simultaneousFieldsAreMergedWithoutOverwritingNewerPreferences() {
        val base = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM)
        val fontEdit = mergeReaderPreferenceEdit(base, base.copy(fontSizeSp = 26f), base)
        val second = mergeReaderPreferenceEdit(base, base.copy(lineSpacingMultiplier = 1.9f), fontEdit.copy(appLanguage = AppLanguage.EN))
        assertEquals(26f, second.fontSizeSp)
        assertEquals(1.9f, second.lineSpacingMultiplier)
        assertEquals(AppLanguage.EN, second.appLanguage)
        assertEquals(second.layoutSnapshot(), second.lastCustomLayout)
    }

    @Test fun changingColorDoesNotMutateCustomOrLayoutFingerprint() {
        val a = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM).let { it.copy(lastCustomLayout = it.layoutSnapshot()) }
        val b = mergeReaderPreferenceEdit(a, a.copy(theme = ReaderTheme.NIGHT), a)
        assertEquals(a.lastCustomLayout, b.lastCustomLayout)
        assertEquals(a.toReaderStyle().layoutFingerprint(), b.toReaderStyle().layoutFingerprint())
    }
}
