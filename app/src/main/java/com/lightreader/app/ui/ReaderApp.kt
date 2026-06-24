package com.lightreader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

@Composable
fun ReaderApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    BackHandler(enabled = state.screen !is AppScreen.Library) { viewModel.goBack() }
    LightReaderTheme {
        Box(Modifier.fillMaxSize()) {
            when (val screen = state.screen) {
                AppScreen.Library -> LibraryScreen(state, viewModel)
                is AppScreen.Reader -> ReaderScreen(state.preferences, viewModel)
                is AppScreen.Search -> SearchScreen(viewModel)
                AppScreen.WebImport -> WebImportScreen(state.tasks, viewModel)
                AppScreen.ApiSettings -> ApiSettingsScreen(viewModel)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            if (state.busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
