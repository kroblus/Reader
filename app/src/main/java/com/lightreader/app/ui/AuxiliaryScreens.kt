package com.lightreader.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lightreader.app.R
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.SearchResult
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.settings.AiConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val state by viewModel.searchState.collectAsState()
    val readerState by viewModel.readerState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.search_keyword)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotBlank()) {
                            IconButton(onClick = viewModel::clearSearchQuery) {
                                Icon(Icons.Outlined.Clear, stringResource(R.string.action_clear_search))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                )
                Button(onClick = viewModel::search, enabled = state.query.isNotBlank() && !state.searching) {
                    Text(stringResource(R.string.action_search))
                }
            }
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            val resultsLabel = when {
                state.searching -> stringResource(R.string.searching)
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
                        EmptyStateCard(stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_body))
                    }
                } else if (!state.searching && state.hasSearched && state.results.isEmpty()) {
                    item {
                        EmptyStateCard(stringResource(R.string.search_no_result_title), stringResource(R.string.search_no_result_body))
                    }
                }
                items(state.results) { result ->
                    SearchResultCard(
                        result = result,
                        chapters = readerState.chapters,
                        onClick = { viewModel.jumpToSearchResult(result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResult, chapters: List<Chapter>, onClick: () -> Unit) {
    val chapter = chapters.firstOrNull { it.id == result.chapterId }
    val positionLabel = chapter?.let {
        stringResource(R.string.search_chapter_position, it.orderIndex + 1, chapterProgress(result, it))
    } ?: stringResource(R.string.search_char_position, result.charOffset)
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(result.chapterTitle, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(highlightedExcerpt(result.excerpt), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(positionLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun highlightedExcerpt(raw: String) = buildAnnotatedString {
    var cursor = 0
    val pattern = Regex("<b>(.*?)</b>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    pattern.findAll(raw).forEach { match ->
        append(raw.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFB35C35))) { append(match.groupValues[1]) }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) append(raw.substring(cursor))
}

private fun chapterProgress(result: SearchResult, chapter: Chapter): Int =
    ((result.charOffset.toFloat() / chapter.charCount.coerceAtLeast(1)) * 100f).toInt().coerceIn(0, 100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImportScreen(tasks: List<DownloadTaskEntity>, viewModel: MainViewModel) {
    val state by viewModel.webState.collectAsState()
    var guideExpanded by remember { mutableStateOf(false) }
    var pendingTaskAction by remember { mutableStateOf<DownloadTaskAction?>(null) }
    val trimmedUrl = state.url.trim()
    val urlError = trimmedUrl.isNotBlank() && !trimmedUrl.startsWith("https://", ignoreCase = true)
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.web_import_title)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ImportGuideCard(expanded = guideExpanded, onToggle = { guideExpanded = !guideExpanded }) }
            item {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::updateWebUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.web_import_url_label)) },
                    placeholder = { Text("https://example.com/book/123") },
                    isError = urlError,
                    supportingText = {
                        if (urlError) Text(stringResource(R.string.web_import_url_error))
                    },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
            item {
                Button(
                    onClick = viewModel::previewWebBook,
                    enabled = trimmedUrl.isNotBlank() && !urlError && !state.loading,
                ) {
                    Text(stringResource(R.string.web_import_parse))
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item { Text(error.asString(), color = MaterialTheme.colorScheme.error) } }
            state.preview?.let { preview ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(preview.title, style = MaterialTheme.typography.titleLarge)
                            preview.author?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.web_import_author, it)) }
                            Text(stringResource(R.string.web_import_source, preview.finalUrl))
                            Text(pluralStringResource(R.plurals.web_import_chapter_count, preview.chapters.size, preview.chapters.size))
                            PreviewWarnings(preview)
                            preview.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            if (preview.parseWarnings.isNotEmpty()) {
                                preview.parseWarnings.forEach { warning ->
                                    Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text(stringResource(R.string.web_import_top_chapters), style = MaterialTheme.typography.titleSmall)
                            preview.chapters.take(10).forEachIndexed { index, chapter ->
                                Text("${index + 1}. ${chapter.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(stringResource(R.string.web_import_sample), style = MaterialTheme.typography.titleSmall)
                            Text(preview.sample, maxLines = 8, overflow = TextOverflow.Ellipsis)
                            Button(onClick = viewModel::startWebDownload) { Text(stringResource(R.string.web_import_download_book)) }
                        }
                    }
                }
            }
            if (tasks.isNotEmpty()) {
                item { Text(stringResource(R.string.web_import_tasks), style = MaterialTheme.typography.titleMedium) }
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        viewModel = viewModel,
                        onConfirmAction = { pendingTaskAction = it },
                    )
                }
            }
        }
    }

    pendingTaskAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingTaskAction = null },
            title = { Text(stringResource(if (action.delete) R.string.web_import_delete_task_title else R.string.web_import_cancel_task_title)) },
            text = {
                Text(
                    stringResource(
                        if (action.delete) R.string.web_import_delete_task_message else R.string.web_import_cancel_task_message,
                        action.title,
                    ),
                )
            },
            dismissButton = { TextButton(onClick = { pendingTaskAction = null }) { Text(stringResource(R.string.action_keep)) } },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (action.delete) viewModel.deleteDownload(action.id) else viewModel.cancelDownload(action.id)
                        pendingTaskAction = null
                    },
                ) { Text(stringResource(if (action.delete) R.string.action_delete else R.string.web_import_cancel_download)) }
            },
        )
    }
}

