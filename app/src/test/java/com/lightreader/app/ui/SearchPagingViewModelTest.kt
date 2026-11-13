package com.lightreader.app.ui

import androidx.lifecycle.SavedStateHandle
import com.lightreader.app.core.data.SearchCursor
import com.lightreader.app.core.data.SearchPage
import com.lightreader.app.core.model.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchPagingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun continuationIsExplicitAndRetainsPreviousMatches() = runTest(dispatcher) {
        val gateway = object : BookSearchGateway {
            override suspend fun search(bookId: String, query: String) = emptyList<SearchResult>()
            override suspend fun page(bookId: String, query: String, cursor: SearchCursor?): SearchPage =
                if (cursor == null) SearchPage(listOf(result(0)), true, SearchCursor(bookId, query, "v1", 0, 10), false)
                else SearchPage(listOf(result(10)), false, null, true)
        }
        val vm = BookSearchViewModel("book", gateway)
        vm.updateQuery("needle"); vm.search(); advanceUntilIdle()
        assertFalse(vm.state.value.complete)
        assertTrue(vm.state.value.hasMore)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(0, 10), vm.state.value.results.map { it.charOffset })
        assertTrue(vm.state.value.complete)
        assertFalse(vm.state.value.hasMore)
    }

    @Test fun oldContinuationCannotOverwriteChangedQueryOrOriginBook() = runTest(dispatcher) {
        val pending = CompletableDeferred<SearchPage>()
        val gateway = object : BookSearchGateway {
            override suspend fun search(bookId: String, query: String) = emptyList<SearchResult>()
            override suspend fun page(bookId: String, query: String, cursor: SearchCursor?): SearchPage =
                if (cursor == null) SearchPage(listOf(result(0)), true, SearchCursor(bookId, query, "v1", 0, 10), false)
                else withContext(NonCancellable) { pending.await() }
        }
        val vm = BookSearchViewModel("book", gateway)
        vm.captureOrigin(SearchOrigin("book", 2, 123))
        vm.updateQuery("old"); vm.search(); advanceUntilIdle()
        vm.loadMore(); runCurrent(); vm.openBook("other")
        pending.complete(SearchPage(listOf(result(10)), false, null, true)); advanceUntilIdle()
        assertEquals(SearchUiState(), vm.state.value)
        assertTrue(vm.isForBook("other"))
    }

    @Test fun smallSavedStateReconstructsResultPagesAndOriginalAnchor() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val gateway = object : BookSearchGateway {
            override suspend fun search(bookId: String, query: String) = emptyList<SearchResult>()
            override suspend fun page(bookId: String, query: String, cursor: SearchCursor?): SearchPage =
                if (cursor == null) SearchPage(listOf(result(0)), true, SearchCursor(bookId, query, "v1", 0, 10), false)
                else SearchPage(listOf(result(10)), false, null, true)
        }
        val before = BookSearchViewModel("book", gateway, handle)
        before.captureOrigin(SearchOrigin("book", 2, 456))
        before.updateQuery("needle"); before.search(); advanceUntilIdle(); before.loadMore(); advanceUntilIdle()
        before.updateListPosition(1, 12)
        assertFalse(handle.keys().any { it.contains("results", true) || it.contains("text", true) })
        val after = BookSearchViewModel("", gateway, handle)
        after.restoreResultsIfNeeded(); advanceUntilIdle()
        assertEquals(2, after.state.value.results.size)
        assertEquals(SearchOrigin("book", 2, 456), after.state.value.origin)
        assertEquals(1, after.state.value.listIndex)
        assertEquals(12, after.state.value.listOffset)
        after.openBook("different")
        assertNull(after.state.value.origin)
    }
    private fun result(offset: Int) = SearchResult(2, "Chapter", "needle", offset)
}
