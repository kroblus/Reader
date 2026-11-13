package com.lightreader.app.core.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException

data class FetchResult(
    val originalUrl: String,
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val bytes: ByteArray,
    val charset: String?,
    val html: String,
) {
    override fun equals(other: Any?): Boolean = other is FetchResult &&
        originalUrl == other.originalUrl &&
        finalUrl == other.finalUrl &&
        statusCode == other.statusCode &&
        contentType == other.contentType &&
        bytes.contentEquals(other.bytes) &&
        charset == other.charset &&
        html == other.html

    override fun hashCode(): Int {
        var result = originalUrl.hashCode()
        result = 31 * result + finalUrl.hashCode()
        result = 31 * result + statusCode
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + (charset?.hashCode() ?: 0)
        result = 31 * result + html.hashCode()
        return result
    }
}

class HtmlFetcher(
    private val client: OkHttpClient,
    private val validator: NovelUrlValidator = NovelUrlValidator(),
) {
    // Redirects are followed here so every target is validated before a connection is made.
    private class AttemptMetadata(var retryAfter: String? = null)
    private val safeClient = client.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 503) {
                // OkHttp 4.12 retries 503 + Retry-After: 0 even with retryOnConnectionFailure=false.
                // Keep the original value in this call's private tag and let our single retry loop own it.
                chain.request().tag(AttemptMetadata::class.java)?.retryAfter = response.header("Retry-After")
                response.newBuilder().removeHeader("Retry-After").build()
            } else response
        }.build()

    suspend fun fetch(
        url: String,
        maxBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
        beforeRequest: suspend (String) -> Unit = {},
    ): FetchResult = fetchWithRetry(url, maxBytes, html = true, beforeRequest)

    suspend fun fetchText(url: String, maxBytes: Int): FetchResult =
        fetchWithRetry(url, maxBytes, html = false) {}

    private suspend fun fetchWithRetry(
        url: String,
        maxBytes: Int,
        html: Boolean,
        beforeRequest: suspend (String) -> Unit,
    ): FetchResult {
        require(maxBytes > 0)
        repeat(3) { attempt ->
            try {
                return fetchOnce(url, maxBytes, html, beforeRequest)
            } catch (error: CancellationException) {
                throw error
            } catch (error: WebImportException) {
                val wait = error.retryAfterMillis ?: (250L shl attempt)
                if (!error.retryable || attempt == 2 || wait > 30_000L) throw error
                kotlinx.coroutines.delay(wait)
            }
        }
        error("Unreachable retry state")
    }

    private suspend fun fetchOnce(
        url: String,
        maxBytes: Int,
        html: Boolean,
        beforeRequest: suspend (String) -> Unit,
    ): FetchResult = withContext(Dispatchers.IO) {
        var current = validator.validate(url).toString()
        try {
            repeat(6) { redirect ->
                beforeRequest(current)
                val metadata = AttemptMetadata()
                val request = Request.Builder().url(current).tag(AttemptMetadata::class.java, metadata)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", if (html) "text/html,application/xhtml+xml" else "text/plain")
                    .build()
                var next: String? = null
                val result = safeClient.newCall(request).readCancellable { response ->
                    if (response.code in listOf(301, 302, 303, 307, 308)) {
                        if (redirect == 5) throw WebImportException(WebImportFailure.HTTP, "Too many redirects.", sourceUrl = current)
                        val location = response.header("Location") ?: throw WebImportException(WebImportFailure.HTTP, "Redirect has no destination.", sourceUrl = current)
                        next = validator.validate(java.net.URI(current).resolve(location).toString()).toString()
                        null
                    } else {
                        if (!response.isSuccessful) {
                            val description = when (response.code) {
                                401, 403 -> "Access is restricted. Open the original webpage to check its requirements."
                                404 -> "This content or address is no longer available."
                                429 -> "The site is limiting requests. Wait before trying again."
                                else -> "Page request failed."
                            }
                            throw WebImportException(WebImportFailure.HTTP, "$description HTTP ${response.code}",
                                httpStatus = response.code, retryable = response.code == 429 || response.code in 500..599,
                                retryAfterMillis = retryAfterMillis(metadata.retryAfter ?: response.header("Retry-After")), sourceUrl = current)
                        }
                        val body = response.body ?: throw WebImportException(WebImportFailure.HTTP, "Page response was empty.", sourceUrl = current)
                        if (body.contentLength() > maxBytes) throw WebImportException(WebImportFailure.RESPONSE_TOO_LARGE, "Page response exceeds the ${maxBytes / 1024}KB safety limit.")
                        val bytes = body.byteStream().use { it.readAtMost(maxBytes) }
                        if (html && bytes.isEmpty()) throw WebImportException(WebImportFailure.HTTP, "Page response was empty.", sourceUrl = current)
                        val contentType = response.header("Content-Type")
                        if (html && !isHtmlLikeContentType(contentType)) throw WebImportException(WebImportFailure.NON_HTML_RESPONSE,
                            "Page response was not HTML (${contentType?.substringBefore(';') ?: "unknown type"}).", sourceUrl = current)
                        if (!html && contentType != null && contentType.substringBefore(';').trim().lowercase() != "text/plain") {
                            throw WebImportException(WebImportFailure.NON_HTML_RESPONSE, "Expected a text response.", sourceUrl = current)
                        }
                        val decoded = decodeHtml(bytes, contentType)
                        if (html && AccessRestrictionDetector.isBrowserVerification(decoded.html)) throw WebImportException(WebImportFailure.ACCESS_RESTRICTED, AccessRestrictionDetector.MESSAGE, sourceUrl = current)
                        FetchResult(url, current, response.code, contentType, bytes, decoded.charsetName, decoded.html)
                    }
                }
                if (result != null) return@withContext result
                current = requireNotNull(next)
            }
            error("Unreachable redirect state")
        } catch (error: CancellationException) {
            throw error
        } catch (error: WebImportException) {
            throw error
        } catch (error: IOException) {
            throw WebImportException(WebImportFailure.NETWORK, "Page request could not reach the network.", error, retryable = true, sourceUrl = current)
        }
    }

    internal fun retryAfterMillis(value: String?): Long? {
        if (value == null) return null
        value.trim().toLongOrNull()?.let { return it.coerceIn(0, Long.MAX_VALUE / 1000) * 1000 }
        return runCatching {
            (java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
        }.getOrNull()
    }

    private fun decodeHtml(bytes: ByteArray, contentType: String?): DecodedHtml {
        charsetFromBom(bytes)?.let { charset ->
            return DecodedHtml(stripBom(String(bytes, charset)), charset.name())
        }
        charsetFromContentType(contentType)?.let { charset ->
            return DecodedHtml(String(bytes, charset), charset.name())
        }
        charsetFromMeta(bytes)?.let { charset ->
            return DecodedHtml(String(bytes, charset), charset.name())
        }
        if (isValidUtf8(bytes)) return DecodedHtml(String(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8.name())
        val gb18030 = Charset.forName("GB18030")
        return DecodedHtml(String(bytes, gb18030), gb18030.name())
    }

    private fun charsetFromBom(bytes: ByteArray): Charset? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            StandardCharsets.UTF_8
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            StandardCharsets.UTF_16LE
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            StandardCharsets.UTF_16BE
        else -> null
    }

    private fun charsetFromContentType(contentType: String?): Charset? {
        if (contentType.isNullOrBlank()) return null
        val name = Regex("""charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
            .find(contentType)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    private fun charsetFromMeta(bytes: ByteArray): Charset? {
        val sample = String(bytes.copyOfRange(0, minOf(bytes.size, META_SCAN_BYTES)), StandardCharsets.ISO_8859_1)
        val name = META_CHARSET.find(sample)?.groupValues?.getOrNull(1)
            ?: META_HTTP_EQUIV_CHARSET.find(sample)?.groupValues?.getOrNull(1)
            ?: return null
        return runCatching { Charset.forName(name.trim()) }.getOrNull()
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
    }.isSuccess

    private fun stripBom(value: String): String = value.trimStart('\uFEFF')

    /** Some small personal sites omit a MIME type, so only reject an explicit non-HTML response. */
    private fun isHtmlLikeContentType(contentType: String?): Boolean {
        val mimeType = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return mimeType.isBlank() || mimeType == "text/html" || mimeType == "application/xhtml+xml"
    }

    private data class DecodedHtml(val html: String, val charsetName: String)

    private fun java.io.InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw WebImportException(
                WebImportFailure.RESPONSE_TOO_LARGE,
                "Page response exceeds the ${maxBytes / 1024}KB safety limit.",
            )
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val USER_AGENT = "LightReader/0.1 (personal offline reader)"
        const val META_SCAN_BYTES = 8192
        const val DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        val META_CHARSET = Regex("""<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
        val META_HTTP_EQUIV_CHARSET = Regex("""content\s*=\s*["'][^"']*charset=([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
    }
}
