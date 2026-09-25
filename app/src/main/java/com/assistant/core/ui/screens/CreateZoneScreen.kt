package com.assistant.core.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.database.entities.Zone
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.settings.SettingEditor
import com.assistant.core.fields.settings.SettingsForm
import com.assistant.core.schemas.ZoneSettings
import com.assistant.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Screen for creating or editing a zone: the form of its declaration (ZoneSettings), with the
 * icon picker and the main screen's zone groups drawn by their own editors. It saves through
 * the service, which checks the zone and says what it refuses.
 */
@Composable
fun CreateZoneScreen(
    existingZone: Zone? = null,
    preSelectedGroup: String? = null,
    onCancel: () -> Unit,
    onCreate: (() -> Unit)? = null,
    onUpdate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    // No page header here: the back key cancels, like the form's cancel button
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coroutineScope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    val nodes = remember { ZoneSettings.nodes(context) }
    val isEditing = existingZone != null

    // The zone's settings as one object, the form's single source of truth
    var settings by rememberSaveable(existingZone, stateSaver = JsonObjectSaver) {
        mutableStateOf(JSONObject().apply {
            existingZone?.let { zone ->
                put("name", zone.name)
                zone.description?.let { put("description", it) }
                zone.icon_name?.let { put("icon_name", it) }
                zone.tool_groups?.let { put("tool_groups", JSONArray(it)) }
            }
            (existingZone?.group ?: preSelectedGroup)?.let { put("group", it) }
        })
    }

    // The zone groups of the main screen
    var availableZoneGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val result = coordinator.processUserAction("app_config.get", mapOf("category" to com.assistant.core.database.entities.AppSettingCategories.MAIN_SCREEN))
        if (result.isSuccess) {
            availableZoneGroups = ((result.data?.get("settings") as Map<*, *>)["zone_groups"] as List<*>).map { it as String }
        }
    }

    val editors = mapOf(
        "icon_name" to object : SettingEditor {
            @Composable
            override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                com.assistant.core.ui.components.IconSelector(current = value as? String ?: "", suggested = ZoneSettings.SUGGESTED_ICONS, onChange = { onChange(it) })
            }
        },
        "group" to object : SettingEditor {
            @Composable
            override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                com.assistant.core.ui.components.GroupSelector(availableGroups = availableZoneGroups, selectedGroup = value as? String,
                    onGroupSelected = { onChange(it) }, label = s.shared("label_group"))
            }
        }
    )

    fun save() {
        coroutineScope.launch {
            val given = JsonUtils.toMap(settings)
            val result = if (existingZone != null) {
                // Every setting is sent: an absent one is sent as null, which empties it
                val params = mutableMapOf<String, Any?>("zone_id" to existingZone.id)
                listOf("name", "description", "icon_name", "group", "tool_groups").forEach { params[it] = given[it] ?: JSONObject.NULL }
                coordinator.processUserAction("zones.update", params)
            } else {
                coordinator.processUserAction("zones.create", given)
            }
            when {
                !result.isSuccess -> UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
                isEditing -> onUpdate?.invoke()
                else -> onCreate?.invoke()
            }
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.Text(
            text = if (isEditing) s.shared("action_edit_zone") else s.shared("action_create_zone"),
            type = TextType.TITLE,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsForm(nodes, settings, { settings = it }, context, editors)

        UI.FormActions {
            UI.ActionButton(
                action = if (isEditing) ButtonAction.SAVE else ButtonAction.CREATE,
                onClick = { save() }
            )

            UI.ActionButton(
                action = ButtonAction.CANCEL,
                onClick = onCancel
            )

            if (existingZone != null && onDelete != null) {
                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    requireConfirmation = true,
                    confirmMessage = "${s.shared("message_delete_zone_confirmation").format(existingZone.name)} ${s.shared("message_irreversible_action")}",
                    onClick = {
                        coroutineScope.launch {
                            val result = coordinator.processUserAction("zones.delete", mapOf("zone_id" to existingZone.id))
                            if (result.isSuccess) onDelete.invoke()
                            else UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
                        }
                    }
                )
            }
        }
    }
}
