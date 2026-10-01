package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.Durations
import com.assistant.core.fields.FieldContainer
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.RunningDurations
import com.assistant.core.fields.defaultValues
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** An entry of the tool whose stopwatch is running. */
data class RunningEntry(val id: String, val name: String, val startedAt: Long, val storedValue: Number?)

/**
 * What creates a tracking tool's entries, the same from its screen and from its tile: an entry
 * saved at once, a stopwatch started or stopped, the entry dialog. One save at a time: [isSaving]
 * holds the controls off meanwhile. What a quick action writes is the main field, "value", the
 * user's fields at their default values.
 */
class TrackingActions internal constructor(
    val toolInstanceId: String,
    val config: JSONObject,
    private val context: android.content.Context,
    private val coordinator: Coordinator,
    private val scope: CoroutineScope,
    private val onConfigChanged: () -> Unit,
    private val saving: MutableState<Boolean>,
    private val dialog: DialogSlot
) {
    private val s = Strings.`for`(tool = "tracking", context = context)

    val kind = TrackingKind.of(config)
    val shortcuts = TrackingConfig.shortcuts(config)
    val valueField: FieldDefinition? =
        TrackingToolType.getEntryFields(config, context).data.firstOrNull { it.definition.name == "value" }?.definition

    // A choice's options, each a quick way to enter it as a yes/no's answers are; none for
    // another kind
    val choice: ChoiceSettings? = valueField?.takeIf { kind == TrackingKind.CHOICE }?.let { ChoiceSettings.fromConfig(it.config) }

    // A new entry takes the default values of the user's fields
    val extraDefaults: Map<String, Any?> = config.optJSONArray("extra_fields")?.toFieldDefinitions()?.defaultValues() ?: emptyMap()

    val isSaving: Boolean get() = saving.value

    /** The entry dialog opened with a draft: its name, value and unit, whether its name can change and it can become a shortcut. */
    data class DialogDraft(val name: String, val value: Any?, val unit: String?, val nameEditable: Boolean, val offerShortcut: Boolean, val timestamp: Long)

    /** Creates an entry; on success returns its id. */
    suspend fun create(name: String, timestamp: Long, value: Any?, unit: String?, extra: Map<String, Any?> = extraDefaults): String? {
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
            saving.value = true
            try { action() } finally { saving.value = false }
        }
    }

    /** An entry named [name] holding [value] and [unit], saved at once, at [timestamp] or now. */
    fun quickSave(name: String, value: Any?, unit: String?, timestamp: Long? = null) = save {
        if (create(name, timestamp ?: System.currentTimeMillis(), value, unit) != null) {
            UI.Toast(context, s.tool("usage_entry_saved"), Duration.SHORT)
        }
    }

    /** The value an entry of [option] holds: the option, alone in a list for a choice of several. */
    fun optionValue(option: String): Any = if (choice?.shape?.isList == true) listOf(option) else option

    /** An entry of [option], named by its label, saved at once, at [timestamp] or now. */
    fun quickSaveOption(option: String, timestamp: Long? = null) =
        quickSave(choice!!.labelOf(option), optionValue(option), null, timestamp)

    /** The entry dialog prefilled with [option], to fill the user's fields before saving. */
    fun openOptionDialog(option: String, timestamp: Long? = null) =
        openDialog(choice!!.labelOf(option), optionValue(option), null, nameEditable = false, offerShortcut = false, timestamp = timestamp)

    fun openDialog(name: String, value: Any?, unit: String?, nameEditable: Boolean, offerShortcut: Boolean, timestamp: Long? = null) {
        dialog.value = DialogDraft(name, value, unit, nameEditable, offerShortcut, timestamp ?: System.currentTimeMillis())
    }

    fun stop(entry: RunningEntry) = save {
        val result = coordinator.processUserAction("tool_data.stop_duration", mapOf("id" to entry.id, "container" to "data", "field" to "value"))
        if (!result.isSuccess) UI.Toast(context, result.error ?: s.tool("error_entry_update_failed"), Duration.LONG)
    }

    // Starting one stops the one running: the service keeps that rule (TrackingStopwatch)
    fun start(name: String) = save {
        val id = create(name, System.currentTimeMillis(), null, null) ?: return@save
        val result = coordinator.processUserAction("tool_data.start_duration", mapOf("id" to id, "container" to "data", "field" to "value"))
        if (!result.isSuccess) UI.Toast(context, result.error ?: s.tool("error_entry_saving"), Duration.LONG)
    }

    /** The entries with a stopwatch running, or null when they cannot be read (logged). */
    suspend fun loadRunning(): List<RunningEntry>? {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId, "running" to true))
        if (!result.isSuccess) {
            LogManager.tracking("Failed to load the running timers: ${result.error}", "ERROR")
            return null
        }
        return (result.data?.get("entries") as? List<*>).orEmpty().mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val state = JsonUtils.toJSONObject((map["state"] as? Map<String, Any?>) ?: emptyMap())
            val startedAt = RunningDurations.startedAt(state, FieldContainer.DATA, "value") ?: return@mapNotNull null
            RunningEntry(map["id"] as String, map["name"] as? String ?: "", startedAt, (map["data"] as? Map<*, *>)?.get("value") as? Number)
        }
    }

    /** The entry dialog, while one is open. */
    @Composable
    fun Dialog() {
        val draft = dialog.value ?: return
        TrackingEntryDialog(
            config = config,
            title = s.tool("usage_dialog_create_entry"),
            dialogType = DialogType.CREATE,
            initial = TrackingEntryDraft(
                name = draft.name,
                timestamp = draft.timestamp,
                value = draft.value,
                unit = draft.unit,
                extra = extraDefaults
            ),
            nameEditable = draft.nameEditable,
            offerShortcut = draft.offerShortcut,
            onConfirm = { entry ->
                dialog.value = null
                save {
                    create(entry.name, entry.timestamp, entry.value, entry.unit, entry.extra) ?: return@save
                    UI.Toast(context, s.tool("usage_entry_saved"), Duration.SHORT)
                    if (entry.addToShortcuts) addShortcut(entry)
                }
            },
            onCancel = { dialog.value = null }
        )
    }

    private suspend fun addShortcut(entry: TrackingEntryDraft) {
        // A shortcut keeps a value only where it enters one at once
        val keepsValue = kind == TrackingKind.NUMERIC || kind == TrackingKind.COUNTER
        val shortcut = TrackingShortcut(
            name = entry.name,
            value = if (keepsValue) entry.value as? Number else null,
            unit = entry.unit
        )
        // Read again: the entry just saved may have grown the config (a new unit)
        val current = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
            .takeIf { it.isSuccess }
            ?.mapSingleData("tool_instance") { map -> @Suppress("UNCHECKED_CAST") (map["config"] as? Map<String, Any?>) }
            ?.let(JsonUtils::toJSONObject)
        if (current == null) {
            UI.Toast(context, s.shared("tools_config_error_save"), Duration.LONG)
            return
        }
        val result = coordinator.processUserAction("tools.update", mapOf(
            "tool_instance_id" to toolInstanceId,
            "config" to JsonUtils.toMap(TrackingConfig.withShortcut(current, shortcut))
        ))
        if (result.isSuccess) onConfigChanged()
        else UI.Toast(context, result.error ?: s.shared("tools_config_error_save"), Duration.LONG)
    }
}

