package com.lightreader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lightreader.app.R
import com.lightreader.app.feature.download.DownloadViewModel
import kotlinx.coroutines.flow.collect

@Composable
fun ReaderApp(
    viewModel: ReaderViewModel,
    libraryViewModel: LibraryViewModel,
    downloadViewModel: DownloadViewModel,
    aiSettingsViewModel: AiSettingsViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val readerState by viewModel.readerState.collectAsState()
    val libraryState by libraryViewModel.state.collectAsState()
    val downloadTasks by downloadViewModel.tasks.collectAsState()
    val libraryBusy by libraryViewModel.busy.collectAsState()
    val downloadBusy by downloadViewModel.busy.collectAsState()
    val aiSettingsBusy by aiSettingsViewModel.busy.collectAsState()
    val activeBusy = when {
        state.busy.active -> state.busy
        libraryBusy.active -> libraryBusy
        downloadBusy.active -> downloadBusy.toBusyState()
        else -> aiSettingsBusy
    }
    val snackbar = remember { SnackbarHostState() }
    LocalizedApp(state.preferences.appLanguage) {
        LaunchedEffect(downloadViewModel) {
            downloadViewModel.events.collect { event -> viewModel.showMessage(event.toUiText()) }
        }
        LaunchedEffect(libraryViewModel) {
            libraryViewModel.events.collect { event ->
                when (event) {
                    is LibraryEvent.Message -> viewModel.showMessage(event.value)
                    is LibraryEvent.OpenBook -> viewModel.openBook(event.bookId)
                    is LibraryEvent.BookMetadataUpdated -> viewModel.updateOpenBookMetadata(event.book)
                }
            }
        }
        LaunchedEffect(aiSettingsViewModel) {
            aiSettingsViewModel.messages.collect(viewModel::showMessage)
        }
        LaunchedEffect(settingsViewModel) {
            settingsViewModel.messages.collect(viewModel::showMessage)
        }
        val messageText = state.message?.asString()
        LaunchedEffect(state.message, messageText) {
            messageText?.let {
                snackbar.showSnackbar(it)
                viewModel.clearMessage()
            }
        }
        BackHandler(enabled = state.screen !is AppScreen.Library) { viewModel.goBack() }
        LightReaderTheme(state.preferences.appSkin) {
            ApplyAppSystemBars(state.preferences.appSkin, state.screen is AppScreen.Reader)
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        when {
                            initialState is AppScreen.Library && targetState is AppScreen.Reader ->
                                (slideInHorizontally(animationSpec = tween(140)) { it / 10 } + fadeIn(tween(140)))
                                    .togetherWith(fadeOut(tween(90)))
                            initialState is AppScreen.Reader && targetState is AppScreen.Library ->
                                fadeIn(tween(120))
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(130)) { it / 10 } + fadeOut(tween(120)))
                            else -> fadeIn(tween(120)).togetherWith(fadeOut(tween(90)))
                        }
                    },
                    label = "readerScreenTransition",
                ) { screen ->
                    when (screen) {
                        AppScreen.Library -> LibraryScreen(
                            state = libraryState,
                            viewModel = libraryViewModel,
                            onOpenAppSettings = { viewModel.navigate(AppScreen.AppSettings) },
                            onSavePreferences = settingsViewModel::savePreferences,
                        )
                        is AppScreen.Reader -> ReaderScreen(
                            preferences = state.preferences,
                            viewModel = viewModel,
                            downloadViewModel = downloadViewModel,
                            settingsViewModel = settingsViewModel,
                        )
                        is AppScreen.ReaderSettingsDetail -> ReaderSettingsDetailScreen(
                            preferences = state.preferences,
                            autoReading = readerState.autoReading,
                            onChange = settingsViewModel::savePreferences,
                            onToggleAutoReading = viewModel::toggleAutoReading,
                            onBack = viewModel::goBack,
                        )
                        is AppScreen.Search -> SearchScreen(viewModel)
                        AppScreen.WebImport -> WebImportScreen(
                            tasks = downloadTasks,
                            viewModel = downloadViewModel,
                            onBack = viewModel::goBack,
                            onOpenDownloadedBook = viewModel::openDownloadedBook,
                        )
                        AppScreen.WebDomBridge -> WebDomBridgeScreen(viewModel)
                        AppScreen.ApiSettings -> ApiSettingsScreen(
                            viewModel = aiSettingsViewModel,
                            onBack = viewModel::goBack,
                        )
                        AppScreen.AppSettings -> AppSettingsScreen(
                            preferences = state.preferences,
                            readerViewModel = viewModel,
                            settingsViewModel = settingsViewModel,
                        )
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = if (state.screen is AppScreen.Reader) 104.dp else 8.dp),
                )
                if (activeBusy.active) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                            shadowElevation = 6.dp,
                        ) {
                            Column(
                                Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    activeBusy.message?.asString() ?: stringResource(R.string.busy_default),
                                    Modifier.padding(top = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
