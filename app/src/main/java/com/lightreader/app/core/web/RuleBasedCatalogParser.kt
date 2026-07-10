package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.model.WebChapter
import com.lightreader.app.core.reader.ChapterParser
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

class RuleBasedCatalogParser(
    private val allowedSchemes: Set<String> = setOf("https"),
) {
    fun parse(
        document: Document,
        sourceUrl: String,
        finalUrl: String,
        extractionPlan: ExtractionPlan? = null,
    ): WebBookPreview {
        document.select("script,style,svg,iframe,noscript").remove()
        val links = chapterLinks(document, extractionPlan?.chapterLinkSelector)
        require(links.size >= MIN_CHAPTERS) {
            "Could not identify a usable catalog. At least $MIN_CHAPTERS chapter links are required."
        }
        val plan = ExtractionPlan(
            titleSelector = extractionPlan?.titleSelector?.takeIf { it.isNotBlank() }
                ?: selectorFor(document.selectFirst("h1") ?: document.selectFirst("title")),
            chapterLinkSelector = extractionPlan?.chapterLinkSelector?.takeIf { it.isNotBlank() }
                ?: inferLinkSelector(links.first().first),
            contentSelector = extractionPlan?.contentSelector.orEmpty(),
            removeSelectors = (DEFAULT_REMOVALS + extractionPlan?.removeSelectors.orEmpty()).distinct(),
        )
        val chapters = links.take(MAX_CHAPTERS).map { it.second }
        val warnings = buildList {
            if (links.size > MAX_CHAPTERS) add("Only the first $MAX_CHAPTERS chapters will be downloaded.")
            if (plan.chapterLinkSelector == "a[href]") add("Chapter links use a broad selector; confirm the preview before downloading.")
        }
        return WebBookPreview(
            title = detectTitle(document, plan.titleSelector),
            author = detectAuthor(document),
            description = detectDescription(document),
            sourceUrl = sourceUrl,
            finalUrl = finalUrl,
            chapters = chapters,
            sample = "",
            extractionPlan = plan,
            parseWarnings = warnings,
        )
    }

    private fun chapterLinks(document: Document, selector: String? = null): List<Pair<Element, WebChapter>> {
        val baseUri = URI(document.baseUri())
        val baseHost = baseUri.host ?: return emptyList()
        val elements = if (selector.isNullOrBlank()) {
            document.select("a[href]")
        } else {
            runCatching { document.select(selector) }.getOrDefault(emptyList())
        }
        return elements.asSequence().mapNotNull { element ->
            val text = element.text().trim()
            val absolute = element.absUrl("href").substringBefore('#')
            if (text.length !in 2..100 || absolute.isBlank()) return@mapNotNull null
            if (isNoiseLinkText(text)) return@mapNotNull null
            val uri = runCatching { URI(absolute) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme?.lowercase() !in allowedSchemes || !uri.host.equals(baseHost, ignoreCase = true)) {
                return@mapNotNull null
            }
            if (selector.isNullOrBlank() && !looksLikeChapter(text, uri)) return@mapNotNull null
            element to WebChapter(text, absolute)
        }.distinctBy { it.second.url }.toList()
    }

    private fun looksLikeChapter(text: String, uri: URI): Boolean =
        ChapterParser.CHAPTER_PATTERN.matches(text) ||
            ENGLISH_CHAPTER_PATTERN.containsMatchIn(text) ||
            CHAPTER_PATH_PATTERN.containsMatchIn(uri.path.orEmpty())

    private fun detectTitle(document: Document, titleSelector: String): String {
        val candidates = listOfNotNull(
            titleSelector.takeIf { it.isNotBlank() }?.let { selector ->
                runCatching { document.selectFirst(selector)?.text() }.getOrNull()
            },
            document.selectFirst("h1")?.text(),
            document.selectFirst("meta[property='og:title']")?.attr("content"),
            document.selectFirst("meta[name='twitter:title']")?.attr("content"),
            document.title(),
        )
        return candidates.asSequence()
            .map { cleanTitle(it) }
            .firstOrNull { it.isNotBlank() }
            ?: "Web book"
    }

    private fun detectAuthor(document: Document): String? {
        val meta = document.selectFirst("meta[name='author']")?.attr("content")?.trim()
        if (!meta.isNullOrBlank()) return meta.take(MAX_AUTHOR_LENGTH)
        val textCandidates = document.select("[class*=author],[id*=author],.info,.book-info,.bookmeta")
            .map { it.text() }
            .ifEmpty { listOf(document.body().text().take(4000)) }
        return textCandidates.asSequence()
            .mapNotNull { AUTHOR_PATTERN.find(it)?.groupValues?.getOrNull(1) }
            .map { cleanAuthor(it) }
            .firstOrNull { it.isNotBlank() }
    }

    private fun detectDescription(document: Document): String? {
        val meta = listOfNotNull(
            document.selectFirst("meta[name='description']")?.attr("content"),
            document.selectFirst("meta[property='og:description']")?.attr("content"),
        ).firstOrNull { it.isNotBlank() }
        if (!meta.isNullOrBlank()) return normalizeWhitespace(meta).take(MAX_DESCRIPTION_LENGTH)
        return document.select("#intro,#description,.intro,.description,.desc,[class*=intro],[class*=desc]")
            .asSequence()
            .map { normalizeWhitespace(it.text()) }
            .filter { it.length >= 20 }
            .maxByOrNull { it.length }
            ?.take(MAX_DESCRIPTION_LENGTH)
    }

    private fun cleanTitle(raw: String): String {
        val trimmed = normalizeWhitespace(raw)
        return trimmed.split(TITLE_SEPARATOR).firstOrNull()?.trim().orEmpty().take(MAX_TITLE_LENGTH)
    }

    private fun cleanAuthor(raw: String): String =
        normalizeWhitespace(raw)
            .replace(AUTHOR_PREFIX, "")
            .replace(AUTHOR_TRAILING_META, "")
            .substringBefore("|")
            .substringBefore("/")
            .substringBefore("\uFF0F")
            .substringBefore("\u72B6\u6001")
            .take(MAX_AUTHOR_LENGTH)
            .trim()

    private fun normalizeWhitespace(raw: String): String = raw.replace(Regex("""\s+"""), " ").trim()

    private fun isNoiseLinkText(text: String): Boolean {
        val lower = text.lowercase()
        return NOISE_WORDS.any { lower.contains(it) }
    }

    private fun selectorFor(element: Element?): String = when {
        element == null -> ""
        element.id().isNotBlank() -> "#${element.id()}"
        element.classNames().isNotEmpty() -> element.tagName() + "." + element.classNames().first()
        else -> element.tagName()
    }

    private fun inferLinkSelector(element: Element): String {
        val parent = element.parent()
        return when {
            parent?.id()?.isNotBlank() == true -> "#${parent.id()} a[href]"
            parent != null && parent.classNames().isNotEmpty() -> ".${parent.classNames().first()} a[href]"
            else -> "a[href]"
        }
    }

    private companion object {
        const val MIN_CHAPTERS = 3
        const val MAX_CHAPTERS = 10_000
        const val MAX_TITLE_LENGTH = 80
        const val MAX_AUTHOR_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 1000
        val DEFAULT_REMOVALS = listOf(".ads", ".advertisement", ".recommend", ".navigation", ".footer", ".chapter-nav")
        val TITLE_SEPARATOR = Regex("""\s*[-_|]\s*""")
        val AUTHOR_PATTERN = Regex("""(?:\u4F5C\s*\u8005|author|writer)\s*[:\uFF1A]?\s*([^\n\r<]{1,80})""", RegexOption.IGNORE_CASE)
        val AUTHOR_PREFIX = Regex("""^(?:\u4F5C\s*\u8005|author|writer)\s*[:\uFF1A]?\s*""", RegexOption.IGNORE_CASE)
        val AUTHOR_TRAILING_META = Regex("""\s+(?:status|state|\u72B6\u6001|\u66F4\u65B0).*$""", RegexOption.IGNORE_CASE)
        val ENGLISH_CHAPTER_PATTERN = Regex("""\b(?:chapter|chap\.?)\s*\d+\b""", RegexOption.IGNORE_CASE)
        val CHAPTER_PATH_PATTERN = Regex("""/(?:chapter|chap|read)?[-_/]?\d{2,}(?:[-_/.]|$)""", RegexOption.IGNORE_CASE)
        val NOISE_WORDS = listOf(
            "login", "register", "rank", "comment", "bookcase", "bookshelf", "home", "next", "prev",
            "\u9996\u9875", "\u767B\u5F55", "\u6CE8\u518C", "\u6392\u884C", "\u8BC4\u8BBA",
            "\u4E66\u67B6", "\u4E0B\u4E00\u9875", "\u4E0A\u4E00\u7AE0", "\u4E0B\u4E00\u7AE0",
            "\u76EE\u5F55", "\u8FD4\u56DE", "\u7ACB\u5373\u9605\u8BFB", "\u7EE7\u7EED\u9605\u8BFB",
            "txt\u4E0B\u8F7D", "\u4E0B\u8F7D", "\u52A0\u5165\u4E66\u67B6",
        )
    }
}
