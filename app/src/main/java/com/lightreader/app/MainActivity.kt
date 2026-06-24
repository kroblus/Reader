package com.lightreader.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lightreader.app.ui.MainViewModel
import com.lightreader.app.ui.ReaderApp

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReaderApp(viewModel) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (viewModel.onVolumeKey(true)) return true
            KeyEvent.KEYCODE_VOLUME_UP -> if (viewModel.onVolumeKey(false)) return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
