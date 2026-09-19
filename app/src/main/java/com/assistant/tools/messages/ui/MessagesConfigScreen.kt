package com.assistant.tools.messages.ui

import com.assistant.core.ui.NullableScheduleConfigSaver
import com.assistant.core.ui.FieldDefinitionsSaver
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.utils.LogManager
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.tools.ui.ToolGeneralConfigSection
import com.assistant.core.fields.CustomFieldsEditor
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.fields.toJsonArray
import com.assistant.core.fields.migration.rememberCustomFieldsMigrationHandler
import com.assistant.core.ai.ui.automation.ScheduleConfigEditor
import com.assistant.core.utils.ScheduleConfig
import com.assistant.core.utils.SchedulePattern
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Configuration screen for the Messages tool type.
 *
 * This screen IS the message. An instance is one notification template, so everything that
 * does not change from one send to the next lives here: the common title and body, the
 * priority of the channel, the recurrence that spawns occurrences, and the two knobs that
 * govern their lifecycle. What changes per send is written on the occurrence itself, in the
 * usage screen.
 *
 * A reminder whose text never varies needs nothing beyond the common part — which is why the
 * common title and body sit at the top, before the recurrence.
 */
@Composable
fun MessagesConfigScreen(
    zoneId: String,
    onSave: (config: String) -> Unit,
    onCancel: () -> Unit,
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null,
    initialGroup: String? = null
) {
    LogManager.ui("MessagesConfigScreen opened - existingToolId=$existingToolId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // General configuration states (8 base fields from ToolGeneralConfigSection)
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var iconName by rememberSaveable { mutableStateOf("notification") }
    var displayMode by rememberSaveable { mutableStateOf("LINE") }
    var management by rememberSaveable { mutableStateOf("manual") }
    var validateConfig by rememberSaveable { mutableStateOf(false) }
    var validateData by rememberSaveable { mutableStateOf(false) }
    var alwaysSend by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }

    // Messages-specific configuration states — the template itself
    var enabled by rememberSaveable { mutableStateOf(true) }
    var commonTitle by rememberSaveable { mutableStateOf("") }
    var commonContent by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("default") }
    var externalNotifications by rememberSaveable { mutableStateOf(true) }
    var creationHorizonDays by rememberSaveable { mutableStateOf("2") }
    var validityWindowMinutes by rememberSaveable { mutableStateOf("60") }
    var scheduleConfig by rememberSaveable(stateSaver = NullableScheduleConfigSaver) { mutableStateOf<ScheduleConfig?>(null) }
    var showScheduleEditor by rememberSaveable { mutableStateOf(false) }

    // Custom fields state
    var customFields by rememberSaveable(stateSaver = FieldDefinitionsSaver) { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    var oldCustomFields by rememberSaveable(stateSaver = FieldDefinitionsSaver) { mutableStateOf<List<FieldDefinition>>(emptyList()) }

    // Zone change tracking
    val isEditing = existingToolId != null
    var currentZoneId by rememberSaveable { mutableStateOf(zoneId) }

    // UI states
    var isLoading by remember { mutableStateOf(existingToolId != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing configuration if editing
    // Set once the stored config parsed: after a rotation the restored edits win over it, and
    // save stays off until then, since the defaults shown would overwrite it
    var configLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existingToolId) {
        if (existingToolId != null && configLoaded) {
            isLoading = false
        } else if (existingToolId != null) {
            LogManager.ui("Loading existing tool configuration for ID: $existingToolId")
            val result = coordinator.processUserAction(
                "tools.get",
                mapOf("tool_instance_id" to existingToolId)
            )

            if (result?.isSuccess == true) {
                val toolData = result.mapSingleData("tool_instance") { map -> map }
                toolData?.let { data ->
                    val configJson = data["config_json"] as? String ?: "{}"
                    try {
                        val config = JSONObject(configJson)
                        // Load general config
                        name = config.optString("name", "")
                        description = config.optString("description", "")
                        iconName = config.optString("icon_name", "notification")
                        displayMode = config.optString("display_mode", "LINE")
                        management = config.optString("management", "USER")
                        validateConfig = config.optBoolean("validateConfig", false)
                        validateData = config.optBoolean("validateData", false)
                        alwaysSend = config.optBoolean("always_send", false)
                        group = config.optString("group").takeIf { it.isNotEmpty() }

                        // Load Messages-specific config
                        enabled = config.optBoolean("enabled", true)
                        commonTitle = config.optString("common_title", "")
                        commonContent = config.optString("common_content", "")
                        priority = config.optString("priority", "default")
                        externalNotifications = config.optBoolean("external_notifications", true)
                        creationHorizonDays = config.optInt("creation_horizon_days", 2).toString()
                        validityWindowMinutes = config.optInt("validity_window_minutes", 60).toString()

                        config.optJSONObject("schedule")?.let { scheduleJson ->
                            scheduleConfig = try {
                                Json.decodeFromString<ScheduleConfig>(scheduleJson.toString())
                            } catch (e: Exception) {
                                LogManager.ui("Error parsing schedule config: ${e.message}", "ERROR")
                                null
                            }
                        }

                        // Load custom fields
                        val customFieldsArray = config.optJSONArray("custom_fields")
                        if (customFieldsArray != null) {
                            try {
                                customFields = customFieldsArray.toFieldDefinitions()
                                oldCustomFields = customFields.toList() // Save copy for migration comparison
                                LogManager.ui("Loaded ${customFields.size} custom fields")
                            } catch (e: Exception) {
                                LogManager.ui("Error parsing custom fields: ${e.message}", "ERROR")
                                // Keep empty list on error
                            }
                        }

                        LogManager.ui("Successfully loaded config: name=$name, enabled=$enabled, priority=$priority, external_notifications=$externalNotifications")
                        configLoaded = true
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing existing config: ${e.message}", "ERROR")
                        errorMessage = s.tool("error_config_load")
                    }
                }
            } else {
                LogManager.ui("Failed to load existing tool", "ERROR")
                errorMessage = s.tool("error_config_not_found")
            }
            isLoading = false
        }
    }

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Early return for loading state
    if (isLoading) {
        UI.Text(s.shared("tools_loading_config"), TextType.BODY)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Page header
        UI.PageHeader(
            title = if (existingToolId != null) s.shared("action_configure") else s.shared("action_create"),
            subtitle = s.tool("display_name"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onCancel
        )

        // Build config for ToolGeneralConfigSection
        val config by remember {
            derivedStateOf {
                JSONObject().apply {
                    put("name", name)
                    put("description", description)
                    put("icon_name", iconName)
                    put("display_mode", displayMode)
                    put("management", management)
                    put("validateConfig", validateConfig)
                    put("validateData", validateData)
                    put("always_send", alwaysSend)
                    group?.let { put("group", it) }
                }
            }
        }

        // General configuration section
        ToolGeneralConfigSection(
            config = config,
            updateConfig = { key, value ->
                when (key) {
                    "name" -> name = value as String
                    "description" -> description = value as String
                    "icon_name" -> iconName = value as String
                    "display_mode" -> displayMode = value as String
                    "management" -> management = value as String
                    "validateConfig" -> validateConfig = value as Boolean
                    "validateData" -> validateData = value as Boolean
                    "always_send" -> alwaysSend = value as Boolean
                    "group" -> group = value as? String
                }
            },
            toolTypeName = "messages",
            zoneId = currentZoneId,
            onZoneChange = { newZoneId -> currentZoneId = newZoneId },
            initialGroup = initialGroup,
            isEditing = isEditing
        )

        // The common part: what every send carries. A reminder whose text never varies is
        // complete with nothing more than this.
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.tool("label_common_title"), type = TextType.SUBTITLE)
                UI.Text(text = s.tool("hint_common_parts"), type = TextType.CAPTION)

                UI.FormField(
                    label = s.tool("label_common_title"),
                    value = commonTitle,
                    onChange = { commonTitle = it },
                    fieldType = FieldType.TEXT
                )

                UI.FormField(
                    label = s.tool("label_common_content"),
                    value = commonContent,
                    onChange = { commonContent = it },
                    fieldType = FieldType.TEXT_LONG
                )
            }
        }

        // Channel settings: how this stream is allowed to reach you
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.shared("label_configuration"), type = TextType.SUBTITLE)

                // Suspends the whole template: while off, nothing of this instance goes out,
                // including an occurrence placed by hand. Pending occurrences are kept, not
                // deleted — they are cancelled as their time comes, so suspending is reversible.
                UI.ToggleField(
                    label = s.tool("label_enabled"),
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )

                UI.FormSelection(
                    label = s.tool("label_priority"),
                    options = listOf(
                        s.tool("priority_default"),
                        s.tool("priority_high"),
                        s.tool("priority_low")
                    ),
                    selected = when (priority) {
                        "high" -> s.tool("priority_high")
                        "low" -> s.tool("priority_low")
                        else -> s.tool("priority_default")
                    },
                    onSelect = { selectedText ->
                        priority = when (selectedText) {
                            s.tool("priority_high") -> "high"
                            s.tool("priority_low") -> "low"
                            else -> "default"
                        }
                    }
                )

                UI.ToggleField(
                    label = s.tool("label_external_notifications"),
                    checked = externalNotifications,
                    onCheckedChange = { externalNotifications = it }
                )
            }
        }

        // Recurrence and the lifecycle of the occurrences it spawns
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(text = s.tool("label_schedule"), type = TextType.SUBTITLE)

                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showScheduleEditor = true }
                ) {
                    UI.Text(s.tool("action_configure_schedule"), TextType.LABEL)
                }

                UI.Text(
                    text = scheduleSummary(scheduleConfig, s),
                    type = if (scheduleConfig != null) TextType.BODY else TextType.CAPTION
                )

                UI.FormField(
                    label = s.tool("label_creation_horizon_days"),
                    value = creationHorizonDays,
                    onChange = { creationHorizonDays = it },
                    fieldType = FieldType.NUMERIC
                )

                UI.FormField(
                    label = s.tool("label_validity_window_minutes"),
                    value = validityWindowMinutes,
                    onChange = { validityWindowMinutes = it },
                    fieldType = FieldType.NUMERIC
                )
            }
        }

        // Custom fields editor
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CustomFieldsEditor(
                    fields = customFields,
                    onFieldsChange = { newFields ->
                        customFields = newFields
                        LogManager.ui("Custom fields updated: ${newFields.size} fields")
                    },
                    context = context
                )
            }
        }

        // Migration handler (reusable)
        val migrationHandler = rememberCustomFieldsMigrationHandler(
            toolInstanceId = existingToolId,
            oldFields = oldCustomFields,
            newFields = customFields,
            context = context,
            onSuccess = {
                // Migration succeeded or not needed - proceed with config save
                coroutineScope.launch {
                    isSaving = true
                    try {
                        // Build complete configuration with schema IDs
                        val configData = mutableMapOf<String, Any>(
                            "schema_id" to "messages_config",
                            "data_schema_id" to "messages_data",
                            "name" to name,
                            "description" to description,
                            "icon_name" to iconName,
                            "display_mode" to displayMode,
                            "management" to management,
                            "validateConfig" to validateConfig,
                            "validateData" to validateData,
                            "always_send" to alwaysSend,
                            "enabled" to enabled,
                            "priority" to priority,
                            "external_notifications" to externalNotifications,
                            "creation_horizon_days" to (creationHorizonDays.toIntOrNull() ?: 0),
                            "validity_window_minutes" to (validityWindowMinutes.toIntOrNull() ?: 0)
                        )
                        // Add group if present
                        group?.let { configData["group"] = it }

                        // Optional common parts: an empty one contributes nothing, so it is
                        // simply absent rather than stored as an empty string
                        commonTitle.takeIf { it.isNotBlank() }?.let { configData["common_title"] = it }
                        commonContent.takeIf { it.isNotBlank() }?.let { configData["common_content"] = it }

                        // Recurrence, without ScheduleConfig's own enabled flag: the switch of a
                        // Messages template is the root "enabled" above, and storing a second one
                        // here would leave two switches with only one of them read.
                        scheduleConfig?.let { schedule ->
                            configData["schedule"] = scheduleWithoutSwitch(schedule)
                        }

                        // Add custom fields if any
                        if (customFields.isNotEmpty()) {
                            configData["custom_fields"] = customFields.toJsonArray()
                        }

                        // Use unified ValidationHelper
                        UI.ValidationHelper.validateAndSave(
                            toolTypeName = "messages",
                            configData = configData,
                            context = context,
                            schemaType = "config",
                            onSuccess = { configJson ->
                                LogManager.ui("Messages config validation success - checking zone change")

                                // If zone changed and we're editing, update zone_id FIRST
                                if (isEditing && currentZoneId != zoneId && existingToolId != null) {
                                    LogManager.ui("Zone changed detected - updating from $zoneId to $currentZoneId BEFORE config save", "DEBUG")
                                    coroutineScope.launch {
                                        val zoneUpdateResult = coordinator.processUserAction(
                                            "tools.update",
                                            mapOf(
                                                "tool_instance_id" to existingToolId,
                                                "zone_id" to currentZoneId
                                            )
                                        )
                                        if (zoneUpdateResult.status != com.assistant.core.commands.CommandStatus.SUCCESS) {
                                            LogManager.ui("Failed to update zone: ${zoneUpdateResult.error}", "ERROR")
                                        } else {
                                            LogManager.ui("Zone updated successfully to $currentZoneId, now saving config", "DEBUG")
                                        }
                                        onSave(configJson)
                                    }
                                } else {
                                    LogManager.ui("No zone change - saving config normally", "DEBUG")
                                    onSave(configJson)
                                }
                            },
                            onError = { error ->
                                LogManager.ui("Messages config validation failed: $error", "ERROR")
                                errorMessage = error
                            }
                        )
                    } catch (e: Exception) {
                        LogManager.ui("Error during save: ${e.message}", "ERROR")
                        errorMessage = s.tool("error_save")
                    } finally {
                        isSaving = false
                    }
                }
            },
            onError = { error ->
                errorMessage = error
            }
        )

        // Form actions
        val handleSave = {
            // Check migration first, then proceed to save
            migrationHandler.checkAndProceed()
        }

        UI.ToolConfigActions(
            isEditing = existingToolId != null,
            onSave = handleSave,
            onCancel = onCancel,
            onDelete = onDelete,
            saveEnabled = !isSaving && (existingToolId == null || configLoaded)
        )
    }

    // Nested recurrence editor, opens on top of the config screen
    if (showScheduleEditor) {
        ScheduleConfigEditor(
            existingConfig = scheduleConfig,
            onConfirm = { config ->
                scheduleConfig = config // null accepted: no recurrence, a channel fed on demand
                showScheduleEditor = false
                LogManager.ui("Schedule updated: ${if (config != null) "configured" else "cleared"}")
            },
            onDismiss = { showScheduleEditor = false }
        )
    }
}

