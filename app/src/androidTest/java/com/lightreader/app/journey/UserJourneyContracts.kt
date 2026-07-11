package com.lightreader.app.journey

import android.content.Context
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class TestPersona {
    FIRST_TIME_READER,
    LOCAL_LIBRARY_READER,
    WEB_NOVEL_READER,
    CUSTOMIZATION_READER,
    CONSTRAINED_DEVICE_READER,
}

enum class FixtureId {
    UTF8_TXT,
    UTF16_TXT,
    GB18030_TXT,
    BIG5_TXT,
    MIXED_LANGUAGE_TXT,
    NO_CHAPTER_TXT,
    EMPTY_TXT,
    DAMAGED_ENCODING_TXT,
    MULTI_PAGE_TXT,
    TWO_MEGABYTE_PARAGRAPH_TXT,
    EPUB_BASIC,
    EPUB_NESTED_TOC,
    EPUB_EMPTY,
    EPUB_ENCRYPTED,
    EPUB_ZIP_SLIP,
    EPUB_CORRUPT,
}

data class UserJourneyResult(
    val journeyId: String,
    val persona: TestPersona,
    val passed: Boolean,
    val steps: List<String>,
    val evidenceDirectory: String,
)

object FixtureCatalog {
    private val chapterText = """
        序章 山门初见

        暮色落在青石长阶上，风从松林深处吹来。

        第一章 入山

        清晨的钟声响了三遍。山门开启时，雾气漫过石桥。

        Chapter 2 The Long Road

        The road continued beyond the mountain without losing any character.
    """.trimIndent()

    fun materialize(context: Context, id: FixtureId): File {
        val directory = File(context.cacheDir, "reader-user-fixtures").apply { mkdirs() }
        val file = File(directory, id.name.lowercase() + if (id.name.startsWith("EPUB")) ".epub" else ".txt")
        when (id) {
            FixtureId.UTF8_TXT -> file.writeBytes(chapterText.toByteArray(Charsets.UTF_8))
            FixtureId.UTF16_TXT -> file.writeBytes(chapterText.toByteArray(Charsets.UTF_16))
            FixtureId.GB18030_TXT -> file.writeBytes(chapterText.toByteArray(Charset.forName("GB18030")))
            FixtureId.BIG5_TXT -> file.writeBytes("第一章 山門\n\n山中閱讀，自此開始。".toByteArray(Charset.forName("Big5")))
            FixtureId.MIXED_LANGUAGE_TXT -> file.writeText(chapterText + "\n\n番外 Mixed 1234567890 !?。")
            FixtureId.NO_CHAPTER_TXT -> file.writeText("这是一篇没有章节标题但仍然应当可以阅读的短篇。\n\n第二段保持完整。")
            FixtureId.EMPTY_TXT -> file.writeBytes(byteArrayOf())
            FixtureId.DAMAGED_ENCODING_TXT -> file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte(), 0xFE.toByte(), 0x00))
            FixtureId.MULTI_PAGE_TXT -> file.writer().buffered().use { output ->
                output.appendLine("第一章 系统按键旅程")
                output.appendLine()
                repeat(500) { index ->
                    output.appendLine("第${index + 1}段用于验证真实音量键翻页、进度保存以及应用后台恢复。")
                    output.appendLine()
                }
            }
            FixtureId.TWO_MEGABYTE_PARAGRAPH_TXT -> file.writer().buffered().use { output ->
                repeat(2 * 1024) { output.write("山".repeat(1024)) }
            }
            FixtureId.EPUB_BASIC -> writeEpub(file, nested = false)
            FixtureId.EPUB_NESTED_TOC -> writeEpub(file, nested = true)
            FixtureId.EPUB_EMPTY -> writeEpub(file, nested = false, emptyChapter = true)
            FixtureId.EPUB_ENCRYPTED -> writeEpub(file, nested = false, encrypted = true)
            FixtureId.EPUB_ZIP_SLIP -> writeEpub(file, nested = false, unsafePath = true)
            FixtureId.EPUB_CORRUPT -> file.writeBytes("not-a-zip".toByteArray())
        }
        return file
    }

    private fun writeEpub(
        file: File,
        nested: Boolean,
        emptyChapter: Boolean = false,
        encrypted: Boolean = false,
        unsafePath: Boolean = false,
    ) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun entry(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            if (encrypted) entry("META-INF/encryption.xml", "<encryption><EncryptedData/></encryption>")
            entry(
                "OEBPS/content.opf",
                """<?xml version="1.0"?><package version="3.0"><metadata><title>QA EPUB</title><creator>LightReader QA</creator></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            entry(
                "OEBPS/nav.xhtml",
                if (nested) "<html><body><nav><ol><li><a href=\"text/chapter1.xhtml\">卷一</a><ol><li><a href=\"text/chapter1.xhtml#part\">第一章</a></li></ol></li></ol></nav></body></html>"
                else "<html><body><nav><ol><li><a href=\"text/chapter1.xhtml\">第一章</a></li></ol></nav></body></html>",
            )
            entry(
                "OEBPS/text/chapter1.xhtml",
                if (emptyChapter) "<html><body></body></html>" else "<html><body><h1>第一章 入山</h1><p id=\"part\">山中修行，自此开始。</p></body></html>",
            )
            if (unsafePath) entry("../escaped.txt", "must never escape")
        }
    }
}
