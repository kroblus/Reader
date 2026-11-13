package com.lightreader.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.core.data.SearchCursor
import com.lightreader.app.core.data.SearchCursorExpiredException
import com.lightreader.app.core.data.SearchPage
import com.lightreader.app.core.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun interface BookSearchGateway {
    suspend fun search(bookId: String, query: String): List<SearchResult>
    suspend fun page(bookId: String, query: String, cursor: SearchCursor?): SearchPage =
        SearchPage(search(bookId, query), false, null, true)
}

data class SearchOrigin(val bookId: String, val chapterId: Long, val charOffset: Int)

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
    val failed: Boolean = false,
    val hasMore: Boolean = false,
    val complete: Boolean = false,
    val cursor: SearchCursor? = null,
    val origin: SearchOrigin? = null,
    val listIndex: Int = 0,
    val listOffset: Int = 0,
)

/** Saves only small reconstruction state, never chapter text or result lists. */
class BookSearchViewModel(
    private var bookId: String,
    private val gateway: BookSearchGateway,
) : ViewModel() {
    private var savedState = SavedStateHandle()
    private val mutableState = MutableStateFlow(SearchUiState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var requestVersion = 0L
    private var needsRestore = false

    constructor(bookId: String, gateway: BookSearchGateway, savedState: SavedStateHandle) : this(bookId, gateway) {
        this.savedState = savedState
        this.bookId = savedState["searchBook"] ?: bookId
        val originBook: String? = savedState["originBook"]
        mutableState.value = SearchUiState(
            query = savedState["searchQuery"] ?: "",
            origin = originBook?.let { SearchOrigin(it, savedState["originChapter"] ?: 0L, savedState["originOffset"] ?: 0) },
            listIndex = savedState["listIndex"] ?: 0,
            listOffset = savedState["listOffset"] ?: 0,
        )
        needsRestore = mutableState.value.query.isNotBlank()
    }

    fun isForBook(value: String): Boolean = bookId == value
    fun openBook(value: String) {
        if (value == bookId) return
        cancelSearch()
        bookId = value
        mutableState.value = SearchUiState()
        persist()
    }

    fun captureOrigin(value: SearchOrigin?) {
        if (value == null || value.bookId != bookId || mutableState.value.origin != null) return
        mutableState.value = mutableState.value.copy(origin = value)
        persist()
    }

    fun clearOrigin() {
        mutableState.value = mutableState.value.copy(origin = null)
        persist()
    }

    fun updateListPosition(index: Int, offset: Int) {
        mutableState.value = mutableState.value.copy(listIndex = index, listOffset = offset)
        persist()
    }

    fun updateQuery(value: String) {
        if (value == mutableState.value.query) return
        cancelSearch()
        mutableState.value = SearchUiState(query = value, origin = mutableState.value.origin)
        persist()
    }
    fun clearQuery() = updateQuery("")
    fun search() = requestPage(append = false)
    fun loadMore() { if (mutableState.value.hasMore && !mutableState.value.searching) requestPage(append = true) }
    fun restoreResultsIfNeeded() {
        if (needsRestore && mutableState.value.query.isNotBlank() && !mutableState.value.hasSearched && !mutableState.value.searching) {
            needsRestore = false
            requestPage(append = false, restorePosition = true)
        }
    }

    private fun requestPage(append: Boolean, restorePosition: Boolean = false) {
        val before = mutableState.value
        val query = before.query.trim()
        if (query.isBlank()) return
        cancelSearch()
        val version = requestVersion
        val targetBookId = bookId
        mutableState.value = if (append) before.copy(searching = true, failed = false)
        else SearchUiState(query = query, searching = true, origin = before.origin,
            listIndex = if (restorePosition) before.listIndex else 0,
            listOffset = if (restorePosition) before.listOffset else 0)
        persist()
        searchJob = viewModelScope.launch {
            try {
                var page = gateway.page(targetBookId, query, if (append) before.cursor else null)
                val results = (if (append) before.results else emptyList()).toMutableList()
                results += page.results
                while (restorePosition && page.hasMore && results.size <= before.listIndex) {
                    ensureActive()
                    page = gateway.page(targetBookId, query, page.cursor)
                    results += page.results
                }
                ensureActive()
                if (version == requestVersion && bookId == targetBookId && mutableState.value.query == query) {
                    mutableState.value = mutableState.value.copy(results = results.distinctBy { it.chapterId to it.charOffset },
                        searching = false, hasSearched = true, failed = false, hasMore = page.hasMore,
                        complete = page.complete, cursor = page.cursor)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == requestVersion && bookId == targetBookId) {
                    mutableState.value = mutableState.value.copy(searching = false, hasSearched = true, failed = true,
                        hasMore = if (error is SearchCursorExpiredException) false else before.hasMore,
                        cursor = if (error is SearchCursorExpiredException) null else before.cursor, complete = false)
                }
            }
        }
    }

    fun cancelSearch() {
        requestVersion++
        searchJob?.cancel()
        searchJob = null
        mutableState.value = mutableState.value.copy(searching = false)
    }
    private fun persist() {
        val value = mutableState.value
        savedState["searchBook"] = bookId
        savedState["searchQuery"] = value.query
        savedState["originBook"] = value.origin?.bookId
        savedState["originChapter"] = value.origin?.chapterId
        savedState["originOffset"] = value.origin?.charOffset
        savedState["listIndex"] = value.listIndex
        savedState["listOffset"] = value.listOffset
    }
}
