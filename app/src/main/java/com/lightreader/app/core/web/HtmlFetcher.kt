package com.lightreader.app.core.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

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
    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        val validated = validator.validate(url)
        val request = Request.Builder()
            .url(validated.toString())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) error("Page request failed: HTTP ${response.code}")
            if (bytes.isEmpty()) error("Page response was empty.")
            val contentType = response.header("Content-Type")
            val decoded = decodeHtml(bytes, contentType)
            require(!AccessRestrictionDetector.isBrowserVerification(decoded.html)) { AccessRestrictionDetector.MESSAGE }
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

    private data class DecodedHtml(val html: String, val charsetName: String)

    private companion object {
        const val USER_AGENT = "LightReader/0.1 (personal offline reader)"
        const val META_SCAN_BYTES = 8192
        val META_CHARSET = Regex("""<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
        val META_HTTP_EQUIV_CHARSET = Regex("""content\s*=\s*["'][^"']*charset=([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
    }
}