/**
 * The entry dialog's draft, kept across recreation as its parts: a draft has no saver of its
 * own, its value being any a field holds.
 */
class DialogSlot internal constructor(
    private val open: MutableState<Boolean>,
    private val name: MutableState<String>,
    private val nameEditable: MutableState<Boolean>,
    private val offerShortcut: MutableState<Boolean>,
    private val timestamp: MutableState<Long>,
    private val values: MutableState<Map<String, Any?>>
) {
    var value: TrackingActions.DialogDraft?
        get() = if (open.value) TrackingActions.DialogDraft(name.value, values.value["value"], values.value["unit"] as? String, nameEditable.value, offerShortcut.value, timestamp.value) else null
        set(draft) {
            open.value = draft != null
            if (draft == null) return
            name.value = draft.name
            values.value = mapOf("value" to draft.value, "unit" to draft.unit)
            nameEditable.value = draft.nameEditable
            offerShortcut.value = draft.offerShortcut
            timestamp.value = draft.timestamp
        }
}

/** The actions of the tracking tool [toolInstanceId], whose config is [config]. */
@Composable
fun rememberTrackingActions(toolInstanceId: String, config: JSONObject, onConfigChanged: () -> Unit = {}): TrackingActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    val saving = remember { mutableStateOf(false) }
    val dialog = DialogSlot(
        rememberSaveable { mutableStateOf(false) },
        rememberSaveable { mutableStateOf("") },
        rememberSaveable { mutableStateOf(false) },
        rememberSaveable { mutableStateOf(false) },
        rememberSaveable { mutableStateOf(0L) },
        rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf(emptyMap()) }
    )
    return remember(toolInstanceId, config.toString()) {
        TrackingActions(toolInstanceId, config, context, coordinator, scope, onConfigChanged, saving, dialog)
    }
}

