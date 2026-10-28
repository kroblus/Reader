package com.lightreader.app.ui

import com.lightreader.app.core.model.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun latestQueryWinsEvenIfOldSourceIgnoresCancellation() = runTest(dispatcher) {
        val oldResult = CompletableDeferred<List<SearchResult>>()
        val model = BookSearchViewModel("book") { _, query ->
            if (query == "old") withContext(NonCancellable) { oldResult.await() }
            else listOf(result(query))
        }
        model.updateQuery("old")
        model.search()
        runCurrent()
        model.updateQuery("new")
        model.search()
        runCurrent()
        oldResult.complete(listOf(result("old")))
        advanceUntilIdle()
        assertEquals("new", model.state.value.query)
        assertEquals(listOf(result("new")), model.state.value.results)
        assertFalse(model.state.value.searching)
    }

    @Test fun clearingQueryDiscardsAnInFlightResult() = runTest(dispatcher) {
        val pending = CompletableDeferred<List<SearchResult>>()
        val model = BookSearchViewModel("book") { _, _ -> withContext(NonCancellable) { pending.await() } }
        model.updateQuery("old")
        model.search()
        runCurrent()
        model.clearQuery()
        pending.complete(listOf(result("old")))
        advanceUntilIdle()
        assertEquals(SearchUiState(), model.state.value)
    }

    @Test fun leavingSearchCancelsLoadingWithoutReportingFailure() = runTest(dispatcher) {
        val pending = CompletableDeferred<List<SearchResult>>()
        val model = BookSearchViewModel("book") { _, _ -> pending.await() }
        model.updateQuery("word")
        model.search()
        runCurrent()
        model.cancelSearch()
        advanceUntilIdle()
        assertEquals(SearchUiState(query = "word"), model.state.value)
    }

    @Test fun editingQueryRemovesPreviouslyCompletedResults() = runTest(dispatcher) {
        val model = BookSearchViewModel("book") { _, query -> listOf(result(query)) }
        model.updateQuery("old")
        model.search()
        advanceUntilIdle()
        model.updateQuery("new")
        assertEquals(SearchUiState(query = "new"), model.state.value)
    }

    @Test fun failureIsDistinctFromNoMatchesAndCanBeRetried() = runTest(dispatcher) {
        var fail = true
        val model = BookSearchViewModel("book") { _, _ ->
            if (fail) error("unavailable") else emptyList()
        }
        model.updateQuery("word")
        model.search()
        advanceUntilIdle()
        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.searching)
        fail = false
        model.search()
        advanceUntilIdle()
        assertFalse(model.state.value.failed)
        assertTrue(model.state.value.hasSearched)
        assertTrue(model.state.value.results.isEmpty())
    }

    @Test fun queryIsTrimmedAndSentToTheCorrectBook() = runTest(dispatcher) {
        val requests = mutableListOf<Pair<String, String>>()
        val model = BookSearchViewModel("selected-book") { book, query ->
            requests += book to query
            emptyList()
        }
        model.updateQuery("  ")
        model.search()
        advanceUntilIdle()
        assertTrue(requests.isEmpty())
        model.updateQuery("  山中  ")
        model.search()
        advanceUntilIdle()
        assertEquals(listOf("selected-book" to "山中"), requests)
        assertEquals("山中", model.state.value.query)
    }

    @Test fun switchingBooksDiscardsAnInFlightResultAndOldQuery() = runTest(dispatcher) {
        val pending = CompletableDeferred<List<SearchResult>>()
        val model = BookSearchViewModel("first") { _, _ -> withContext(NonCancellable) { pending.await() } }
        model.updateQuery("old")
        model.search()
        runCurrent()
        model.openBook("second")
        pending.complete(listOf(result("old")))
        advanceUntilIdle()
        assertTrue(model.isForBook("second"))
        assertEquals(SearchUiState(), model.state.value)
    }

    @Test fun reconnectingTheSameRouteKeepsThePendingRequest() = runTest(dispatcher) {
        val pending = CompletableDeferred<List<SearchResult>>()
        val model = BookSearchViewModel("book") { _, _ -> pending.await() }
        model.updateQuery("word")
        model.search()
        runCurrent()
        model.openBook("book")
        assertTrue(model.state.value.searching)
        pending.complete(listOf(result("word")))
        advanceUntilIdle()
        assertEquals(listOf(result("word")), model.state.value.results)
    }

    private fun result(value: String) = SearchResult(1L, "Chapter", value, 10)
}
