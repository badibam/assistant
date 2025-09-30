package com.assistant.tools.notes.ui

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
    onDelete: (() -> Unit)? = null
) {
    LogManager.ui("NotesConfigScreen opened - existingToolId=$existingToolId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Configuration states
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var iconName by remember { mutableStateOf("note") }
    var displayMode by remember { mutableStateOf("EXTENDED") }
    var management by remember { mutableStateOf("manual") }
    var configValidation by remember { mutableStateOf("disabled") }
    var dataValidation by remember { mutableStateOf("disabled") }

    // UI states
    var isLoading by remember { mutableStateOf(existingToolId != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing configuration if editing
    LaunchedEffect(existingToolId) {
        if (existingToolId != null) {
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
                        configValidation = config.optString("config_validation", "disabled")
                        dataValidation = config.optString("data_validation", "disabled")
                        LogManager.ui("Successfully loaded tool config: name=$name, description=$description, icon=$iconName, displayMode=$displayMode")
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

    // Note: No validation here - validation happens at save time

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

        val config by remember {
            derivedStateOf {
                JSONObject().apply {
                    put("name", name)
                    put("description", description)
                    put("icon_name", iconName)
                    put("display_mode", displayMode)
                    put("management", management)
                    put("config_validation", configValidation)
                    put("data_validation", dataValidation)
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
                    "config_validation" -> configValidation = value as String
                    "data_validation" -> dataValidation = value as String
                }
            },
            toolTypeName = "notes"
        )

        // Form actions - using standard ToolConfigActions
        val handleSave = {
            coroutineScope.launch {
                isSaving = true
                try {
                    val configData = mapOf(
                        "schema_id" to "notes_config",  // Add schema_id for validation
                        "data_schema_id" to "notes_data", // Add data_schema_id for runtime
                        "name" to name,
                        "description" to description,
                        "icon_name" to iconName,
                        "display_mode" to displayMode,
                        "management" to management,
                        "config_validation" to configValidation,
                        "data_validation" to dataValidation
                    )

                    // Use unified ValidationHelper
                    UI.ValidationHelper.validateAndSave(
                        toolTypeName = "notes",
                        configData = configData,
                        context = context,
                        schemaType = "config",
                        onSuccess = { configJson ->
                            LogManager.ui("Notes config validation success")
                            onSave(configJson)
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
        }

        UI.ToolConfigActions(
            isEditing = existingToolId != null,
            onSave = handleSave,
            onCancel = onCancel,
            onDelete = onDelete,
            saveEnabled = !isSaving
        )
    }
}