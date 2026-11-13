package com.lightreader.app.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.WebBookPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.firstOrNull
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
    data class Failure(
        val failure: DownloadImportFailure,
        val httpStatus: Int? = null,
        val retryAfterMillis: Long? = null,
        val sourceUrl: String? = null,
    ) : DownloadPreviewResult
}

enum class DownloadImportFailure {
    NETWORK,
    HTTP,
    RESPONSE_TOO_LARGE,
    NON_HTML_RESPONSE,
    ACCESS_RESTRICTED,
    INVALID_URL,
    PARSE,
    NOT_FOUND,
    RATE_LIMITED,
    AI_CONFIGURATION,
    AI_FAILURE,
    SUBMISSION,
}

enum class DownloadNotice {
    TASK_CREATED,
    WEB_ONLY_REFRESH,
    CHECKING_UPDATES,
    NO_NEW_CHAPTERS,
    ADDED_CHAPTERS,
    REFRESH_FAILED,
    TASK_REUSED,
}

data class DownloadFeatureEvent(
    val notice: DownloadNotice,
    val count: Int? = null,
    val taskId: String? = null,
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
    val submitting: Boolean = false,
    val httpStatus: Int? = null,
    val retryAfterMillis: Long? = null,
    val sourceUrl: String? = null,
)

/** Owns web preview and durable download task actions, independent of app navigation and resources. */
class DownloadViewModel(
    private val gateway: DownloadFeatureGateway,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var previewJob: Job? = null
    private var previewVersion = 0L
    private val eventsChannel = Channel<DownloadFeatureEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()
    val tasks: StateFlow<List<DownloadTaskEntity>> = gateway.tasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val mutableWebState = MutableStateFlow(WebImportUiState(url = savedState["download.url"] ?: ""))
    val webState = mutableWebState.asStateFlow()
    private val mutableBusy = MutableStateFlow(DownloadBusyState())
    val busy = mutableBusy.asStateFlow()

    fun updateWebUrl(value: String) {
        if (webState.value.submitting) return
        if (value == webState.value.url) return
        invalidatePreview()
        savedState["download.url"] = value
        mutableWebState.value = WebImportUiState(url = value, submitting = webState.value.submitting)
    }

    /** Leaving the preview page cancels its request; only the small input survives recreation. */
    fun dismissWebPreview() {
        invalidatePreview()
        mutableWebState.value = webState.value.copy(loading = false)
    }

    private fun invalidatePreview() {
        previewVersion++
        previewJob?.cancel()
        previewJob = null
    }

    fun previewWebBook() {
        val url = webState.value.url.trim()
        if (webState.value.submitting) return
        invalidatePreview()
        val version = previewVersion
        mutableWebState.value = webState.value.copy(loading = true, error = null, preview = null,
            httpStatus = null, retryAfterMillis = null, sourceUrl = null)
        previewJob = viewModelScope.launch {
            val result = try {
                gateway.preview(url)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DownloadPreviewResult.Failure(DownloadImportFailure.NETWORK)
            }
            if (version != previewVersion || webState.value.url.trim() != url) return@launch
            when (result) {
                is DownloadPreviewResult.Success -> {
                    mutableWebState.value = webState.value.copy(loading = false, preview = result.preview)
                }
                is DownloadPreviewResult.Failure -> {
                    mutableWebState.value = webState.value.copy(loading = false, error = result.failure,
                        httpStatus = result.httpStatus, retryAfterMillis = result.retryAfterMillis, sourceUrl = result.sourceUrl)
                }
            }
        }
    }

    fun startWebDownload() {
        val state = webState.value
        val preview = state.preview ?: return
        if (state.submitting || state.loading) return
        // Set synchronously, before the coroutine can be scheduled, to close the double tap window.
        mutableWebState.value = state.copy(submitting = true, error = null)
        val version = previewVersion
        viewModelScope.launch {
            try {
                gateway.start(preview)
                if (version == previewVersion) {
                    savedState["download.url"] = ""
                    mutableWebState.value = WebImportUiState()
                }
                post(DownloadNotice.TASK_CREATED)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (version == previewVersion) {
                    mutableWebState.value = webState.value.copy(error = DownloadImportFailure.SUBMISSION)
                }
            } finally {
                mutableWebState.value = webState.value.copy(submitting = false)
            }
        }
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
        if (busy.value.active) return
        mutableBusy.value = DownloadBusyState(active = true, notice = DownloadNotice.CHECKING_UPDATES)
        viewModelScope.launch {
            post(DownloadNotice.CHECKING_UPDATES)
            try {
                val existing = gateway.tasks.firstOrNull().orEmpty().firstOrNull {
                    it.importedBookId == book.id && it.status !in listOf("COMPLETED", "CANCELED")
                }
                if (existing != null) {
                    post(DownloadNotice.TASK_REUSED, taskId = existing.id)
                    return@launch
                }
                val count = gateway.refreshBook(book.id)
                post(if (count == 0) DownloadNotice.NO_NEW_CHAPTERS else DownloadNotice.ADDED_CHAPTERS, count)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                post(DownloadNotice.REFRESH_FAILED)
            } finally {
                mutableBusy.value = DownloadBusyState()
            }
        }
    }

    private fun post(notice: DownloadNotice, count: Int? = null, taskId: String? = null) {
        eventsChannel.trySend(DownloadFeatureEvent(notice, count, taskId))
    }
}
