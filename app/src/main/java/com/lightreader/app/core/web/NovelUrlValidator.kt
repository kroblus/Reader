package com.lightreader.app.core.web

import java.net.URI

class NovelUrlValidator(
    private val allowedSchemes: Set<String> = setOf("https"),
) {
    fun validate(rawUrl: String): URI {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "Enter a catalog page URL." }
        val uri = runCatching { URI(trimmed) }
            .getOrElse { throw IllegalArgumentException("Enter a valid URL.") }
        val scheme = uri.scheme?.lowercase()
        require(scheme in allowedSchemes && !uri.host.isNullOrBlank()) {
            if ("https" in allowedSchemes && allowedSchemes.size == 1) {
                "Only HTTPS catalog URLs are supported in this version."
            } else {
                "Unsupported URL scheme."
            }
        }
        require(uri.userInfo == null) { "URLs with embedded credentials are not supported." }
        return uri
    }
}
