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
            viewport.safeTopPx + TOP_CHROME_RESERVE_DP * viewport.density,
        )
        val bottomReserved = max(
            style.verticalPaddingBottomDp * viewport.density,
            viewport.safeBottomPx + FOOTER_SAFE_RESERVE_DP * viewport.density,
        )
        val contentBottom = viewport.heightPx - bottomReserved
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(1f)
        val contentHeight = (contentBottom - contentTop).coerceAtLeast(1f)
        val paint = createPaint(style, viewport)
        val titlePaint = createPaint(style, viewport).apply {
            textSize = style.fontSizeSp * viewport.scaledDensity * TITLE_TEXT_SCALE
            typeface = Typeface.create(typeface, Typeface.BOLD)
        }
        val fontMetrics = paint.fontMetrics
        val fontHeight = fontMetrics.descent - fontMetrics.ascent
        val requestedLineHeight = style.fontSizeSp * viewport.scaledDensity * style.lineHeightMultiplier
        val lineHeight = max(fontHeight, requestedLineHeight).coerceAtMost(contentHeight)
        val baselineOffset = ((lineHeight - fontHeight) / 2f) - fontMetrics.ascent
        val titleFontMetrics = titlePaint.fontMetrics
        val titleFontHeight = titleFontMetrics.descent - titleFontMetrics.ascent
        val titleLineHeight = max(titleFontHeight, titlePaint.textSize * TITLE_LINE_HEIGHT_MULTIPLIER).coerceAtMost(contentHeight)
        val titleBaselineOffset = ((titleLineHeight - titleFontHeight) / 2f) - titleFontMetrics.ascent
        val titleBottomSpacing = TITLE_BOTTOM_SPACING_DP * viewport.density
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

        val normalizedChapterTitle = chapterTitle.trim()
        val firstParagraphIsTitle = paragraphs.firstOrNull()?.text?.trim() == normalizedChapterTitle
        val bodyParagraphStartIndex = if (firstParagraphIsTitle) 1 else 0
        val titleSourceStart = paragraphs.firstOrNull()?.sourceStart ?: 0
        val titleSourceEnd = if (firstParagraphIsTitle) paragraphs.first().sourceEnd else titleSourceStart

        fun appendChapterTitle() {
            if (normalizedChapterTitle.isBlank()) return
            var localStart = 0
            var firstTitleLine = true
            while (localStart < normalizedChapterTitle.length) {
                var localEnd = findLineEnd(titlePaint, normalizedChapterTitle, localStart, contentWidth)
                localEnd = adjustForChinesePunctuation(normalizedChapterTitle, localStart, localEnd)
                if (localEnd <= localStart) localEnd = (localStart + 1).coerceAtMost(normalizedChapterTitle.length)
                if (pageLines.isNotEmpty() && yTop + titleLineHeight > contentBottom + .5f) commitPage()
                val lineText = normalizedChapterTitle.substring(localStart, localEnd)
                pageLines += ReaderLine(
                    text = lineText,
                    paragraphIndex = -1,
                    sourceStart = titleSourceStart,
                    sourceEnd = titleSourceEnd,
                    xOffsetPx = contentLeft,
                    availableWidthPx = contentWidth,
                    baselinePx = yTop + titleBaselineOffset,
                    widthPx = titlePaint.measureText(lineText),
                    lineHeightPx = titleLineHeight,
                    isFirstLineOfParagraph = firstTitleLine,
                    isLastLineOfParagraph = localEnd == normalizedChapterTitle.length,
                    isChapterTitle = true,
                )
                yTop += titleLineHeight
                localStart = localEnd
                firstTitleLine = false
            }
            yTop += titleBottomSpacing
        }

        appendChapterTitle()

        paragraphs.drop(bodyParagraphStartIndex).forEachIndexed { bodyIndex, paragraph ->
            val paragraphIndex = bodyIndex + bodyParagraphStartIndex
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
                val contentLines = lines.filterNot { it.isChapterTitle }
                val startOffset = contentLines.firstOrNull()?.sourceStart ?: lines.first().sourceStart
                val endOffset = contentLines.lastOrNull()?.sourceEnd ?: startOffset
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
        // Supplying a multi-hundred-kilobyte paragraph as shaping context for every line causes
        // quadratic behavior on some Android text stacks. No phone viewport can contain 4096
        // UTF-16 units on one line, so the bounded context keeps the operation linear without
        // changing visible line breaks.
        val measurementEnd = (start + MAX_LINE_BREAK_CONTEXT_CHARS).coerceAtMost(text.length)
        val count = paint.breakText(text, start, measurementEnd, true, width, null).coerceAtLeast(1)
        val measuredEnd = (start + count).coerceAtMost(text.length)
        val graphemeSafeEnd = UnicodeTextBoundary.safeEnd(text, start, measuredEnd)
        return adjustForLatinWordBoundary(text, start, graphemeSafeEnd)
    }

    private fun adjustForLatinWordBoundary(text: String, start: Int, proposedEnd: Int): Int {
        if (proposedEnd >= text.length || proposedEnd - start < MIN_WORD_WRAP_LENGTH) return proposedEnd
        val previous = text.getOrNull(proposedEnd - 1) ?: return proposedEnd
        val next = text.getOrNull(proposedEnd) ?: return proposedEnd
        if (!previous.isLetterOrDigit() || !next.isLetterOrDigit()) return proposedEnd
        val whitespace = text.lastIndexOfAny(charArrayOf(' ', '\t'), proposedEnd - 1)
        return whitespace.takeIf { it > start + (proposedEnd - start) / 2 } ?: proposedEnd
    }

    private fun adjustForChinesePunctuation(text: String, start: Int, proposedEnd: Int): Int {
        var end = proposedEnd
        if (end < text.length && text[end] in FORBIDDEN_LINE_START && end - start > 1) end--
        while (end - start > 1 && text[end - 1] in FORBIDDEN_LINE_END) end--
        if (end < text.length && end > start + 1 && text[end - 1] == text[end] && text[end] in PAIRED_PUNCTUATION) end--
        return end
    }

    private companion object {
        // The reader toolbar overlays the page while visible. Reserve its full
        // height once so page boundaries never change when chrome auto-hides.
        const val TOP_CHROME_RESERVE_DP = 52f
        const val FOOTER_SAFE_RESERVE_DP = 22f
        const val TITLE_TEXT_SCALE = 1.45f
        const val TITLE_LINE_HEIGHT_MULTIPLIER = 1.2f
        const val TITLE_BOTTOM_SPACING_DP = 30f
        const val FORBIDDEN_LINE_START = "，。！？；：、”’）】》〉」』〕］｝…—·～"
        const val FORBIDDEN_LINE_END = "“‘（【《〈「『〔［｛"
        const val PAIRED_PUNCTUATION = "…—"
        const val MIN_WORD_WRAP_LENGTH = 8
        const val MAX_LINE_BREAK_CONTEXT_CHARS = 4_096
    }
}
