package com.lightreader.app.core.web

import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URI

interface WebSourceParser {
    suspend fun preview(url: String): WebBookPreview
    suspend fun chapterText(url: String, plan: ExtractionPlan, chapterTitle: String = ""): String
}

/** Contract for a specific novel site, with the generic HTML parser as the initial fallback. */
interface NovelSourceAdapter : WebSourceParser {
    val id: String
    fun canHandle(url: String): Boolean
}

class GenericHtmlNovelSourceAdapter(
    private val parser: WebSourceParser,
) : NovelSourceAdapter {
    override val id: String = "generic-html"

    override fun canHandle(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    override suspend fun preview(url: String): WebBookPreview = parser.preview(url).copy(sourceId = id)

    override suspend fun chapterText(url: String, plan: ExtractionPlan, chapterTitle: String): String =
        parser.chapterText(url, plan, chapterTitle)
}

class NovelSourceRegistry(
    adapters: List<NovelSourceAdapter>,
) : WebSourceParser {
    private val adapters = adapters.associateBy(NovelSourceAdapter::id)
    private val orderedAdapters = adapters

    override suspend fun preview(url: String): WebBookPreview = adapterFor(url).preview(url)

    override suspend fun chapterText(url: String, plan: ExtractionPlan, chapterTitle: String): String =
        adapterFor(url).chapterText(url, plan, chapterTitle)

    suspend fun chapterText(sourceId: String, url: String, plan: ExtractionPlan, chapterTitle: String): String =
        (adapters[sourceId] ?: adapterFor(url)).chapterText(url, plan, chapterTitle)

    private fun adapterFor(url: String): NovelSourceAdapter = orderedAdapters.firstOrNull { it.canHandle(url) }
        ?: error("No enabled source adapter supports this URL.")
}

class JsoupWebSourceParser(
    private val fetcher: HtmlFetcher,
    private val catalogParser: RuleBasedCatalogParser,
    private val contentExtractor: ChapterContentExtractor,
    private val validator: NovelUrlValidator,
    private val aiProvider: AiExtractionProvider? = null,
) : WebSourceParser {
    constructor(
        client: OkHttpClient,
        aiProvider: AiExtractionProvider? = null,
        allowedSchemes: Set<String> = setOf("https"),
    ) : this(
        fetcher = HtmlFetcher(client, NovelUrlValidator(allowedSchemes)),
        catalogParser = RuleBasedCatalogParser(allowedSchemes),
        contentExtractor = ChapterContentExtractor(),
        validator = NovelUrlValidator(allowedSchemes),
        aiProvider = aiProvider,
    )

    override suspend fun preview(url: String): WebBookPreview = withContext(Dispatchers.IO) {
        val inputUri = validator.validate(url)
        checkRobots(inputUri)
        val catalogFetch = fetcher.fetch(url, MAX_CATALOG_BYTES)
        val finalUri = validator.validate(catalogFetch.finalUrl)
        if (!sameOrigin(inputUri, finalUri)) checkRobots(finalUri)

        val catalogDocument = Jsoup.parse(catalogFetch.html, catalogFetch.finalUrl)
        val parsed = parseCatalog(catalogDocument, url, catalogFetch)
        val (finalPlan, sample) = previewSample(parsed)
        parsed.copy(sample = sample, extractionPlan = finalPlan)
    }

    private suspend fun previewSample(parsed: WebBookPreview): Pair<ExtractionPlan, String> {
        require(parsed.chapters.isNotEmpty()) { "Catalog did not contain chapters." }
        var lastFailure: Throwable? = null
        parsed.chapters.take(SAMPLE_CHAPTER_ATTEMPTS).forEach { chapter ->
            val attempt = runCatching {
                val fetch = fetcher.fetch(chapter.url, MAX_CHAPTER_BYTES)
                val document = Jsoup.parse(fetch.html, fetch.finalUrl)
                require(!AccessRestrictionDetector.isBrowserVerification(document.outerHtml())) { AccessRestrictionDetector.MESSAGE }
                val contentSelector = parsed.extractionPlan.contentSelector.ifBlank {
                    contentExtractor.detectContentSelector(document)
                }
                require(contentSelector.isNotBlank()) { "Could not identify the chapter body." }
                val plan = parsed.extractionPlan.copy(contentSelector = contentSelector)
                val sample = contentExtractor.extract(document, plan, chapter.title).take(600)
                require(sample.isNotBlank()) { "Chapter preview was empty." }
                plan to sample
            }
            attempt.onSuccess { return it }
            lastFailure = attempt.exceptionOrNull()
            if (lastFailure?.message == AccessRestrictionDetector.MESSAGE) throw lastFailure
        }
        val message = lastFailure?.message.orEmpty()
        if (message.contains("security verification", ignoreCase = true) || message.contains("access restrictions", ignoreCase = true)) {
            lastFailure?.let { throw it }
        }
        error("Chapter pages did not expose readable text. They may require browser/security verification or other access restrictions.")
    }

    private suspend fun parseCatalog(
        document: org.jsoup.nodes.Document,
        sourceUrl: String,
        fetch: FetchResult,
    ): WebBookPreview {
        val local = runCatching { catalogParser.parse(document, sourceUrl = sourceUrl, finalUrl = fetch.finalUrl) }
        if (local.isSuccess) return local.getOrThrow()
        val provider = aiProvider ?: throw local.exceptionOrNull() ?: error("Catalog parsing failed.")
        val plan = provider.extract(sourceUrl, fetch.html)
        val reparsed = catalogParser.parse(
            Jsoup.parse(fetch.html, fetch.finalUrl),
            sourceUrl = sourceUrl,
            finalUrl = fetch.finalUrl,
            extractionPlan = plan,
        )
        return reparsed.copy(
            parseWarnings = (
                reparsed.parseWarnings +
                    "Catalog was parsed with DeepSeek fallback; confirm chapters before downloading."
                ).distinct(),
        )
    }

    override suspend fun chapterText(url: String, plan: ExtractionPlan, chapterTitle: String): String = withContext(Dispatchers.IO) {
        validator.validate(url)
        val fetch = fetcher.fetch(url, MAX_CHAPTER_BYTES)
        val document = Jsoup.parse(fetch.html, fetch.finalUrl)
        contentExtractor.extract(document, plan, chapterTitle).also {
            require(it.length <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS * 4) { "Chapter body is unexpectedly large." }
        }
    }

    private suspend fun checkRobots(uri: URI) {
        val robotsUrl = "${uri.scheme}://${uri.authority}/robots.txt"
        val rules = runCatching { fetcher.fetch(robotsUrl, MAX_ROBOTS_BYTES).html }.getOrNull() ?: return
        var applies = false
        val path = uri.rawPath.ifBlank { "/" }
        rules.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter(String::isNotBlank)
            .forEach { line ->
                when {
                    line.startsWith("user-agent:", ignoreCase = true) -> {
                        val agent = line.substringAfter(':').trim()
                        applies = agent == "*" || agent.contains("LightReader", ignoreCase = true)
                    }
                    applies && line.startsWith("disallow:", ignoreCase = true) -> {
                        val blocked = line.substringAfter(':').trim()
                        if (blocked.isNotEmpty() && path.startsWith(blocked)) {
                            error("The site's robots.txt does not allow fetching this path.")
                        }
                    }
                }
            }
    }

    private fun sameOrigin(left: URI, right: URI): Boolean =
        left.scheme.equals(right.scheme, ignoreCase = true) &&
            left.host.equals(right.host, ignoreCase = true) &&
            effectivePort(left) == effectivePort(right)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }

    private companion object {
        const val SAMPLE_CHAPTER_ATTEMPTS = 5
        const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
        const val MAX_CHAPTER_BYTES = 1 * 1024 * 1024
        const val MAX_ROBOTS_BYTES = 256 * 1024
    }
}
