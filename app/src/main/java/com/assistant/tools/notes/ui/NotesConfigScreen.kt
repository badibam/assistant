package com.assistant.tools.notes.ui

import com.assistant.core.ui.FieldDefinitionsSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.tools.ui.ToolGeneralConfigSection
import com.assistant.core.fields.CustomFieldsEditor
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.fields.toJsonArray
import com.assistant.core.fields.migration.rememberCustomFieldsMigrationHandler
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Configuration screen for Notes tool type
 * Uses minimal configuration - only general tool configuration section
 */
@Composable
fun NotesConfigScreen(
    zoneId: String,
    onSave: (config: String) -> Unit,
    onCancel: () -> Unit,
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null,
    initialGroup: String? = null
) {
    LogManager.ui("NotesConfigScreen opened - existingToolId=$existingToolId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Configuration states
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var iconName by rememberSaveable { mutableStateOf("note") }
    var displayMode by rememberSaveable { mutableStateOf("EXTENDED") }
    var management by rememberSaveable { mutableStateOf("manual") }
    var validateConfig by rememberSaveable { mutableStateOf(false) }
    var validateData by rememberSaveable { mutableStateOf(false) }
    var alwaysSend by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }

    // Custom fields state
    var customFields by rememberSaveable(stateSaver = FieldDefinitionsSaver) { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    var oldCustomFields by rememberSaveable(stateSaver = FieldDefinitionsSaver) { mutableStateOf<List<FieldDefinition>>(emptyList()) }

    // Zone change tracking
    val isEditing = existingToolId != null
    var currentZoneId by rememberSaveable { mutableStateOf(zoneId) }

    // UI states
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing configuration if editing; a new tool has nothing to load
    val configLoad = rememberLoadOnce(existingToolId) {
        if (existingToolId == null) return@rememberLoadOnce true
        var loaded = false
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
                    name = config.optString("name", "")
                    description = config.optString("description", "")
                    iconName = config.optString("icon_name", "note")
                    displayMode = config.optString("display_mode", "EXTENDED")
                    management = config.optString("management", "manual")
                    validateConfig = config.optBoolean("validate_config", false)
                    validateData = config.optBoolean("validate_data", false)
                    alwaysSend = config.optBoolean("always_send", false)
                    group = config.optString("group").takeIf { it.isNotEmpty() }

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

                    LogManager.ui("Successfully loaded tool config: name=$name, description=$description, icon=$iconName, displayMode=$displayMode")
                    loaded = true
                } catch (e: Exception) {
                    LogManager.ui("Error parsing existing config: ${e.message}", "ERROR")
                    errorMessage = s.tool("error_config_load")
                }
            }
        } else {
            LogManager.ui("Failed to load existing tool", "ERROR")
            errorMessage = s.tool("error_config_not_found")
        }
        loaded
    }

    // Note: No validation here - validation happens at save time

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Early return for loading state
    if (configLoad == LoadState.LOADING) {
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

        val config by remember {
            derivedStateOf {
                JSONObject().apply {
                    put("name", name)
                    put("description", description)
                    put("icon_name", iconName)
                    put("display_mode", displayMode)
                    put("management", management)
                    put("validate_config", validateConfig)
                    put("validate_data", validateData)
                    put("always_send", alwaysSend)
                    group?.let { put("group", it) }
                }
            }
        }

        ToolGeneralConfigSection(
            config = config,
            updateConfig = { key, value ->
                when (key) {
                    "name" -> name = value as String
                    "description" -> description = value as String
                    "icon_name" -> iconName = value as String
                    "display_mode" -> displayMode = value as String
                    "management" -> management = value as String
                    "validate_config" -> validateConfig = value as Boolean
                    "validate_data" -> validateData = value as Boolean
                    "always_send" -> alwaysSend = value as Boolean
                    "group" -> group = value as? String
                }
            },
            toolTypeName = "notes",
            zoneId = currentZoneId,
            onZoneChange = { newZoneId -> currentZoneId = newZoneId },
            initialGroup = initialGroup,
            isEditing = isEditing
        )

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
                        val configData = mutableMapOf<String, Any>(
                            "schema_id" to "notes_config",  // Add schema_id for validation
                            "data_schema_id" to "notes_data", // Add data_schema_id for runtime
                            "name" to name,
                            "description" to description,
                            "icon_name" to iconName,
                            "display_mode" to displayMode,
                            "management" to management,
                            "validate_config" to validateConfig,
                            "validate_data" to validateData,
                            "always_send" to alwaysSend
                        )
                        // Add group if present
                        group?.let { configData["group"] = it }

                        // Add custom fields if any
                        if (customFields.isNotEmpty()) {
                            configData["custom_fields"] = customFields.toJsonArray()
                        }

                        // Use unified ValidationHelper
                        UI.ValidationHelper.validateAndSave(
                            toolTypeName = "notes",
                            configData = configData,
                            context = context,
                            schemaType = "config",
                            onSuccess = { configJson ->
                                LogManager.ui("Notes config validation success - checking zone change")

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
                                LogManager.ui("Notes config validation failed: $error", "ERROR")
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

        // Form actions - using standard ToolConfigActions
        val handleSave = {
            // Check migration first, then proceed to save
            migrationHandler.checkAndProceed()
        }

        UI.ToolConfigActions(
            isEditing = existingToolId != null,
            onSave = handleSave,
            onCancel = onCancel,
            onDelete = onDelete,
            saveEnabled = !isSaving && configLoad == LoadState.LOADED
        )
    }
}