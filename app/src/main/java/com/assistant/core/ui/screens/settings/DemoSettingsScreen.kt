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
 * The demo's settings (docs/design/demo.md): whether it is installed at each update, applied as
 * soon as it is saved — off removes the demo there is, on installs it now — and a button that
 * installs it afresh, afloat at this moment.
 */
@Composable
fun DemoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }

    AppSettingsScreen(
        category = AppSettingCategories.DEMO,
        onBack = onBack,
        afterSave = { settings ->
            val operation = if (settings.optBoolean(DemoStartup.INSTALL_ON_UPDATE)) "demo.install" else "demo.remove"
            coordinator.processUserAction(operation, emptyMap()).let { if (it.isSuccess) null else it.error ?: s.shared("error_operation_failed") }
        },
        below = {
            UI.Button(
                type = ButtonType.DEFAULT,
                state = if (installing) com.assistant.core.ui.ComponentState.LOADING else com.assistant.core.ui.ComponentState.NORMAL,
                onClick = {
                    if (installing) return@Button
                    installing = true
                    scope.launch {
                        val result = coordinator.processUserAction("demo.install", emptyMap())
                        installing = false
                        UI.Toast(context, if (result.isSuccess) s.shared("demo_installed") else result.error ?: s.shared("error_operation_failed"), Duration.LONG)
                    }
                }
            ) {
                UI.Text(s.shared("demo_reinstall_now"), TextType.LABEL)
            }
        }
    )
}