/**
 * The quick buttons of [shortcut], as the tool's screen shows them, its dialog's edit button
 * aside: what a press enters depends on what the tool follows (TrackingQuickEntry).
 *
 * @param running The entries whose stopwatch runs, for a timer's start or stop
 * @param timestamp The moment of the entries, or null for now
 */
@Composable
fun ShortcutButtons(actions: TrackingActions, shortcut: TrackingShortcut, running: List<RunningEntry>, timestamp: Long?) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    val enabled = !actions.isSaving
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically) {
        when (actions.kind) {
            TrackingKind.NUMERIC -> QuickButton(ButtonAction.ADD, enabled) {
                if (shortcut.value != null) actions.quickSave(shortcut.name, shortcut.value, shortcut.unit, timestamp)
                else actions.openDialog(shortcut.name, null, shortcut.unit, nameEditable = false, offerShortcut = false, timestamp = timestamp)
            }
            TrackingKind.COUNTER -> {
                val step = TrackingConfig.counterStep(shortcut)
                TextButton("+$step", enabled) { actions.quickSave(shortcut.name, step, null, timestamp) }
                if (TrackingConfig.allowsDecrement(actions.config, context)) TextButton("-$step", enabled) { actions.quickSave(shortcut.name, -step, null, timestamp) }
            }
            TrackingKind.BOOLEAN -> {
                val labels = actions.valueField?.config
                TextButton(labels?.get("true_label") as? String ?: s.shared("label_yes"), enabled) { actions.quickSave(shortcut.name, true, null, timestamp) }
                TextButton(labels?.get("false_label") as? String ?: s.shared("label_no"), enabled) { actions.quickSave(shortcut.name, false, null, timestamp) }
            }
            TrackingKind.TIMER -> {
                val entry = running.firstOrNull { it.name == shortcut.name }
                if (entry != null) {
                    ElapsedText(entry, actions.valueField?.config, s)
                    QuickButton(ButtonAction.STOP, enabled) { actions.stop(entry) }
                } else {
                    QuickButton(ButtonAction.START, enabled) { actions.start(shortcut.name) }
                }
            }
            TrackingKind.OCCURRENCE -> QuickButton(ButtonAction.ADD, enabled) { actions.quickSave(shortcut.name, null, null, timestamp) }
            TrackingKind.SCALE, TrackingKind.CHOICE, TrackingKind.TEXT -> QuickButton(ButtonAction.ADD, enabled) {
                actions.openDialog(shortcut.name, null, null, nameEditable = false, offerShortcut = false, timestamp = timestamp)
            }
        }
    }
}

/** The text of a shortcut: its name, and the value and unit it enters when it has one. */
fun shortcutLabel(shortcut: TrackingShortcut, kind: TrackingKind, format: (Number) -> String): String = buildString {
    append(shortcut.name)
    if (kind == TrackingKind.NUMERIC && (shortcut.value != null || shortcut.unit != null)) {
        append(" (")
        append(listOfNotNull(shortcut.value?.let(format), shortcut.unit).joinToString(" "))
        append(")")
    }
}

/** An option of a choice as its row shows it: a tag in its color when the choice has colors, its label otherwise. */
@Composable
fun OptionLabel(choice: ChoiceSettings, option: String) {
    if (choice.colors.isNotEmpty()) UI.Tag(text = choice.labelOf(option), color = choice.colors[option] ?: com.assistant.core.themes.TagColor.GREY)
    else UI.Text(choice.labelOf(option), TextType.BODY, maxLines = 1)
}

@Composable
internal fun QuickButton(action: ButtonAction, enabled: Boolean, onClick: () -> Unit) =
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
fun ElapsedText(entry: RunningEntry, valueConfig: Map<String, Any>?, s: StringsContext) {
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
    UI.Text(Durations.format(elapsed, (valueConfig ?: emptyMap()) + ("precision" to "SECOND"), s), TextType.CAPTION, maxLines = 1)
}
