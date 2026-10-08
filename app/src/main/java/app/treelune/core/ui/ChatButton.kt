package app.treelune.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The chat's floating button, at the bottom right of the places one reads (Place.chatButton): the
 * home screen, a zone, a tool, the Guide, an automation. It floats over the content, so a screen
 * of such a place leaves room for it at the end of what scrolls ([chatButtonSpace]), and what
 * holds fixed controls at the bottom while it shows (a tile being moved, a session running) hides
 * it ([HideChatButton]).
 */
object ChatButton {

    /** The button's height as drawn by the theme, measured where it is placed (MainScreen). */
    var height by mutableStateOf(0.dp)

    private var hiders by mutableIntStateOf(0)

    /** Whether something on screen asked for the button to go. */
    val hidden: Boolean get() = hiders > 0

    internal fun hide() { hiders++ }
    internal fun show() { hiders-- }
}

/** The chat's button away while this is composed: fixed controls at the bottom of the screen. */
@Composable
fun HideChatButton() {
    DisposableEffect(Unit) {
        ChatButton.hide()
        onDispose { ChatButton.show() }
    }
}

/**
 * Room at the end of a scrolled content for the chat's button: its height, the space it stands
 * off the bottom, and a margin. Put after the vertical scroll, it is padding inside the scroll, kept
 * even when the content stops early; a lazy list takes [chatButtonEnd] as its bottom content padding.
 */
fun Modifier.chatButtonSpace(): Modifier = composed { padding(bottom = chatButtonEnd()) }

/** The room [chatButtonSpace] leaves, for a lazy list's content padding. */
@Composable
fun chatButtonEnd(): Dp = ChatButton.height + UI.Space.L + UI.Space.S
