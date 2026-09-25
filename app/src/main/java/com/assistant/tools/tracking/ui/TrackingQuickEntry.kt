package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.CoreFields
import com.assistant.core.fields.Durations
import com.assistant.core.fields.FieldContainer
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.RunningDurations
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.FieldValuesSaver
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import com.assistant.tools.tracking.TrackingConfig
import com.assistant.tools.tracking.TrackingKind
import com.assistant.tools.tracking.TrackingShortcut
import com.assistant.tools.tracking.TrackingToolType
import com.assistant.tools.tracking.ui.components.TrackingEntryDialog
import com.assistant.tools.tracking.ui.components.TrackingEntryDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    val kind = remember(config) { TrackingKind.of(config) }
    val shortcuts = remember(config) { TrackingConfig.shortcuts(config) }
    val valueField = remember(config) {
        TrackingToolType.getEntryFields(config, context).data.firstOrNull { it.definition.name == "value" }?.definition
    }

    var isSaving by remember { mutableStateOf(false) }
    // The date chosen for the next entries, or null for "now", read when the entry is saved
    var customTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }
    // The entries of this tool with a stopwatch running, by entry id
    var running by remember { mutableStateOf<List<RunningEntry>>(emptyList()) }

    // Dialog: the draft it opens with, and whether it is a free entry (which can become a shortcut)
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var dialogName by rememberSaveable { mutableStateOf("") }
    var dialogFree by rememberSaveable { mutableStateOf(false) }
    var dialogTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    var dialogValues by rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf(emptyMap<String, Any?>()) }

    LaunchedEffect(toolInstanceId, refreshTrigger) {
        if (kind != TrackingKind.TIMER) return@LaunchedEffect
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId, "running" to true))
        if (!result.isSuccess) {
            LogManager.tracking("Failed to load the running timers: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        running = (result.data?.get("entries") as? List<*>).orEmpty().mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val state = JsonUtils.toJSONObject((map["state"] as? Map<String, Any?>) ?: emptyMap())
            val startedAt = RunningDurations.startedAt(state, FieldContainer.DATA, "value") ?: return@mapNotNull null
            RunningEntry(map["id"] as String, map["name"] as? String ?: "", startedAt, (map["data"] as? Map<*, *>)?.get("value") as? Number)
        }
    }

    /** Creates an entry; on success returns its id. */
    suspend fun create(name: String, timestamp: Long, value: Any?, unit: String?, extra: Map<String, Any?> = emptyMap()): String? {
        val params = mutableMapOf<String, Any>(
            "tool_instance_id" to toolInstanceId,
            "tooltype" to "tracking",
            "name" to name,
            "timestamp" to timestamp,
            "data" to TrackingConfig.entryData(value, unit)
        )
        if (extra.isNotEmpty()) params["extra"] = JSONObject(extra)
        val result = coordinator.processUserAction("tool_data.create", params)
        return if (result.isSuccess) {
            result.data?.get("id") as? String
        } else {
            UI.Toast(context, result.error ?: s.tool("error_entry_saving"), Duration.LONG)
            null
        }
    }

    /** Runs [action] as one save, the controls disabled meanwhile. */
    fun save(action: suspend () -> Unit) {
        scope.launch {
            isSaving = true
            try { action() } finally { isSaving = false }
        }
    }

    fun quickSave(shortcut: TrackingShortcut, value: Any?) = save {
        if (create(shortcut.name, customTimestamp ?: System.currentTimeMillis(), value, shortcut.unit) != null) {
            UI.Toast(context, s.tool("usage_entry_saved"), Duration.SHORT)
        }
    }

    fun openDialog(name: String, value: Any?, unit: String?, free: Boolean) {
        dialogName = name
        dialogValues = mapOf("value" to value, "unit" to unit)
        dialogFree = free
        dialogTimestamp = customTimestamp ?: System.currentTimeMillis()
        dialogOpen = true
    }

    fun stop(entry: RunningEntry) = save {
        val result = coordinator.processUserAction("tool_data.stop_duration", mapOf("id" to entry.id, "container" to "data", "field" to "value"))
        if (!result.isSuccess) UI.Toast(context, result.error ?: s.tool("error_entry_update_failed"), Duration.LONG)
    }

    fun start(shortcut: TrackingShortcut) = save {
        // One activity at a time in a tool: starting one stops the one running
        running.forEach { entry ->
            coordinator.processUserAction("tool_data.stop_duration", mapOf("id" to entry.id, "container" to "data", "field" to "value"))
        }
        val id = create(shortcut.name, System.currentTimeMillis(), null, null) ?: return@save
        val result = coordinator.processUserAction("tool_data.start_duration", mapOf("id" to id, "container" to "data", "field" to "value"))
        if (!result.isSuccess) UI.Toast(context, result.error ?: s.tool("error_entry_saving"), Duration.LONG)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The date of the next entries: now, or one chosen. A stopwatch starts now, so the
        // choice is off while one runs.
        if (shortcuts.isNotEmpty()) {
            val canChooseDate = running.isEmpty()
            UI.ToggleField(
                label = "",
                checked = customTimestamp != null && canChooseDate,
                trueLabel = s.tool("usage_custom_date"),
                falseLabel = s.tool("usage_current_time"),
                onCheckedChange = { checked ->
                    if (canChooseDate) customTimestamp = if (checked) System.currentTimeMillis() else null
                }
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

        shortcuts.forEach { shortcut ->
            ShortcutRow(label = shortcutLabel(shortcut, kind) { com.assistant.core.utils.NumberFormatting.formatForDisplay(it.toDouble(), context = context) }) {
                when (kind) {
                    TrackingKind.NUMERIC -> {
                        QuickButton(ButtonAction.ADD, !isSaving) {
                            if (shortcut.value != null) quickSave(shortcut, shortcut.value)
                            else openDialog(shortcut.name, null, shortcut.unit, free = false)
                        }
                        QuickButton(ButtonAction.EDIT, !isSaving) { openDialog(shortcut.name, shortcut.value, shortcut.unit, free = false) }
                    }
                    TrackingKind.COUNTER -> {
                        val step = TrackingConfig.counterStep(shortcut)
                        TextButton("+$step", !isSaving) { quickSave(shortcut, step) }
                        if (TrackingConfig.allowsDecrement(config)) TextButton("-$step", !isSaving) { quickSave(shortcut, -step) }
                        QuickButton(ButtonAction.EDIT, !isSaving) { openDialog(shortcut.name, step, null, free = false) }
                    }
                    TrackingKind.BOOLEAN -> {
                        val labels = valueField?.config
                        TextButton(labels?.get("true_label") as? String ?: s.shared("label_yes"), !isSaving) { quickSave(shortcut, true) }
                        TextButton(labels?.get("false_label") as? String ?: s.shared("label_no"), !isSaving) { quickSave(shortcut, false) }
                        QuickButton(ButtonAction.EDIT, !isSaving) { openDialog(shortcut.name, null, null, free = false) }
                    }
                    TrackingKind.TIMER -> {
                        val entry = running.firstOrNull { it.name == shortcut.name }
                        if (entry != null) {
                            ElapsedText(entry, valueField?.config, s)
                            QuickButton(ButtonAction.STOP, !isSaving) { stop(entry) }
                        } else {
                            QuickButton(ButtonAction.START, !isSaving) { start(shortcut) }
                        }
                    }
                    TrackingKind.OCCURRENCE -> {
                        QuickButton(ButtonAction.ADD, !isSaving) { quickSave(shortcut, null) }
                        QuickButton(ButtonAction.EDIT, !isSaving) { openDialog(shortcut.name, null, null, free = false) }
                    }
                    TrackingKind.SCALE, TrackingKind.CHOICE, TrackingKind.TEXT -> {
                        QuickButton(ButtonAction.ADD, !isSaving) { openDialog(shortcut.name, null, null, free = false) }
                    }
                }
            }
        }

        // A free entry, whose name is typed and which can become a shortcut
        Box(modifier = Modifier.fillMaxWidth()) {
            UI.ActionButton(
                action = ButtonAction.ADD,
                display = ButtonDisplay.ICON,
                enabled = !isSaving,
                onClick = { openDialog("", null, null, free = true) }
            )
        }
    }

    if (dialogOpen) {
        TrackingEntryDialog(
            config = config,
            title = s.tool("usage_dialog_create_entry"),
            dialogType = DialogType.CREATE,
            initial = TrackingEntryDraft(
                name = dialogName,
                timestamp = dialogTimestamp,
                value = dialogValues["value"],
                unit = dialogValues["unit"] as? String,
                extra = emptyMap()
            ),
            nameEditable = dialogFree,
            offerShortcut = dialogFree,
            onConfirm = { draft ->
                dialogOpen = false
                save {
                    create(draft.name, draft.timestamp, draft.value, draft.unit, draft.extra) ?: return@save
                    UI.Toast(context, s.tool("usage_entry_saved"), Duration.SHORT)
                    if (draft.addToShortcuts) {
                        // A shortcut keeps a value only where it enters one at once
                        val keepsValue = kind == TrackingKind.NUMERIC || kind == TrackingKind.COUNTER
                        val shortcut = TrackingShortcut(
                            name = draft.name,
                            value = if (keepsValue) draft.value as? Number else null,
                            unit = draft.unit
                        )
                        val result = coordinator.processUserAction("tools.update", mapOf(
                            "tool_instance_id" to toolInstanceId,
                            "config" to JsonUtils.toMap(TrackingConfig.withShortcut(config, shortcut))
                        ))
                        if (result.isSuccess) onConfigChanged()
                        else UI.Toast(context, result.error ?: s.shared("tools_config_error_save"), Duration.LONG)
                    }
                }
            },
            onCancel = { dialogOpen = false }
        )
    }
}

/** An entry of the tool whose stopwatch is running. */
private data class RunningEntry(val id: String, val name: String, val startedAt: Long, val storedValue: Number?)

/** The text of a shortcut: its name, and the value and unit it enters when it has one. */
private fun shortcutLabel(shortcut: TrackingShortcut, kind: TrackingKind, format: (Number) -> String): String = buildString {
    append(shortcut.name)
    if (kind == TrackingKind.NUMERIC && (shortcut.value != null || shortcut.unit != null)) {
        append(" (")
        append(listOfNotNull(shortcut.value?.let(format), shortcut.unit).joinToString(" "))
        append(")")
    }
}

@Composable
private fun ShortcutRow(label: String, actions: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            UI.Text(label, TextType.BODY)
        }
        actions()
    }
}

@Composable
private fun QuickButton(action: ButtonAction, enabled: Boolean, onClick: () -> Unit) =
    UI.ActionButton(action = action, display = ButtonDisplay.ICON, size = Size.S, enabled = enabled, onClick = onClick)

@Composable
private fun TextButton(text: String, enabled: Boolean, onClick: () -> Unit) =
    UI.Button(
        type = ButtonType.DEFAULT,
        size = Size.S,
        state = if (enabled) com.assistant.core.ui.ComponentState.NORMAL else com.assistant.core.ui.ComponentState.DISABLED,
        onClick = { if (enabled) onClick() }
    ) {
        UI.Text(text, TextType.BODY)
    }

/** The time a running stopwatch shows, counted from its start and redrawn every second. */
@Composable
private fun ElapsedText(entry: RunningEntry, valueConfig: Map<String, Any>?, s: com.assistant.core.strings.StringsContext) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = (entry.storedValue?.toLong() ?: 0L) + (now - entry.startedAt).coerceAtLeast(0L)
    // Shown to the second whatever the field's precision: a stopwatch that only moved every
    // minute would look stopped
    UI.Text(Durations.format(elapsed, (valueConfig ?: emptyMap()) + ("precision" to "SECOND"), s), TextType.CAPTION)
}
