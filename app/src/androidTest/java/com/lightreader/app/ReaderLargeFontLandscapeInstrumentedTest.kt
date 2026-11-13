package com.lightreader.app

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.journey.TestEvidenceOutput
import com.lightreader.app.ui.ReaderTestTags
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderLargeFontLandscapeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun twoTimesFontLandscapeToolsAndPauseRemainReachable() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val original = listOf("font_scale", "accelerometer_rotation", "user_rotation").associateWith {
            device.executeShellCommand("settings get system $it").trim()
        }
        val application = compose.activity.application as ReaderApplication
        val vm = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
        val runId = InstrumentationRegistry.getArguments().getString("runId")
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_") ?: "direct-${android.os.Process.myPid()}"
        val screenshots = "/sdcard/Download/LightReader-QA-Evidence/$runId/screenshots"
        fun capture(name: String) {
            compose.waitForIdle()
            device.executeShellCommand("mkdir -p $screenshots")
            device.executeShellCommand("screencap -p $screenshots/$name.png")
        }
        fun revealChrome() {
            if (!vm.readerState.value.toolbarVisible) {
                compose.onNodeWithTag(ReaderTestTags.READER).performTouchInput { click(center) }
            }
        }
        try {
            runBlocking {
                application.container.database.clearAllTables()
                application.container.settingsRepository.save(ReaderPreferences(lockPortrait = false, theme = ReaderTheme.NIGHT, autoReadIntervalSeconds = 60))
                val root = File(application.filesDir, "books/large-font-landscape").apply { mkdirs() }
                val raw = "中文 English 😀大字体短横屏正文。".repeat(800)
                val file = File(root, "chapter.txt").apply { writeText(raw) }
                application.container.database.readerDao().insertBookWithChapters(
                    BookEntity("large-font-landscape", "大字号与横屏验收", null, "TXT", root.absolutePath, 1, null, raw.length.toLong(), 1, null),
                    listOf(ChapterEntity(bookId = "large-font-landscape", orderIndex = 0, title = "很长的章节标题 A long chapter title", contentPath = file.absolutePath, charCount = raw.length, sourceUrl = null)),
                )
            }
            device.executeShellCommand("settings put system font_scale 2.0")
            device.setOrientationLeft()
            compose.waitUntil(10_000) {
                val configuration = compose.activity.resources.configuration
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && configuration.fontScale >= 1.99f
            }
            assertTrue("This acceptance scenario requires a short landscape viewport", compose.activity.resources.configuration.screenHeightDp <= 480)
            compose.runOnIdle { vm.openBook("large-font-landscape") }
            compose.waitUntil(20_000) { !vm.readerState.value.loading && vm.readerState.value.pages.isNotEmpty() }
            val origin = vm.currentSearchOrigin()
            revealChrome()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.reader_settings_content_description))
                .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag(ReaderTestTags.READER_SETTINGS_MORE).performScrollTo().assertIsDisplayed()
            capture("025_reader_font2_landscape_tools")
            compose.onNodeWithTag(ReaderTestTags.READER_SETTINGS_MORE).performClick()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_back)).assertIsDisplayed().performClick()
            assertEquals("Tool scrolling must retain the body anchor", origin, vm.currentSearchOrigin())
            revealChrome()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.reader_more_actions)).assertIsDisplayed().performClick()
            compose.onNodeWithTag(ReaderTestTags.READER_AUTO_TOGGLE).performClick()
            val pause = compose.onNodeWithText(compose.activity.getString(R.string.reader_pause_auto)).assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertTrue("Pause must expose an accessibility click action", pause.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.reader_pause_auto_reading)).assertIsDisplayed()
            capture("026_reader_font2_landscape_auto_pause")
            pause.performClick()
            compose.waitUntil(5_000) { !vm.readerState.value.autoReading }
            TestEvidenceOutput.writeText("accessibility/reader-font2-landscape.txt",
                "device=${android.os.Build.MODEL}; api=${android.os.Build.VERSION.SDK_INT}; fontScale=${compose.activity.resources.configuration.fontScale}; " +
                "orientation=landscape; moreSettings=visible/clicked; pause=visible/clickable/named; anchorRetained=true; TalkBackSpeech=not-tested")
        } finally {
            try {
                compose.runOnIdle { vm.setAutoReading(false) }
            } finally {
                try { device.unfreezeRotation() } finally {
                    val restoreFailures = original.mapNotNull { (key, value) ->
                        runCatching {
                            if (value == "null" || value.isBlank()) device.executeShellCommand("settings delete system $key")
                            else {
                                require(value.matches(Regex("[-0-9.]+")))
                                device.executeShellCommand("settings put system $key $value")
                            }
                        }.exceptionOrNull()
                    }
                    if (restoreFailures.isNotEmpty()) throw IllegalStateException("Failed to restore temporary display settings", restoreFailures.first())
                }
            }
        }
    }
}
