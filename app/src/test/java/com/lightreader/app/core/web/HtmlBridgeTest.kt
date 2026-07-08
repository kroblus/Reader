package com.lightreader.app.core.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlBridgeTest {
    @Test
    fun snapshotExtractsBodyTextFromHtml() {
        val snapshot = HtmlDomSnapshot.from(
            """
            <!doctype html>
            <html>
              <head><title>Bridge Test</title></head>
              <body><h1>第一章</h1><p>山中修行，自此开始。</p></body>
            </html>
            """.trimIndent(),
        )

        assertTrue(snapshot.rawHtml.contains("Bridge Test"))
        assertEquals("第一章 山中修行，自此开始。", snapshot.bodyText)
    }

    @Test
    fun snapshotHandlesEmptyHtml() {
        val snapshot = HtmlDomSnapshot.from("")

        assertEquals("", snapshot.rawHtml)
        assertEquals("", snapshot.bodyText)
    }

    @Test
    fun bodyPreviewTruncatesLongText() {
        val snapshot = HtmlDomSnapshot(rawHtml = "<html></html>", bodyText = "abcdef")

        assertEquals("abc...", snapshot.bodyPreview(3))
    }
}
