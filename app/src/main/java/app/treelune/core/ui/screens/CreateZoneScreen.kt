package app.treelune.core.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.ui.UI
import app.treelune.core.ui.*
import app.treelune.core.strings.Strings
import app.treelune.core.database.entities.Zone
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.settings.SettingEditor
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.schemas.ZoneSettings
import app.treelune.core.utils.JsonUtils
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
    // The page of the form open, which the actions show under alone
    val pages = app.treelune.core.fields.settings.rememberSettingsPages()
    val nodes = remember { ZoneSettings.nodes(context) }
    val isEditing = existingZone != null

    // The zone's settings as one object, the form's single source of truth
    var settings by rememberSaveable(existingZone, stateSaver = JsonObjectSaver) {
        mutableStateOf(JSONObject().apply {
            existingZone?.let { zone ->
                put("name", zone.name)
                zone.description?.let { put("description", it) }
                zone.icon_name?.let { put("icon_name", it) }
                zone.icon_color?.let { put(app.treelune.core.themes.IconColor.KEY, it) }
                zone.tool_groups?.let { put("tool_groups", JSONArray(it)) }
                put("display_mode", zone.display_mode)
                put("validate", zone.validate)
            }
            (existingZone?.group ?: preSelectedGroup)?.let { put("group", it) }
        })
    }

    // The zone groups of the main screen
    var availableZoneGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val result = coordinator.processUserAction("app_config.get", mapOf("category" to app.treelune.core.database.entities.AppSettingCategories.MAIN_SCREEN))
        if (result.isSuccess) {
            availableZoneGroups = ((result.data?.get("settings") as Map<*, *>)["zone_groups"] as List<*>).map { it as String }
        }
    }

    val editors = mapOf(
        "icon_name" to object : SettingEditor {
            @Composable
            override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                app.treelune.core.ui.components.IconSelector(current = value as? String ?: "",
                    color = app.treelune.core.themes.IconColor.of(settings.optString(app.treelune.core.themes.IconColor.KEY)),
                    suggested = ZoneSettings.SUGGESTED_ICONS, onChange = { onChange(it) })
            }
        },
        app.treelune.core.themes.IconColor.KEY to object : SettingEditor {
            @Composable
            override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                app.treelune.core.ui.components.IconColorSelector(current = value as? String, iconName = settings.optString("icon_name"), onChange = { onChange(it) })
            }
        },
        "group" to object : SettingEditor {
            @Composable
            override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                app.treelune.core.ui.components.GroupSelector(availableGroups = availableZoneGroups, selectedGroup = value as? String,
                    onGroupSelected = { onChange(it) }, label = s.shared("label_zone_group"))
            }
        }
    )

    // The names the zone's tool groups came with: a group renamed here is renamed in its tools,
    // automations and variables
    val origins = remember(existingZone) { app.treelune.core.fields.settings.ListOrigins() }

    fun save() {
        coroutineScope.launch {
            val given = JsonUtils.toMap(settings)
            val result = if (existingZone != null) {
                // Every setting is sent: an absent one is sent as null, which empties it
                val params = mutableMapOf<String, Any?>("zone_id" to existingZone.id)
                listOf("name", "description", "icon_name", app.treelune.core.themes.IconColor.KEY, "group", "tool_groups", "display_mode", "validate").forEach { params[it] = given[it] ?: JSONObject.NULL }
                params["renames"] = mapOf("tool_groups" to origins.renames("tool_groups", settings.optJSONArray("tool_groups")))
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
            .padding(UI.Space.XL),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.Text(
            text = if (isEditing) s.shared("action_edit_zone") else s.shared("action_create_zone"),
            type = TextType.TITLE,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(UI.Space.S))

        SettingsForm(nodes, settings, { settings = it }, context, editors, origins = origins, pages = pages)

        // The actions on the root page alone: under it, the back arrow goes up (SettingsPages)
        if (pages.atRoot) UI.FormActions {
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
