package com.lightreader.app.core.web

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HtmlFetcherTest {
    private lateinit var server: MockWebServer
    private lateinit var fetcher: HtmlFetcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = HtmlFetcher(OkHttpClient(), NovelUrlValidator(allowedSchemes = setOf("http")))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun rejectsAnExplicitNonHtmlResponseBeforeParsing() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/pdf")
                .setBody("not an HTML document"),
        )

        val failure = runCatching { fetcher.fetch(server.url("/book").toString()) }.exceptionOrNull()

        assertTrue(failure?.message?.contains("not HTML") == true)
    }

    @Test
    fun stopsAChunkedResponseThatExceedsTheConfiguredLimit() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=UTF-8")
                .setChunkedBody("x".repeat(1_025), 128),
        )

        val failure = runCatching { fetcher.fetch(server.url("/book").toString(), maxBytes = 1_024) }.exceptionOrNull()

        assertTrue(failure?.message?.contains("safety limit") == true)
    }
}
