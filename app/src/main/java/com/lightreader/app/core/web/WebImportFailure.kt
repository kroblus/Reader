package com.lightreader.app.core.web

enum class WebImportFailure {
    NETWORK,
    HTTP,
    RESPONSE_TOO_LARGE,
    NON_HTML_RESPONSE,
    ACCESS_RESTRICTED,
}

class WebImportException(
    val failure: WebImportFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
