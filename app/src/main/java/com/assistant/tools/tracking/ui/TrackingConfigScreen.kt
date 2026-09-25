package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.fields.CustomFieldsEditor
import com.assistant.core.fields.FieldConfigEditor
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.toFieldConfig
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.fields.toJsonArray
import com.assistant.core.fields.migration.rememberCustomFieldsMigrationHandler
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ui.ToolGeneralConfigSection
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.CardType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.FieldDefinitionsSaver
import com.assistant.core.ui.FieldType as UIFieldType
import com.assistant.core.ui.JsonObjectSaver
import com.assistant.core.ui.LoadState
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.rememberLoadOnce
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import com.assistant.tools.tracking.TrackingConfig
import com.assistant.tools.tracking.TrackingKind
import com.assistant.tools.tracking.TrackingShortcut
import com.assistant.tools.tracking.TrackingToolType
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuration screen of a tracking tool: its general settings, what it follows (its type),
 * the settings of its value (edited by the value's field type, like any field's), its units
 * for a numeric one, its shortcuts and the user's fields.
 *
 * Saving a change of type deletes the tool's entries, after confirmation: they hold a value of
 * another kind. A change of the value's settings or of the units keeps the entries as they
 * are, after a warning: a value recorded on the former scale or with a former option stays as
 * it was recorded.
 */
