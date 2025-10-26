package com.assistant.tools.messages.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleConfig
import com.assistant.core.ai.ui.automation.ScheduleConfigEditor
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.tools.ToolTypeManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Edit/Create Message Dialog
 *
 * FullScreenDialog for creating or editing message templates with:
 * - Title field (SHORT_LENGTH, required)
 * - Content field (LONG_LENGTH, optional)
 * - Priority selection (default/high/low)
 * - Schedule configuration via nested ScheduleConfigEditor
 * - Triggers section (disabled, stub for future)
 *
 * Pattern reference: EditNoteDialog (FullScreenDialog, validation before save)
 * Nested dialogs: ScheduleConfigEditor opens on top of this dialog
 */
@Composable
fun EditMessageDialog(
    isVisible: Boolean,
    toolInstanceId: String,
    defaultPriority: String = "default",
    initialMessage: MessageDialogData? = null, // null = creation mode
    onConfirm: suspend (title: String, content: String?, schedule: ScheduleConfig?, priority: String) -> Boolean,
    onCancel: () -> Unit
) {
    // State management
    var title by remember(isVisible, initialMessage) {
        mutableStateOf(initialMessage?.title ?: "")
    }
    var content by remember(isVisible, initialMessage) {
        mutableStateOf(initialMessage?.content ?: "")
    }
    var priority by remember(isVisible, initialMessage) {
        mutableStateOf(initialMessage?.priority ?: defaultPriority)
    }
    var scheduleConfig by remember(isVisible, initialMessage) {
        mutableStateOf<ScheduleConfig?>(initialMessage?.schedule)
    }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationResult: ValidationResult by remember { mutableStateOf(ValidationResult.success()) }

    // Schedule editor nested dialog state
    var showScheduleEditor by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Get strings
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "messages", context = context) }

    // Reset validation when dialog opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            validationResult = ValidationResult.success()
        }
    }

    // Validation function
    fun validateForm() {
        val toolType = ToolTypeManager.getToolType("messages")
        if (toolType != null) {
            // Serialize ScheduleConfig to Map for validation (recursive conversion)
            val scheduleMap = scheduleConfig?.let {
                // Configure JSON to encode defaults (timezone, enabled, etc.)
                val json = kotlinx.serialization.json.Json {
                    encodeDefaults = true
                }
                val jsonString = json.encodeToString(
                    ScheduleConfig.serializer(),
                    it
                )
                LogManager.ui("ScheduleConfig serialized JSON: $jsonString")
                // Convert JSON string to Map recursively
                val map = convertJsonToMap(JSONObject(jsonString))
                LogManager.ui("ScheduleConfig converted Map: $map")
                map
            }

            // Build data structure like service expects
            val messageData = mapOf(
                "tool_instance_id" to toolInstanceId,
                "tooltype" to "messages",
                "name" to title.trim(),  // Title goes in name field
                "timestamp" to System.currentTimeMillis(),
                "data" to mapOf(
                    "schema_id" to "messages_data",
                    "content" to content.trim().takeIf { it.isNotEmpty() },
                    "schedule" to scheduleMap,
                    "priority" to priority,
                    "triggers" to null,
                    "executions" to emptyList<Map<String, Any>>()  // Required by schema
                )
            )

            val schema = toolType.getSchema("messages_data", context)
            validationResult = if (schema != null) {
                SchemaValidator.validate(schema, messageData, context)
            } else {
                ValidationResult.error("Messages data schema not found")
            }
            LogManager.ui("Messages validation result: isValid=${validationResult.isValid}")
            if (!validationResult.isValid) {
                LogManager.ui("Messages validation error: ${validationResult.errorMessage}")
            }
        } else {
            validationResult = ValidationResult.error("Tool type 'messages' not found")
        }
    }

    // Dialog title
    val dialogTitle = if (initialMessage == null) {
        s.tool("create_message_title")
    } else {
        s.tool("edit_message_title")
    }

    // Get schedule summary for display
    fun getScheduleSummary(config: ScheduleConfig?): String {
        if (config == null) return s.tool("schedule_summary_on_demand")
        // TODO: Use ScheduleFormatter when available for proper summary
        return when (config.pattern) {
            is com.assistant.core.utils.SchedulePattern.DailyMultiple -> "Quotidien (${(config.pattern as com.assistant.core.utils.SchedulePattern.DailyMultiple).times.size} fois)"
            is com.assistant.core.utils.SchedulePattern.WeeklySimple -> "Hebdomadaire"
            is com.assistant.core.utils.SchedulePattern.MonthlyRecurrent -> "Mensuel"
            is com.assistant.core.utils.SchedulePattern.WeeklyCustom -> "Hebdomadaire personnalisé"
            is com.assistant.core.utils.SchedulePattern.YearlyRecurrent -> "Annuel"
            is com.assistant.core.utils.SchedulePattern.SpecificDates -> "Dates spécifiques"
        }
    }

    if (isVisible) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                if (!isSaving) {
                    isSaving = true

                    // Validate before sending
                    validateForm()
                    if (validationResult.isValid) {
                        // Use coroutine to call suspend function
                        coroutineScope.launch {
                            try {
                                val success = onConfirm(
                                    title.trim(),
                                    content.trim().takeIf { it.isNotEmpty() },
                                    scheduleConfig,
                                    priority
                                )
                                if (!success) {
                                    errorMessage = s.shared("message_error_simple")
                                }
                            } catch (e: Exception) {
                                LogManager.ui("Error saving message: ${e.message}", "ERROR")
                                errorMessage = s.shared("message_error").format(e.message ?: "")
                            } finally {
                                isSaving = false
                            }
                        }
                    } else {
                        // Validation failed, show error
                        errorMessage = validationResult.errorMessage ?: s.shared("message_validation_error_simple")
                        isSaving = false
                    }
                }
            },
            onCancel = onCancel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(dialogTitle, TextType.TITLE)

                // Title field (required, SHORT_LENGTH)
                UI.FormField(
                    label = s.tool("label_title"),
                    value = title,
                    onChange = { title = it },
                    required = true,
                    fieldType = FieldType.TEXT
                )

                // Content field (optional, LONG_LENGTH)
                UI.FormField(
                    label = s.tool("label_content"),
                    value = content,
                    onChange = { content = it },
                    required = false,
                    fieldType = FieldType.TEXT_LONG
                )

                // Priority selection
                UI.FormSelection(
                    label = s.tool("label_priority"),
                    options = listOf(
                        s.tool("priority_default"),
                        s.tool("priority_high"),
                        s.tool("priority_low")
                    ),
                    selected = when (priority) {
                        "default" -> s.tool("priority_default")
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

                // Schedule configuration section
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UI.Text(
                            text = s.tool("label_schedule"),
                            type = TextType.SUBTITLE
                        )

                        // Button to open schedule editor
                        UI.Button(
                            type = ButtonType.DEFAULT,
                            size = Size.M,
                            onClick = { showScheduleEditor = true }
                        ) {
                            UI.Text(s.tool("action_configure_schedule"), TextType.LABEL)
                        }

                        // Display schedule summary if configured
                        if (scheduleConfig != null) {
                            UI.Text(
                                text = getScheduleSummary(scheduleConfig),
                                type = TextType.BODY
                            )
                        } else {
                            UI.Text(
                                text = s.tool("schedule_summary_not_configured"),
                                type = TextType.CAPTION
                            )
                        }
                    }
                }

                // Triggers section (disabled, stub for future)
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UI.Text(
                            text = s.tool("label_triggers_stub"),
                            type = TextType.CAPTION,
                            fillMaxWidth = true
                        )
                    }
                }

                // Note: Form actions removed - using Dialog's onConfirm/onCancel
            }
        }

        // Nested ScheduleConfigEditor dialog (opens on top)
        if (showScheduleEditor) {
            ScheduleConfigEditor(
                existingConfig = scheduleConfig,
                onConfirm = { config ->
                    scheduleConfig = config // null accepted (on-demand)
                    showScheduleEditor = false
                    LogManager.ui("Schedule config updated: ${if (config != null) "configured" else "cleared"}")
                },
                onDismiss = {
                    showScheduleEditor = false
                }
            )
        }

        // Show error toast when errorMessage is set
        errorMessage?.let { message ->
            LaunchedEffect(message) {
                UI.Toast(
                    context,
                    message,
                    Duration.LONG
                )
                errorMessage = null
            }
        }
    }
}

/**
 * Helper function to recursively convert JSONObject to Map
 * Necessary for proper schema validation with nested objects like ScheduleConfig
 */
private fun convertJsonToMap(jsonObject: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    jsonObject.keys().forEach { key ->
        val value = jsonObject.get(key)
        map[key] = when (value) {
            is JSONObject -> convertJsonToMap(value)  // Recursive for nested objects
            is org.json.JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    val item = value.get(i)
                    list.add(when (item) {
                        is JSONObject -> convertJsonToMap(item)
                        JSONObject.NULL -> null
                        else -> item
                    })
                }
                list
            }
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

/**
 * Data class for initial message values
 * Used when editing existing message (not for creation)
 */
data class MessageDialogData(
    val id: String,
    val title: String,
    val content: String?,
    val schedule: ScheduleConfig?,
    val priority: String
)
