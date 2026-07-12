package com.lightreader.app.feature.reader

import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderViewport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
