package com.lightreader.app.feature.reader

import com.lightreader.app.core.model.BookParagraph
import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderViewport
import com.lightreader.app.core.reader.MemoryBoundedCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReaderLayoutCacheKey(
    val bookId: String,
    val chapterId: Long,
    val styleHash: Int,
    val viewport: ReaderViewport,
)

data class ReaderChapterCacheKey(val bookId: String, val chapterId: Long)

data class ReaderChapterContent(
    val rawText: String,
    val paragraphs: List<BookParagraph>,
) {
    val estimatedBytes: Long = rawText.length * 2L + paragraphs.sumOf { paragraph ->
        paragraph.text.length * 2L + paragraph.sourceOffsets.size * Int.SIZE_BYTES.toLong()
    }
}

data class ReaderSessionCacheStats(
    val chapterEntries: Int,
    val chapterBytes: Long,
    val pageEntries: Int,
    val pageBytes: Long,
)

/** Coordinates cancellable reader work and bounded caches outside the Compose ViewModel. */
class ReaderSessionController(
    private val scope: CoroutineScope,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    chapterCacheBytes: Long = DEFAULT_CHAPTER_CACHE_BYTES,
    pageCacheBytes: Long = DEFAULT_PAGE_CACHE_BYTES,
) {
    private var paginationJob: Job? = null
    private var prefetchJob: Job? = null
    private var progressSaveJob: Job? = null
    private val progressSaveMutex = Mutex()
    private val chapterCache = MemoryBoundedCache<ReaderChapterCacheKey, ReaderChapterContent>(
        chapterCacheBytes,
        ReaderChapterContent::estimatedBytes,
    )
    private val pageCache = MemoryBoundedCache<ReaderLayoutCacheKey, List<ReaderPage>>(
        pageCacheBytes,
        { pages -> estimatedPageBytes(pages) },
    )

    fun launchPagination(block: suspend CoroutineScope.() -> Unit): Job {
        paginationJob?.cancel()
        prefetchJob?.cancel()
        return scope.launch(block = block).also { paginationJob = it }
    }

    fun launchPrefetch(block: suspend CoroutineScope.() -> Unit): Job {
        prefetchJob?.cancel()
        return scope.launch(block = block).also { prefetchJob = it }
    }

    fun launchProgressSave(immediate: Boolean, block: suspend CoroutineScope.() -> Unit): Job {
        progressSaveJob?.cancel()
        return scope.launch {
            if (!immediate) delay(PROGRESS_SAVE_DEBOUNCE_MS)
            // A canceled database write can finish its transaction before cancellation is observed.
            // Keep newer writes behind it so an older position never wins the final write.
            progressSaveMutex.withLock { block() }
        }.also { progressSaveJob = it }
    }

    /** Reopening a book must read progress only after the outgoing session has saved it. */
    suspend fun awaitProgressSave() {
        while (true) {
            val pending = progressSaveJob ?: return
            pending.join()
            if (pending === progressSaveJob) return
        }
    }

    suspend fun <T> compute(block: () -> T): T = withContext(computationDispatcher) { block() }

    fun chapter(key: ReaderChapterCacheKey): ReaderChapterContent? = chapterCache[key]
    fun putChapter(key: ReaderChapterCacheKey, content: ReaderChapterContent) = chapterCache.put(key, content)
    fun pages(key: ReaderLayoutCacheKey): List<ReaderPage>? = pageCache[key]
    fun putPages(key: ReaderLayoutCacheKey, pages: List<ReaderPage>) = pageCache.put(key, pages)

    fun onMemoryPressure() {
        prefetchJob?.cancel()
        clearCaches()
    }

    /** Leaving the reader cancels speculative work, but lets its final position save finish. */
    fun cancelReaderWork() {
        paginationJob?.cancel()
        prefetchJob?.cancel()
        paginationJob = null
        prefetchJob = null
    }

    fun cancelAll() {
        cancelReaderWork()
        progressSaveJob?.cancel()
        progressSaveJob = null
    }

    fun clearCaches() {
        chapterCache.clear()
        pageCache.clear()
    }

    fun cacheStats() = ReaderSessionCacheStats(
        chapterEntries = chapterCache.size,
        chapterBytes = chapterCache.byteSize,
        pageEntries = pageCache.size,
        pageBytes = pageCache.byteSize,
    )

    private fun estimatedPageBytes(pages: List<ReaderPage>): Long =
        pages.sumOf { page ->
            page.lines.sumOf { line -> line.text.length * 2L + 64L } + 96L
        }

    private companion object {
        const val DEFAULT_CHAPTER_CACHE_BYTES = 3L * 1024 * 1024
        const val DEFAULT_PAGE_CACHE_BYTES = 4L * 1024 * 1024
        const val PROGRESS_SAVE_DEBOUNCE_MS = 250L
    }
}
