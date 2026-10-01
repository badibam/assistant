package com.assistant.core.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.demo.DemoStartup
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import kotlinx.coroutines.launch

/**
 * The demo's settings (docs/design/demo.md): whether it is installed at each update — turned on,
 * it waits for the next update; turned off, it removes the demo there is —, a button that
 * installs it afresh, afloat at this moment, its progress shown while it runs, and one that
 * removes it, once confirmed. Removing leaves the setting as the user set it: on, the next update
 * brings the demo back.
 */
@Composable
fun DemoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }

    /** Runs [operation] on the demo, then says how it went, [done] on success. */
    fun runDemo(operation: String, done: String) {
        if (working) return
        working = true
        scope.launch {
            val result = coordinator.processUserAction(operation, emptyMap())
            working = false
            UI.Toast(context, if (result.isSuccess) done else result.error ?: s.shared("error_operation_failed"), Duration.LONG)
        }
    }

    if (confirmingRemoval) {
        UI.Dialog(
            type = com.assistant.core.ui.DialogType.DANGER,
            onConfirm = { confirmingRemoval = false; runDemo("demo.remove", s.shared("demo_removed")) },
            onCancel = { confirmingRemoval = false }
        ) {
            UI.Text(s.shared("demo_remove_confirm"), TextType.BODY)
        }
    }

    AppSettingsScreen(
        category = AppSettingCategories.DEMO,
        onBack = onBack,
        afterSave = { before, after ->
            // Turned off, the demo there is goes; turned on or saved unchanged, nothing until the next update
            val turnedOff = before.optBoolean(DemoStartup.INSTALL_ON_UPDATE) && !after.optBoolean(DemoStartup.INSTALL_ON_UPDATE)
            if (!turnedOff) null
            else coordinator.processUserAction("demo.remove", emptyMap())
                .let { if (it.isSuccess) null else it.error ?: s.shared("error_operation_failed") }
        },
        below = {
            val state = if (working) com.assistant.core.ui.ComponentState.LOADING else com.assistant.core.ui.ComponentState.NORMAL
            UI.Button(type = ButtonType.DEFAULT, state = state, onClick = { runDemo("demo.install", s.shared("demo_installed")) }) {
                UI.Text(s.shared("demo_reinstall_now"), TextType.LABEL)
            }
            UI.Button(type = ButtonType.DEFAULT, state = state, onClick = { if (!working) confirmingRemoval = true }) {
                UI.Text(s.shared("demo_remove_now"), TextType.LABEL)
            }
            if (working) com.assistant.core.ui.components.DemoInstallProgress()
        }
    )
}
