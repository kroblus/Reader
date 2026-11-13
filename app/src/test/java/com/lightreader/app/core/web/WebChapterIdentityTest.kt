package com.lightreader.app.core.web

import com.lightreader.app.core.data.WebChapterIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WebChapterIdentityTest {
    @Test fun normalizesOnlySchemeHostAndFragment() {
        assertEquals("https://example.com/Novel/A?Key=Value&x=1",
            WebChapterIdentity.url("HTTPS://EXAMPLE.COM/Novel/A?Key=Value&x=1#section"))
        assertNotEquals(WebChapterIdentity.url("https://example.com/A"), WebChapterIdentity.url("https://example.com/a"))
        assertNotEquals(WebChapterIdentity.url("https://example.com/a/"), WebChapterIdentity.url("https://example.com/a"))
        assertNotEquals(WebChapterIdentity.url("https://example.com/a?x=A"), WebChapterIdentity.url("https://example.com/a?x=a"))
        assertNotEquals(WebChapterIdentity.url("https://example.com/a?x=1&y=2"), WebChapterIdentity.url("https://example.com/a?y=2&x=1"))
    }

    @Test fun sameTitleDifferentUrlsAreDistinctAndRenamedSameUrlIsIdentical() {
        assertNotEquals(WebChapterIdentity.key("https://example.com/1", "Chapter"), WebChapterIdentity.key("https://example.com/2", "Chapter"))
        assertEquals(WebChapterIdentity.key("https://example.com/1", "Old"), WebChapterIdentity.key("https://example.com/1", "New"))
    }
}
