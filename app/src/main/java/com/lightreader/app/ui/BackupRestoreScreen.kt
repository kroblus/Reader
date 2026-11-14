package com.lightreader.app.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.backup.RestorePreview
import com.lightreader.app.core.backup.RestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupUiState(
    val busy: Boolean = false, val preview: RestorePreview? = null, val result: RestoreResult? = null,
    val exportedBooks: Int? = null, val error: String? = null,
)

class BackupRestoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ReaderApplication).container.backupRepository
    private val mutableState = MutableStateFlow(BackupUiState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var operationVersion = 0L
    private var cancelling = false
    fun export(uri: Uri) = runOperation { BackupUiState(exportedBooks = repository.export(uri)) }
    fun inspect(uri: Uri) = runOperation {
        val previousPreview = mutableState.value.preview
        mutableState.value = mutableState.value.copy(preview = null)
        repository.discard(previousPreview)
        BackupUiState(preview = repository.preview(uri))
    }
    fun restore(settings: Boolean) {
        val preview = mutableState.value.preview ?: return
        runOperation { BackupUiState(result = repository.restore(preview, settings)) }
    }
    fun cancel() {
        if (cancelling) return
        cancelling = true
        val previous = operation
        operationVersion++
        val preview = mutableState.value.preview
        mutableState.value = mutableState.value.copy(busy = true)
        operation = viewModelScope.launch {
            try {
                previous?.cancelAndJoin()
                repository.discard(preview)
                mutableState.value = BackupUiState()
            } finally {
                cancelling = false
            }
        }
    }
    private fun runOperation(block: suspend () -> BackupUiState) {
        if (mutableState.value.busy) return
        val version = ++operationVersion
        mutableState.value = mutableState.value.copy(busy = true, error = null, result = null, exportedBooks = null)
        operation = viewModelScope.launch {
            try { val result = block(); if (version == operationVersion) mutableState.value = result }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (version == operationVersion) mutableState.value = mutableState.value.copy(busy = false, error = error.message) }
            finally { if (version == operationVersion) mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(viewModel: BackupRestoreViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var restoreSettings by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(viewModel::export) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::inspect) }
    Scaffold(topBar = {
        TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = { Text(stringResource(R.string.backup_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { create.launch("LightReader-backup.zip") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_export))
                    }
                    OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
                }
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.backup_working))
                TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.action_cancel)) }
            }
            state.preview?.let { preview ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.backup_preview, preview.restorableBooks.size, preview.duplicateBookIds.size, preview.invalidBooks.size),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.backup_duplicate_rule))
                        preview.manifest.books.forEach { book ->
                            val status = when (book.id) {
                                in preview.invalidBooks -> R.string.backup_invalid
                                in preview.duplicateBookIds -> R.string.backup_duplicate
                                else -> R.string.backup_ready
                            }
                            Text("${book.title} · ${stringResource(status)}")
                        }
                        Row {
                            Checkbox(checked = restoreSettings, onCheckedChange = { restoreSettings = it }, enabled = !state.busy)
                            Text(stringResource(R.string.backup_settings), modifier = Modifier.padding(top = 12.dp))
                        }
                        Button(onClick = { viewModel.restore(restoreSettings) }, enabled = !state.busy && (preview.restorableBooks.isNotEmpty() || restoreSettings),
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_confirm)) }
                    }
                }
            }
            state.result?.let {
                Text(stringResource(R.string.backup_result, it.restored, it.skipped, it.invalid))
                if (it.settingsError != null) Text(stringResource(R.string.backup_settings_failed), color = MaterialTheme.colorScheme.error)
            }
            state.exportedBooks?.let { Text(stringResource(R.string.backup_exported, it)) }
            state.error?.let { Text(stringResource(R.string.backup_error, it), color = MaterialTheme.colorScheme.error) }
        }
    }
}
