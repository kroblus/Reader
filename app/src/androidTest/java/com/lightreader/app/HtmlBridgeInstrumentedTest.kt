package com.lightreader.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.core.web.HtmlBridge
import com.lightreader.app.core.web.HtmlDomSnapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HtmlBridgeInstrumentedTest {
    private var webView: WebView? = null

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.apply {
                stopLoading()
                removeJavascriptInterface(HtmlBridge.BRIDGE_NAME)
                destroy()
            }
            webView = null
        }
    }

    @Test
    fun visibleWebViewCanPassOuterHtmlThroughJavascriptBridge() {
        val latch = CountDownLatch(1)
        val snapshotRef = AtomicReference<HtmlDomSnapshot>()
        val html = """
            <!doctype html>
            <html>
              <head><title>DOM Bridge Fixture</title></head>
              <body>
                <main id="content">
                  <h1>第一章 入山</h1>
                  <p>山中修行，自此开始。</p>
                </main>
              </body>
            </html>
        """.trimIndent()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(
                    HtmlBridge { snapshot ->
                        snapshotRef.set(snapshot)
                        latch.countDown()
                    },
                    HtmlBridge.BRIDGE_NAME,
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(HtmlBridge.extractionScript, null)
                    }
                }
                loadDataWithBaseURL("https://example.test/", html, "text/html", "UTF-8", null)
            }
        }

        assertTrue("Timed out waiting for WebView DOM callback", latch.await(5, TimeUnit.SECONDS))
        val snapshot = snapshotRef.get()
        assertNotNull(snapshot)
        assertTrue(snapshot.rawHtml.contains("DOM Bridge Fixture"))
        assertEquals("第一章 入山 山中修行，自此开始。", snapshot.bodyText)
    }
}
