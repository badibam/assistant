package app.treelune.core.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import kotlinx.coroutines.launch

/**
 * The demo's settings: whether it is installed at each update, which acts at
 * the next update only and never removes it; a button that installs it afresh, afloat at this
 * moment, its progress shown while it runs; and one that removes it, once confirmed. The buttons
 * leave the setting as the user set it.
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
            type = app.treelune.core.ui.DialogType.DANGER,
            onConfirm = { confirmingRemoval = false; runDemo("demo.remove", s.shared("demo_removed")) },
            onCancel = { confirmingRemoval = false }
        ) {
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(s.shared("demo_remove_confirm"), TextType.BODY)
                // The tutorials begun in the demo, and how to have it back for them
                val tutorials = app.treelune.core.guide.GuideChapters.all(context).filter { it.usesDemo }.map { it.title(context) }
                if (tutorials.isNotEmpty()) UI.Text(s.shared("guide_demo_removal_warning").format(tutorials.joinToString(", ")), TextType.CAPTION)
            }
        }
    }

    AppSettingsScreen(
        category = AppSettingCategories.DEMO,
        onBack = onBack,
        below = {
            // A long operation running, this screen's or another started elsewhere, holds the buttons
            val running by app.treelune.core.coordinator.LongOperation.running.collectAsState()
            val busy = working || running != null
            val state = if (busy) app.treelune.core.ui.ComponentState.LOADING else app.treelune.core.ui.ComponentState.NORMAL
            UI.Button(type = ButtonType.DEFAULT, state = state, onClick = { runDemo("demo.install", s.shared("demo_installed")) }) {
                UI.Text(s.shared("demo_reinstall_now"), TextType.LABEL)
            }
            UI.Button(type = ButtonType.DEFAULT, state = state, onClick = { if (!busy) confirmingRemoval = true }) {
                UI.Text(s.shared("demo_remove_now"), TextType.LABEL)
            }
            if (busy) app.treelune.core.ui.components.LongOperationProgress()
        }
    )
}
