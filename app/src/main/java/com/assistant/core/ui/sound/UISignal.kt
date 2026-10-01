package com.assistant.core.ui.sound

import com.assistant.core.ui.ButtonAction

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

/** The signal a press on an action button sends: what the action does, not what it looks like. */
fun ButtonAction.signal(): UISignal = when (this) {
    ButtonAction.BACK -> UISignal.BACK
    ButtonAction.CANCEL -> UISignal.CLOSE
    ButtonAction.CONFIGURE, ButtonAction.VIEW -> UISignal.ENTER
    ButtonAction.AI_CHAT, ButtonAction.ATTACH -> UISignal.OPEN
    ButtonAction.LEFT, ButtonAction.RIGHT, ButtonAction.UP, ButtonAction.DOWN -> UISignal.STEP
    else -> UISignal.CONFIRM
}
