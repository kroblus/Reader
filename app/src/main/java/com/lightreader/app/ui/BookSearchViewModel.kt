package com.lightreader.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.core.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun interface BookSearchGateway {
    suspend fun search(bookId: String, query: String): List<SearchResult>
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
    val failed: Boolean = false,
)

/** Owns a single book's search session, independently of reader pagination and navigation. */
class BookSearchViewModel(
    private var bookId: String,
    private val gateway: BookSearchGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var requestVersion = 0L

    fun isForBook(value: String): Boolean = bookId == value

    /** Keep one bounded search session; switching books discards the previous results. */
    fun openBook(value: String) {
        if (value == bookId) return
        cancelSearch()
        bookId = value
        mutableState.value = SearchUiState()
    }

    fun updateQuery(value: String) {
        if (value == mutableState.value.query) return
        cancelSearch()
        mutableState.value = SearchUiState(query = value)
    }

    fun clearQuery() {
        cancelSearch()
        mutableState.value = SearchUiState()
    }

    fun search() {
        val query = mutableState.value.query.trim()
        if (query.isBlank()) return
        cancelSearch()
        val version = requestVersion
        val targetBookId = bookId
        mutableState.value = SearchUiState(query = query, searching = true)
        searchJob = viewModelScope.launch {
            try {
                val results = gateway.search(targetBookId, query)
                ensureActive()
                if (version == requestVersion) {
                    mutableState.value = SearchUiState(query = query, results = results, hasSearched = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == requestVersion) {
                    mutableState.value = SearchUiState(query = query, hasSearched = true, failed = true)
                }
            }
        }
    }

    /** Invalidate results even when a slow source does not cooperate with cancellation. */
    fun cancelSearch() {
        requestVersion++
        searchJob?.cancel()
        searchJob = null
        mutableState.value = mutableState.value.copy(searching = false)
    }
}
