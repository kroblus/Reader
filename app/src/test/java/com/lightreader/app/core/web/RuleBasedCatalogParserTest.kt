package com.lightreader.app.core.web

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedCatalogParserTest {
    private val parser = RuleBasedCatalogParser()

    @Test
    fun extractsTitleAuthorDescriptionAndRelativeChapterLinks() {
        val document = Jsoup.parse(
            """
            <html>
              <head><title>Catalog Novel - Site</title><meta name="description" content="Short public description." /></head>
              <body>
                <h1>Catalog Novel</h1>
                <div class="book-info">Author: Example Writer status ongoing</div>
                <a href="/login">Login</a>
                <div id="chapters">
                  <a href="/read/001.html">Chapter 1 Start</a>
                  <a href="/read/002.html">Chapter 2 Middle</a>
                  <a href="/read/002.html">Chapter 2 Middle Duplicate</a>
                  <a href="/read/003.html">Chapter 3 End</a>
                  <a href="https://other.example/read/004.html">Chapter 4 External</a>
                </div>
              </body>
            </html>
            """.trimIndent(),
            "https://example.com/book/",
        )

        val preview = parser.parse(document, "https://example.com/book/", "https://example.com/book/")

        assertEquals("Catalog Novel", preview.title)
        assertEquals("Example Writer", preview.author)
        assertEquals("Short public description.", preview.description)
        assertEquals(3, preview.chapters.size)
        assertEquals("https://example.com/read/001.html", preview.chapters.first().url)
        assertEquals("#chapters a[href]", preview.extractionPlan.chapterLinkSelector)
    }

    @Test
    fun rejectsPagesWithoutEnoughChapters() {
        val document = Jsoup.parse(
            "<html><body><h1>Not a catalog</h1><a href='/1'>Chapter 1</a></body></html>",
            "https://example.com/book/",
        )

        val result = runCatching { parser.parse(document, "https://example.com/book/", "https://example.com/book/") }

        assertTrue(result.isFailure)
    }

    @Test
    fun ignoresBookActionLinksThatLookLikeChapterPaths() {
        val document = Jsoup.parse(
            """
            <html><body>
              <h1>Action Link Novel</h1>
              <a href="/read/123/p1.html">立即阅读</a>
              <a href="/download/123.zip">TXT下载</a>
              <div class="catalog">
                <a href="/read/123/p10.html">第一章 起行</a>
                <a href="/read/123/p11.html">第二章 入山</a>
                <a href="/read/123/p12.html">第三章 归来</a>
              </div>
            </body></html>
            """.trimIndent(),
            "https://example.com/read/123/",
        )

        val preview = parser.parse(document, "https://example.com/read/123/", "https://example.com/read/123/")

        assertEquals(listOf("第一章 起行", "第二章 入山", "第三章 归来"), preview.chapters.map { it.title })
    }
}
