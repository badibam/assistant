package app.treelune.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.CoreFields
import app.treelune.core.fields.FieldInput
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.tools.tracking.TrackingConfig
import app.treelune.tools.tracking.TrackingKind
import org.json.JSONObject

/**
 * The quick ways a tracking tool creates entries: its shortcuts, laid out by what it follows,
 * and a free entry. What a quick action writes is always the main field, "value"; the user's
 * fields are filled in the entry dialog.
 *
 * - numeric: a shortcut with a value enters it at once, one without opens the dialog
 * - counter: + and - enter the shortcut's step (or its negative)
 * - yes/no: one button per answer
 * - timer: start and stop; the start is kept in the entry's state, so a running stopwatch
 *   survives the app being killed, and starting one stops the one running in the same tool
 * - occurrence: one press records that it happened
 * - scale, choice, text: the shortcut opens the dialog to enter the value
 * - choice: after the shortcuts, each option, entered at once or through the dialog prefilled
 *   with it, the way a yes/no offers its answers
 *
 * @param refreshTrigger Bumped when the tool's entries change, to reread the running timers
 * @param onConfigChanged Called once a shortcut has been added to the config
 */
@Composable
fun TrackingQuickEntry(
    toolInstanceId: String,
    config: JSONObject,
    refreshTrigger: Int,
    onConfigChanged: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    val actions = rememberTrackingActions(toolInstanceId, config, onConfigChanged)

    // The date chosen for the next entries, or null for "now", read when the entry is saved
    var customTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }
    // The entries of this tool with a stopwatch running
    var running by remember { mutableStateOf<List<RunningEntry>>(emptyList()) }

    LaunchedEffect(toolInstanceId, refreshTrigger) {
        if (actions.kind != TrackingKind.TIMER) return@LaunchedEffect
        actions.loadRunning()?.let { running = it }
    }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        // The date of the next entries: now, or one chosen. A stopwatch starts now, so the
        // choice is off while one runs.
        val options = actions.choice?.options.orEmpty()
        if (actions.shortcuts.isNotEmpty() || options.isNotEmpty()) {
            val canChooseDate = running.isEmpty()
            UI.BooleanField(
                label = "",
                value = customTimestamp != null && canChooseDate,
                onValueChange = { chosen ->
                    if (canChooseDate) customTimestamp = if (chosen) System.currentTimeMillis() else null
                },
                trueLabel = s.tool("usage_custom_date"),
                falseLabel = s.tool("usage_current_time")
            )
            customTimestamp?.takeIf { canChooseDate }?.let { chosen ->
                FieldInput(
                    fieldDef = CoreFields.timestamp(s::shared),
                    value = chosen,
                    onChange = { new -> (new as? Number)?.toLong()?.let { customTimestamp = it } },
                    context = context
                )
            }
        }

        actions.shortcuts.forEach { shortcut ->
            ShortcutRow(label = shortcutLabel(shortcut, actions.kind) { app.treelune.core.utils.NumberFormatting.formatForDisplay(it.toDouble(), context = context) }) {
                ShortcutButtons(actions, shortcut, running, customTimestamp)
                // The dialog opened on the shortcut, to change its value or its fields first
                val edits = when (actions.kind) {
                    TrackingKind.NUMERIC -> shortcut.value
                    TrackingKind.COUNTER -> TrackingConfig.counterStep(shortcut)
                    TrackingKind.BOOLEAN, TrackingKind.OCCURRENCE -> null
                    else -> return@ShortcutRow
                }
                QuickButton(ButtonAction.EDIT, !actions.isSaving) {
                    actions.openDialog(shortcut.name, edits, shortcut.unit.takeIf { actions.kind == TrackingKind.NUMERIC }, nameEditable = false, offerShortcut = false, timestamp = customTimestamp)
                }
            }
        }

        // A choice's options, named by their label
        options.forEach { option ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    OptionLabel(actions.choice!!, option)
                }
                QuickButton(ButtonAction.ADD, !actions.isSaving) { actions.quickSaveOption(option, customTimestamp) }
                QuickButton(ButtonAction.EDIT, !actions.isSaving) { actions.openOptionDialog(option, customTimestamp) }
            }
        }

        // A free entry, whose name is typed and which can become a shortcut
        Box(modifier = Modifier.fillMaxWidth()) {
            UI.ActionButton(
                action = ButtonAction.ADD,
                display = ButtonDisplay.ICON,
                enabled = !actions.isSaving,
                onClick = { actions.openDialog("", null, null, nameEditable = true, offerShortcut = true, timestamp = customTimestamp) }
            )
        }
    }

    actions.Dialog()
}

@Composable
private fun ShortcutRow(label: String, actions: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            UI.Text(label, TextType.BODY)
        }
        actions()
    }
}
