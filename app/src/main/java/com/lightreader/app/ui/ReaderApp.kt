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

@Composable
fun ReaderApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val readerState by viewModel.readerState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
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
                    AppScreen.Library -> LibraryScreen(state, viewModel)
                    is AppScreen.Reader -> ReaderScreen(state.preferences, viewModel)
                    is AppScreen.ReaderSettingsDetail -> ReaderSettingsDetailScreen(
                        preferences = state.preferences,
                        autoReading = readerState.autoReading,
                        onChange = viewModel::savePreferences,
                        onToggleAutoReading = viewModel::toggleAutoReading,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.Search -> SearchScreen(viewModel)
                    AppScreen.WebImport -> WebImportScreen(state.tasks, viewModel)
                    AppScreen.WebDomBridge -> WebDomBridgeScreen(viewModel)
                    AppScreen.ApiSettings -> ApiSettingsScreen(viewModel)
                }
            }
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (state.screen is AppScreen.Reader) 104.dp else 8.dp),
            )
            if (state.busy.active) {
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
                                state.busy.message.ifBlank { stringResource(R.string.busy_default) },
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
