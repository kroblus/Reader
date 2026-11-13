package com.lightreader.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.ui.ReaderSettingsPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsDraftInstrumentedTest {
    @get:Rule val compose = createComposeRule()

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
