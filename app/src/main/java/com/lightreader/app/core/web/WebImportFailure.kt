package com.lightreader.app.core.web

enum class WebImportFailure {
    NETWORK,
    HTTP,
    RESPONSE_TOO_LARGE,
    NON_HTML_RESPONSE,
    ACCESS_RESTRICTED,
    AI_CONFIGURATION,
    AI_FAILURE,
}

class WebImportException(
    val failure: WebImportFailure,
    message: String,
    cause: Throwable? = null,
    val httpStatus: Int? = null,
    val retryable: Boolean = false,
    val retryAfterMillis: Long? = null,
    val sourceUrl: String? = null,
) : IllegalStateException(message, cause)
