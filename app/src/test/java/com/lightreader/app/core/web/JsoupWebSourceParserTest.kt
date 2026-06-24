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

class JsoupWebSourceParserTest {
    private lateinit var server: MockWebServer
    private lateinit var parser: JsoupWebSourceParser

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        parser = JsoupWebSourceParser(OkHttpClient(), FailingAiProvider, setOf("http"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun discoversDirectoryAndExtractsChapterContentWithoutAi() = runBlocking {
        server.enqueue(MockResponse().setBody("User-agent: *\nAllow: /"))
        server.enqueue(MockResponse().setBody("""
            <html><head><title>测试仙途</title></head><body><h1>测试仙途</h1>
            <div id="list"><a href="/1">第一章 入山</a><a href="/2">第二章 筑基</a><a href="/3">第三章 金丹</a></div>
            </body></html>
        """.trimIndent()))
        server.enqueue(MockResponse().setBody("<html><body><div id='content'><p>山中无甲子，寒尽不知年。少年背着竹篓走过青石山路，来到云雾深处的古老宗门。</p><p>少年开始修行，从引气入体到凝练灵台，日复一日不曾懈怠。山风吹过松林，钟声响彻群峰，这条漫长仙途由此展开。</p></div></body></html>"))
        val preview = parser.preview(server.url("/book").toString())

        assertEquals("测试仙途", preview.title)
        assertEquals(3, preview.chapters.size)
        assertEquals("#content", preview.extractionPlan.contentSelector)
        assertTrue(preview.sample.contains("少年开始修行"))

        server.enqueue(MockResponse().setBody("<div id='content'><p>第二次读取正文。</p></div>"))
        assertTrue(parser.chapterText(preview.chapters.first().url, preview.extractionPlan).contains("第二次读取正文"))
    }

    @Test(expected = IllegalStateException::class)
    fun respectsRobotsDisallow() {
        runBlocking {
            server.enqueue(MockResponse().setBody("User-agent: *\nDisallow: /private"))
            parser.preview(server.url("/private/book").toString())
        }
    }

    private object FailingAiProvider : AiExtractionProvider {
        override suspend fun extract(sourceUrl: String, htmlSample: String): ExtractionPlan =
            error("AI should not be called for a regular directory")
        override suspend fun testConnection(): Boolean = false
    }
}
