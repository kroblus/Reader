package com.lightreader.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lightreader.app.R
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.SearchResult

@Composable
fun SearchRoute(bookId: String, readerViewModel: ReaderViewModel, searchViewModel: BookSearchViewModel) {
    // Read the snapshot even before the navigation effect binds this book. A delegated value
    // read only inside the matching-book branch would not subscribe this route to later updates.
    val state = searchViewModel.state.collectAsStateWithLifecycle().value
    val readerState by readerViewModel.readerState.collectAsStateWithLifecycle()
    SearchScreen(
        state = if (searchViewModel.isForBook(bookId)) state else SearchUiState(),
        chapters = readerState.chapters,
        // Input may arrive before the shell's navigation effect; bind the session synchronously.
        onQueryChange = { query ->
            searchViewModel.openBook(bookId)
            searchViewModel.updateQuery(query)
        },
        onClear = {
            searchViewModel.openBook(bookId)
            searchViewModel.clearQuery()
        },
        onSearch = {
            searchViewModel.openBook(bookId)
            searchViewModel.search()
        },
        onBack = readerViewModel::goBack,
        onResultClick = readerViewModel::jumpToSearchResult,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    chapters: List<Chapter>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(ReaderTestTags.SEARCH),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().testTag(ReaderTestTags.SEARCH_INPUT),
                    label = { Text(stringResource(R.string.search_keyword)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotBlank()) {
                            IconButton(onClick = onClear) {
                                Icon(Icons.Outlined.Clear, stringResource(R.string.action_clear_search))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
                Button(
                    onClick = onSearch,
                    enabled = state.query.isNotBlank() && !state.searching,
                    modifier = Modifier.fillMaxWidth().testTag(ReaderTestTags.SEARCH_SUBMIT),
                ) {
                    Text(stringResource(R.string.action_search))
                }
            }
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            val resultsLabel = when {
                state.searching -> stringResource(R.string.searching)
                state.failed -> stringResource(R.string.message_search_failed)
                state.hasSearched -> pluralStringResource(R.plurals.search_results_found, state.results.size, state.results.size)
                else -> stringResource(R.string.search_prompt)
            }
            Text(resultsLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.searching && state.query.isBlank()) {
                    item {
                        EmptyStateCard(
                            stringResource(R.string.search_empty_title),
                            stringResource(R.string.search_empty_body),
                            Modifier.testTag(ReaderTestTags.SEARCH_EMPTY),
                        )
                    }
                } else if (state.failed) {
                    item {
                        EmptyStateCard(
                            stringResource(R.string.message_search_failed),
                            stringResource(R.string.search_failed_body),
                            Modifier.testTag(ReaderTestTags.SEARCH_ERROR),
                        )
                    }
                } else if (!state.searching && state.hasSearched && state.results.isEmpty()) {
                    item {
                        EmptyStateCard(
                            stringResource(R.string.search_no_result_title),
                            stringResource(R.string.search_no_result_body),
                            Modifier.testTag(ReaderTestTags.SEARCH_NO_RESULTS),
                        )
                    }
                }
                items(state.results, key = { "${it.chapterId}:${it.charOffset}" }) { result ->
                    SearchResultCard(
                        result = result,
                        chapters = chapters,
                        onClick = { onResultClick(result) },
                        modifier = Modifier.testTag(ReaderTestTags.SEARCH_RESULT),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, chapters: List<Chapter>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val chapter = chapters.firstOrNull { it.id == result.chapterId }
    val positionLabel = chapter?.let {
        stringResource(R.string.search_chapter_position, it.orderIndex + 1, chapterProgress(result, it))
    } ?: stringResource(R.string.search_char_position, result.charOffset)
    val highlightColor = MaterialTheme.colorScheme.primary
    Card(modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(result.chapterTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(highlightedExcerpt(result.excerpt, highlightColor), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(positionLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun highlightedExcerpt(raw: String, highlightColor: Color) = buildAnnotatedString {
    var cursor = 0
    val pattern = Regex("<b>(.*?)</b>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    pattern.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) { append(match.groupValues[1]) }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

private fun chapterProgress(result: SearchResult, chapter: Chapter): Int =
    ((result.charOffset.toFloat() / chapter.charCount.coerceAtLeast(1)) * 100f).toInt().coerceIn(0, 100)
