package com.assistant.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.assistant.core.ui.UI
import com.assistant.core.ui.hideNavigationBar
import com.assistant.core.ui.sound.UISignal
import com.assistant.core.ui.sound.rememberUISound
import com.assistant.core.ui.sound.scrollEndSound

/**
 * A window over the whole screen (the floating chat), on the theme's background (UI.FullScreen).
 * Its own window, with the navigation bar hidden in it as in the activity's: otherwise it keeps
 * the bar's place, and stops short of the bottom of the screen. Back closes it; a touch cannot
 * fall outside it.
 *
 * The window lays itself out under the bars and the keyboard, its content kept clear of them by
 * their insets. Android is told to do nothing of the keyboard: sliding the window showed only
 * the line being typed, and resizing it shrank it in its centre, its bottom still under the
 * keys and no keyboard left in its insets for the content to keep clear of.
 */
@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sound = rememberUISound()
    LaunchedEffect(Unit) { sound(UISignal.OPEN) }
    Dialog(
        onDismissRequest = { sound(UISignal.CLOSE); onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            hideNavigationBar(window)
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        UI.FullScreen {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding().scrollEndSound()) { content() }
        }
    }
}
