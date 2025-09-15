package com.assistant.tools.notes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.tools.ToolTypeManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Edit/Create Note Dialog
 *
 * Simple dialog for creating or editing note content
 */
@Composable
fun EditNoteDialog(
    isVisible: Boolean,
    toolInstanceId: String,
    isCreating: Boolean = false,
    insertPosition: Int? = null,
    initialContent: String = "",
    initialNoteId: String? = null,
    onConfirm: suspend (content: String, position: Int?) -> Boolean,
    onCancel: () -> Unit
) {
    // State management
    var content by remember(isVisible) { mutableStateOf(initialContent) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var validationResult: ValidationResult by remember { mutableStateOf(ValidationResult.success()) }
    val coroutineScope = rememberCoroutineScope()

    // Get strings
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "notes", context = context) }

    // Reset validation when dialog opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            validationResult = ValidationResult.success()
        }
    }

    // Validation function
    fun validateForm() {
        val toolType = ToolTypeManager.getToolType("notes")
        if (toolType != null) {
            // Build data structure like service expects
            val entryData = mapOf(
                "tool_instance_id" to toolInstanceId,
                "tooltype" to "notes",
                "name" to "Note",
                "timestamp" to System.currentTimeMillis(),
                "data" to mapOf(
                    "content" to content.trim(),
                    "position" to (insertPosition ?: 0)
                )
            )

            validationResult = SchemaValidator.validate(toolType, entryData, context, schemaType = "data")
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
                                val success = onConfirm(content.trim(), insertPosition)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(dialogTitle, TextType.SUBTITLE)

                // Content field
                UI.FormField(
                    label = s.tool("label_content"),
                    value = content,
                    onChange = { content = it },
                    required = true,
                    fieldType = FieldType.TEXT_MEDIUM
                )

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