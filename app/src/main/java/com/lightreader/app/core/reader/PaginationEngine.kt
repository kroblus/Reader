package com.lightreader.app.core.reader

import android.graphics.Typeface
import android.text.TextPaint
import com.lightreader.app.core.model.BookParagraph
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.ReaderLayoutResult
import com.lightreader.app.core.model.ReaderLine
import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderStyle
import com.lightreader.app.core.model.ReaderViewport
import kotlin.math.max
import kotlin.math.min

interface ReaderLayoutEngine {
    fun paginate(
        chapterIndex: Int,
        chapterTitle: String,
        paragraphs: List<BookParagraph>,
        viewport: ReaderViewport,
        style: ReaderStyle,
    ): ReaderLayoutResult
}

class PaintReaderLayoutEngine : ReaderLayoutEngine {
    override fun paginate(
        chapterIndex: Int,
        chapterTitle: String,
        paragraphs: List<BookParagraph>,
        viewport: ReaderViewport,
        style: ReaderStyle,
    ): ReaderLayoutResult {
        require(viewport.widthPx > 0 && viewport.heightPx > 0)
        val minimumHorizontalPadding = style.horizontalPaddingDp * viewport.density
        val contentWidthLimit = style.maxContentWidthDp * viewport.density
        val contentWidthAvailable = (viewport.widthPx - minimumHorizontalPadding * 2f).coerceAtLeast(1f)
        val centeredContentWidth = min(contentWidthAvailable, contentWidthLimit)
        val contentLeft = (viewport.widthPx - centeredContentWidth) / 2f
        val contentRight = contentLeft + centeredContentWidth
        val contentTop = max(
            style.verticalPaddingTopDp * viewport.density,
            viewport.safeTopPx + 64f * viewport.density,
        )
        val bottomReserved = max(
            style.verticalPaddingBottomDp * viewport.density,
            viewport.safeBottomPx + 64f * viewport.density,
        )
        val contentBottom = viewport.heightPx - bottomReserved
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(1f)
        val contentHeight = (contentBottom - contentTop).coerceAtLeast(1f)
        val paint = createPaint(style, viewport)
        val fontMetrics = paint.fontMetrics
        val fontHeight = fontMetrics.descent - fontMetrics.ascent
        val requestedLineHeight = style.fontSizeSp * viewport.scaledDensity * style.lineHeightMultiplier
        val lineHeight = max(fontHeight, requestedLineHeight).coerceAtMost(contentHeight)
        val baselineOffset = ((lineHeight - fontHeight) / 2f) - fontMetrics.ascent
        val paragraphSpacing = style.paragraphSpacingDp * viewport.density
        val indent = style.firstLineIndentEm * style.fontSizeSp * viewport.scaledDensity

        val pages = ArrayList<MutableList<ReaderLine>>()
        var pageLines = ArrayList<ReaderLine>()
        var yTop = contentTop

        fun commitPage() {
            if (pageLines.isNotEmpty()) pages += pageLines
            pageLines = ArrayList()
            yTop = contentTop
        }

        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            if (paragraph.text.isEmpty()) return@forEachIndexed
            var localStart = 0
            var firstLine = true
            while (localStart < paragraph.text.length) {
                val lineIndent = if (firstLine) indent else 0f
                val availableWidth = (contentWidth - lineIndent).coerceAtLeast(1f)
                var localEnd = findLineEnd(paint, paragraph.text, localStart, availableWidth)
                localEnd = adjustForChinesePunctuation(paragraph.text, localStart, localEnd)
                if (localEnd <= localStart) localEnd = (localStart + 1).coerceAtMost(paragraph.text.length)

                if (pageLines.isNotEmpty() && yTop + lineHeight > contentBottom + .5f) commitPage()
                val lineText = paragraph.text.substring(localStart, localEnd)
                pageLines += ReaderLine(
                    text = lineText,
                    paragraphIndex = paragraphIndex,
                    sourceStart = paragraph.sourceOffset(localStart),
                    sourceEnd = paragraph.sourceOffset(localEnd),
                    xOffsetPx = contentLeft + lineIndent,
                    availableWidthPx = availableWidth,
                    baselinePx = yTop + baselineOffset,
                    widthPx = paint.measureText(lineText),
                    lineHeightPx = lineHeight,
                    isFirstLineOfParagraph = firstLine,
                    isLastLineOfParagraph = localEnd == paragraph.text.length,
                )
                yTop += lineHeight
                localStart = localEnd
                firstLine = false
            }
            yTop += paragraphSpacing
        }
        commitPage()

        val totalSourceLength = paragraphs.maxOfOrNull(BookParagraph::sourceEnd)?.coerceAtLeast(1) ?: 1
        val immutablePages = if (pages.isEmpty()) {
            listOf(ReaderPage(chapterIndex, 0, chapterTitle, emptyList(), 0, 0, 0f))
        } else {
            pages.mapIndexed { pageIndex, lines ->
                val startOffset = lines.first().sourceStart
                val endOffset = lines.last().sourceEnd
                ReaderPage(
                    chapterIndex = chapterIndex,
                    pageIndex = pageIndex,
                    chapterTitle = chapterTitle,
                    lines = lines,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    progressInChapter = (endOffset.toFloat() / totalSourceLength).coerceIn(0f, 1f),
                )
            }
        }
        return ReaderLayoutResult(
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            pages = immutablePages,
            viewport = viewport,
            layoutFingerprint = style.layoutFingerprint(),
        )
    }

    private fun createPaint(style: ReaderStyle, viewport: ReaderViewport) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = style.fontSizeSp * viewport.scaledDensity
        typeface = Typeface.create(
            when (style.fontFamily) {
                FontFamilyOption.SYSTEM -> Typeface.DEFAULT
                FontFamilyOption.SANS -> Typeface.SANS_SERIF
                FontFamilyOption.SERIF -> Typeface.SERIF
                FontFamilyOption.MONOSPACE -> Typeface.MONOSPACE
            },
            if (style.fontWeight >= 500) Typeface.BOLD else Typeface.NORMAL,
        )
    }

    private fun findLineEnd(paint: TextPaint, text: String, start: Int, width: Float): Int {
        var low = start + 1
        var high = text.length
        var best = start
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (paint.measureText(text, start, middle) <= width) {
                best = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return if (best == start) (start + 1).coerceAtMost(text.length) else best
    }

    private fun adjustForChinesePunctuation(text: String, start: Int, proposedEnd: Int): Int {
        var end = proposedEnd
        if (end < text.length && text[end] in FORBIDDEN_LINE_START && end - start > 1) end--
        while (end - start > 1 && text[end - 1] in FORBIDDEN_LINE_END) end--
        if (end < text.length && end > start + 1 && text[end - 1] == text[end] && text[end] in PAIRED_PUNCTUATION) end--
        return end
    }

    private companion object {
        const val FORBIDDEN_LINE_START = "，。！？；：、”’）】》〉」』〕］｝…—·～"
        const val FORBIDDEN_LINE_END = "“‘（【《〈「『〔［｛"
        const val PAIRED_PUNCTUATION = "…—"
    }
}
