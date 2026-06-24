package com.lightreader.app.core.reader

import com.lightreader.app.core.model.BookParagraph

/** Converts raw chapter text into display paragraphs without using spaces for indentation. */
class BookTextNormalizer {
    fun normalize(rawText: String): List<BookParagraph> {
        if (rawText.isEmpty()) return emptyList()
        val paragraphs = ArrayList<BookParagraph>()
        var lineStart = 0
        var cursor = 0
        while (cursor <= rawText.length) {
            val atEnd = cursor == rawText.length
            val character = rawText.getOrNull(cursor)
            if (atEnd || character == '\n' || character == '\r') {
                normalizedLine(rawText, lineStart, cursor)?.let(paragraphs::add)
                if (!atEnd && character == '\r' && rawText.getOrNull(cursor + 1) == '\n') cursor++
                lineStart = cursor + 1
            }
            cursor++
        }
        return paragraphs
    }

    fun normalizeLine(rawLine: String, sourceStart: Int = 0): BookParagraph? =
        normalizedLine(rawLine, 0, rawLine.length, sourceStart)

    private fun normalizedLine(
        source: String,
        start: Int,
        endExclusive: Int,
        offsetBase: Int = 0,
    ): BookParagraph? {
        var startIndex = start
        var endIndex = endExclusive
        while (startIndex < endIndex && source[startIndex].isDiscardableEdgeSpace()) startIndex++
        while (endIndex > startIndex && source[endIndex - 1].isDiscardableEdgeSpace()) endIndex--
        if (startIndex >= endIndex) return null

        val text = StringBuilder(endIndex - startIndex)
        val offsets = ArrayList<Int>(endIndex - startIndex)
        for (index in startIndex until endIndex) {
            val value = source[index]
            if (value == '\t' || value == '\uFEFF') continue
            text.append(value)
            offsets += offsetBase + index
        }
        if (text.isEmpty()) return null
        return BookParagraph(text.toString(), sourceOffsets = offsets.toIntArray())
    }

    private fun Char.isDiscardableEdgeSpace(): Boolean =
        this == ' ' || this == '\u3000' || this == '\t' || this == '\uFEFF'
}
