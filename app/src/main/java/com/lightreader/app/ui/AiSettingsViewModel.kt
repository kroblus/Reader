package com.lightreader.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.settings.AiConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Keeps API-key and AI fallback configuration out of the reader session lifecycle. */
class AiSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ReaderApplication).container
    private val messagesChannel = Channel<UiText>(Channel.BUFFERED)

    val messages = messagesChannel.receiveAsFlow()
    private val mutableState = MutableStateFlow(
        ApiSettingsUiState(
            hasApiKey = container.keyStore.hasKey(),
            configuration = container.aiConfigurationStore.get(),
        ),
    )
    val state = mutableState.asStateFlow()
    private val mutableBusy = MutableStateFlow(BusyState())
    val busy = mutableBusy.asStateFlow()
    private var connectionJob: Job? = null
    private var testVersion = 0L

    fun updateApiKeyDraft(value: String) {
        mutableState.value = state.value.copy(keyDraft = value)
    }

    fun toggleApiKeyVisibility() {
        mutableState.value = state.value.copy(showKey = !state.value.showKey)
    }

    fun toggleAiAdvancedSettings() {
        mutableState.value = state.value.copy(advancedExpanded = !state.value.advancedExpanded)
    }

    fun updateAiConfigurationDraft(value: AiConfiguration) {
        cancelConnectionTest()
        mutableState.value = state.value.copy(configuration = value, connectionSucceeded = false)
    }

    fun saveApiKeyDraft() {
        val key = state.value.keyDraft.trim()
        if (key.isBlank()) return
        cancelConnectionTest()
        runCatching { container.keyStore.save(key) }
            .onSuccess {
                mutableState.value = state.value.copy(hasApiKey = true, keyDraft = "", showKey = false, connectionSucceeded = false)
                post(R.string.message_api_key_saved)
            }
            .onFailure { post(R.string.message_api_key_save_failed) }
    }

    fun deleteApiKey() {
        cancelConnectionTest()
        container.keyStore.clear()
        mutableState.value = state.value.copy(hasApiKey = false, connectionSucceeded = false)
        post(R.string.message_api_key_deleted)
    }

    fun setAiEnabled(enabled: Boolean) {
        container.aiConfigurationStore.save(container.aiConfigurationStore.get().copy(enabled = enabled))
        mutableState.value = state.value.copy(configuration = state.value.configuration.copy(enabled = enabled))
    }

    fun cancelConnectionTest() {
        testVersion++
        connectionJob?.cancel()
        connectionJob = null
        mutableBusy.value = BusyState()
    }

    fun testApiKey() {
        if (connectionJob?.isActive == true || !state.value.hasApiKey) return
        val version = ++testVersion
        connectionJob = viewModelScope.launch {
            mutableBusy.value = BusyState(active = true, message = uiText(R.string.busy_testing_connection))
            try {
                val connected = container.aiProvider.testConnection()
                if (version == testVersion) {
                    mutableState.value = state.value.copy(connectionSucceeded = connected)
                    post(if (connected) R.string.message_deepseek_connected else R.string.message_deepseek_failed)
                }
            } finally {
                if (version == testVersion) mutableBusy.value = BusyState()
            }
        }
    }

    fun saveAiConfiguration() {
        cancelConnectionTest()
        val configuration = state.value.configuration
        runCatching { container.aiConfigurationStore.save(configuration) }
            .onSuccess {
                mutableState.value = state.value.copy(configuration = container.aiConfigurationStore.get(), connectionSucceeded = false)
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
    val connectionSucceeded: Boolean = false,
    val configuration: AiConfiguration = AiConfiguration(),
)
