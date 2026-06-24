package com.lightreader.app.core.web

import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.model.WebChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

interface WebSourceParser {
    suspend fun preview(url: String): WebBookPreview
    suspend fun chapterText(url: String, plan: ExtractionPlan): String
}

class JsoupWebSourceParser(
    private val client: OkHttpClient,
    private val aiProvider: AiExtractionProvider,
    private val allowedSchemes: Set<String> = setOf("https"),
) : WebSourceParser {
    override suspend fun preview(url: String): WebBookPreview = withContext(Dispatchers.IO) {
        requireHttps(url)
        checkRobots(url)
        val html = fetch(url)
        val document = Jsoup.parse(html, url)
        document.select("script,style,svg,iframe,noscript").remove()
        val localLinks = chapterLinks(document, null)
        val plan = if (localLinks.size >= 3) {
            ExtractionPlan(
                titleSelector = selectorFor(document.selectFirst("h1") ?: document.selectFirst("title")),
                chapterLinkSelector = inferLinkSelector(localLinks.first().first),
                contentSelector = "",
                removeSelectors = DEFAULT_REMOVALS,
            )
        } else {
            aiProvider.extract(url, html)
        }
        val links = chapterLinks(document, plan.chapterLinkSelector).ifEmpty { localLinks }
        require(links.isNotEmpty()) { "没有识别到章节链接；暂不支持依赖 JavaScript 的目录页" }
        val chapters = links.take(MAX_CHAPTERS).map { (_, chapter) -> chapter }
        val firstHtml = fetch(chapters.first().url)
        val firstDocument = Jsoup.parse(firstHtml, chapters.first().url)
        val contentSelector = plan.contentSelector.ifBlank { detectContentSelector(firstDocument) }
        require(contentSelector.isNotBlank()) { "没有识别到正文区域" }
        val finalPlan = plan.copy(contentSelector = contentSelector)
        val sample = extractContent(firstDocument, finalPlan).take(600)
        require(sample.isNotBlank()) { "识别到的正文为空" }
        val title = plan.titleSelector.takeIf { it.isNotBlank() }
            ?.let { runCatching { document.selectFirst(it)?.text() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: document.title().substringBeforeLast('-').trim().ifBlank { "网页小说" }
        WebBookPreview(title, url, chapters, sample, finalPlan)
    }

    override suspend fun chapterText(url: String, plan: ExtractionPlan): String = withContext(Dispatchers.IO) {
        requireHttps(url)
        val document = Jsoup.parse(fetch(url), url)
        extractContent(document, plan).also {
            require(it.isNotBlank()) { "正文为空" }
            require(it.length <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS * 4) { "单章正文异常过大" }
        }
    }

    private fun extractContent(document: Document, plan: ExtractionPlan): String {
        val root = runCatching { document.selectFirst(plan.contentSelector) }.getOrNull()
            ?: error("正文选择器不匹配")
        val removals = listOf("script", "style", "nav", "form", "iframe", "noscript") + plan.removeSelectors
        root.select(removals.filter(String::isNotBlank).joinToString(",")).remove()
        root.select("br").after("\n")
        root.select("p,div").append("\n")
        return root.wholeText().lines().map(String::trim).filter(String::isNotBlank).joinToString("\n\n")
    }

    private fun chapterLinks(document: Document, selector: String?): List<Pair<Element, WebChapter>> {
        val baseHost = URI(document.baseUri()).host
        val elements = if (selector.isNullOrBlank()) document.select("a[href]") else runCatching { document.select(selector) }.getOrDefault(emptyList())
        return elements.asSequence().mapNotNull { element ->
            val text = element.text().trim()
            val absolute = element.absUrl("href").substringBefore('#')
            if (text.length !in 2..80 || absolute.isBlank()) return@mapNotNull null
            val uri = runCatching { URI(absolute) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme !in allowedSchemes || uri.host != baseHost) return@mapNotNull null
            val looksLikeChapter = TxtBookFormatPlugin.CHAPTER_PATTERN.matches(text) ||
                Regex("""(?:chapter|chap|/\d{2,})(?:[-_/.]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(uri.path.orEmpty())
            if (!looksLikeChapter && selector.isNullOrBlank()) return@mapNotNull null
            element to WebChapter(text, absolute)
        }.distinctBy { it.second.url }.toList()
    }

    private fun detectContentSelector(document: Document): String {
        val candidates = document.select("article,#content,.content,#chaptercontent,.chapter-content,.read-content,.reading-content,main")
        val best = candidates.maxByOrNull { it.text().length } ?: return ""
        if (best.text().length < 40) return ""
        return selectorFor(best)
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

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("网页请求失败：HTTP ${response.code}")
            return response.body?.string() ?: error("网页响应为空")
        }
    }

    private fun checkRobots(url: String) {
        val uri = URI(url)
        val robotsUrl = "${uri.scheme}://${uri.authority}/robots.txt"
        val rules = runCatching { fetch(robotsUrl) }.getOrNull() ?: return
        var applies = false
        val path = uri.rawPath.ifBlank { "/" }
        rules.lineSequence().map { it.substringBefore('#').trim() }.forEach { line ->
            when {
                line.startsWith("user-agent:", true) -> applies = line.substringAfter(':').trim() == "*"
                applies && line.startsWith("disallow:", true) -> {
                    val blocked = line.substringAfter(':').trim()
                    if (blocked.isNotEmpty() && path.startsWith(blocked)) error("站点 robots.txt 不允许抓取该路径")
                }
            }
        }
    }

    private fun requireHttps(url: String) {
        val uri = runCatching { URI(url) }.getOrElse { error("网址格式不正确") }
        require(uri.scheme in allowedSchemes && !uri.host.isNullOrBlank()) { "首版仅支持 HTTPS 网址" }
    }

    private companion object {
        const val MAX_CHAPTERS = 10_000
        const val USER_AGENT = "LightReader/0.1 (personal offline reader)"
        val DEFAULT_REMOVALS = listOf(".ads", ".advertisement", ".recommend", ".navigation", ".footer")
    }
}
