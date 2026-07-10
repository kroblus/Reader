package com.lightreader.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.settings.AiConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Keeps API-key and AI fallback configuration out of the reader session lifecycle. */
class AiSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ReaderApplication).container
    private val messagesChannel = Channel<UiText>(Channel.BUFFERED)

    val messages = messagesChannel.receiveAsFlow()
    val state = MutableStateFlow(
        ApiSettingsUiState(
            hasApiKey = container.keyStore.hasKey(),
            configuration = container.aiConfigurationStore.get(),
        ),
    )
    val busy = MutableStateFlow(BusyState())

    fun updateApiKeyDraft(value: String) {
        state.value = state.value.copy(keyDraft = value)
    }

    fun toggleApiKeyVisibility() {
        state.value = state.value.copy(showKey = !state.value.showKey)
    }

    fun toggleAiAdvancedSettings() {
        state.value = state.value.copy(advancedExpanded = !state.value.advancedExpanded)
    }

    fun updateAiConfigurationDraft(value: AiConfiguration) {
        state.value = state.value.copy(configuration = value)
    }

    fun saveApiKeyDraft() {
        val key = state.value.keyDraft.trim()
        if (key.isBlank()) return
        runCatching { container.keyStore.save(key) }
            .onSuccess {
                state.value = state.value.copy(hasApiKey = true, keyDraft = "", showKey = false)
                post(R.string.message_api_key_saved)
            }
            .onFailure { post(R.string.message_api_key_save_failed) }
    }

    fun deleteApiKey() {
        container.keyStore.clear()
        state.value = state.value.copy(hasApiKey = false)
        post(R.string.message_api_key_deleted)
    }

    fun testApiKey() {
        viewModelScope.launch {
            busy.value = BusyState(active = true, message = uiText(R.string.busy_testing_connection))
            val connected = runCatching { container.aiProvider.testConnection() }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    false
                }
            post(if (connected) R.string.message_deepseek_connected else R.string.message_deepseek_failed)
            busy.value = BusyState()
        }
    }

    fun saveAiConfiguration() {
        val configuration = state.value.configuration
        runCatching { container.aiConfigurationStore.save(configuration) }
            .onSuccess {
                state.value = state.value.copy(configuration = container.aiConfigurationStore.get())
                post(R.string.message_ai_advanced_saved)
            }
            .onFailure { post(R.string.message_ai_advanced_save_failed) }
    }

    private fun post(resId: Int, vararg args: Any) {
        messagesChannel.trySend(uiText(resId, *args))
    }
}

data class ApiSettingsUiState(
    val keyDraft: String = "",
    val showKey: Boolean = false,
    val advancedExpanded: Boolean = false,
    val hasApiKey: Boolean = false,
    val configuration: AiConfiguration = AiConfiguration(),
)
