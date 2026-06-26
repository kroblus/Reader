package com.lightreader.app.ui

import com.lightreader.app.core.model.ReaderLine
import com.lightreader.app.core.model.ReaderPage
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageCanvasTest {
    @Test
    fun firstChapterTitlePageUsesBookTitleInHeader() {
        val page = page(
            pageIndex = 0,
            lines = listOf(line("第8章 超凡的名堂", isChapterTitle = true)),
        )

        assertEquals("元始法则", readerHeaderTitle(page, "元始法则"))
    }

    @Test
    fun nonFirstPageKeepsChapterTitleInHeader() {
        val page = page(
            pageIndex = 2,
            lines = listOf(line("正文内容")),
        )

        assertEquals("第8章 超凡的名堂", readerHeaderTitle(page, "元始法则"))
    }

    @Test
    fun firstPageWithoutBodyTitleKeepsChapterTitleInHeader() {
        val page = page(
            pageIndex = 0,
            lines = listOf(line("正文内容")),
        )

        assertEquals("第8章 超凡的名堂", readerHeaderTitle(page, "元始法则"))
    }

    private fun page(pageIndex: Int, lines: List<ReaderLine>) = ReaderPage(
        chapterIndex = 7,
        pageIndex = pageIndex,
        chapterTitle = "第8章 超凡的名堂",
        lines = lines,
        startOffset = 0,
        endOffset = 10,
        progressInChapter = .1f,
    )

    private fun line(text: String, isChapterTitle: Boolean = false) = ReaderLine(
        text = text,
        paragraphIndex = if (isChapterTitle) -1 else 0,
        sourceStart = 0,
        sourceEnd = text.length,
        xOffsetPx = 0f,
        availableWidthPx = 100f,
        baselinePx = 0f,
        widthPx = 0f,
        lineHeightPx = 0f,
        isFirstLineOfParagraph = true,
        isLastLineOfParagraph = true,
        isChapterTitle = isChapterTitle,
    )
}
