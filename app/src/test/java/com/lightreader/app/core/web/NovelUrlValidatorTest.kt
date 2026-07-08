package com.lightreader.app.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelUrlValidatorTest {
    @Test
    fun acceptsHttpsCatalogUrls() {
        val uri = NovelUrlValidator().validate(" https://example.com/book/123 ")

        assertEquals("https", uri.scheme)
        assertEquals("example.com", uri.host)
    }

    @Test
    fun rejectsNonHttpsByDefault() {
        val result = runCatching { NovelUrlValidator().validate("http://example.com/book/123") }

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsEmbeddedCredentials() {
        val result = runCatching { NovelUrlValidator().validate("https://user:pass@example.com/book/123") }

        assertTrue(result.isFailure)
    }
}