@Composable
private fun ImportGuideCard(expanded: Boolean, onToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .86f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.web_import_flow_title), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onToggle) {
                    Text(stringResource(if (expanded) R.string.action_collapse else R.string.action_expand))
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
            }
            Text(stringResource(R.string.web_import_flow_steps), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Text(
                    stringResource(R.string.web_import_flow_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PreviewWarnings(preview: WebBookPreview) {
    val noChapters = stringResource(R.string.web_import_warning_no_chapters)
    val fewChapters = stringResource(R.string.web_import_warning_few_chapters)
    val broadSelector = stringResource(R.string.web_import_warning_broad_selector)
    val warnings = buildList {
        if (preview.chapters.isEmpty()) add(noChapters)
        if (preview.chapters.size in 1..3) add(fewChapters)
        if (preview.extractionPlan.chapterLinkSelector == "a[href]") add(broadSelector)
        addAll(preview.parseWarnings)
    }
    if (warnings.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = .62f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            warnings.distinct().forEach { warning ->
                Text(warning, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    viewModel: MainViewModel,
    onConfirmAction: (DownloadTaskAction) -> Unit,
) {
    var showErrorDetail by remember(task.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                    task.author?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val failedSuffix = if (task.failedChapters > 0) stringResource(R.string.web_import_failed_suffix, task.failedChapters) else ""
                    Text("${task.completedChapters}/${task.totalChapters} · ${task.statusLabel()}$failedSuffix", style = MaterialTheme.typography.labelMedium)
                }
                when (task.status) {
                    "DOWNLOADING", "QUEUED" -> IconButton(onClick = { viewModel.pauseDownload(task.id) }) {
                        Icon(Icons.Outlined.Pause, stringResource(R.string.action_pause))
                    }
                    "PAUSED", "FAILED" -> IconButton(onClick = { viewModel.resumeDownload(task.id) }) {
                        Icon(Icons.Outlined.PlayArrow, stringResource(R.string.action_resume_or_retry))
                    }
                    "COMPLETED" -> IconButton(onClick = { viewModel.openDownloadedBook(task.importedBookId ?: task.id) }) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.action_open_book))
                    }
                }
                if (task.status == "DOWNLOADING" || task.status == "QUEUED" || task.status == "PAUSED" || task.status == "FAILED") {
                    TextButton(onClick = { onConfirmAction(DownloadTaskAction(task.id, task.title, delete = false)) }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                if (task.status == "COMPLETED" || task.status == "CANCELED" || task.status == "FAILED") {
                    IconButton(onClick = { onConfirmAction(DownloadTaskAction(task.id, task.title, delete = true)) }) {
                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.action_delete_task))
                    }
                }
            }
            LinearProgressIndicator(
                progress = { if (task.totalChapters == 0) 0f else task.completedChapters.toFloat() / task.totalChapters },
                modifier = Modifier.fillMaxWidth(),
            )
            task.error?.let {
                TextButton(onClick = { showErrorDetail = !showErrorDetail }) {
                    Text(stringResource(if (showErrorDetail) R.string.web_import_hide_error else R.string.web_import_show_error))
                }
                if (showErrorDetail) Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(viewModel: MainViewModel) {
    val web by viewModel.webState.collectAsState()
    val api by viewModel.apiSettingsState.collectAsState()
    var confirmDeleteKey by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.api_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.api_settings_privacy))
            Text(
                stringResource(if (web.hasApiKey) R.string.api_settings_configured else R.string.api_settings_not_configured),
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = api.keyDraft,
                onValueChange = viewModel::updateApiKeyDraft,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_settings_key_label)) },
                visualTransformation = if (api.showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleApiKeyVisibility) {
                        Icon(
                            if (api.showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            stringResource(if (api.showKey) R.string.api_settings_hide_key else R.string.api_settings_show_key),
                        )
                    }
                },
                singleLine = true,
            )
            Button(onClick = viewModel::saveApiKeyDraft, enabled = api.keyDraft.isNotBlank()) {
                Text(stringResource(R.string.api_settings_save_key))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::testApiKey, enabled = web.hasApiKey) { Text(stringResource(R.string.api_settings_test_connection)) }
                OutlinedButton(onClick = { confirmDeleteKey = true }, enabled = web.hasApiKey) { Text(stringResource(R.string.api_settings_delete_key)) }
            }
            TextButton(onClick = viewModel::toggleAiAdvancedSettings) {
                Text(stringResource(if (api.advancedExpanded) R.string.api_settings_hide_advanced else R.string.api_settings_show_advanced))
                Icon(if (api.advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (api.advancedExpanded) {
                AiAdvancedSettings(api.configuration, viewModel)
            }
            Text(stringResource(R.string.api_settings_usage_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDeleteKey) {
        AlertDialog(
            onDismissRequest = { confirmDeleteKey = false },
            title = { Text(stringResource(R.string.api_settings_delete_title)) },
            text = { Text(stringResource(R.string.api_settings_delete_message)) },
            dismissButton = { TextButton(onClick = { confirmDeleteKey = false }) { Text(stringResource(R.string.action_keep)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteApiKey(); confirmDeleteKey = false }) { Text(stringResource(R.string.action_delete)) }
            },
        )
    }
}

@Composable
private fun AiAdvancedSettings(configuration: AiConfiguration, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = configuration.baseUrl,
            onValueChange = { viewModel.updateAiConfigurationDraft(configuration.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.api_settings_base_url)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = configuration.model,
            onValueChange = { viewModel.updateAiConfigurationDraft(configuration.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.api_settings_model)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = configuration.timeoutSeconds.toString(),
            onValueChange = { raw -> raw.toIntOrNull()?.let { viewModel.updateAiConfigurationDraft(configuration.copy(timeoutSeconds = it)) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.api_settings_timeout)) },
            singleLine = true,
        )
        OutlinedButton(onClick = { viewModel.saveAiConfiguration() }) { Text(stringResource(R.string.api_settings_save_advanced)) }
    }
}

@Composable
private fun EmptyStateCard(title: String, body: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .56f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class DownloadTaskAction(val id: String, val title: String, val delete: Boolean)

@Composable
private fun DownloadTaskEntity.statusLabel(): String = when (status) {
    "QUEUED" -> stringResource(R.string.download_status_queued)
    "DOWNLOADING" -> stringResource(R.string.download_status_downloading)
    "PAUSED" -> stringResource(R.string.download_status_paused)
    "FAILED" -> stringResource(R.string.download_status_failed)
    "COMPLETED" -> stringResource(R.string.download_status_completed)
    "CANCELED" -> stringResource(R.string.download_status_canceled)
    else -> status
}
