package app.treelune.core.ui

import androidx.compose.runtime.Composable

/*
 * What an action is, whichever theme draws its button: the words it says, the question its
 * confirmation asks, the look it takes by default. A theme draws them, never decides them.
 */

/** The look an action takes when its caller names none: what it does decides how much it stands out. */
fun ButtonAction.defaultType(): ButtonType {
    return when (this) {
        // PRIMARY: Actions critiques/importantes
        ButtonAction.SAVE, ButtonAction.CREATE, ButtonAction.ADD, ButtonAction.CONFIGURE, ButtonAction.SELECT, ButtonAction.EDIT, ButtonAction.UPDATE, ButtonAction.CONFIRM, ButtonAction.AI_CHAT, ButtonAction.START, ButtonAction.ATTACH, ButtonAction.PHOTO, ButtonAction.GALLERY, ButtonAction.REPEAT, ButtonAction.SETTINGS -> ButtonType.PRIMARY

        // DANGER: destructive actions, behind a confirmation
        ButtonAction.DELETE, ButtonAction.STOP -> ButtonType.DANGER

        // DEFAULT: Actions neutres/navigation standard
        ButtonAction.CANCEL, ButtonAction.BACK, ButtonAction.REFRESH, ButtonAction.RESET, ButtonAction.LEFT, ButtonAction.RIGHT, ButtonAction.UP, ButtonAction.DOWN, ButtonAction.ARRANGE, ButtonAction.INTERRUPT, ButtonAction.PAUSE, ButtonAction.RESUME, ButtonAction.VIEW, ButtonAction.HISTORY, ButtonAction.GUIDE -> ButtonType.DEFAULT
    }
}

/** What a button of this action says, in the app's language. */
@Composable
fun ButtonAction.label(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = app.treelune.core.strings.Strings.`for`(context = context)

    return when (this) {
        ButtonAction.SAVE -> s.shared("action_save")
        ButtonAction.CREATE -> s.shared("action_create")
        ButtonAction.UPDATE -> s.shared("action_update")
        ButtonAction.DELETE -> s.shared("action_delete")
        ButtonAction.CANCEL -> s.shared("action_cancel")
        ButtonAction.BACK -> s.shared("action_back")
        ButtonAction.CONFIGURE -> s.shared("action_configure")
        ButtonAction.ADD -> s.shared("action_add")
        ButtonAction.EDIT -> s.shared("action_edit")
        ButtonAction.REFRESH -> s.shared("action_refresh")
        ButtonAction.SELECT -> s.shared("action_select")
        ButtonAction.CONFIRM -> s.shared("action_confirm")
        ButtonAction.RESET -> s.shared("action_reset")
        ButtonAction.LEFT -> s.shared("action_left")
        ButtonAction.RIGHT -> s.shared("action_right")
        ButtonAction.AI_CHAT -> s.shared("action_ai_chat")
        ButtonAction.INTERRUPT -> s.shared("action_interrupt")
        ButtonAction.STOP -> s.shared("action_stop")
        ButtonAction.PAUSE -> s.shared("action_pause")
        ButtonAction.RESUME -> s.shared("action_resume")
        ButtonAction.START -> s.shared("action_start")
        ButtonAction.VIEW -> s.shared("action_view")
        ButtonAction.ATTACH -> s.shared("action_attach")
        ButtonAction.PHOTO -> s.shared("action_photo")
        ButtonAction.GALLERY -> s.shared("action_gallery")
        ButtonAction.REPEAT -> s.shared("action_repeat")
        ButtonAction.ARRANGE -> s.shared("action_arrange")
        ButtonAction.UP -> s.shared("action_up")
        ButtonAction.DOWN -> s.shared("action_down")
        ButtonAction.SETTINGS -> s.shared("settings_title")
        ButtonAction.HISTORY -> s.shared("settings_history")
        ButtonAction.GUIDE -> s.shared("guide_title")
    }
}

/** What a confirmation of this action asks when its caller gives no message. */
@Composable
fun ButtonAction.confirmMessage(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = app.treelune.core.strings.Strings.`for`(context = context)

    return when (this) {
        ButtonAction.DELETE -> s.shared("confirm_delete")
        ButtonAction.RESET -> s.shared("confirm_reset")
        else -> s.shared("confirm_action")
    }
}
