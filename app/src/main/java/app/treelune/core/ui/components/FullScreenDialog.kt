package app.treelune.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.treelune.core.ui.UI
import app.treelune.core.ui.hideNavigationBar
import app.treelune.core.ui.sound.UISignal
import app.treelune.core.ui.sound.rememberUISound
import app.treelune.core.ui.sound.scrollEndSound

/**
 * A window over the whole screen (the floating chat), on the theme's background (UI.FullScreen).
 * Its own window, with the navigation bar hidden in it as in the activity's: otherwise it keeps
 * the bar's place, and stops short of the bottom of the screen. Back closes it; a touch cannot
 * fall outside it.
 *
 * The window spans the whole screen and lays itself out under the bars and the keyboard, its
 * content kept clear of them by their insets. The status bar's band is drawn as the activity's,
 * its colour and its icons' tone copied from it: the app does not draw under it, and the
 * window's background there would leave the icons on whatever tone the theme has. Android is told to do nothing of the keyboard: sliding the window showed only
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
        val activity = LocalContext.current.activity()
        @Suppress("DEPRECATION") // Read only, as the activity's window has it until the app goes edge-to-edge
        val barColor = Color(activity.window.statusBarColor)
        SideEffect {
            hideNavigationBar(window)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(barColor))
            UI.FullScreen {
                Box(modifier = Modifier.fillMaxSize().imePadding().scrollEndSound()) { content() }
            }
        }
    }
}

/** The activity this context belongs to, through the wrappers a dialog's context adds. */
private tailrec fun android.content.Context.activity(): android.app.Activity = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.activity()
    else -> error("No activity behind this context")
}
