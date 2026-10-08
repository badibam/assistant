package app.treelune.core.ui

import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The navigation bar hidden in [window], the status bar kept. A swipe from the edge brings it
 * back for a moment, over the screen; with gesture navigation there is only its handle to hide,
 * and the gestures still answer.
 *
 * Said per window: the activity's (MainActivity) and a full-screen dialog's (FullScreenDialog)
 * each keep the bar's place until they are told, and a dialog that does not leaves a strip
 * of the screen behind it at the bottom.
 */
fun hideNavigationBar(window: Window) {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.navigationBars())
    }
}
