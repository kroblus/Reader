package com.lightreader.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicodeTextBoundaryTest {
    @Test
    fun chunksNeverSplitSurrogatesCombiningMarksOrEmojiSequences() {
        val text = "甲e\u0301👩🏽‍💻🇨🇳乙"
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = UnicodeTextBoundary.safeEnd(text, start, (start + 2).coerceAtMost(text.length))
            assertTrue(end > start)
            chunks += text.substring(start, end)
            start = end
        }

        assertEquals(text, chunks.joinToString(""))
        chunks.forEach { chunk ->
            assertFalse(chunk.first().isLowSurrogate())
            assertFalse(chunk.last().isHighSurrogate())
            assertFalse(chunk.first().let { Character.getType(it) == Character.NON_SPACING_MARK.toInt() })
            assertFalse(chunk.first() == '\u200D' || chunk.last() == '\u200D')
        }
    }

    @Test
    fun graphemeCountTreatsVisibleClustersAsSingleUnits() {
        assertEquals(5, UnicodeTextBoundary.graphemeCount("甲e\u0301👩🏽‍💻🇨🇳乙"))
    }

    @Test
    fun everyProposedBoundaryReconstructsTheOriginalText() {
        val samples = listOf(
            "plain English words",
            "繁體中文，標點。",
            "العربية مرحبا",
            "e\u0301 cafe\u0301",
            "👨‍👩‍👧‍👦 flags 🇺🇸🇨🇳",
        )
        samples.forEach { sample ->
            for (width in 1..sample.length.coerceAtLeast(1)) {
                val pieces = mutableListOf<String>()
                var start = 0
                while (start < sample.length) {
                    val end = UnicodeTextBoundary.safeEnd(sample, start, (start + width).coerceAtMost(sample.length))
                    pieces += sample.substring(start, end)
                    start = end
                }
                assertEquals(sample, pieces.joinToString(""))
            }
        }
    }
}
