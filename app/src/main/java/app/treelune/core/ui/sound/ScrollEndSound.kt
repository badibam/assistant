package app.treelune.core.ui.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Hears a list pushed against its end, under a window's root: what a dragged list cannot scroll
 * any more travels up to its parents, and what reaches the root unconsumed means the end. One per
 * window, then, and none per list. Once per push: it sounds again only after the list moved.
 */
class ScrollEndConnection(private val onEnd: () -> Unit) : NestedScrollConnection {

    /** Whether this push has already sounded, until a list moves again. */
    private var sounded = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.Drag) {
            if (consumed != Offset.Zero) sounded = false
            else if (available != Offset.Zero && !sounded) {
                sounded = true
                onEnd()
            }
        }
        return Offset.Zero
    }
}

/** A window's root that sounds when one of its lists is pushed against its end (ScrollEndConnection). */
@Composable
fun Modifier.scrollEndSound(): Modifier {
    val sound = rememberUISound()
    val connection = remember(sound) { ScrollEndConnection { sound(UISignal.SCROLL_END) } }
    return nestedScroll(connection)
}
