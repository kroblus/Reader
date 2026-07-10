package com.lightreader.app.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.WebBookPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Boundary consumed by the download feature. Network parsers, persistence and
 * scheduling stay behind this interface so the screen state is site-agnostic.
 */
interface DownloadFeatureGateway {
    val tasks: Flow<List<DownloadTaskEntity>>

    suspend fun preview(url: String): DownloadPreviewResult
    suspend fun start(preview: WebBookPreview)
    suspend fun pause(id: String)
    suspend fun resume(id: String)
    suspend fun cancel(id: String)
    suspend fun delete(id: String)
    suspend fun refreshBook(bookId: String): Int
}

sealed interface DownloadPreviewResult {
    data class Success(val preview: WebBookPreview) : DownloadPreviewResult
    data class Failure(val failure: DownloadImportFailure) : DownloadPreviewResult
}

enum class DownloadImportFailure {
    NETWORK,
    HTTP,
    RESPONSE_TOO_LARGE,
    NON_HTML_RESPONSE,
    ACCESS_RESTRICTED,
    INVALID_URL,
    PARSE,
}

enum class DownloadNotice {
    TASK_CREATED,
    WEB_ONLY_REFRESH,
    CHECKING_UPDATES,
    NO_NEW_CHAPTERS,
    ADDED_CHAPTERS,
    REFRESH_FAILED,
}

data class DownloadFeatureEvent(
    val notice: DownloadNotice,
    val count: Int? = null,
)

data class DownloadBusyState(
    val active: Boolean = false,
    val notice: DownloadNotice? = null,
)

data class WebImportUiState(
    val url: String = "",
    val loading: Boolean = false,
    val preview: WebBookPreview? = null,
    val error: DownloadImportFailure? = null,
)

/** Owns web preview and durable download task actions, independent of app navigation and resources. */
class DownloadViewModel(
    private val gateway: DownloadFeatureGateway,
) : ViewModel() {
    private val eventsChannel = Channel<DownloadFeatureEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()
    val tasks: StateFlow<List<DownloadTaskEntity>> = gateway.tasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val webState = MutableStateFlow(WebImportUiState())
    val busy = MutableStateFlow(DownloadBusyState())

    fun updateWebUrl(value: String) {
        webState.value = webState.value.copy(url = value, preview = null, error = null)
    }

    fun previewWebBook() {
        val url = webState.value.url.trim()
        viewModelScope.launch {
            webState.value = webState.value.copy(loading = true, error = null, preview = null)
            when (val result = gateway.preview(url)) {
                is DownloadPreviewResult.Success -> {
                    webState.value = webState.value.copy(loading = false, preview = result.preview)
                }
                is DownloadPreviewResult.Failure -> {
                    webState.value = webState.value.copy(loading = false, error = result.failure)
                }
            }
        }
    }

    fun startWebDownload() = viewModelScope.launch {
        val preview = webState.value.preview ?: return@launch
        gateway.start(preview)
        webState.value = webState.value.copy(preview = null, url = "")
        post(DownloadNotice.TASK_CREATED)
    }

    fun pauseDownload(id: String) = viewModelScope.launch { gateway.pause(id) }
    fun resumeDownload(id: String) = viewModelScope.launch { gateway.resume(id) }
    fun cancelDownload(id: String) = viewModelScope.launch { gateway.cancel(id) }
    fun deleteDownload(id: String) = viewModelScope.launch { gateway.delete(id) }

    fun refreshWebBook(book: Book?) {
        if (book == null) return
        if (book.format != BookFormat.WEB || book.sourceUrl.isNullOrBlank()) {
            post(DownloadNotice.WEB_ONLY_REFRESH)
            return
        }
        viewModelScope.launch {
            busy.value = DownloadBusyState(active = true, notice = DownloadNotice.CHECKING_UPDATES)
            post(DownloadNotice.CHECKING_UPDATES)
            try {
                val count = gateway.refreshBook(book.id)
                post(if (count == 0) DownloadNotice.NO_NEW_CHAPTERS else DownloadNotice.ADDED_CHAPTERS, count)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                post(DownloadNotice.REFRESH_FAILED)
            } finally {
                busy.value = DownloadBusyState()
            }
        }
    }

    private fun post(notice: DownloadNotice, count: Int? = null) {
        eventsChannel.trySend(DownloadFeatureEvent(notice, count))
    }
}
