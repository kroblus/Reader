package com.lightreader.app.core.web

import android.util.Log
import android.webkit.JavascriptInterface
import com.lightreader.app.BuildConfig
import org.jsoup.Jsoup

data class HtmlDomSnapshot(
    val rawHtml: String,
    val bodyText: String,
) {
    fun bodyPreview(maxChars: Int = 500): String {
        if (bodyText.length <= maxChars) return bodyText
        return bodyText.take(maxChars).trimEnd() + "..."
    }

    companion object {
        fun from(html: String): HtmlDomSnapshot {
            val document = Jsoup.parse(html)
            return HtmlDomSnapshot(
                rawHtml = html,
                bodyText = document.body().text(),
            )
        }
    }
}

class HtmlBridge(
    private val onHtmlReceived: (HtmlDomSnapshot) -> Unit,
) {
    @JavascriptInterface
    fun processHTML(html: String) {
        val snapshot = HtmlDomSnapshot.from(html.take(MAX_DOM_CHARACTERS))
        if (BuildConfig.DEBUG) Log.d(TAG, "Received DOM HTML: length=${html.length}")
        onHtmlReceived(snapshot)
    }

    companion object {
        const val BRIDGE_NAME = "AndroidBridge"

        val extractionScript: String = """
            (function() {
                if (window.$BRIDGE_NAME && document.documentElement) {
                    window.$BRIDGE_NAME.processHTML(document.documentElement.outerHTML);
                }
            })();
        """.trimIndent()

        private const val TAG = "HtmlBridge"
        private const val MAX_DOM_CHARACTERS = 1_000_000
    }
}
