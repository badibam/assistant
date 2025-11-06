package com.assistant.tools.journal.ui

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
import com.assistant.core.tools.ui.ToolGeneralConfigSection
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Configuration screen for Journal tool type
 * Includes general configuration section and sort order selection
 */
@Composable
fun JournalConfigScreen(
    zoneId: String,
    onSave: (config: String) -> Unit,
    onCancel: () -> Unit,
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null,
    initialGroup: String? = null
) {
    LogManager.ui("JournalConfigScreen opened - existingToolId=$existingToolId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "journal", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Configuration states - general
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var iconName by remember { mutableStateOf("book-open") }
    var displayMode by remember { mutableStateOf("EXTENDED") }
    var management by remember { mutableStateOf("manual") }
    var validateConfig by remember { mutableStateOf(false) }
    var validateData by remember { mutableStateOf(false) }
    var alwaysSend by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf<String?>(null) }

    // Configuration states - journal specific
    var sortOrder by remember { mutableStateOf("descending") }

    // UI states
    var isLoading by remember { mutableStateOf(existingToolId != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing configuration if editing
    LaunchedEffect(existingToolId) {
        if (existingToolId != null) {
            LogManager.ui("Loading existing journal configuration for ID: $existingToolId")
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
                        iconName = config.optString("icon_name", "book-open")
                        displayMode = config.optString("display_mode", "EXTENDED")
                        management = config.optString("management", "manual")
                        validateConfig = config.optBoolean("validateConfig", false)
                        validateData = config.optBoolean("validateData", false)
                        alwaysSend = config.optBoolean("always_send", false)
                        group = config.optString("group").takeIf { it.isNotEmpty() }
                        sortOrder = config.optString("sort_order", "descending")
                        LogManager.ui("Successfully loaded journal config: name=$name, sortOrder=$sortOrder")
                    } catch (e: Exception) {
                        LogManager.ui("Error parsing existing config: ${e.message}", "ERROR")
                        errorMessage = s.tool("error_config_load")
                    }
                }
            } else {
                LogManager.ui("Failed to load existing journal tool", "ERROR")
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

        // Derived config for ToolGeneralConfigSection
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
            toolTypeName = "journal",
            zoneId = zoneId,
            initialGroup = initialGroup
        )

        // Journal-specific configuration section
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = s.tool("config_section_order"),
                    type = TextType.SUBTITLE,
                    fillMaxWidth = true
                )

                // Sort order selection
                UI.FormSelection(
                    label = s.tool("label_sort_order"),
                    options = listOf(
                        s.tool("sort_order_ascending"),
                        s.tool("sort_order_descending")
                    ),
                    selected = when (sortOrder) {
                        "ascending" -> s.tool("sort_order_ascending")
                        else -> s.tool("sort_order_descending")
                    },
                    onSelect = { selectedLabel ->
                        sortOrder = when (selectedLabel) {
                            s.tool("sort_order_ascending") -> "ascending"
                            else -> "descending"
                        }
                    },
                    required = true
                )
            }
        }

        // Form actions
        val handleSave = {
            coroutineScope.launch {
                isSaving = true
                try {
                    val configData = mutableMapOf<String, Any>(
                        "schema_id" to "journal_config",  // Add schema_id for validation
                        "data_schema_id" to "journal_data", // Add data_schema_id for runtime
                        "name" to name,
                        "description" to description,
                        "icon_name" to iconName,
                        "display_mode" to displayMode,
                        "management" to management,
                        "validateConfig" to validateConfig,
                        "validateData" to validateData,
                        "always_send" to alwaysSend,
                        "sort_order" to sortOrder
                    )
                    // Add group if present
                    group?.let { configData["group"] = it }

                    // Use unified ValidationHelper
                    UI.ValidationHelper.validateAndSave(
                        toolTypeName = "journal",
                        configData = configData,
                        context = context,
                        schemaType = "config",
                        onSuccess = { configJson ->
                            LogManager.ui("Journal config validation success")
                            onSave(configJson)
                        },
                        onError = { error ->
                            LogManager.ui("Journal config validation failed: $error", "ERROR")
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
