package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ChapterContentExtractor(
    private val cleaner: WebTextCleaner = WebTextCleaner(),
) {
    fun detectContentSelector(document: Document): String {
        require(!AccessRestrictionDetector.isBrowserVerification(document.outerHtml())) { AccessRestrictionDetector.MESSAGE }
        document.select("script,style,svg,iframe,noscript,nav,header,footer,form").remove()
        val candidates = document.select(CONTENT_CANDIDATES)
            .filter { it.textLength() >= MIN_CONTENT_CHARS }
        val best = candidates.maxByOrNull { score(it) } ?: return ""
        if (score(best) < MIN_CONTENT_CHARS) return ""
        return selectorFor(best)
    }

    fun extract(document: Document, plan: ExtractionPlan, chapterTitle: String): String {
        require(!AccessRestrictionDetector.isBrowserVerification(document.outerHtml())) { AccessRestrictionDetector.MESSAGE }
        val selector = plan.contentSelector.ifBlank { detectContentSelector(document) }
        require(selector.isNotBlank()) { "Could not identify the chapter body." }
        val root = runCatching { document.selectFirst(selector) }.getOrNull()
            ?: error("Chapter body selector did not match.")
        removeNoise(root, plan)
        root.select("br").after("\n")
        root.select("p,div,section,article").append("\n")
        return cleaner.clean(root.wholeText(), chapterTitle).also {
            require(it.isNotBlank()) { "Chapter body was empty." }
        }
    }

    private fun removeNoise(root: Element, plan: ExtractionPlan) {
        val removals = BASE_REMOVALS + plan.removeSelectors
        val selector = removals.filter(String::isNotBlank).joinToString(",")
        if (selector.isNotBlank()) root.select(selector).remove()
    }

    private fun score(element: Element): Int {
        val textLength = element.textLength()
        val linkTextLength = element.select("a").sumOf { it.textLength() }
        val linkDensity = if (textLength == 0) 1f else linkTextLength.toFloat() / textLength
        if (linkDensity > MAX_LINK_DENSITY) return 0
        return textLength - linkTextLength * 3
    }

    private fun Element.textLength(): Int = text().trim().length

    private fun selectorFor(element: Element): String = when {
        element.id().isNotBlank() -> "#${element.id()}"
        element.classNames().isNotEmpty() -> element.tagName() + "." + element.classNames().first()
        else -> element.tagName()
    }

    private companion object {
        const val MIN_CONTENT_CHARS = 40
        const val MAX_LINK_DENSITY = .35f
        const val CONTENT_CANDIDATES =
            "article,main,#content,#chaptercontent,#chapterContent,#booktxt,#readcontent," +
                ".content,.chapter-content,.read-content,.reading-content,.book-content,.chapter"
        val BASE_REMOVALS = listOf(
            "script", "style", "svg", "iframe", "noscript", "nav", "header", "footer", "form",
            ".ads", ".advertisement", ".recommend", ".navigation", ".chapter-nav", ".pager",
        )
    }
}
