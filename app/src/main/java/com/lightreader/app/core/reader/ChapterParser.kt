package com.lightreader.app.core.reader

import com.lightreader.app.core.model.BookParagraph

data class ParsedChapter(
    val chapterIndex: Int,
    val title: String,
    val startOffset: Int,
    val paragraphs: List<BookParagraph>,
)

class ChapterParser(private val normalizer: BookTextNormalizer = BookTextNormalizer()) {
    fun chapterTitle(line: String): String? {
        val candidate = line.trim(' ', '\t', '\u3000', '\uFEFF')
        return candidate.takeIf { it.length <= MAX_TITLE_LENGTH && CHAPTER_PATTERN.matches(it) }
    }

    fun parse(rawText: String): List<ParsedChapter> {
        val paragraphs = normalizer.normalize(rawText)
        if (paragraphs.isEmpty()) return listOf(ParsedChapter(0, DEFAULT_TITLE, 0, emptyList()))

        val chapters = ArrayList<ParsedChapter>()
        var title = DEFAULT_TITLE
        var startOffset = paragraphs.first().sourceStart
        var body = ArrayList<BookParagraph>()

        fun flush() {
            if (body.isEmpty() && chapters.isNotEmpty()) return
            chapters += ParsedChapter(chapters.size, title, startOffset, body.toList())
            body = ArrayList()
        }

        paragraphs.forEach { paragraph ->
            val heading = chapterTitle(paragraph.text)
            if (heading != null) {
                if (body.isNotEmpty()) flush()
                title = heading
                startOffset = paragraph.sourceStart
            } else {
                if (body.isEmpty()) startOffset = paragraph.sourceStart
                body += paragraph
            }
        }
        if (body.isNotEmpty() || chapters.isEmpty()) flush()
        return chapters.mapIndexed { index, chapter -> chapter.copy(chapterIndex = index) }
    }

    companion object {
        const val DEFAULT_TITLE = "正文"
        private const val MAX_TITLE_LENGTH = 80
        internal val CHAPTER_PATTERN = Regex(
            "^(?:正文[\\s　]*)?(?:(?:第[\\s　]*[0-9０-９零〇一二三四五六七八九十百千万两]+[\\s　]*[章回节卷部篇])|" +
                "(?:卷[\\s　]*[0-9０-９零〇一二三四五六七八九十百千万两]+)|" +
                "(?:Chapter[\\s　]+[0-9]+)|序章|楔子|引子|前言|后记|尾声|番外)" +
                "(?:[\\s　:：—-].{0,50})?$",
            RegexOption.IGNORE_CASE,
        )
    }
}
