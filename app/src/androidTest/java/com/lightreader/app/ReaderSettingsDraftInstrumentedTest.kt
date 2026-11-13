package com.lightreader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.reader.layoutSnapshot
import com.lightreader.app.ui.ReaderSettingsPanel
import com.lightreader.app.ui.ReaderSettingsDetailScreen
import com.lightreader.app.ui.ReaderTestTags
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsDraftInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun friendlySpacingChoicesPreserveOtherPreferencesAndAllFontsAreSelectable() {
        val original = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM,
            fontSizeSp = 23f, lineSpacingMultiplier = 1.65f, horizontalPaddingDp = 25f,
            verticalPaddingTopDp = 70f, verticalPaddingBottomDp = 44f, brightness = .4f)
        val preferences = mutableStateOf(original)
        compose.setContent {
            MaterialTheme { ReaderSettingsDetailScreen(preferences.value, false, { preferences.value = it }, {}, {}) }
        }
        FontFamilyOption.entries.forEach { font ->
            compose.onNodeWithTag("reader_font_${font.name.lowercase()}")
                .performScrollTo().performClick().assertIsSelected()
            assertEquals(font, preferences.value.fontFamily)
            assertEquals(original.layoutSnapshot().copy(fontFamily = font), preferences.value.layoutSnapshot())
        }
        compose.onNodeWithTag("reader_layout_fine_tune").performScrollTo().performClick()
        compose.onNodeWithTag("reader_line_spacing_0").performScrollTo().performClick().assertIsSelected()
        assertEquals(1.48f, preferences.value.lineSpacingMultiplier, .001f)
        assertEquals(original.horizontalPaddingDp, preferences.value.horizontalPaddingDp, .001f)
        compose.onNodeWithTag("reader_side_space_2").performScrollTo().performClick().assertIsSelected()
        assertEquals(42f, preferences.value.horizontalPaddingDp, .001f)
        assertEquals(original.verticalPaddingTopDp, preferences.value.verticalPaddingTopDp, .001f)
        assertEquals(original.verticalPaddingBottomDp, preferences.value.verticalPaddingBottomDp, .001f)
        compose.onNodeWithTag("reader_top_space_0").performScrollTo().performClick()
        assertEquals(34f, preferences.value.verticalPaddingTopDp, .001f)
        assertEquals(original.verticalPaddingBottomDp, preferences.value.verticalPaddingBottomDp, .001f)
        compose.onNodeWithTag("reader_indent_0").performScrollTo().performClick()
        assertFalse(preferences.value.firstLineIndent)
        compose.onNodeWithTag("reader_indent_3").performScrollTo().performClick()
        assertTrue(preferences.value.firstLineIndent)
        assertEquals(3f, preferences.value.firstLineIndentEm, .001f)
        assertEquals(FontFamilyOption.entries.last(), preferences.value.fontFamily)
        assertEquals(original.brightness, preferences.value.brightness, .001f)
        assertEquals(original.fontSizeSp, preferences.value.fontSizeSp, .001f)
    }

    @Test fun threePresetsAreVisibleImmediatelyAndSavedCustomLayoutRemainsReachable() {
        val original = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 25f,
            lineSpacingMultiplier = 1.9f, brightness = .4f)
        val preferences = mutableStateOf(original)
        val detail = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                if (detail.value) {
                    ReaderSettingsDetailScreen(preferences.value, false, { preferences.value = it }, {}, { detail.value = false })
                } else {
                    Box(Modifier.fillMaxWidth().height(260.dp).verticalScroll(rememberScrollState())) {
                        ReaderSettingsPanel(preferences.value, { preferences.value = it }, { detail.value = true })
                    }
                }
            }
        }
        listOf("comfort", "compact", "immersive").forEach {
            compose.onNodeWithTag("reader_preset_$it").assertIsDisplayed()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onAllNodesWithText(context.getString(R.string.layout_custom)).assertCountEquals(0)
        for (preset in listOf(ReaderLayoutPreset.COMFORT, ReaderLayoutPreset.COMPACT, ReaderLayoutPreset.IMMERSIVE)) {
            compose.onNodeWithTag("reader_preset_${preset.name.lowercase()}").performClick().assertIsSelected()
            assertEquals(preset, preferences.value.layoutPreset)
        }
        compose.onNodeWithTag(ReaderTestTags.READER_SETTINGS_MORE).performScrollTo().performClick()
        compose.onNodeWithTag("reader_saved_layout").performScrollTo().performClick()
        assertEquals(ReaderLayoutPreset.CUSTOM, preferences.value.layoutPreset)
        assertEquals(original.layoutSnapshot(), preferences.value.layoutSnapshot())
        assertEquals(.4f, preferences.value.brightness, .001f)
        compose.onNodeWithTag("reader_layout_fine_tune_content").assertDoesNotExist()
        compose.onNodeWithTag("reader_layout_fine_tune").performScrollTo().performClick()
        compose.onNodeWithTag("reader_layout_fine_tune_content").assertExists()
    }

    @Test fun dragPreviewsButClosingPanelCommitsLastValueExactlyOnce() {
        val visible = mutableStateOf(true)
        val initial = ReaderPreferences(brightness = .2f)
        val commits = mutableListOf<ReaderPreferences>()
        val previews = mutableListOf<ReaderPreferences>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    if (visible.value) ReaderSettingsPanel(initial, { commits += it }, {}, onPreview = { previews += it })
                }
            }
        }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.settings_brightness)
        compose.onNodeWithContentDescription(label).performTouchInput {
            down(Offset(width * .2f, center.y))
            moveTo(Offset(width * .8f, center.y), 150)
        }
        compose.waitUntil(5_000) { previews.isNotEmpty() }
        assertTrue("Drag must not persist each move", commits.isEmpty())
        val lastPreview = previews.last().brightness
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertEquals(1, commits.size)
        assertEquals(lastPreview, commits.single().brightness)
        assertTrue(commits.single().brightness > initial.brightness)
    }

    @Test fun releasingSliderCommitsOnce() {
        val commits = mutableListOf<ReaderPreferences>()
        compose.setContent { MaterialTheme { ReaderSettingsPanel(ReaderPreferences(brightness = .2f), { commits += it }, {}) } }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.settings_brightness)
        compose.onNodeWithContentDescription(label).performTouchInput {
            down(Offset(width * .2f, center.y))
            moveTo(Offset(width * .7f, center.y), 150)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, commits.size)
        assertTrue(commits.single().brightness > .2f)
    }
}
