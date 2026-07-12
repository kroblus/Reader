package com.lightreader.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.model.AppLanguage
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Owns persisted reader and application preferences, independently of the reading session. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = (application as ReaderApplication).container.settingsRepository
    private val messagesChannel = Channel<UiText>(Channel.BUFFERED)

    val messages = messagesChannel.receiveAsFlow()
    fun savePreferences(preferences: ReaderPreferences) {
        val normalized = if (preferences.theme != ReaderTheme.NIGHT) {
            preferences.copy(lastNonNightTheme = preferences.theme)
        } else {
            preferences
        }
        viewModelScope.launch { settingsRepository.save(normalized) }
    }

    fun saveAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(appLanguage = language) }
        }
    }

    fun toggleDeveloperTools() {
        viewModelScope.launch {
            val updated = settingsRepository.update {
                it.copy(developerToolsEnabled = !it.developerToolsEnabled)
            }
            messagesChannel.send(
                uiText(
                    if (updated.developerToolsEnabled) {
                        R.string.message_developer_tools_enabled
                    } else {
                        R.string.message_developer_tools_disabled
                    },
                ),
            )
        }
    }

    fun toggleNightMode() {
        viewModelScope.launch {
            settingsRepository.update(::toggleNightTheme)
        }
    }
}

internal fun toggleNightTheme(current: ReaderPreferences): ReaderPreferences =
    if (current.theme == ReaderTheme.NIGHT) {
        current.copy(
            theme = current.lastNonNightTheme.takeUnless { it == ReaderTheme.NIGHT } ?: ReaderTheme.EYE_CARE,
        )
    } else {
        current.copy(lastNonNightTheme = current.theme, theme = ReaderTheme.NIGHT)
    }
