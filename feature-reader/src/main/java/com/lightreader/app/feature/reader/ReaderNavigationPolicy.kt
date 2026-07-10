package com.lightreader.app.feature.reader

import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.ReaderPage

/** Pure reader navigation decisions, separated from Compose and persistence. */
object ReaderNavigationPolicy {
    fun pageForOffset(pages: List<ReaderPage>, offset: Int): Int {
        if (pages.isEmpty()) return 0
        val exact = pages.indexOfFirst { offset >= it.startOffset && offset < it.endOffset }
        if (exact >= 0) return exact
        return pages.indexOfLast { it.startOffset <= offset }.coerceAtLeast(0)
    }

    fun progressTarget(chapters: List<Chapter>, progress: Float): ReaderProgressTarget? {
        if (chapters.isEmpty()) return null
        val total = chapters.sumOf { it.charCount.toLong() }.coerceAtLeast(1L)
        var remaining = (progress.coerceIn(0f, 1f) * total).toLong()
        chapters.forEachIndexed { index, chapter ->
            if (remaining < chapter.charCount || index == chapters.lastIndex) {
                return ReaderProgressTarget(
                    chapterIndex = index,
                    charOffset = remaining.coerceIn(0L, (chapter.charCount - 1).coerceAtLeast(0).toLong()).toInt(),
                )
            }
            remaining -= chapter.charCount
        }
        return null
    }
}

data class ReaderProgressTarget(
    val chapterIndex: Int,
    val charOffset: Int,
)

/** Bounded turn queue used while a cross-chapter animation settles. */
class ReaderPageTurnQueue(private val maxSize: Int = 3) {
    var pendingDelta: Int = 0
        private set

    fun enqueue(next: Boolean) {
        val delta = if (next) 1 else -1
        pendingDelta = (pendingDelta + delta).coerceIn(-maxSize, maxSize)
    }

    fun poll(): Boolean? = when {
        pendingDelta > 0 -> {
            pendingDelta -= 1
            true
        }
        pendingDelta < 0 -> {
            pendingDelta += 1
            false
        }
        else -> null
    }

    fun clear() {
        pendingDelta = 0
    }
}