/**
 * Serializes a recurrence for storage, dropping ScheduleConfig's own "enabled" flag.
 *
 * A Messages template is suspended by the "enabled" field at the root of its config, which
 * covers everything the instance owes and exists even without a recurrence. ScheduleConfig
 * carries a flag of the same name that Messages never reads; storing it would leave two
 * switches with one silently ignored. Its Kotlin default is true, so a recurrence read back
 * without it deserializes unchanged.
 */
private fun scheduleWithoutSwitch(schedule: ScheduleConfig): Map<String, Any> {
    val json = JSONObject(Json.encodeToString(ScheduleConfig.serializer(), schedule))
    json.remove("enabled")
    return json.toMap()
}

/** Recursive JSONObject to Map, so the config payload stays plain Kotlin collections. */
private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        when (val value = get(key)) {
            is JSONObject -> map[key] = value.toMap()
            is org.json.JSONArray -> map[key] = value.toList()
            JSONObject.NULL -> Unit // absent rather than null
            else -> map[key] = value
        }
    }
    return map
}

private fun org.json.JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        when (val value = get(i)) {
            is JSONObject -> list.add(value.toMap())
            is org.json.JSONArray -> list.add(value.toList())
            else -> list.add(value)
        }
    }
    return list
}

/** One line describing the recurrence, or what its absence means. */
private fun scheduleSummary(schedule: ScheduleConfig?, s: StringsContext): String {
    if (schedule == null) return s.tool("schedule_summary_not_configured")
    return when (val pattern = schedule.pattern) {
        is SchedulePattern.DailyMultiple -> s.tool("schedule_summary_daily").format(pattern.times.size)
        is SchedulePattern.WeeklySimple -> s.tool("schedule_summary_weekly")
        is SchedulePattern.WeeklyCustom -> s.tool("schedule_summary_weekly_custom")
        is SchedulePattern.MonthlyRecurrent -> s.tool("schedule_summary_monthly")
        is SchedulePattern.YearlyRecurrent -> s.tool("schedule_summary_yearly")
        is SchedulePattern.SpecificDates -> s.tool("schedule_summary_specific_dates")
    }
}
