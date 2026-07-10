package com.lightreader.app.ui

import com.lightreader.app.R
import com.lightreader.app.feature.download.DownloadBusyState
import com.lightreader.app.feature.download.DownloadFeatureEvent
import com.lightreader.app.feature.download.DownloadImportFailure
import com.lightreader.app.feature.download.DownloadNotice

internal fun DownloadFeatureEvent.toUiText(): UiText = notice.toUiText(count)

internal fun DownloadBusyState.toBusyState(): BusyState = BusyState(
    active = active,
    message = notice?.toUiText(),
)

internal fun DownloadImportFailure.toUiText(): UiText = when (this) {
    DownloadImportFailure.NETWORK -> uiText(R.string.message_web_network)
    DownloadImportFailure.HTTP -> uiText(R.string.message_web_http)
    DownloadImportFailure.RESPONSE_TOO_LARGE -> uiText(R.string.message_web_response_too_large)
    DownloadImportFailure.NON_HTML_RESPONSE -> uiText(R.string.message_web_non_html)
    DownloadImportFailure.ACCESS_RESTRICTED -> uiText(R.string.message_web_access_restricted)
    DownloadImportFailure.INVALID_URL -> uiText(R.string.message_web_invalid_url)
    DownloadImportFailure.PARSE -> uiText(R.string.message_parse_failed)
}

private fun DownloadNotice.toUiText(count: Int? = null): UiText = when (this) {
    DownloadNotice.TASK_CREATED -> uiText(R.string.message_download_task_created)
    DownloadNotice.WEB_ONLY_REFRESH -> uiText(R.string.message_refresh_web_only)
    DownloadNotice.CHECKING_UPDATES -> uiText(R.string.busy_checking_updates)
    DownloadNotice.NO_NEW_CHAPTERS -> uiText(R.string.message_no_new_chapters)
    DownloadNotice.ADDED_CHAPTERS -> uiText(R.string.message_added_chapters_to_refresh, count ?: 0)
    DownloadNotice.REFRESH_FAILED -> uiText(R.string.message_refresh_failed)
}
