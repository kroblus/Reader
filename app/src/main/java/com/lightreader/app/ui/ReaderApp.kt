package com.lightreader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lightreader.app.feature.download.DownloadViewModel
import kotlinx.coroutines.launch

@Composable
fun ReaderApp(
    viewModel: ReaderViewModel,
    libraryViewModel: LibraryViewModel,
    downloadViewModel: DownloadViewModel,
    aiSettingsViewModel: AiSettingsViewModel,
    settingsViewModel: SettingsViewModel,
    searchViewModel: BookSearchViewModel,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryBusy by libraryViewModel.busy.collectAsStateWithLifecycle()
    val downloadBusy by downloadViewModel.busy.collectAsStateWithLifecycle()
    val aiSettingsBusy by aiSettingsViewModel.busy.collectAsStateWithLifecycle()
    val activeBusy = when {
        state.busy.active -> state.busy
        libraryBusy.active -> libraryBusy
        downloadBusy.active -> downloadBusy.toBusyState()
        else -> aiSettingsBusy
    }
    val onCancelBusy: (() -> Unit)? =
        if (!state.busy.active && libraryBusy.active && libraryBusy.cancelable) libraryViewModel::cancelImport else null
    val snackbar = remember { SnackbarHostState() }
    val screenStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(searchViewModel, state.screen) {
        val search = state.screen as? AppScreen.Search
        if (search != null) searchViewModel.openBook(search.bookId) else searchViewModel.cancelSearch()
    }
    LocalizedApp(state.preferences.appLanguage) {
        LaunchedEffect(lifecycle, viewModel, downloadViewModel, libraryViewModel, aiSettingsViewModel, settingsViewModel) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    downloadViewModel.events.collect { event -> viewModel.showMessage(event.toUiText()) }
                }
                launch {
                    libraryViewModel.events.collect { event ->
                        when (event) {
                            is LibraryEvent.Message -> viewModel.showMessage(event.value)
                            is LibraryEvent.OpenBook -> viewModel.openBook(event.bookId)
                            is LibraryEvent.BookMetadataUpdated -> viewModel.updateOpenBookMetadata(event.book)
                        }
                    }
                }
                launch { aiSettingsViewModel.messages.collect(viewModel::showMessage) }
                launch { settingsViewModel.messages.collect(viewModel::showMessage) }
            }
        }
        val messageText = state.message?.asString()
        LaunchedEffect(lifecycle, state.message, messageText) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                messageText?.let {
                    snackbar.showSnackbar(it)
                    viewModel.clearMessage()
                }
            }
        }
        BackHandler(enabled = state.screen !is AppScreen.Library) { viewModel.goBack() }
        LightReaderTheme(state.preferences.appSkin) {
            ApplyAppSystemBars(state.preferences.appSkin, state.screen is AppScreen.Reader)
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                            else -> fadeIn(tween(100)).togetherWith(fadeOut(tween(70)))
                        }
                    },
                    label = "readerScreenTransition",
                ) { screen ->
                    when (screen) {
                        AppScreen.Library -> screenStateHolder.SaveableStateProvider("library") {
                            val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
                            LibraryScreen(
                                state = libraryState,
                                viewModel = libraryViewModel,
                                onOpenAppSettings = { viewModel.navigate(AppScreen.AppSettings) },
                                onOpenWebImport = { viewModel.navigate(AppScreen.WebImport) },
                                onSavePreferences = settingsViewModel::savePreferences,
                            )
                        }
                        is AppScreen.Reader -> ReaderScreen(
                            preferences = state.preferences,
                            viewModel = viewModel,
                            downloadViewModel = downloadViewModel,
                            settingsViewModel = settingsViewModel,
                        )
                        is AppScreen.ReaderSettingsDetail -> {
                            val readerState by viewModel.readerState.collectAsStateWithLifecycle()
                            ReaderSettingsDetailScreen(
                                preferences = state.preferences,
                                autoReading = readerState.autoReading,
                                onChange = settingsViewModel::savePreferences,
                                onToggleAutoReading = viewModel::toggleAutoReading,
                                onBack = viewModel::goBack,
                            )
                        }
                        is AppScreen.Search -> SearchRoute(screen.bookId, viewModel, searchViewModel)
                        AppScreen.WebImport -> {
                            val downloadTasks by downloadViewModel.tasks.collectAsStateWithLifecycle()
                            WebImportScreen(
                                tasks = downloadTasks,
                                viewModel = downloadViewModel,
                                onBack = viewModel::goBack,
                                onOpenDownloadedBook = viewModel::openDownloadedBook,
                            )
                        }
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
                AppBusyOverlay(activeBusy, onCancelBusy)
            }
            }
        }
    }
}
