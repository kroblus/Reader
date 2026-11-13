package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RobotsPolicyTest {
    @Test fun encodedUnreservedAndUnicodePathsUseTheSameRules() {
        val rules = RobotsRules.parse("User-agent: *\nDisallow: /private\nDisallow: /中文")
        assertFalse(rules.allows("/%70rivate/book"))
        assertFalse(rules.allows("/%E4%B8%AD%E6%96%87/book"))
    }

    @Test fun emptyRuleStillSeparatesTheFollowingAgentGroup() {
        val rules = RobotsRules.parse("User-agent: LightReader\nDisallow:\nUser-agent: Other\nDisallow: /")
        assertTrue(rules.allows("/book"))
    }

    @Test fun longestAllowAndAgentGroupsAreRespected() {
        val rules = RobotsRules.parse("User-agent: *\nDisallow: /\nUser-agent: LightReader\nDisallow: /private\nAllow: /private/public\nUser-agent: LightReader\nDisallow: /*.pdf$")
        assertTrue(rules.allows("/book"))
        assertFalse(rules.allows("/private/book"))
        assertTrue(rules.allows("/private/public/book"))
        assertFalse(rules.allows("/book.pdf"))
        assertTrue(rules.allows("/book.pdf?preview=1"))
    }

    @Test fun chapterDownloadsCheckPlainTextRobotsAndCacheByOrigin() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val parser = JsoupWebSourceParser(OkHttpClient(), allowedSchemes = setOf("http"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("User-agent: *\nDisallow: /private"))
            val error = runCatching { parser.chapterText(server.url("/private/1").toString(), ExtractionPlan(contentSelector = "#content")) }.exceptionOrNull()
            assertTrue(error is WebImportException)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setBody("<div id=content>A permitted chapter body.</div>"))
            assertTrue(parser.chapterText(server.url("/public/1").toString(), ExtractionPlan(contentSelector = "#content")).contains("permitted"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun failedRobotsReadNeverBecomesAnAllow() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val parser = JsoupWebSourceParser(OkHttpClient(), allowedSchemes = setOf("http"))
            server.enqueue(MockResponse().setResponseCode(403))
            val error = runCatching { parser.preview(server.url("/book").toString()) }.exceptionOrNull() as WebImportException
            assertEquals(403, error.httpStatus)
            assertEquals(1, server.requestCount)
        }
    }
}
