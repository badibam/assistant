package com.assistant.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.assistant.core.ui.UI
import com.assistant.core.ui.hideNavigationBar

/**
 * A window over the whole screen (the floating chat), on the theme's background (UI.FullScreen).
 * Its own window, with the navigation bar hidden in it as in the activity's: otherwise it keeps
 * the bar's place, and stops short of the bottom of the screen. Back closes it; a touch cannot
 * fall outside it.
 */
@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect { hideNavigationBar(window) }
        UI.FullScreen(content)
    }
}
