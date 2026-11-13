package com.lightreader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lightreader.app.feature.download.DownloadViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun ReaderApp(
    viewModel: ReaderViewModel,
    libraryViewModel: LibraryViewModel,
    downloadViewModel: DownloadViewModel,
    aiSettingsViewModel: AiSettingsViewModel,
    settingsViewModel: SettingsViewModel,
    searchViewModel: BookSearchViewModel,
    backupViewModel: BackupRestoreViewModel,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.screen) {
        // AnimatedContent keeps the outgoing editor alive during the transition. Navigation
        // ends that input session immediately instead of waiting for its eventual disposal.
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    val toolbarVisible by remember(viewModel) {
        viewModel.readerState.map { it.toolbarVisible }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = remember(viewModel) { viewModel.readerState.value.toolbarVisible })
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
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
        if (search != null) {
            searchViewModel.openBook(search.bookId)
            searchViewModel.captureOrigin(viewModel.currentSearchOrigin())
        } else {
            searchViewModel.cancelSearch()
            if (state.screen is AppScreen.Library) searchViewModel.clearOrigin()
        }
    }
    LocalizedApp(state.preferences.appLanguage) {
        LaunchedEffect(lifecycle, viewModel, downloadViewModel, libraryViewModel, aiSettingsViewModel, settingsViewModel) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    downloadViewModel.events.collect { event ->
                        viewModel.showMessage(event.toUiText())
                        if (event.notice == com.lightreader.app.feature.download.DownloadNotice.TASK_REUSED) {
                            viewModel.navigate(AppScreen.WebImport)
                        }
                    }
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
            val screenTransition = updateTransition(state.screen, label = "readerScreenTransition")
            val chromeTransition = updateTransition(
                state.screen is AppScreen.Reader && toolbarVisible,
                label = "readerChromeTransition",
            )
            val readerActive = screenTransition.currentState.usesReaderPalette() ||
                screenTransition.targetState.usesReaderPalette()
            ApplyAppSystemBars(state.preferences.appSkin, readerActive)
            // One owner outlives outgoing AnimatedContent screens. Disposing Reader while Search
            // is entering must never restore decor fitting over the new screen's window policy.
            // Keep bars until the actual chrome transition completes (also immediate at scale=0).
            ApplyReaderSystemBars(
                state.preferences,
                visible = state.screen !is AppScreen.Reader || chromeTransition.currentState || chromeTransition.targetState,
                readerActive = readerActive,
            )
            val screenOffsetPx = with(LocalDensity.current) { 12.dp.roundToPx() }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                screenTransition.AnimatedContent(
                    transitionSpec = {
                        UiMotion.enter(screenOffsetPx).togetherWith(UiMotion.exit(-screenOffsetPx))
                            .using(null)
                    },
                    // Every book shares one reader slot and one VM. A rapid book/navigation change
                    // must not mount a second ReaderScreen that writes another viewport or anchor.
                    contentKey = { if (it is AppScreen.Reader) AppScreen.Reader::class else it },
                ) { screen ->
                    when (screen) {
                        AppScreen.Library -> screenStateHolder.SaveableStateProvider("library") {
                            val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
                            LibraryScreen(
                                state = libraryState,
                                screenActive = state.screen is AppScreen.Library,
                                viewModel = libraryViewModel,
                                onOpenAppSettings = { viewModel.navigate(AppScreen.AppSettings) },
                                onOpenWebImport = { viewModel.navigate(AppScreen.WebImport) },
                                onSavePreferences = { settingsViewModel.savePreferences(it, state.preferences) },
                            )
                        }
                        is AppScreen.Reader -> ReaderScreen(
                            preferences = state.preferences,
                            chromeTransition = chromeTransition,
                            viewModel = viewModel,
                            downloadViewModel = downloadViewModel,
                            settingsViewModel = settingsViewModel,
                            onReturnSearchResults = if (searchState.origin?.bookId == screen.bookId) {
                                { viewModel.navigate(AppScreen.Search(screen.bookId)) }
                            } else null,
                            onReturnSearchOrigin = searchState.origin?.takeIf { it.bookId == screen.bookId }?.let { origin ->
                                { viewModel.restoreSearchOrigin(origin) }
                            },
                        )
                        is AppScreen.ReaderSettingsDetail -> {
                            val readerState by viewModel.readerState.collectAsStateWithLifecycle()
                            ReaderSettingsDetailScreen(
                                preferences = state.preferences,
                                autoReading = readerState.autoReading,
                                onChange = { settingsViewModel.savePreferences(it, state.preferences) },
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
                        AppScreen.Backup -> BackupRestoreScreen(backupViewModel, viewModel::goBack)
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

private fun AppScreen.usesReaderPalette(): Boolean =
    this is AppScreen.Reader || this is AppScreen.Search || this is AppScreen.ReaderSettingsDetail
