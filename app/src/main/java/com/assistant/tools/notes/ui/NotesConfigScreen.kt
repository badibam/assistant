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
                "tools.get_single",
                mapOf("tool_instance_id" to existingToolId)
            )

            if (result?.isSuccess == true) {
                val toolData = result.mapSingleData("tool_instance") { map -> map }
                toolData?.let { data ->
                    val configJson = data["config"] as? String ?: "{}"
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
                        errorMessage = "Erreur lors du chargement de la configuration"
                    }
                }
            } else {
                LogManager.ui("Failed to load existing tool", "ERROR")
                errorMessage = "Impossible de charger la configuration existante"
            }
            isLoading = false
        }
    }

    // Note: No validation here - validation happens at save time like Tracking does

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Early return for loading state
    if (isLoading) {
        UI.Text(s.shared("message_loading"), TextType.BODY)
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

        // Form actions - using standard ToolConfigActions like Tracking
        val handleSave = {
            coroutineScope.launch {
                isSaving = true
                try {
                    val configData = mapOf(
                        "name" to name,
                        "description" to description,
                        "icon_name" to iconName,
                        "display_mode" to displayMode,
                        "management" to management,
                        "config_validation" to configValidation,
                        "data_validation" to dataValidation
                    )

                    // Validate at save time like Tracking does
                    val toolType = ToolTypeManager.getToolType("notes")
                    if (toolType != null) {
                        val validation = SchemaValidator.validate(toolType, configData, context, "config")

                        if (validation.isValid) {
                            val configJson = JSONObject().apply {
                                put("name", name)
                                put("description", description)
                                put("icon_name", iconName)
                                put("display_mode", displayMode)
                                put("management", management)
                                put("config_validation", configValidation)
                                put("data_validation", dataValidation)
                            }
                            LogManager.ui("Saving Notes config: $configJson")
                            onSave(configJson.toString())
                        } else {
                            LogManager.ui("Validation failed: ${validation.errorMessage}", "ERROR")
                            errorMessage = validation.errorMessage ?: "Erreur de validation"
                        }
                    } else {
                        LogManager.ui("ToolType notes not found", "ERROR")
                        errorMessage = "Type d'outil introuvable"
                    }
                } catch (e: Exception) {
                    LogManager.ui("Error during save: ${e.message}", "ERROR")
                    errorMessage = "Erreur lors de la sauvegarde"
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