@Composable
fun TrackingConfigScreen(
    zoneId: String,
    onSave: (config: String) -> Unit,
    onCancel: () -> Unit,
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null,
    initialGroup: String? = null
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    val isEditing = existingToolId != null

    // The whole config, the single source of truth of the screen; the user's fields apart,
    // since their editor and their migration work on definitions
    var config by rememberSaveable(stateSaver = JsonObjectSaver) { mutableStateOf(com.assistant.core.tools.ToolConfigSettings.defaults(TrackingToolType, context)) }
    var initialConfig by rememberSaveable(stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject()) }
    var customFields by rememberSaveable(stateSaver = FieldDefinitionsSaver) { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    var oldCustomFields by rememberSaveable(stateSaver = FieldDefinitionsSaver) { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    var currentZoneId by rememberSaveable { mutableStateOf(zoneId) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var warning by rememberSaveable { mutableStateOf<String?>(null) }
    var deletesEntries by rememberSaveable { mutableStateOf(false) }
    // The shortcut being edited: its index, or -1 for a new one; null when the dialog is closed
    var editingShortcut by rememberSaveable { mutableStateOf<Int?>(null) }

    fun update(block: JSONObject.() -> Unit) {
        config = JSONObject(config.toString()).apply(block)
    }

    val configLoad = rememberLoadOnce(existingToolId) {
        if (existingToolId == null) return@rememberLoadOnce true
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to existingToolId))
        if (!result.isSuccess) throw IllegalStateException("Cannot load tracking tool $existingToolId: ${result.error}")
        val loaded = result.mapSingleData("tool_instance") { it }?.get("config") as? Map<*, *>
            ?: throw IllegalStateException("Tracking tool $existingToolId has no config")
        @Suppress("UNCHECKED_CAST")
        val loadedConfig = JsonUtils.toJSONObject(loaded as Map<String, Any?>)
        customFields = loadedConfig.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        oldCustomFields = customFields.toList()
        loadedConfig.remove("extra_fields")
        config = loadedConfig
        initialConfig = JSONObject(loadedConfig.toString())
        true
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    if (configLoad == LoadState.LOADING) {
        UI.Text(s.shared("tools_loading_config"), TextType.BODY)
        return
    }

    val kind = TrackingKind.of(config)

    /** Validates and saves, the entries deleted first when the type changed. */
    fun save() {
        val toSave = JSONObject(config.toString()).apply {
            if (customFields.isNotEmpty()) put("extra_fields", customFields.toJsonArray()) else remove("extra_fields")
        }
        UI.ValidationHelper.validateAndSave(
            toolTypeName = "tracking",
            configData = toSave.keys().asSequence().associateWith { toSave.get(it) },
            context = context,
            onSuccess = { configJson ->
                scope.launch {
                    if (deletesEntries && existingToolId != null) {
                        val deleted = coordinator.processUserAction("tool_data.delete_all", mapOf("tool_instance_id" to existingToolId))
                        if (!deleted.isSuccess) {
                            errorMessage = deleted.error ?: s.shared("tools_config_error_save")
                            isSaving = false
                            return@launch
                        }
                    }
                    if (isEditing && currentZoneId != zoneId && existingToolId != null) {
                        val moved = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to existingToolId, "zone_id" to currentZoneId))
                        if (moved.status != CommandStatus.SUCCESS) LogManager.tracking("Failed to update zone: ${moved.error}", "ERROR")
                    }
                    onSave(configJson)
                }
            },
            onError = { error ->
                errorMessage = error
                isSaving = false
            }
        )
    }

    /** Asks before a save that changes what the recorded entries mean, saves otherwise. */
    fun checkAndSave() {
        val before = if (isEditing) initialConfig else null
        when {
            before != null && before.optString("type") != config.optString("type") -> {
                deletesEntries = true
                warning = s.tool("config_warning_type_change_desc").format(before.optString("type"), config.optString("type")) +
                    "\n" + s.tool("config_warning_data_deletion")
            }
            before != null && (before.optJSONObject("value")?.toString() != config.optJSONObject("value")?.toString() ||
                TrackingConfig.units(before).any { it !in TrackingConfig.units(config) }) -> {
                deletesEntries = false
                warning = s.tool("config_warning_value_change")
            }
            else -> save()
        }
    }

    val migrationHandler = rememberCustomFieldsMigrationHandler(
        toolInstanceId = existingToolId,
        oldFields = oldCustomFields,
        newFields = customFields,
        context = context,
        onSuccess = { checkAndSave() },
        onError = { error ->
            errorMessage = error
            isSaving = false
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.PageHeader(
            title = if (isEditing) s.tool("config_title_edit") else s.tool("config_title_create"),
            subtitle = s.tool("display_name"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onCancel
        )

        ToolGeneralConfigSection(
            config = config,
            updateConfig = { key, value -> update { put(key, value) } },
            toolTypeName = "tracking",
            zoneId = currentZoneId,
            onZoneChange = { currentZoneId = it },
            initialGroup = initialGroup,
            isEditing = isEditing
        )

        // What the tool follows, and the settings of its value
        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UI.Text(s.tool("config_section_specific_params"), TextType.SUBTITLE)

                UI.FormSelection(
                    label = s.tool("config_label_tracking_type"),
                    options = TrackingKind.entries.map { s.tool("config_option_${it.key}") },
                    selected = s.tool("config_option_${kind.key}"),
                    onSelect = { selected ->
                        val chosen = TrackingKind.entries.first { s.tool("config_option_${it.key}") == selected }
                        if (chosen != kind) update {
                            // The value's settings, units and shortcut values belong to the former type
                            put("type", chosen.key)
                            remove("value")
                            remove("units")
                            remove("allow_decrement")
                            put("items", JSONArray(TrackingConfig.shortcuts(this).map { JSONObject().put("name", it.name) }))
                        }
                    }
                )

                kind.valueType?.let { valueType ->
                    FieldConfigEditor(
                        fieldType = valueType,
                        config = config.optJSONObject("value")?.toFieldConfig(),
                        onConfigChange = { valueConfig ->
                            update { if (valueConfig.isNullOrEmpty()) remove("value") else put("value", JSONObject(valueConfig)) }
                        },
                        context = context
                    )
                }

                if (kind == TrackingKind.NUMERIC) {
                    UI.DynamicList(
                        label = s.tool("field_units"),
                        items = TrackingConfig.units(config),
                        onItemsChanged = { units ->
                            update { if (units.isEmpty()) remove("units") else put("units", JSONArray(units)) }
                        },
                        placeholder = s.tool("config_label_unit"),
                        required = false
                    )
                }

                if (kind == TrackingKind.COUNTER) {
                    UI.ToggleField(
                        label = s.tool("config_label_allow_decrement"),
                        checked = TrackingConfig.allowsDecrement(config, context),
                        onCheckedChange = { allowed -> update { put("allow_decrement", allowed) } },
                        required = false
                    )
                }
            }
        }

        // Shortcuts
        val shortcuts = TrackingConfig.shortcuts(config)
        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { UI.Text(s.tool("field_items"), TextType.SUBTITLE) }
                    UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.ICON, size = Size.S, onClick = { editingShortcut = -1 })
                }
                if (shortcuts.isEmpty()) UI.Text(s.tool("config_message_no_items"), TextType.CAPTION)
                shortcuts.forEachIndexed { index, shortcut ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            UI.Text(listOfNotNull(shortcut.name, shortcut.value?.toString(), shortcut.unit).joinToString(" · "), TextType.BODY)
                        }
                        UI.ActionButton(action = ButtonAction.UP, display = ButtonDisplay.ICON, size = Size.S, enabled = index > 0, onClick = {
                            config = TrackingConfig.withShortcuts(config, shortcuts.toMutableList().apply { add(index - 1, removeAt(index)) })
                        })
                        UI.ActionButton(action = ButtonAction.DOWN, display = ButtonDisplay.ICON, size = Size.S, enabled = index < shortcuts.size - 1, onClick = {
                            config = TrackingConfig.withShortcuts(config, shortcuts.toMutableList().apply { add(index + 1, removeAt(index)) })
                        })
                        UI.ActionButton(action = ButtonAction.EDIT, display = ButtonDisplay.ICON, size = Size.S, onClick = { editingShortcut = index })
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                            config = TrackingConfig.withShortcuts(config, shortcuts.filterIndexed { i, _ -> i != index })
                        })
                    }
                }
            }
        }

        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CustomFieldsEditor(fields = customFields, onFieldsChange = { customFields = it }, context = context)
            }
        }

        UI.ToolConfigActions(
            isEditing = isEditing,
            onSave = {
                isSaving = true
                migrationHandler.checkAndProceed()
            },
            onCancel = onCancel,
            onDelete = onDelete,
            saveEnabled = !isSaving && configLoad == LoadState.LOADED
        )
    }

    editingShortcut?.let { index ->
        val current = TrackingConfig.shortcuts(config).getOrNull(index)
        ShortcutDialog(
            kind = kind,
            units = TrackingConfig.units(config),
            initial = current ?: TrackingShortcut(""),
            isNew = current == null,
            onConfirm = { shortcut ->
                val shortcuts = TrackingConfig.shortcuts(config).toMutableList()
                if (current == null) shortcuts.add(shortcut) else shortcuts[index] = shortcut
                config = TrackingConfig.withShortcuts(config, shortcuts)
                editingShortcut = null
            },
            onCancel = { editingShortcut = null }
        )
    }

    warning?.let { text ->
        UI.Dialog(
            type = if (deletesEntries) DialogType.DANGER else DialogType.CONFIRM,
            onConfirm = {
                warning = null
                save()
            },
            onCancel = {
                warning = null
                isSaving = false
            }
        ) {
            UI.Text(text, TextType.BODY)
        }
    }
}

