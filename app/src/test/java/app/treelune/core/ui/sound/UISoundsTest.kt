package app.treelune.core.ui.sound

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import app.treelune.core.ui.ButtonAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers what the interface sounds hear: a list's end once per push, and what each action button
 * says it did.
 */
class UISoundsTest {

    private val moved = Offset(0f, -10f)
    private val none = Offset.Zero

    /** A drag that a list takes is no end; what is left of it at the root is, once per push. */
    @Test
    fun scrollEnd_soundsOncePerPush() {
        var ends = 0
        val connection = ScrollEndConnection { ends++ }

        connection.onPostScroll(consumed = moved, available = none, source = NestedScrollSource.Drag)
        assertEquals(0, ends)

        connection.onPostScroll(consumed = none, available = moved, source = NestedScrollSource.Drag)
        connection.onPostScroll(consumed = none, available = moved, source = NestedScrollSource.Drag)
        assertEquals(1, ends)

        // The list moves again, then meets its end again: a second sound
        connection.onPostScroll(consumed = moved, available = none, source = NestedScrollSource.Drag)
        connection.onPostScroll(consumed = none, available = moved, source = NestedScrollSource.Drag)
        assertEquals(2, ends)
    }

    /** A fling running on after the finger left meets the end without a sound: only a push sounds. */
    @Test
    fun scrollEnd_silentOnAFling() {
        var ends = 0
        val connection = ScrollEndConnection { ends++ }
        connection.onPostScroll(consumed = none, available = moved, source = NestedScrollSource.Fling)
        assertEquals(0, ends)
    }

    /**
     * Each action button sends what its action does: close, open, a step, or done. A button that
     * moves between places sends nothing: the stack sounds the move, whatever the gesture.
     */
    @Test
    fun actionButtons_sendWhatTheyDo() {
        assertEquals(null, ButtonAction.BACK.signal())
        assertEquals(null, ButtonAction.CONFIGURE.signal())
        assertEquals(null, ButtonAction.VIEW.signal())
        assertEquals(null, ButtonAction.AI_CHAT.signal())
        assertEquals(UISignal.CLOSE, ButtonAction.CANCEL.signal())
        assertEquals(UISignal.OPEN, ButtonAction.ATTACH.signal())
        assertEquals(UISignal.STEP, ButtonAction.LEFT.signal())
        assertEquals(UISignal.CONFIRM, ButtonAction.SAVE.signal())
        assertEquals(UISignal.CONFIRM, ButtonAction.DELETE.signal())
    }
}
