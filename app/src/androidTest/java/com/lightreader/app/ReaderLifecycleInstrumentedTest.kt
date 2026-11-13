package com.lightreader.app

import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.feature.reader.ReaderSessionController
import com.lightreader.app.feature.reader.ReaderChapterCacheKey
import com.lightreader.app.feature.reader.ReaderChapterContent
import com.lightreader.app.core.reader.selectLayoutPreset
import com.lightreader.app.core.reader.layoutSnapshot
import java.io.File
import kotlinx.coroutines.flow.first
import com.lightreader.app.journey.FixtureCatalog
import com.lightreader.app.journey.FixtureId
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.lightreader.app.ui.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var application: ReaderApplication

    @Before
    fun resetIsolatedQaData() = runBlocking {
        assertTrue("Lifecycle tests must use the isolated QA package", BuildConfig.APPLICATION_ID.endsWith(".qa"))
        application = composeRule.activity.application as ReaderApplication
        application.container.database.clearAllTables()
        application.container.settingsRepository.save(
            ReaderPreferences(pageTurnMode = PageTurnMode.NONE, autoReadIntervalSeconds = 3),
        )
    }

    @Test
    fun automaticReadingPausesWhileInactiveAndResumesFromSavedPosition() {
        val fixture = FixtureCatalog.materialize(composeRule.activity, FixtureId.MULTI_PAGE_TXT)
        val book = runBlocking { application.container.bookRepository.import(Uri.fromFile(fixture)) }
        val viewModel = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        composeRule.runOnIdle { viewModel.openBook(book.id) }
        composeRule.waitUntil(20_000) {
            val state = viewModel.readerState.value
            !state.loading && state.pages.size >= 4 && viewModel.uiState.value.preferences.autoReadIntervalSeconds == 3
        }
        composeRule.runOnIdle {
            viewModel.pageSelected(1)
            viewModel.setAutoReading(true)
        }
        val scenario = composeRule.activityRule.scenario
        try {
            // STARTED sends ON_PAUSE while the activity is still visible, such as under another activity.
            scenario.moveToState(Lifecycle.State.STARTED)
            val paused = viewModel.readerState.value
            SystemClock.sleep(6_500)
            assertEquals("Automatic reading must not turn pages while paused", paused.pageIndex, viewModel.readerState.value.pageIndex)
            assertEquals(paused.chapterIndex, viewModel.readerState.value.chapterIndex)

            // CREATED sends ON_STOP without disposing this activity's composition.
            scenario.moveToState(Lifecycle.State.CREATED)
            val stopped = viewModel.readerState.value
            val expectedOffset = stopped.pages[stopped.pageIndex].startOffset
            SystemClock.sleep(6_500)
            assertEquals("Background time must not consume unread pages", stopped.pageIndex, viewModel.readerState.value.pageIndex)
            assertEquals(stopped.chapterIndex, viewModel.readerState.value.chapterIndex)
            val saved = runBlocking { application.container.bookRepository.progress(book.id) }
            assertEquals("Leaving the foreground must persist the visible position", expectedOffset, saved?.charOffset)

            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitUntil(8_000) {
                viewModel.readerState.value.pageIndex > stopped.pageIndex ||
                    viewModel.readerState.value.chapterIndex > stopped.chapterIndex
            }
        } finally {
            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.runOnIdle { viewModel.setAutoReading(false) }
        }
    }

    @Test
    fun immediatelyLeavingAndReopeningRestoresTheLastVisiblePage() {
        val fixture = FixtureCatalog.materialize(composeRule.activity, FixtureId.MULTI_PAGE_TXT)
        val book = runBlocking { application.container.bookRepository.import(Uri.fromFile(fixture)) }
        val viewModel = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        composeRule.runOnIdle { viewModel.openBook(book.id) }
        composeRule.waitUntil(20_000) {
            !viewModel.readerState.value.loading && viewModel.readerState.value.pages.size >= 4
        }
        var expectedOffset = -1
        composeRule.runOnIdle {
            viewModel.pageSelected(2)
            expectedOffset = viewModel.readerState.value.pages[2].startOffset
            // Keep all three actions in one main-thread turn: the debounced save cannot finish first.
            viewModel.goBack()
            viewModel.openBook(book.id)
        }
        composeRule.waitUntil(20_000) {
            val state = viewModel.readerState.value
            state.book?.id == book.id && !state.loading && state.pages.isNotEmpty()
        }
        val restored = viewModel.readerState.value
        assertEquals(expectedOffset, restored.pages[restored.pageIndex].startOffset)
        assertEquals(expectedOffset, runBlocking { application.container.bookRepository.progress(book.id)?.charOffset })
    }
    @Test
    fun evictedBodyWithCachedPagesRepaginatesTargetAndRapidChangesOnlyShowLastChapter() {
        val id = "cache-consistency-${System.nanoTime()}"
        val root = File(application.filesDir, "books/$id").apply { mkdirs() }
        val markers = listOf("ALPHA", "BRAVO", "CHARLIE")
        val rows = markers.mapIndexed { index, marker ->
            val raw = ("$marker 中文😀正文不串章。\n").repeat(700)
            val file = File(root, "$index.txt").apply { writeText(raw) }
            ChapterEntity(bookId = id, orderIndex = index, title = marker, contentPath = file.absolutePath, charCount = raw.length, sourceUrl = null)
        }
        runBlocking {
            application.container.settingsRepository.save(ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, pageTurnMode = PageTurnMode.NONE))
            application.container.database.readerDao().insertBookWithChapters(
                BookEntity(id, "Cache consistency", null, "TXT", root.absolutePath, 1, null, rows.sumOf { it.charCount.toLong() }, 3, null), rows,
            )
        }
        val vm = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        composeRule.runOnIdle { vm.openBook(id) }
        composeRule.waitUntil(20_000) { !vm.readerState.value.loading && vm.readerState.value.nextPreview != null }
        val sessionField = ReaderViewModel::class.java.getDeclaredField("sessionController").apply { isAccessible = true }
        val session = sessionField.get(vm) as ReaderSessionController
        val targetId = vm.readerState.value.chapters[1].id
        composeRule.runOnIdle {
            session.cancelReaderWork()
            assertTrue("Target page cache was prefetched", vm.readerState.value.nextPreview != null)
            session.putChapter(ReaderChapterCacheKey(id, -1), ReaderChapterContent("x".repeat(2_000_000), emptyList()))
            assertEquals(null, session.chapter(ReaderChapterCacheKey(id, targetId)))
            vm.selectChapter(1)
        }
        composeRule.waitUntil(20_000) { !vm.readerState.value.loading && vm.readerState.value.chapterIndex == 1 }
        composeRule.runOnIdle { vm.pageSelected(2) }
        val anchor = vm.readerState.value.pages[2].startOffset
        runBlocking { application.container.settingsRepository.update { it.copy(fontSizeSp = 26f) } }
        composeRule.waitUntil(20_000) {
            val state = vm.readerState.value
            !state.loading && state.layoutPreferences?.fontSizeSp == 26f
        }
        val repaged = vm.readerState.value
        val body = repaged.pages.flatMap { it.lines }.filterNot { it.isChapterTitle }.joinToString("") { it.text }
        assertTrue(body.contains("BRAVO"))
        assertTrue(!body.contains("ALPHA") && !body.contains("CHARLIE"))
        val current = repaged.pages[repaged.pageIndex]
        assertTrue(current.startOffset <= anchor && current.endOffset > anchor)
        composeRule.runOnIdle { vm.selectChapter(0); vm.selectChapter(1); vm.selectChapter(2) }
        composeRule.waitUntil(20_000) { !vm.readerState.value.loading && vm.readerState.value.chapterIndex == 2 }
        assertTrue(vm.readerState.value.pages.joinToString("") { it.text }.contains("CHARLIE"))
        assertEquals(null, vm.readerState.value.error)
        val latest = vm.readerState.value.pages[vm.readerState.value.pageIndex].startOffset
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(20_000) { !vm.readerState.value.loading && vm.readerState.value.chapterIndex == 2 }
        assertEquals(latest, vm.readerState.value.pages[vm.readerState.value.pageIndex].startOffset)
    }

    @Test
    fun customSnapshotSurvivesRepositoryReload() = runBlocking {
        val custom = ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 25f, lineSpacingMultiplier = 1.92f)
        application.container.settingsRepository.save(custom)
        val saved = application.container.settingsRepository.preferences.first()
        application.container.settingsRepository.save(saved.selectLayoutPreset(ReaderLayoutPreset.COMPACT))
        val freshRepository = com.lightreader.app.core.settings.ReaderSettingsRepository(application)
        val restored = freshRepository.preferences.first().selectLayoutPreset(ReaderLayoutPreset.CUSTOM)
        assertEquals(custom.layoutSnapshot(), restored.layoutSnapshot())
    }
    @Test
    fun legacyPresetWithoutCustomSnapshotKeepsItsEffectiveDisplay() = runBlocking {
        val legacy = ReaderPreferences(layoutPreset = ReaderLayoutPreset.COMPACT, fontSizeSp = 29f, lastCustomLayout = null)
        application.container.settingsRepository.save(legacy)
        val reloaded = application.container.settingsRepository.preferences.first()
        assertEquals(ReaderLayoutPreset.COMPACT, reloaded.layoutPreset)
        assertEquals(null, reloaded.lastCustomLayout)
        assertEquals(16f, reloaded.layoutSnapshot().fontSizeSp)
    }

    @Test
    fun firstPageReflowKeepsDisplayedPagesAndOnlyCommitsOneReplacement() {
        runBlocking {
            application.container.settingsRepository.save(ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM, fontSizeSp = 18f, pageTurnMode = PageTurnMode.NONE))
        }
        val book = runBlocking {
            val id = "first-page-reflow-${System.nanoTime()}"
            val root = File(application.filesDir, "books/$id").apply { mkdirs() }
            // The body deliberately starts at UTF-16 offset zero; a repeated chapter
            // heading in the source would make page zero start later and miss this bug.
            val raw = "First body 中文正文😀 needs consistent reflow.\n".repeat(900)
            val file = File(root, "body.txt").apply { writeText(raw) }
            application.container.database.readerDao().insertBookWithChapters(
                BookEntity(id, "First-page reflow", null, "TXT", root.absolutePath, 1, null, raw.length.toLong(), 1, null),
                listOf(ChapterEntity(bookId = id, orderIndex = 0, title = "A separate title", contentPath = file.absolutePath, charCount = raw.length, sourceUrl = null)),
            )
            application.container.bookRepository.book(id)!!
        }
        val vm = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        val openingStates = mutableListOf<ReaderUiState>()
        val reflowStates = mutableListOf<ReaderUiState>()
        var observingReflow = false
        lateinit var observer: Job
        composeRule.runOnIdle {
            observer = CoroutineScope(Dispatchers.Main.immediate).launch(start = CoroutineStart.UNDISPATCHED) {
                vm.readerState.collect { state ->
                    if (state.book?.id == book.id) {
                        if (observingReflow) reflowStates += state else openingStates += state
                    }
                }
            }
            vm.openBook(book.id)
        }
        try {
            composeRule.waitUntil(20_000) { !vm.readerState.value.loading && vm.readerState.value.pages.size >= 4 }
            val before = vm.readerState.value
            assertEquals(0, before.pageIndex)
            assertEquals("Exercise the zero-offset preview branch", 0, before.pages[0].startOffset)
            assertTrue("A first open still provides an early page before full pagination", openingStates.any { it.loading && it.pages.size == 1 })
            composeRule.runOnIdle { observingReflow = true }
            runBlocking { application.container.settingsRepository.update { it.copy(fontSizeSp = 27f) } }
            composeRule.waitUntil(20_000) {
                val value = vm.readerState.value
                !value.loading && value.layoutPreferences?.fontSizeSp == 27f
            }
            val after = vm.readerState.value
            assertEquals("Reflow must publish one complete replacement, not a one-page intermediate layout", before.layoutVersion + 1, after.layoutVersion)
            assertEquals(before.chapters[before.chapterIndex].id, after.chapters[after.chapterIndex].id)
            assertEquals(before.pages[0].startOffset, after.pages[after.pageIndex].startOffset)
            val loadingStates = reflowStates.filter { it.loading }
            assertTrue("The actual asynchronous reflow was observed", loadingStates.isNotEmpty())
            assertTrue("Keep old pages and font snapshot while computing the replacement", loadingStates.all {
                it.pages === before.pages && it.layoutPreferences == before.layoutPreferences
            })
        } finally {
            composeRule.runOnIdle { observer.cancel() }
        }
    }

}
