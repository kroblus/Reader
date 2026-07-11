package com.lightreader.app.journey

import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject

class QaHttpsFixtureServer : Closeable {
    private val server = MockWebServer()
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    init {
        val context = InstrumentationRegistry.getInstrumentation().context
        val encoded = context.assets.open("reader-qa-tls.p12.b64").bufferedReader().use { it.readText() }
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(Base64.decode(encoded, Base64.DEFAULT)), PASSWORD)
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, PASSWORD)
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, null, SecureRandom())
        }
        server.useHttps(sslContext.socketFactory, false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return responseFor(request)
            }
        }
        server.start()
    }

    fun url(path: String): String = server.url(path).toString()

    override fun close() {
        server.shutdown()
    }

    private fun responseFor(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
        "/robots.txt" -> html(200, "<html><body>User-agent: *<br/>Disallow:</body></html>")
        "/catalog" -> html(
            200,
            """
                <html><head><title>LightReader QA Novel</title><meta name="author" content="QA Author"/></head>
                <body><h1>LightReader QA Novel</h1><div class="chapters">
                <a href="/chapter/1">第一章</a><a href="/chapter/2">第二章</a>
                <a href="/chapter/3">第三章</a><a href="/chapter/4">第四章</a>
                </div></body></html>
            """.trimIndent(),
        )
        "/redirect" -> MockResponse().setResponseCode(302).addHeader("Location", "/catalog")
        "/chapter/1", "/chapter/2", "/chapter/3", "/chapter/4" -> html(
            200,
            "<html><body><main class=\"content\"><h1>章节</h1><p>山中修行，自此开始。QA正文可读，晨钟响过三遍，雾气漫过石桥，少年沿着山路继续前行，没有遗漏任何文字。</p></main></body></html>",
        )
        "/non-html" -> MockResponse().setResponseCode(200).addHeader("Content-Type", "application/pdf").setBody("PDF")
        "/too-large" -> html(200, "x".repeat(2 * 1024 * 1024 + 8))
        "/disconnect" -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        "/verification" -> html(
            200,
            "<html><title>Checking your browser</title><body><p>Security verification</p><script>window.location.href='?challenge=token'</script></body></html>",
        )
        "/v1/chat/completions", "/chat/completions" -> deepSeekResponse(request)
        "/status/403" -> html(403, "forbidden")
        "/status/404" -> html(404, "missing")
        "/status/500" -> html(500, "server error")
        else -> html(404, "missing")
    }

    private fun deepSeekResponse(request: RecordedRequest): MockResponse {
        if (request.getHeader("Authorization") != "Bearer qa-fake-key") return html(401, "unauthorized")
        val requestBody = request.body.readUtf8()
        val content = if (requestBody.contains("response_format")) {
            """{"titleSelector":"h1","chapterLinkSelector":".chapters a","contentSelector":".content","removeSelectors":[]}"""
        } else {
            "OK"
        }
        val body = JSONObject().put(
            "choices",
            JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))),
        )
        return MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody(body.toString())
    }

    private fun html(code: Int, body: String) = MockResponse()
        .setResponseCode(code)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(body)

    private companion object {
        val PASSWORD = "readerqa".toCharArray()
    }
}
