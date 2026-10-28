package com.lightreader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightreader.app.R
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.feature.download.DownloadViewModel
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebImportScreen(
    tasks: List<DownloadTaskEntity>,
    viewModel: DownloadViewModel,
    onBack: () -> Unit,
    onOpenDownloadedBook: (String) -> Unit,
) {
    val state by viewModel.webState.collectAsStateWithLifecycle()
    var guideExpanded by remember { mutableStateOf(false) }
    var showPreviewChapters by remember { mutableStateOf(false) }
    var pendingTaskAction by remember { mutableStateOf<DownloadTaskAction?>(null) }
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val trimmedUrl = state.url.trim()
    val urlError = trimmedUrl.isNotBlank() && !trimmedUrl.startsWith("https://", ignoreCase = true)
    Scaffold(
        modifier = Modifier.testTag(ReaderTestTags.WEB_IMPORT),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.web_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            item {
                Text(
                    stringResource(R.string.web_import_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = {
                        showPreviewChapters = false
                        viewModel.updateWebUrl(it)
                    },
                    modifier = Modifier.fillMaxWidth().testTag(ReaderTestTags.WEB_IMPORT_URL),
                    label = { Text(stringResource(R.string.web_import_url_label)) },
                    placeholder = { Text(stringResource(R.string.web_import_url_example)) },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(viewModel::updateWebUrl)
                            },
                        ) {
                            Text(stringResource(R.string.web_import_paste))
                        }
                    },
                    isError = urlError,
                    supportingText = {
                        if (urlError) Text(stringResource(R.string.web_import_url_error))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (trimmedUrl.isNotBlank() && !urlError && !state.loading) viewModel.previewWebBook()
                        },
                    ),
                )
            }
            item {
                Button(
                    onClick = viewModel::previewWebBook,
                    enabled = trimmedUrl.isNotBlank() && !urlError && !state.loading,
                    modifier = Modifier.fillMaxWidth().testTag(ReaderTestTags.WEB_IMPORT_PARSE),
                ) {
                    Text(stringResource(R.string.web_import_parse))
                }
            }
            if (state.loading) {
                item { WebImportLoadingCard() }
            }
            state.error?.let { error ->
                item {
                    WebImportErrorCard(
                        message = error.toUiText().asString(),
                        canOpenBrowser = !urlError && trimmedUrl.isNotBlank(),
                        onRetry = viewModel::previewWebBook,
                        onOpenBrowser = { runCatching { uriHandler.openUri(trimmedUrl) } },
                        onChangeUrl = {
                            showPreviewChapters = false
                            viewModel.updateWebUrl("")
                        },
                    )
                }
            }
            state.preview?.let { preview ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(preview.title, style = MaterialTheme.typography.titleLarge)
                            preview.author?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.web_import_author, it)) }
                            Text(stringResource(R.string.web_import_source, sourceDomain(preview.finalUrl)))
                            Text(pluralStringResource(R.plurals.web_import_chapter_count, preview.chapters.size, preview.chapters.size))
                            PreviewWarnings(preview)
                            preview.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            Text(stringResource(R.string.web_import_top_chapters), style = MaterialTheme.typography.titleSmall)
                            preview.chapters.take(if (showPreviewChapters) 20 else 3).forEachIndexed { index, chapter ->
                                Text("${index + 1}. ${chapter.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(stringResource(R.string.web_import_sample), style = MaterialTheme.typography.titleSmall)
                            Text(preview.sample, maxLines = 8, overflow = TextOverflow.Ellipsis)
                            Button(
                                onClick = viewModel::startWebDownload,
                                enabled = preview.chapters.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.web_import_download_book))
                            }
                            OutlinedButton(
                                onClick = { showPreviewChapters = !showPreviewChapters },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(
                                        if (showPreviewChapters) R.string.web_import_hide_contents else R.string.web_import_view_contents,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            item { ImportGuideCard(expanded = guideExpanded, onToggle = { guideExpanded = !guideExpanded }) }
            if (tasks.isNotEmpty()) {
                item { Text(stringResource(R.string.web_import_tasks), style = MaterialTheme.typography.titleMedium) }
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        viewModel = viewModel,
                        onOpenDownloadedBook = onOpenDownloadedBook,
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
private fun WebImportLoadingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .64f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.web_import_loading_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.web_import_loading_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WebImportErrorCard(
    message: String,
    canOpenBrowser: Boolean,
    onRetry: () -> Unit,
    onOpenBrowser: () -> Unit,
    onChangeUrl: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(ReaderTestTags.WEB_IMPORT_ERROR),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.web_import_error_title),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.web_import_retry))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenBrowser,
                    enabled = canOpenBrowser,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.web_import_open_browser))
                }
                OutlinedButton(onClick = onChangeUrl, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.web_import_change_url))
                }
            }
        }
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
        if (preview.extractionPlan.chapterLinkSelector == "a[href]" || preview.parseWarnings.isNotEmpty()) add(broadSelector)
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
    viewModel: DownloadViewModel,
    onOpenDownloadedBook: (String) -> Unit,
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
                    Text(
                        stringResource(
                            R.string.web_import_task_progress,
                            task.completedChapters,
                            task.totalChapters,
                            task.statusLabel(),
                            failedSuffix,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                when (task.status) {
                    "DOWNLOADING", "QUEUED" -> IconButton(onClick = { viewModel.pauseDownload(task.id) }) {
                        Icon(Icons.Outlined.Pause, stringResource(R.string.action_pause))
                    }
                    "PAUSED", "FAILED" -> IconButton(onClick = { viewModel.resumeDownload(task.id) }) {
                        Icon(Icons.Outlined.PlayArrow, stringResource(R.string.action_resume_or_retry))
                    }
                    "COMPLETED" -> IconButton(onClick = { onOpenDownloadedBook(task.importedBookId ?: task.id) }) {
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
                val fallback = stringResource(R.string.web_import_task_error_fallback)
                TextButton(onClick = { showErrorDetail = !showErrorDetail }) {
                    Text(stringResource(if (showErrorDetail) R.string.web_import_hide_error else R.string.web_import_show_error))
                }
                if (showErrorDetail) {
                    Text(userFacingDownloadError(it, fallback), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun sourceDomain(url: String): String = runCatching {
    URI(url).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
}.getOrNull() ?: url

private fun userFacingDownloadError(raw: String, fallback: String): String {
    val normalized = raw.trim()
    val technicalMarker = Regex(
        "exception|stacktrace|java\\.|kotlin\\.|okhttp|worker|selector|parser|dom|\\.kt:\\d+",
        RegexOption.IGNORE_CASE,
    )
    return normalized.takeIf { it.isNotBlank() && it.length <= 240 && !technicalMarker.containsMatchIn(it) } ?: fallback
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
