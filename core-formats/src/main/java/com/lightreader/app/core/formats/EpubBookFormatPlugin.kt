package com.lightreader.app.core.formats

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.reader.UnicodeTextBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

data class EpubImportLimits(
    val maxEntryBytes: Long = 16L * 1024 * 1024,
    val maxZipEntries: Int = 10_000,
    val maxSpineItems: Int = 5_000,
    val maxTotalExtractedTextBytes: Long = 256L * 1024 * 1024,
)

class EpubBookFormatPlugin(
    private val limits: EpubImportLimits = EpubImportLimits(),
) : BookFormatPlugin {
    override fun supports(displayName: String, mimeType: String?): Boolean =
        displayName.endsWith(".epub", ignoreCase = true) || mimeType == "application/epub+zip"

    override suspend fun import(
        context: Context,
        source: Uri,
        displayName: String,
        bookDirectory: File,
        options: BookImportOptions,
        onProgress: ImportProgressCallback,
    ): ImportResult = withContext(Dispatchers.IO) {
        val epubFile = File(bookDirectory, "original.epub")
        val totalBytes = sourceSize(context, source)
        val importContext = currentCoroutineContext()
        context.contentResolver.openInputStream(source)?.use { input ->
            epubFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    importContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    val fraction = totalBytes?.takeIf { it > 0L }
                        ?.let { .2f + .35f * (copied.toFloat() / it).coerceIn(0f, 1f) }
                        ?: .2f
                    onProgress(ImportProgress(ImportStage.READING, fraction, copied, totalBytes))
                }
            }
        } ?: error("无法读取 EPUB")

        ZipFile(epubFile).use { zip ->
            var entryCount = 0
            zip.entries().asSequence().forEach { entry ->
                importContext.ensureActive()
                entryCount++
                if (entryCount > limits.maxZipEntries) throw contentLimit("EPUB 条目数量过多")
                validatedPath(entry.name)
            }
            if (zip.getEntry("META-INF/encryption.xml") != null) {
                error("暂不支持加密或 DRM EPUB")
            }
            val container = readEntry(zip, "META-INF/container.xml")
            val containerDoc = Jsoup.parse(container, "", Parser.xmlParser())
            val opfPath = containerDoc.getElementsByTag("rootfile").firstOrNull()?.attr("full-path")
                ?.let(::validatedPath) ?: error("EPUB 缺少 OPF")
            val opf = Jsoup.parse(readEntry(zip, opfPath), "", Parser.xmlParser())
            val bookTitle = opf.getElementsByTag("dc:title").firstOrNull()?.text()
                ?.ifBlank { null } ?: safeBaseName(displayName)
            val author = opf.getElementsByTag("dc:creator").firstOrNull()?.text()?.ifBlank { null }
            val base = opfPath.substringBeforeLast('/', "")
            val manifest = opf.getElementsByTag("item").associate { item ->
                item.attr("id") to resolvePath(base, item.attr("href"))
            }
            val chapterDirectory = File(bookDirectory, "chapters").apply { mkdirs() }
            val chapters = mutableListOf<ImportedChapter>()
            val spineItems = opf.getElementsByTag("itemref")
            if (spineItems.size > limits.maxSpineItems) throw contentLimit("EPUB 正文章节数量过多")
            var extractedUtf8Bytes = 0L
            spineItems.forEachIndexed { index, itemRef ->
                importContext.ensureActive()
                val path = manifest[itemRef.attr("idref")] ?: return@forEachIndexed
                val html = runCatching { readEntry(zip, path) }.getOrNull() ?: return@forEachIndexed
                val document = Jsoup.parse(html)
                document.select("script,style,nav,svg,noscript").remove()
                val heading = document.selectFirst("h1,h2,h3")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() } ?: "第${index + 1}章"
                val body = extractReadableBody(document, heading)
                if (body.isBlank()) return@forEachIndexed
                extractedUtf8Bytes += body.toByteArray(StandardCharsets.UTF_8).size
                if (extractedUtf8Bytes > limits.maxTotalExtractedTextBytes) {
                    throw contentLimit("EPUB 解压后的正文过大")
                }
                unicodeSafeChunks(body, TxtBookFormatPlugin.MAX_CHAPTER_CHARS).forEachIndexed { part, text ->
                    val chapterTitle = if (part == 0) heading else "$heading（续$part）"
                    val file = File(chapterDirectory, "%05d.txt".format(chapters.size))
                    file.writeText(text, StandardCharsets.UTF_8)
                    chapters += ImportedChapter(chapterTitle, file, text.length)
                }
                onProgress(
                    ImportProgress(
                        ImportStage.PARSING,
                        .55f + .35f * ((index + 1f) / spineItems.size.coerceAtLeast(1)),
                        (index + 1).toLong(),
                        spineItems.size.toLong(),
                    ),
                )
            }
            if (chapters.isEmpty()) error("EPUB 没有可阅读的正文")
            ImportResult(bookTitle, author, BookFormat.EPUB, chapters)
        }
    }

    private fun readEntry(zip: ZipFile, rawPath: String): String {
        val path = validatedPath(rawPath)
        val entry = zip.getEntry(path) ?: error("EPUB 条目不存在：$path")
        if (entry.isDirectory) error("EPUB 条目无效")
        if (entry.size > limits.maxEntryBytes) throw contentLimit("EPUB 单个条目过大")
        val bytes = zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limits.maxEntryBytes) throw contentLimit("EPUB 条目解压后过大")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun resolvePath(base: String, href: String): String {
        val decoded = URLDecoder.decode(href.substringBefore('#'), StandardCharsets.UTF_8.name())
        val combined = if (base.isBlank()) decoded else "$base/$decoded"
        val parts = mutableListOf<String>()
        combined.replace('\\', '/').split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) else error("EPUB 路径越界")
                else -> parts += part
            }
        }
        return validatedPath(parts.joinToString("/"))
    }

    private fun validatedPath(path: String): String {
        val normalized = path.replace('\\', '/')
        require(!normalized.startsWith('/') && normalized.split('/').none { it == ".." }) {
            "EPUB 包含不安全路径"
        }
        return normalized
    }

    private fun extractReadableBody(document: org.jsoup.nodes.Document, heading: String): String {
        val body = document.body().clone()
        body.select("br").forEach { element -> element.replaceWith(TextNode("\n")) }
        body.select(BLOCK_ELEMENTS).forEach { element -> element.appendChild(TextNode("\n")) }
        val lines = body.wholeText()
            .lineSequence()
            .map { line -> line.replace(HORIZONTAL_WHITESPACE, " ").trim() }
            .filter(String::isNotBlank)
            .toMutableList()
        if (lines.firstOrNull()?.normalizeHeading() == heading.normalizeHeading()) lines.removeAt(0)
        return lines.joinToString("\n")
    }

    private fun unicodeSafeChunks(text: String, maxChars: Int): List<String> {
        val chunks = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            val proposedEnd = (start + maxChars).coerceAtMost(text.length)
            var end = UnicodeTextBoundary.safeEnd(text, start, proposedEnd)
            if (end < text.length) {
                val paragraphEnd = text.lastIndexOf('\n', end - 1)
                if (paragraphEnd >= start + maxChars / 2) end = paragraphEnd + 1
            }
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }

    private fun String.normalizeHeading(): String = trim().replace(HORIZONTAL_WHITESPACE, " ")

    private fun contentLimit(message: String) =
        BookImportException(BookImportFailure.CONTENT_LIMIT, message)

    private fun sourceSize(context: Context, source: Uri): Long? {
        context.contentResolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0).takeIf { it >= 0 }
        }
        return null
    }

    private companion object {
        const val BLOCK_ELEMENTS = "p,div,li,blockquote,h1,h2,h3,h4,h5,h6,pre,section,article"
        val HORIZONTAL_WHITESPACE = Regex("[\\t\\u000B\\f ]+")
    }
}
