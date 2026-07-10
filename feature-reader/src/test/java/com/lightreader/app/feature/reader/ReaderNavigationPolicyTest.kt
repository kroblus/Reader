package com.lightreader.app.feature.reader

import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.ReaderLine
import com.lightreader.app.core.model.ReaderPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderNavigationPolicyTest {
    @Test
    fun pageForOffsetUsesContainingThenNearestPriorPage() {
        val pages = listOf(page(0, 0, 100), page(1, 100, 200))

        assertEquals(1, ReaderNavigationPolicy.pageForOffset(pages, 150))
        assertEquals(1, ReaderNavigationPolicy.pageForOffset(pages, 240))
        assertEquals(0, ReaderNavigationPolicy.pageForOffset(emptyList(), 20))
    }

    @Test
    fun progressTargetClampsToLastReadableCharacter() {
        val chapters = listOf(chapter(0, 10), chapter(1, 20))

        assertEquals(ReaderProgressTarget(0, 0), ReaderNavigationPolicy.progressTarget(chapters, 0f))
        assertEquals(ReaderProgressTarget(1, 19), ReaderNavigationPolicy.progressTarget(chapters, 1f))
        assertNull(ReaderNavigationPolicy.progressTarget(emptyList(), .5f))
    }

    @Test
    fun queueCoalescesOppositeTurnsWithinBound() {
        val queue = ReaderPageTurnQueue(maxSize = 2)

        repeat(4) { queue.enqueue(true) }
        queue.enqueue(false)

        assertEquals(true, queue.poll())
        assertNull(queue.poll())
    }

    private fun page(index: Int, start: Int, end: Int) = ReaderPage(
        chapterIndex = 0,
        pageIndex = index,
        chapterTitle = "第一章",
        lines = listOf(
            ReaderLine(
                text = "正文",
                paragraphIndex = 0,
                sourceStart = 0,
                sourceEnd = 2,
                xOffsetPx = 0f,
                availableWidthPx = 100f,
                baselinePx = 16f,
                widthPx = 20f,
                lineHeightPx = 20f,
                isFirstLineOfParagraph = true,
                isLastLineOfParagraph = true,
            ),
        ),
        startOffset = start,
        endOffset = end,
        progressInChapter = 0f,
    )

    private fun chapter(index: Int, chars: Int) = Chapter(
        id = index.toLong(),
        bookId = "book",
        orderIndex = index,
        title = "第${index + 1}章",
        contentPath = "",
        charCount = chars,
        sourceUrl = null,
    )
}
