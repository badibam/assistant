package com.assistant.tools.messages.ui

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
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.tools.ui.ToolGeneralConfigSection
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Configuration screen for Messages tool type
 *
 * Displays:
 * - General tool configuration (ToolGeneralConfigSection)
 * - Messages-specific fields (default_priority, external_notifications)
 *
 * Pattern reference: NotesConfigScreen (minimal configuration)
 */
@Composable
fun MessagesConfigScreen(
    zoneId: String,
    onSave: (config: String) -> Unit,
    onCancel: () -> Unit,
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null
) {
    LogManager.ui("MessagesConfigScreen opened - existingToolId=$existingToolId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // General configuration states (8 base fields from ToolGeneralConfigSection)
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var iconName by remember { mutableStateOf("notification") }
    var displayMode by remember { mutableStateOf("LINE") }
    var management by remember { mutableStateOf("manual") }
    var validateConfig by remember { mutableStateOf(false) }
    var validateData by remember { mutableStateOf(false) }
    var alwaysSend by remember { mutableStateOf(false) }

    // Messages-specific configuration states
    var defaultPriority by remember { mutableStateOf("default") }
    var externalNotifications by remember { mutableStateOf(true) }

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
                        // Load general config
                        name = config.optString("name", "")
                        description = config.optString("description", "")
                        iconName = config.optString("icon_name", "notification")
                        displayMode = config.optString("display_mode", "LINE")
                        management = config.optString("management", "USER")
                        validateConfig = config.optBoolean("validateConfig", false)
                        validateData = config.optBoolean("validateData", false)
                        alwaysSend = config.optBoolean("always_send", false)

                        // Load Messages-specific config
                        defaultPriority = config.optString("default_priority", "default")
                        externalNotifications = config.optBoolean("external_notifications", true)

                        LogManager.ui("Successfully loaded config: name=$name, default_priority=$defaultPriority, external_notifications=$externalNotifications")
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
                }
            },
            toolTypeName = "messages"
        )

        // Messages-specific configuration section
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(
                    text = s.shared("label_configuration"),
                    type = TextType.SUBTITLE
                )

                // Default priority selection
                UI.FormSelection(
                    label = s.tool("label_default_priority"),
                    options = listOf(
                        s.tool("priority_default"),
                        s.tool("priority_high"),
                        s.tool("priority_low")
                    ),
                    selected = when (defaultPriority) {
                        "default" -> s.tool("priority_default")
                        "high" -> s.tool("priority_high")
                        "low" -> s.tool("priority_low")
                        else -> s.tool("priority_default")
                    },
                    onSelect = { selectedText ->
                        defaultPriority = when (selectedText) {
                            s.tool("priority_high") -> "high"
                            s.tool("priority_low") -> "low"
                            else -> "default"
                        }
                    }
                )

                // External notifications toggle
                UI.ToggleField(
                    label = s.tool("label_external_notifications"),
                    checked = externalNotifications,
                    onCheckedChange = { externalNotifications = it }
                )
            }
        }

        // Form actions
        val handleSave = {
            coroutineScope.launch {
                isSaving = true
                try {
                    // Build complete configuration with schema IDs
                    val configData = mapOf(
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
                        "default_priority" to defaultPriority,
                        "external_notifications" to externalNotifications
                    )

                    // Use unified ValidationHelper
                    UI.ValidationHelper.validateAndSave(
                        toolTypeName = "messages",
                        configData = configData,
                        context = context,
                        schemaType = "config",
                        onSuccess = { configJson ->
                            LogManager.ui("Messages config validation success")
                            onSave(configJson)
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
