package com.assistant.tools.notes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.tools.ToolTypeManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Note creation and editing screen
 * Full screen interface with simple text field
 */
@Composable
fun NotesEditScreen(
    noteId: String? = null, // null for new note
    toolInstanceId: String,
    insertPosition: Int? = null, // Position to insert new note (null = at end)
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    LogManager.ui("NotesEditScreen opened - noteId=$noteId, insertPosition=$insertPosition")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Content state
    var content by remember { mutableStateOf("") }

    // UI states
    var isLoading by remember { mutableStateOf(noteId != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing note content if editing
    LaunchedEffect(noteId) {
        if (noteId != null) {
            LogManager.ui("Loading existing note for ID: $noteId")
            val result = coordinator.executeWithLoading(
                operation = "tool_data.get_single",
                params = mapOf("entry_id" to noteId),
                onLoading = { isLoading = it },
                onError = { error -> errorMessage = error }
            )

            result?.let { data ->
                val entryData = data.mapSingleData("entry") { map -> map }
                entryData?.let { entry ->
                    val dataMap = entry["data"] as? Map<String, Any>
                    content = dataMap?.get("content") as? String ?: ""
                    LogManager.ui("Successfully loaded note content: ${content.take(50)}...")
                }
            }
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

    // Save function with validation at save time like Tracking does
    val handleSave = {
        coroutineScope.launch {
            isSaving = true
            try {
                // Validate at save time - include all required schema fields like Tracking does
                val currentTime = System.currentTimeMillis()

                // Build data object first, like Tracking does
                val dataObject = mapOf(
                    "content" to content.trim()
                )

                // Try with explicit type conversion to avoid Map nesting issues
                val entryDataMap = mutableMapOf<String, Any>().apply {
                    put("id", "temp-validation-id")
                    put("tool_instance_id", toolInstanceId)
                    put("tooltype", "notes")
                    put("name", "Note")
                    put("timestamp", currentTime)
                    put("created_at", currentTime)
                    put("updated_at", currentTime)
                    put("data", dataObject)
                }

                val toolType = ToolTypeManager.getToolType("notes")
                if (toolType != null) {
                    // Debug logs like Tracking does
                    LogManager.ui("=== Validation start ===")
                    LogManager.ui("entryDataMap: $entryDataMap")
                    LogManager.ui("data content: ${entryDataMap["data"]}")

                    val validation = SchemaValidator.validate(toolType, entryDataMap, context, "data")

                    if (validation.isValid) {
                        // Use same API structure as Tracking
                        val params = mutableMapOf<String, Any>(
                            "toolInstanceId" to toolInstanceId, // Same key as Tracking
                            "tooltype" to "notes",
                            "timestamp" to currentTime,
                            "name" to "Note",
                            "data" to JSONObject().apply {
                                put("content", content.trim())
                            }
                        )

                        // Handle existing note update
                        if (noteId != null) {
                            params["entryId"] = noteId
                        }

                        if (insertPosition != null) {
                            params["insert_position"] = insertPosition
                        }

                        // Use same operation as Tracking
                        val operation = if (noteId != null) "tool_data.update" else "tool_data.create"
                        LogManager.ui("Saving note with operation: $operation, params: $params")

                        val result = coordinator.processUserAction(operation, params)
                        if (result?.isSuccess == true) {
                            LogManager.ui("Note saved successfully")
                            onSave()
                        } else {
                            LogManager.ui("Failed to save note", "ERROR")
                            LogManager.ui("Result status: ${result?.status}, error: ${result?.error}")
                            errorMessage = "Erreur lors de la sauvegarde"
                        }
                    } else {
                        LogManager.ui("Validation failed: ${validation.errorMessage}", "ERROR")
                        errorMessage = validation.errorMessage ?: "Erreur de validation"
                    }
                } else {
                    LogManager.ui("ToolType notes not found", "ERROR")
                    errorMessage = "Type d'outil introuvable"
                }
            } catch (e: Exception) {
                LogManager.ui("Error saving note: ${e.message}", "ERROR")
                errorMessage = "Erreur lors de la sauvegarde: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }

    // Early return for loading state
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            UI.Text(s.shared("message_loading"), TextType.BODY)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Page header
        UI.PageHeader(
            title = if (noteId != null) s.shared("action_edit") else s.shared("action_create"),
            subtitle = s.tool("display_name"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onCancel
        )

        // Content input field
        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.padding(16.dp)) {
                UI.FormField(
                    label = s.tool("field_content"),
                    value = content,
                    onChange = { content = it },
                    fieldType = FieldType.TEXT_LONG, // 1500 char limit
                    required = true
                )
            }
        }

        // Form actions
        UI.FormActions {
            UI.ActionButton(
                action = ButtonAction.SAVE,
                enabled = !isSaving, // Only disable while saving, no content validation
                onClick = handleSave
            )

            UI.ActionButton(
                action = ButtonAction.CANCEL,
                onClick = onCancel
            )
        }
    }
}