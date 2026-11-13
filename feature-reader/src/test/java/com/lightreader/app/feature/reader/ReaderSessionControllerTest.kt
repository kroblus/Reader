package com.lightreader.app.feature.reader

import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderViewport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionControllerTest {
    @Test
    fun latestPaginationCancelsOlderPaginationAndPrefetch() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler))
        var paginationCancelled = false
        var prefetchCancelled = false
        controller.launchPagination { try { awaitCancellation() } finally { paginationCancelled = true } }
        controller.launchPrefetch { try { awaitCancellation() } finally { prefetchCancelled = true } }
        runCurrent()

        controller.launchPagination { }
        runCurrent()

        assertTrue(paginationCancelled)
        assertTrue(prefetchCancelled)
    }

    @Test
    fun progressSaveDebounceKeepsOnlyTheLatestWrite() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler))
        val writes = mutableListOf<Int>()
        controller.launchProgressSave(immediate = false) { writes += 1 }
        controller.launchProgressSave(immediate = false) { writes += 2 }
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf(2), writes)
    }

    @Test
    fun leavingReaderCancelsPaginationButPreservesFinalProgress() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler))
        var paginationCancelled = false
        var prefetchCancelled = false
        val writes = mutableListOf<Int>()
        controller.launchPagination { try { awaitCancellation() } finally { paginationCancelled = true } }
        controller.launchPrefetch { try { awaitCancellation() } finally { prefetchCancelled = true } }
        controller.launchProgressSave(immediate = false) { writes += 1 }
        runCurrent()

        controller.launchProgressSave(immediate = true) { writes += 2 }
        controller.cancelReaderWork()
        runCurrent()
        advanceTimeBy(300)
        runCurrent()

        assertTrue(paginationCancelled)
        assertTrue(prefetchCancelled)
        assertEquals(listOf(2), writes)
    }

    @Test
    fun reopeningWaitsUntilOutgoingProgressHasBeenSaved() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler))
        var persistedOffset = 0
        var restoredOffset: Int? = null
        controller.launchProgressSave(immediate = true) {
            delay(100)
            persistedOffset = 240
        }
        controller.cancelReaderWork()
        launch {
            controller.awaitProgressSave()
            restoredOffset = persistedOffset
        }
        advanceTimeBy(99)
        assertEquals(null, restoredOffset)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(240, restoredOffset)
    }

    @Test
    fun slowCancelledWriteCannotOverwriteNewerProgressEvenWhenAnIntermediateSaveIsCancelled() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler))
        val finishOldTransaction = CompletableDeferred<Unit>()
        val writes = mutableListOf<Int>()
        controller.launchProgressSave(immediate = true) {
            withContext(NonCancellable) {
                finishOldTransaction.await()
                writes += 1
            }
        }
        runCurrent()
        controller.launchProgressSave(immediate = true) { writes += 2 }
        runCurrent()
        controller.launchProgressSave(immediate = true) { writes += 3 }
        runCurrent()
        assertTrue(writes.isEmpty())

        finishOldTransaction.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 3), writes)
    }

    @Test
    fun restoreBarrierFollowsAReplacedPendingSave() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler))
        var persistedOffset = 0
        var restoredOffset: Int? = null
        controller.launchProgressSave(immediate = false) { persistedOffset = 1 }
        launch {
            controller.awaitProgressSave()
            restoredOffset = persistedOffset
        }
        runCurrent()
        controller.launchProgressSave(immediate = true) {
            delay(100)
            persistedOffset = 2
        }
        runCurrent()
        assertEquals(null, restoredOffset)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, restoredOffset)
    }

    @Test
    fun pageHitWithEvictedBodyReloadsTargetTextAndKeepsUtf16Anchor() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler), 64, 1024)
        val target = ReaderChapterCacheKey("book", 2)
        val layout = ReaderLayoutCacheKey("book", 2, 1, ReaderViewport(100, 100, 1f, 1f))
        val targetText = "乙😀章正文"
        controller.putChapter(target, ReaderChapterContent(targetText, emptyList()))
        controller.putPages(layout, listOf(ReaderPage(1, 0, "乙", emptyList(), 3, 7, 1f)))
        controller.putChapter(ReaderChapterCacheKey("book", 1), ReaderChapterContent("甲".repeat(32), emptyList()))
        assertEquals(null, controller.chapter(target))
        assertTrue(controller.pages(layout) != null)
        var reads = 0
        val content = controller.resolveContent(target) {
            reads++
            ReaderChapterContent(targetText, listOf(com.lightreader.app.core.model.BookParagraph(targetText)))
        }
        assertEquals(1, reads)
        assertEquals(targetText, content.rawText)
        assertEquals(targetText, content.paragraphs.single().text)
        assertEquals(3, controller.pages(layout)!!.single().startOffset)
        assertEquals(1, controller.pages(layout)!!.single().chapterIndex)
    }

    @Test
    fun lateSuccessErrorAndLoadingCannotCommitAfterNewBookOrChapter() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler))
        val a = controller.beginRequest("book", 1)
        val b = controller.beginRequest("book", 2)
        val c = controller.beginRequest("book", 3)
        val commits = mutableListOf<String>()
        for (request in listOf(a, b)) {
            for (state in listOf("success", "error", "loading")) {
                try { controller.commit(request) { commits += state } }
                catch (_: kotlinx.coroutines.CancellationException) { }
            }
        }
        controller.commit(c) { commits += "C" }
        assertEquals(listOf("C"), commits)
        controller.beginRequest("other", 3)
        assertTrue(!controller.isCurrent(c))
    }

    @Test
    fun memoryPressureClearsBothBoundedCaches() = runTest {
        val controller = ReaderSessionController(this, StandardTestDispatcher(testScheduler), 128, 128)
        controller.putChapter(ReaderChapterCacheKey("book", 1), ReaderChapterContent("chapter", emptyList()))
        controller.putPages(
            ReaderLayoutCacheKey("book", 1, 1, ReaderViewport(100, 100, 1f, 1f)),
            listOf(ReaderPage(0, 0, "chapter", emptyList(), 0, 0, 0f)),
        )
        assertTrue(controller.cacheStats().chapterEntries > 0)
        assertTrue(controller.cacheStats().pageEntries > 0)
        controller.onMemoryPressure()
        assertEquals(0, controller.cacheStats().chapterEntries)
        assertEquals(0, controller.cacheStats().pageEntries)
    }
}
