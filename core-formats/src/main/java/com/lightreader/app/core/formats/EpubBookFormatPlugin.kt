package com.lightreader.app.core.formats

import android.content.Context
import android.net.Uri
import com.lightreader.app.core.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

class EpubBookFormatPlugin : BookFormatPlugin {
    override fun supports(displayName: String, mimeType: String?): Boolean =
        displayName.endsWith(".epub", ignoreCase = true) || mimeType == "application/epub+zip"

    override suspend fun import(
        context: Context,
        source: Uri,
        displayName: String,
        bookDirectory: File,
        options: BookImportOptions,
    ): ImportResult = withContext(Dispatchers.IO) {
        val epubFile = File(bookDirectory, "original.epub")
        context.contentResolver.openInputStream(source)?.use { input ->
            epubFile.outputStream().buffered().use(input::copyTo)
        } ?: error("无法读取 EPUB")

        ZipFile(epubFile).use { zip ->
            zip.entries().asSequence().forEach { entry -> validatedPath(entry.name) }
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
            opf.getElementsByTag("itemref").forEachIndexed { index, itemRef ->
                val path = manifest[itemRef.attr("idref")] ?: return@forEachIndexed
                val html = runCatching { readEntry(zip, path) }.getOrNull() ?: return@forEachIndexed
                val document = Jsoup.parse(html)
                document.select("script,style,nav,svg,noscript").remove()
                val body = document.body().text().trim()
                if (body.isBlank()) return@forEachIndexed
                val heading = document.selectFirst("h1,h2,h3")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() } ?: "第${index + 1}章"
                body.chunked(TxtBookFormatPlugin.MAX_CHAPTER_CHARS).forEachIndexed { part, text ->
                    val chapterTitle = if (part == 0) heading else "$heading（续$part）"
                    val file = File(chapterDirectory, "%05d.txt".format(chapters.size))
                    file.writeText(text, StandardCharsets.UTF_8)
                    chapters += ImportedChapter(chapterTitle, file, text.length)
                }
            }
            if (chapters.isEmpty()) error("EPUB 没有可阅读的正文")
            ImportResult(bookTitle, author, BookFormat.EPUB, chapters)
        }
    }

    private fun readEntry(zip: ZipFile, rawPath: String): String {
        val path = validatedPath(rawPath)
        val entry = zip.getEntry(path) ?: error("EPUB 条目不存在：$path")
        if (entry.isDirectory || entry.size > MAX_ENTRY_BYTES) error("EPUB 条目无效或过大")
        val bytes = zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_ENTRY_BYTES) error("EPUB 条目解压后过大")
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

    private companion object { const val MAX_ENTRY_BYTES = 16L * 1024 * 1024 }
}
