package com.lightreader.app.core.data

import java.net.URI
import java.util.Locale

/** HTTP scheme/host are case insensitive; path, query order, case and slash remain significant. */
object WebChapterIdentity {
    fun url(value: String): String {
        val trimmed = value.trim().substringBefore('#')
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val scheme = uri.scheme ?: return trimmed
        val host = uri.host ?: return trimmed
        val authority = buildString {
            uri.rawUserInfo?.let { append(it).append('@') }
            append(host.lowercase(Locale.ROOT))
            if (uri.port >= 0) append(':').append(uri.port)
        }
        return "${scheme.lowercase(Locale.ROOT)}://$authority${uri.rawPath.orEmpty()}" +
            (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun key(sourceUrl: String?, title: String): String =
        sourceUrl?.takeIf(String::isNotBlank)?.let { "url:${url(it)}" }
            ?: "title:${title.trim().replace(Regex("\\s+"), "")}" // Legacy chapters with no source URL only.
}
