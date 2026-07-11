package com.lightreader.app.journey

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.settings.AiConfiguration
import com.lightreader.app.core.web.DeepSeekAiExtractionProvider
import com.lightreader.app.core.web.WebImportException
import com.lightreader.app.core.web.WebImportFailure
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QaHttpsWebInstrumentedTest {
    private lateinit var application: ReaderApplication
    private lateinit var server: QaHttpsFixtureServer

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        server = QaHttpsFixtureServer()
        application.container.keyStore.clear()
    }

    @After
    fun tearDown() {
        application.container.keyStore.clear()
        server.close()
    }

    @Test
    fun qaTrustStoreExercisesRealHttpsCatalogParserAndFailureMapping() = runBlocking {
        val parser = application.container.webSourceParser
        val preview = parser.preview(server.url("/catalog"))

        assertEquals("LightReader QA Novel", preview.title)
        assertEquals("QA Author", preview.author)
        assertEquals(4, preview.chapters.size)
        assertTrue(preview.sample.contains("QA正文可读"))

        val failure = runCatching { parser.preview(server.url("/status/403")) }.exceptionOrNull()
        assertEquals(WebImportFailure.HTTP, (failure as WebImportException).failure)
    }

    @Test
    fun deepSeekMockUsesEncryptedFakeKeyAndNeverSendsTrimmedSecrets() = runBlocking {
        application.container.keyStore.save("qa-fake-key")
        application.container.aiConfigurationStore.save(
            AiConfiguration(baseUrl = server.url("/v1").trimEnd('/'), model = "qa-model", timeoutSeconds = 10),
        )
        val provider: DeepSeekAiExtractionProvider = application.container.aiProvider
        assertTrue(provider.testConnection())

        val secret = "WHOLE_DOWNLOADED_BOOK_SECRET_MARKER"
        val plan = provider.extract(
            server.url("/catalog"),
            "<html><script>$secret</script><body><h1>书名</h1><div class=\"chapters\"><a>第一章</a></div></body></html>",
        )
        assertEquals(".chapters a", plan.chapterLinkSelector)
        val sentBodies = server.requests.map { it.body.clone().readUtf8() }
        assertFalse(sentBodies.any { it.contains(secret) })
        assertTrue(server.requests.any { it.getHeader("Authorization") == "Bearer qa-fake-key" })
    }
}
