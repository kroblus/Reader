package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

class JsoupWebSourceParserTest {
    private lateinit var server: MockWebServer
    private lateinit var parser: JsoupWebSourceParser

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        parser = JsoupWebSourceParser(OkHttpClient(), allowedSchemes = setOf("http"))
    }

    @Test
    fun usesAiSelectorFallbackOnlyWhenLocalCatalogRulesFail() = runBlocking {
        parser = JsoupWebSourceParser(OkHttpClient(), PlanAiProvider, setOf("http"))
        server.enqueue(MockResponse().setBody("User-agent: *\nAllow: /"))
        server.enqueue(MockResponse().setBody("""
            <html>
              <head><title>Fallback Novel</title></head>
              <body>
                <h1>Fallback Novel</h1>
                <div id="toc">
                  <a href="/a">Opening</a>
                  <a href="/b">Crossing</a>
                  <a href="/c">Return</a>
                </div>
              </body>
            </html>
        """.trimIndent()))
        server.enqueue(MockResponse().setBody(chapterHtml("Opening", "The fallback selector found this chapter body after local title heuristics could not recognize the catalog links.")))

        val preview = parser.preview(server.url("/fallback").toString())

        assertEquals(3, preview.chapters.size)
        assertEquals("#toc a", preview.extractionPlan.chapterLinkSelector)
        assertTrue(preview.parseWarnings.any { it.contains("DeepSeek fallback") })
        assertTrue(preview.sample.contains("fallback selector"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun discoversDirectoryAndExtractsChapterContentWithoutAi() = runBlocking {
        server.enqueue(MockResponse().setBody("User-agent: *\nAllow: /"))
        server.enqueue(MockResponse().setBody("""
            <html>
              <head><title>Sample Novel - Example</title><meta name="description" content="A public sample novel." /></head>
              <body>
                <h1>Sample Novel</h1>
                <div class="info">Author: Ada Writer</div>
                <div id="list">
                  <a href="/1">Chapter 1 Arrival</a>
                  <a href="/2">Chapter 2 Training</a>
                  <a href="/3">Chapter 3 Road</a>
                </div>
              </body>
            </html>
        """.trimIndent()))
        server.enqueue(MockResponse().setBody(chapterHtml("Chapter 1 Arrival", "The young traveler reached the old gate after a long mountain road. The bell echoed over the valley while the first lesson began under quiet lamps.")))

        val preview = parser.preview(server.url("/book").toString())

        assertEquals("Sample Novel", preview.title)
        assertEquals("Ada Writer", preview.author)
        assertEquals("A public sample novel.", preview.description)
        assertEquals(3, preview.chapters.size)
        assertEquals("#content", preview.extractionPlan.contentSelector)
        assertTrue(preview.sample.contains("young traveler"))

        server.enqueue(MockResponse().setBody(chapterHtml("Chapter 1 Arrival", "Second read body text is still extracted from the same content selector and remains long enough to be accepted as a chapter body.")))
        assertTrue(parser.chapterText(preview.chapters.first().url, preview.extractionPlan, preview.chapters.first().title).contains("Second read body text"))
    }

    @Test
    fun decodesGb18030CatalogBeforeParsing() = runBlocking {
        server.enqueue(MockResponse().setBody("User-agent: *\nAllow: /"))
        val body = """
            <html>
              <head><title>编码小说</title></head>
              <body>
                <h1>编码小说</h1>
                <div id="list">
                  <a href="/1">第一章 入山</a>
                  <a href="/2">第二章 筑基</a>
                  <a href="/3">第三章 金丹</a>
                </div>
              </body>
            </html>
        """.trimIndent().toByteArray(Charset.forName("GB18030"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=GB18030").setBody(okio.Buffer().write(body)))
        server.enqueue(MockResponse().setBody(chapterHtml("第一章 入山", "山中无甲子，寒尽不知年。少年背着竹篓走过青石山路，来到云雾深处的古老宗门。钟声回荡群峰，故事由此展开。")))

        val preview = parser.preview(server.url("/book-gbk").toString())

        assertEquals("编码小说", preview.title)
        assertEquals(3, preview.chapters.size)
        assertTrue(preview.sample.contains("少年背着竹篓"))
    }

    @Test(expected = IllegalStateException::class)
    fun respectsRobotsDisallow() {
        runBlocking {
            server.enqueue(MockResponse().setBody("User-agent: *\nDisallow: /private"))
            parser.preview(server.url("/private/book").toString())
        }
    }

    @Test
    fun reportsBrowserVerificationChallengeAsAccessRestriction() = runBlocking {
        server.enqueue(MockResponse().setBody("User-agent: *\nAllow: /"))
        server.enqueue(MockResponse().setBody("""
            <html><body>
              <h1>Guarded Novel</h1>
              <div id="list">
                <a href="/1">Chapter 1 Arrival</a>
                <a href="/2">Chapter 2 Training</a>
                <a href="/3">Chapter 3 Road</a>
              </div>
            </body></html>
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("""
            <!DOCTYPE html>
            <html>
              <head><title>正在验证浏览器</title></head>
              <body>
                <p>請稍等，正在進行安全驗證...</p>
                <script>
                  window.location.href = location.pathname + "?challenge=token";
                </script>
              </body>
            </html>
        """.trimIndent()))

        val failure = runCatching { parser.preview(server.url("/book").toString()) }.exceptionOrNull()

        assertTrue(failure?.message?.contains("security verification") == true)
    }

    private fun chapterHtml(title: String, body: String): String = """
        <html><body>
          <div id="content">
            <h1>$title</h1>
            <p>$body</p>
            <p>Next chapter</p>
          </div>
        </body></html>
    """.trimIndent()

    private object PlanAiProvider : AiExtractionProvider {
        override suspend fun extract(sourceUrl: String, htmlSample: String): ExtractionPlan = ExtractionPlan(
            titleSelector = "h1",
            chapterLinkSelector = "#toc a",
            contentSelector = "#content",
        )

        override suspend fun testConnection(): Boolean = true
    }
}
