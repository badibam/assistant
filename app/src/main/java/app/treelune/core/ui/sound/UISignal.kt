package app.treelune.core.ui.sound

import app.treelune.core.ui.ButtonAction

/**
 * What the interface says by a sound: a closed list the core's components send, and that each
 * theme answers with a sound of its own or none (ThemeContract.sound). A screen never names a
 * sound, only what happened.
 */
enum class UISignal {
    /** An action done here: saved, added, started. */
    CONFIRM,
    /** Another screen entered: a zone, a tool, a configuration. */
    ENTER,
    /** Back to the screen before. */
    BACK,
    /** A panel or a window opened over the screen. */
    OPEN,
    /** A panel or a window closed. */
    CLOSE,
    /** Something checked or switched: a box, a switch, a yes or no, a tab. */
    TOGGLE,
    /** A step taken: a slider's stop, a period's arrow. */
    STEP,
    /** A list pushed against its end. */
    SCROLL_END,
    /** A disabled element touched: it heard, and refuses. */
    REFUSE
}

/**
 * The signal a press on an action button sends: what the action does, not what it looks like.
 * None for a button that moves between places (back, configure, view, the chat): the stack plays
 * the sound of coming and going (Navigator), or the chat's window its own.
 */
fun ButtonAction.signal(): UISignal? = when (this) {
    ButtonAction.BACK, ButtonAction.CONFIGURE, ButtonAction.VIEW, ButtonAction.AI_CHAT, ButtonAction.GUIDE -> null
    ButtonAction.CANCEL -> UISignal.CLOSE
    ButtonAction.ATTACH, ButtonAction.PHOTO, ButtonAction.GALLERY -> UISignal.OPEN
    ButtonAction.LEFT, ButtonAction.RIGHT, ButtonAction.UP, ButtonAction.DOWN -> UISignal.STEP
    else -> UISignal.CONFIRM
}
