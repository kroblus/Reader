package com.lightreader.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.lightreader.app.R
import com.lightreader.app.core.settings.AiConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(viewModel: AiSettingsViewModel, onBack: () -> Unit) {
    val api by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.cancelConnectionTest() } }
    val dismissTextInput = rememberDismissTextInput()
    var confirmDeleteKey by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag(ReaderTestTags.API_SETTINGS),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.api_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    stringResource(if (api.hasApiKey) R.string.api_settings_configured else R.string.api_settings_not_configured),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.web_ai_enable), Modifier.weight(1f))
                        Switch(checked = api.configuration.enabled, onCheckedChange = viewModel::setAiEnabled)
                    }
                    Text(stringResource(R.string.web_ai_disclosure, api.configuration.baseUrl, api.configuration.model),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(if (api.connectionSucceeded) R.string.web_ai_test_succeeded else R.string.web_ai_not_tested),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = api.keyDraft,
                        onValueChange = viewModel::updateApiKeyDraft,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(LightReaderShapeTokens.control),
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
                    Text(
                        stringResource(R.string.api_settings_security_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = viewModel::saveApiKeyDraft,
                        enabled = api.keyDraft.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.api_settings_save_key))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { dismissTextInput(); viewModel.testApiKey() },
                            enabled = api.hasApiKey && !busy.active,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.api_settings_test_connection))
                        }
                        OutlinedButton(
                            onClick = { dismissTextInput(); confirmDeleteKey = true },
                            enabled = api.hasApiKey,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.api_settings_delete_key))
                        }
                    }
                }
            }
            TextButton(onClick = viewModel::toggleAiAdvancedSettings) {
                Text(stringResource(if (api.advancedExpanded) R.string.api_settings_hide_advanced else R.string.api_settings_show_advanced))
                Icon(if (api.advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (api.advancedExpanded) {
                AiAdvancedSettings(api.configuration, viewModel)
            }
            TextButton(onClick = { securityExpanded = !securityExpanded }) {
                Text(
                    stringResource(
                        if (securityExpanded) R.string.api_settings_hide_security else R.string.api_settings_show_security,
                    ),
                )
                Icon(if (securityExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (securityExpanded) {
                Text(
                    stringResource(R.string.api_settings_privacy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.api_settings_usage_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (confirmDeleteKey) {
        ActionAlertDialog(
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
private fun AiAdvancedSettings(configuration: AiConfiguration, viewModel: AiSettingsViewModel) {
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