/**
 * Creates or edits a shortcut: its name, and for a numeric or counter tool the value it
 * enters, and for a numeric one its unit.
 */
@Composable
private fun ShortcutDialog(
    kind: TrackingKind,
    units: List<String>,
    initial: TrackingShortcut,
    isNew: Boolean,
    onConfirm: (TrackingShortcut) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var value by rememberSaveable { mutableStateOf(initial.value?.toString() ?: "") }
    var unit by rememberSaveable { mutableStateOf(initial.unit ?: "") }
    val keepsValue = kind == TrackingKind.NUMERIC || kind == TrackingKind.COUNTER
    // A whole number is kept whole: 150, not 150.0
    val number: Number? = value.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toLong() else it }
    val valid = name.isNotBlank() && (value.isBlank() || number != null)

    UI.Dialog(
        type = if (isNew) DialogType.CREATE else DialogType.EDIT,
        confirmEnabled = valid,
        onConfirm = {
            onConfirm(TrackingShortcut(
                name = name.trim(),
                value = if (keepsValue) number else null,
                unit = if (kind == TrackingKind.NUMERIC) unit.takeIf { it.isNotEmpty() } else null
            ))
        },
        onCancel = onCancel
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UI.Text(if (isNew) s.tool("config_dialog_create_item") else s.tool("config_dialog_edit_item"), TextType.SUBTITLE)
            UI.FormField(label = s.tool("field_name"), value = name, onChange = { name = it }, fieldType = UIFieldType.TEXT, required = true)
            if (keepsValue) {
                UI.FormField(
                    label = if (kind == TrackingKind.COUNTER) s.tool("config_label_default_increment") else s.tool("config_label_default_quantity"),
                    value = value,
                    onChange = { value = it },
                    fieldType = UIFieldType.NUMERIC,
                    required = false
                )
            }
            if (kind == TrackingKind.NUMERIC && units.isNotEmpty()) {
                UI.FormSelection(label = s.tool("config_label_unit"), options = listOf("") + units, selected = unit, onSelect = { unit = it }, required = false)
            }
        }
    }
}
