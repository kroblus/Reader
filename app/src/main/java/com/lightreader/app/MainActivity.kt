package com.lightreader.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.lightreader.app.ui.AiSettingsViewModel
import com.lightreader.app.feature.download.DownloadViewModel
import com.lightreader.app.ui.LibraryViewModel
import com.lightreader.app.ui.ReaderViewModel
import com.lightreader.app.ui.SettingsViewModel
import com.lightreader.app.ui.ReaderApp
import com.lightreader.app.ui.BookSearchGateway
import com.lightreader.app.ui.BookSearchViewModel
import com.lightreader.app.ui.BackupRestoreViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ReaderViewModel>()
    private val downloadViewModel by viewModels<DownloadViewModel> {
        DownloadViewModelFactory((application as ReaderApplication).container.downloadFeatureGateway)
    }
    private val aiSettingsViewModel by viewModels<AiSettingsViewModel>()
    private val libraryViewModel by viewModels<LibraryViewModel>()
    private val settingsViewModel by viewModels<SettingsViewModel>()
    private val searchViewModel by viewModels<BookSearchViewModel> {
        BookSearchViewModelFactory((application as ReaderApplication).container.bookSearchGateway)
    }
    private val backupViewModel by viewModels<BackupRestoreViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReaderApp(viewModel, libraryViewModel, downloadViewModel, aiSettingsViewModel, settingsViewModel, searchViewModel, backupViewModel) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (viewModel.onVolumeKey(true)) return true
            KeyEvent.KEYCODE_VOLUME_UP -> if (viewModel.onVolumeKey(false)) return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private class BookSearchViewModelFactory(private val gateway: BookSearchGateway) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        check(modelClass.isAssignableFrom(BookSearchViewModel::class.java))
        return BookSearchViewModel("", gateway, extras.createSavedStateHandle()) as T
    }
}

private class DownloadViewModelFactory(
    private val gateway: com.lightreader.app.feature.download.DownloadFeatureGateway,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        check(modelClass.isAssignableFrom(DownloadViewModel::class.java)) { "Unsupported ViewModel: ${modelClass.name}" }
        return DownloadViewModel(gateway, extras.createSavedStateHandle()) as T
    }
}
