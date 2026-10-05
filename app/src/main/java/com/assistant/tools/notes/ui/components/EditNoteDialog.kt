package com.assistant.tools.notes.ui.components

import com.assistant.core.tools.BaseSchemas
import com.assistant.core.ui.FieldValuesSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.toFieldDefinitions
import kotlinx.coroutines.launch
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Edit/Create Note Dialog
 *
 * Simple dialog for creating or editing a note: its title when the tool's notes take one
 * ([titles]), its content, its user's fields. [onConfirm] gets the title trimmed, empty when
 * cleared, or null when the notes take no title.
 */
@Composable
fun EditNoteDialog(
    isVisible: Boolean,
    toolInstanceId: String,
    isCreating: Boolean = false,
    insertPosition: Int? = null,
    titles: Boolean = false,
    initialTitle: String = "",
    initialContent: String = "",
    initialNoteId: String? = null,
    initialCustomFields: Map<String, Any?> = emptyMap(),
    onConfirm: suspend (title: String?, content: String, position: Int?, customFields: Map<String, Any?>) -> Boolean,
    onCancel: () -> Unit
) {
    // Get context and coordinator
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }

    // State management
    var title by rememberSaveable(isVisible) { mutableStateOf(initialTitle) }
    var content by rememberSaveable(isVisible) { mutableStateOf(initialContent) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationResult: ValidationResult by remember { mutableStateOf(ValidationResult.success()) }
    val coroutineScope = rememberCoroutineScope()

    // Custom fields states
    var customFieldsDefinitions by remember { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    var customFieldsValues by rememberSaveable(isVisible, initialCustomFields, stateSaver = FieldValuesSaver) {
        mutableStateOf(initialCustomFields)
    }

    // Load custom fields definitions from tool instance config
    LaunchedEffect(toolInstanceId) {
        val configResult = coordinator.processUserAction(
            "tools.get",
            mapOf("tool_instance_id" to toolInstanceId)
        )

        if (configResult?.isSuccess == true) {
            val toolData = configResult.data?.get("tool_instance") as? Map<*, *>
            toolData?.let { data ->
                val configJson = JsonUtils.toJSONObject(data["config"] as? Map<String, Any?> ?: emptyMap())
                try {
                    val config = configJson
                    val customFieldsArray = config.optJSONArray("extra_fields")
                    if (customFieldsArray != null) {
                        customFieldsDefinitions = customFieldsArray.toFieldDefinitions()
                        LogManager.ui("Loaded ${customFieldsDefinitions.size} custom field definitions")
                    }
                } catch (e: Exception) {
                    LogManager.ui("Error parsing custom fields definitions: ${e.message}", "ERROR")
                }
            }
        }
    }

    // Focus management
    val focusRequester = remember { FocusRequester() }

    // Get strings
    val s = remember { Strings.`for`(tool = "notes", context = context) }

    // Reset validation when dialog opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            validationResult = ValidationResult.success()
        }
    }

    // Request focus when dialog opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        }
    }

    // Validation function
    fun validateForm() {
        val toolType = ToolTypeManager.getToolType("notes")
        if (toolType != null) {
            // Build data structure like service expects
            val entryData = mutableMapOf(
                "tool_instance_id" to toolInstanceId,
                "tooltype" to "notes",
                "timestamp" to System.currentTimeMillis(),
                "data" to mapOf(
                    "content" to content.trim()
                ),
                "state" to mapOf(
                    "position" to (insertPosition ?: 0)
                )
            )

            if (titles && title.isNotBlank()) entryData["name"] = title.trim()

            validationResult = try {
                SchemaValidator.validate(BaseSchemas.entrySchema(toolType, toolInstanceId, context), entryData, context)
            } catch (e: IllegalStateException) {
                ValidationResult.error(e.message ?: "Notes data schema unavailable")
            }
            LogManager.ui("Notes validation result: isValid=${validationResult.isValid}")
            if (!validationResult.isValid) {
                LogManager.ui("Notes validation error: ${validationResult.errorMessage}")
            }
        } else {
            validationResult = ValidationResult.error("Tool type 'notes' not found")
        }
    }

    // Dialog title
    val dialogTitle = if (isCreating) {
        s.tool("create_note_title")
    } else {
        s.tool("edit_note_title")
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
                                val success = onConfirm(if (titles) title.trim() else null, content.trim(), insertPosition, customFieldsValues)
                                if (success) {
                                    // Dialog will close automatically
                                } else {
                                    errorMessage = s.shared("message_error_simple")
                                }
                            } catch (e: Exception) {
                                LogManager.ui("Error saving note: ${e.message}", "ERROR")
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {
                UI.Text(dialogTitle, TextType.SUBTITLE)

                if (titles) {
                    UI.FormField(
                        label = s.tool("label_title"),
                        value = title,
                        onChange = { title = it },
                        fieldType = FieldType.TEXT,
                        required = false
                    )
                }

                // Content field
                UI.FormField(
                    label = s.tool("label_content"),
                    value = content,
                    onChange = { content = it },
                    required = true,
                    fieldType = FieldType.TEXT_LONG,
                    fieldModifier = FieldModifier.withFocus(focusRequester)
                )

                // Custom fields input (if any custom fields defined)
                if (customFieldsDefinitions.isNotEmpty()) {
                    CustomFieldsInput(
                        customFieldsMetadata = customFieldsDefinitions,
                        values = customFieldsValues,
                        onValuesChange = { newValues ->
                            customFieldsValues = newValues
                            LogManager.ui("Custom fields values updated")
                        },
                        context = context,
                        newEntry = isCreating
                    )
                }

            }
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