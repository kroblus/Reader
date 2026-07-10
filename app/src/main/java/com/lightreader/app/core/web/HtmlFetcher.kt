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
    suspend fun fetch(url: String, maxBytes: Int = DEFAULT_MAX_RESPONSE_BYTES): FetchResult = withContext(Dispatchers.IO) {
        val validated = validator.validate(url)
        val request = Request.Builder()
            .url(validated.toString())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        try {
            client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw WebImportException(
                WebImportFailure.HTTP,
                "Page request failed: HTTP ${response.code}",
            )
            val body = response.body ?: error("Page response was empty.")
            if (body.contentLength() > maxBytes) throw WebImportException(
                WebImportFailure.RESPONSE_TOO_LARGE,
                "Page response exceeds the ${maxBytes / 1024}KB safety limit.",
            )
            val bytes = body.byteStream().use { input -> input.readAtMost(maxBytes) }
            if (bytes.isEmpty()) error("Page response was empty.")
            val contentType = response.header("Content-Type")
            if (!isHtmlLikeContentType(contentType)) throw WebImportException(
                WebImportFailure.NON_HTML_RESPONSE,
                "Page response was not HTML (${contentType?.substringBefore(';') ?: "unknown type"}).",
            )
            val decoded = decodeHtml(bytes, contentType)
            if (AccessRestrictionDetector.isBrowserVerification(decoded.html)) throw WebImportException(
                WebImportFailure.ACCESS_RESTRICTED,
                AccessRestrictionDetector.MESSAGE,
            )
            FetchResult(
                originalUrl = url,
                finalUrl = response.request.url.toString(),
                statusCode = response.code,
                contentType = contentType,
                bytes = bytes,
                charset = decoded.charsetName,
                html = decoded.html,
            )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: WebImportException) {
            throw error
        } catch (error: IOException) {
            throw WebImportException(WebImportFailure.NETWORK, "Page request could not reach the network.", error)
        }
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
