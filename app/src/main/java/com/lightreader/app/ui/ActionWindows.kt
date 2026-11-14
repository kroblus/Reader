package com.lightreader.app.ui

import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties

/** Action windows retain Back/key navigation, but must never become an IME target. */
internal val ActionMenuProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
)

@Composable
internal fun rememberDismissTextInput(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        { focusManager.clearFocus(force = true); keyboard?.hide() }
    }
}

/** Call inside dedicated, non-editable Dialog content, before its first window focus. */
@Composable
internal fun ActionWindowPolicy() {
    val view = LocalView.current
    val window = remember(view) {
        generateSequence(view) { it.parent as? View }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    }
    SideEffect {
        window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
    // Keep the flag through exit animation and window teardown. Clearing it on disposal
    // would make the closing window eligible for the input method again.
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionModalBottomSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, containerColor = containerColor) {
        ActionWindowPolicy()
        content()
    }
}

/** Text-entry dialogs deliberately use Material AlertDialog directly. */
@Composable
internal fun ActionAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { ActionWindowPolicy(); title() },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}
