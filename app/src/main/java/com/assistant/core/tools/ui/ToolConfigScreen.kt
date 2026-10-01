package com.assistant.core.tools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.fields.settings.SettingEditor
import com.assistant.core.fields.settings.SettingsForm
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.Duration
import com.assistant.core.ui.JsonObjectSaver
import com.assistant.core.ui.LoadState
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.GroupSelector
import com.assistant.core.ui.components.IconSelector
import com.assistant.core.ui.rememberLoadOnce
import com.assistant.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The config screen of any tool (docs/DATA.md): the form of its
 * declaration (ToolConfigSettings), with the parts the core draws itself: the icon picker, the
 * zone's tool groups.
 *
 * It saves through the service and shows its refusal. A change that loses recorded data is
 * refused by the service until the user agrees: the screen shows what it costs, and sends it
 * again with the agreement.
 *
 * @param existingToolId The tool being edited; null to create one in [zoneId]
 * @param initialGroup The group a new tool starts in
 * @param onDone Called once the tool is saved or deleted
 */
@Composable
fun ToolConfigScreen(
    toolType: ToolTypeContract,
    tooltype: String,
    zoneId: String,
    existingToolId: String?,
    initialGroup: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val nodes = remember(toolType) { ToolConfigSettings.nodes(toolType, context) }
    val isEditing = existingToolId != null

    // A new tool starts from its declared defaults, in the group it was asked for
    var config by rememberSaveable(stateSaver = JsonObjectSaver) {
        mutableStateOf(ToolConfigSettings.defaults(toolType, context).apply { initialGroup?.let { put("group", it) } })
    }
    var currentZoneId by rememberSaveable { mutableStateOf(zoneId) }
    var isSaving by remember { mutableStateOf(false) }
    // What the change costs, once the service refused it for that: values removed, entries deleted
    var pendingMigration by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    // The fields the change makes required, with the number of entries without a value for each,
    // once the service refused it for that; and the values the user gives them
    var pendingFill by rememberSaveable { mutableStateOf<Map<String, Int>?>(null) }
    var fill by rememberSaveable(stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject()) }
    // The config as it was before any change, and whether leaving with changes is being asked
    var asLoaded by rememberSaveable { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }

    val configLoad = rememberLoadOnce(existingToolId) {
        if (existingToolId == null) return@rememberLoadOnce true
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to existingToolId))
        @Suppress("UNCHECKED_CAST")
        val loaded = result.mapSingleData("tool_instance") { it }?.get("config") as? Map<String, Any?>
        if (!result.isSuccess || loaded == null) {
            UI.Toast(context, result.error ?: s.shared("tools_config_error_not_found"), Duration.LONG)
            return@rememberLoadOnce false
        }
        config = JsonUtils.toJSONObject(loaded)
        true
    }

    // The zones a tool can move to, and the groups of the one it is in
    var zones by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val result = coordinator.processUserAction("zones.list", emptyMap())
        zones = (result.data?.get("zones") as? List<*>).orEmpty().mapNotNull { zone ->
            val map = zone as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"] as? String ?: return@mapNotNull null
            id to (map["name"] as? String ?: id)
        }
    }
    LaunchedEffect(currentZoneId) {
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to currentZoneId))
        groups = ((result.data?.get("zone") as? Map<*, *>)?.get("tool_groups") as? List<*>).orEmpty().mapNotNull { it as? String }
    }

    fun save(confirmed: Boolean) {
        isSaving = true
        scope.launch {
            val configMap = JsonUtils.toMap(config)
            val result = if (existingToolId != null) {
                coordinator.processUserAction("tools.update", buildMap {
                    put("tool_instance_id", existingToolId)
                    put("config", configMap)
                    if (currentZoneId != zoneId) put("zone_id", currentZoneId)
                    if (confirmed) put("confirm_migration", true)
                    if (fill.length() > 0) put("fill_values", mapOf("data" to JsonUtils.toMap(fill)))
                })
            } else {
                coordinator.processUserAction("tools.create", mapOf("zone_id" to currentZoneId, "tooltype" to tooltype, "config" to configMap))
            }
            isSaving = false
            val migration = result.data?.get("migration") as? Map<*, *>
            val missing = ((migration?.get("missing_values") as? Map<*, *>)?.get("data") as? Map<*, *>).orEmpty()
                .map { (field, count) -> field.toString() to (count as Number).toInt() }.toMap()
            when {
                result.isSuccess -> onDone()
                // Refused for values the user can give: asked for them
                missing.isNotEmpty() -> pendingFill = missing
                // Refused for what it loses, and for nothing else: the user decides
                migration != null && !confirmed ->
                    pendingMigration = ((migration["removed_values"] as Number).toInt()) to ((migration["deleted_entries"] as Number).toInt())
                else -> UI.Toast(context, result.error ?: s.shared("tools_config_error_save"), Duration.LONG)
            }
        }
    }

    LaunchedEffect(configLoad) {
        if (configLoad == LoadState.LOADED && asLoaded == null) asLoaded = config.toString()
    }
    // Compared as values: an object rebuilt by the form may hold its keys in another order
    val changed = asLoaded?.let { JsonUtils.toMap(config) != JsonUtils.toMap(JSONObject(it)) || currentZoneId != zoneId } == true
    fun leave() = if (changed) leaving = true else onCancel()
    // Before the form's own, which goes up its pages first
    androidx.activity.compose.BackHandler(enabled = changed) { leaving = true }

    if (configLoad == LoadState.LOADING) {
        UI.Text(s.shared("tools_loading_config"), TextType.BODY)
        return
    }

    val editors = mapOf(
        "icon_name" to iconEditor(toolType.getSuggestedIcons(), toolType.getDefaultIconName()),
        "group" to groupEditor(groups, s.shared("label_group"))
    )

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(UI.Space.L)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = if (isEditing) s.shared("action_configure") else s.shared("action_create"),
            subtitle = toolType.getDisplayName(context),
            leftButton = ButtonAction.BACK,
            onLeftClick = { leave() }
        )

        // Values given for a former version of the change answer nothing about this one
        SettingsForm(nodes, config, { config = it; fill = JSONObject() }, context, editors, rows = toolType.getRowFields(), scroll = scroll) {
            // The zone is the tool's place, not a setting of its config: offered once the tool
            // exists, on the root page alone
            if (isEditing && zones.isNotEmpty()) {
                UI.FormSelection(
                    required = false,
                    label = s.shared("label_zone"),
                    options = zones.map { it.second },
                    selected = zones.find { it.first == currentZoneId }?.second ?: "",
                    onSelect = { name -> zones.find { it.second == name }?.let { currentZoneId = it.first } }
                )
            }
        }

        UI.ToolConfigActions(
            isEditing = isEditing,
            onSave = { save(confirmed = false) },
            onCancel = { leave() },
            // The button asks for confirmation itself
            onDelete = existingToolId?.let { toolId ->
                {
                    scope.launch {
                        val result = coordinator.processUserAction("tools.delete", mapOf("tool_instance_id" to toolId))
                        if (result.isSuccess) onDone()
                        else UI.Toast(context, result.error ?: s.shared("tools_config_error_save"), Duration.LONG)
                    }
                }
            },
            saveEnabled = !isSaving && configLoad == LoadState.LOADED
        )
    }

    if (leaving) UI.Dialog(
        type = com.assistant.core.ui.DialogType.CONFIRM,
        onConfirm = { leaving = false; onCancel() },
        onCancel = { leaving = false }
    ) {
        UI.Text(s.shared("settings_leave_unsaved"), TextType.BODY)
    }

    pendingFill?.let { missing ->
        // The fields as the new config makes them, for their inputs
        val fields = remember(config) { toolType.getEntryFields(config, context).data.associateBy { it.definition.name } }
        // A field's default value is proposed, once, for the entries that lack a value
        LaunchedEffect(missing) {
            val defaults = missing.keys.mapNotNull { name -> fields[name]?.definition?.defaultValue?.let { name to it } }
                .filter { (name, _) -> !fill.has(name) }
            if (defaults.isNotEmpty()) fill = JSONObject(fill.toString()).apply { defaults.forEach { (name, value) -> put(name, JSONObject.wrap(value)) } }
        }
        UI.Dialog(
            type = com.assistant.core.ui.DialogType.CONFIRM,
            onConfirm = {
                pendingFill = null
                save(confirmed = false)
            },
            onCancel = { pendingFill = null },
            confirmEnabled = missing.keys.all { fill.has(it) }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("migration_fill_title"), TextType.SUBTITLE)
                missing.forEach { (name, count) ->
                    val field = fields[name]?.definition ?: return@forEach
                    UI.Text(s.shared("migration_fill_message").format(count, field.displayName), TextType.BODY)
                    com.assistant.core.fields.FieldInput(field, fill.opt(name)?.takeIf { it != JSONObject.NULL }, { value ->
                        fill = JSONObject(fill.toString()).apply { if (value == null) remove(name) else put(name, value) }
                    }, context)
                }
            }
        }
    }

    pendingMigration?.let { (removedValues, deletedEntries) ->
        UI.ConfirmDialog(
            title = s.shared("migration_confirm_title"),
            message = s.shared("migration_confirm_counts").format(removedValues, deletedEntries),
            onConfirm = {
                pendingMigration = null
                save(confirmed = true)
            },
            onDismiss = { pendingMigration = null }
        )
    }
}

/** The icon picker, the type's suggestions first; an absent icon shows the type's own. */
private fun iconEditor(suggested: List<String>, default: String) = object : SettingEditor {
    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        IconSelector(current = value as? String ?: default, suggested = suggested, onChange = { onChange(it) })
    }
}

/** The groups of the zone the tool is in, or none. */
private fun groupEditor(groups: List<String>, label: String) = object : SettingEditor {
    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        GroupSelector(availableGroups = groups, selectedGroup = value as? String, onGroupSelected = { onChange(it) }, label = label)
    }
}
