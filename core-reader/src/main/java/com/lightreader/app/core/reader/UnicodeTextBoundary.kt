package com.lightreader.app.core.reader

/**
 * UTF-16 boundary helpers shared by import, layout and search.
 *
 * Persisted reader offsets intentionally stay in UTF-16 code units, while the helpers prevent a
 * visible grapheme from being split between files, pages or search chunks.
 */
object UnicodeTextBoundary {
    fun safeEnd(text: CharSequence, start: Int, proposedEnd: Int): Int {
        require(start in 0..text.length)
        if (start == text.length) return start
        val clamped = proposedEnd.coerceIn(start + 1, text.length)
        val previous = previousBoundary(text, clamped, start)
        return if (previous > start) previous else nextBoundary(text, start)
    }

    fun previousBoundary(text: CharSequence, proposedEnd: Int, floor: Int = 0): Int {
        require(floor in 0..text.length)
        var boundary = proposedEnd.coerceIn(floor, text.length)
        if (boundary in 1 until text.length &&
            Character.isHighSurrogate(text[boundary - 1]) && Character.isLowSurrogate(text[boundary])
        ) {
            boundary--
        }

        while (boundary > floor && boundary < text.length) {
            val previous = Character.codePointBefore(text, boundary)
            val next = Character.codePointAt(text, boundary)
            val splitsCluster = isGraphemeExtension(next) || next == ZERO_WIDTH_JOINER || previous == ZERO_WIDTH_JOINER
            if (!splitsCluster) break
            boundary -= Character.charCount(previous)
        }

        if (boundary > floor && boundary < text.length) {
            val previous = Character.codePointBefore(text, boundary)
            val next = Character.codePointAt(text, boundary)
            if (isRegionalIndicator(previous) && isRegionalIndicator(next)) {
                var count = 0
                var cursor = boundary
                while (cursor > floor) {
                    val codePoint = Character.codePointBefore(text, cursor)
                    if (!isRegionalIndicator(codePoint)) break
                    count++
                    cursor -= Character.charCount(codePoint)
                }
                if (count % 2 == 1) boundary -= Character.charCount(previous)
            }
        }
        return boundary.coerceAtLeast(floor)
    }

    fun nextBoundary(text: CharSequence, start: Int): Int {
        require(start in 0 until text.length)
        var boundary = start + Character.charCount(Character.codePointAt(text, start))
        val first = Character.codePointAt(text, start)
        if (isRegionalIndicator(first) && boundary < text.length) {
            val next = Character.codePointAt(text, boundary)
            if (isRegionalIndicator(next)) boundary += Character.charCount(next)
        }
        while (boundary < text.length) {
            val next = Character.codePointAt(text, boundary)
            val previous = Character.codePointBefore(text, boundary)
            if (!isGraphemeExtension(next) && next != ZERO_WIDTH_JOINER && previous != ZERO_WIDTH_JOINER) break
            boundary += Character.charCount(next)
        }
        return boundary
    }

    fun graphemeCount(text: CharSequence): Int {
        var count = 0
        var cursor = 0
        while (cursor < text.length) {
            cursor = nextBoundary(text, cursor)
            count++
        }
        return count
    }

    private fun isGraphemeExtension(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint in VARIATION_SELECTORS ||
            codePoint in VARIATION_SELECTORS_SUPPLEMENT ||
            codePoint in EMOJI_MODIFIERS ||
            codePoint in EMOJI_TAG_CHARACTERS
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in REGIONAL_INDICATORS

    private const val ZERO_WIDTH_JOINER = 0x200D
    private val VARIATION_SELECTORS = 0xFE00..0xFE0F
    private val VARIATION_SELECTORS_SUPPLEMENT = 0xE0100..0xE01EF
    private val EMOJI_MODIFIERS = 0x1F3FB..0x1F3FF
    private val EMOJI_TAG_CHARACTERS = 0xE0020..0xE007F
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
}
