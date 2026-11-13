package com.lightreader.app.core.web

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
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
    fun textReaderAcceptsPlainRobotsWhileHtmlReaderRejectsIt() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("User-agent: *"))
        assertTrue(fetcher.fetchText(server.url("/robots.txt").toString(), 1024).html.contains("User-agent"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("not html"))
        assertTrue(runCatching { fetcher.fetch(server.url("/book").toString()) }.exceptionOrNull() is WebImportException)
    }

    @Test
    fun retainsHttpStatusRetryabilityAndRetryAfter() = runBlocking {
        for (status in listOf(401, 403, 404, 429, 503)) {
            server.enqueue(MockResponse().setResponseCode(status).setHeader("Retry-After", "120"))
            val error = runCatching { fetcher.fetch(server.url("/status/$status").toString()) }.exceptionOrNull() as WebImportException
            org.junit.Assert.assertEquals(status, error.httpStatus)
            org.junit.Assert.assertEquals(status == 429 || status >= 500, error.retryable)
            org.junit.Assert.assertEquals(120_000L, error.retryAfterMillis)
        }
    }

    @Test
    fun retriesTemporaryServerFailureWithABound() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0")) }
        val error = runCatching { fetcher.fetch(server.url("/retry").toString()) }.exceptionOrNull() as WebImportException
        org.junit.Assert.assertEquals(503, error.httpStatus)
        org.junit.Assert.assertEquals(3, server.requestCount)
    }

    @Test
    fun validatesRedirectBeforeSendingAnotherRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "file:///secret"))
        assertTrue(runCatching { fetcher.fetch(server.url("/redirect").toString()) }.exceptionOrNull() is IllegalArgumentException)
        org.junit.Assert.assertEquals(1, server.requestCount)
    }

    @Test
    fun appliesRequestPolicyToEveryRedirectTarget() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/destination"))
        server.enqueue(MockResponse().setBody("<html>body</html>"))
        val checked = mutableListOf<String>()
        fetcher.fetch(server.url("/redirect").toString(), beforeRequest = { checked += it })
        org.junit.Assert.assertEquals(listOf(server.url("/redirect").toString(), server.url("/destination").toString()), checked)
    }

    @Test
    fun cancellationClosesAResponseStillBeingRead() = runBlocking {
        val client = OkHttpClient()
        val cancellable = HtmlFetcher(client, NovelUrlValidator(setOf("http")))
        server.enqueue(MockResponse().setBody("<html>" + "x".repeat(4096)).throttleBody(1, 1, java.util.concurrent.TimeUnit.SECONDS))
        val job = launch { cancellable.fetch(server.url("/slow").toString()) }
        kotlinx.coroutines.withTimeout(5000) {
            while (client.dispatcher.runningCallsCount() == 0) kotlinx.coroutines.delay(10)
        }
        job.cancel()
        job.join()
        kotlinx.coroutines.withTimeout(5000) {
            while (client.dispatcher.runningCallsCount() != 0) kotlinx.coroutines.delay(10)
        }
        assertTrue(job.isCancelled)
    }

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
