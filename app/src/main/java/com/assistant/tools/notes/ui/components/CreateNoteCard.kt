package com.assistant.tools.notes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Inline note creation card
 */
@Composable
fun CreateNoteCard(
    toolInstanceId: String,
    insertPosition: Int? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val coroutineScope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }
    var content by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Auto-focus when created
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Auto-save function
    val performSave = {
        if (content.trim().isNotEmpty()) {
            coroutineScope.launch {
                isSaving = true
                try {
                    val currentTime = System.currentTimeMillis()
                    val nextPosition = insertPosition ?: 999999

                    val params = mutableMapOf<String, Any>(
                        "toolInstanceId" to toolInstanceId,
                        "tooltype" to "notes",
                        "timestamp" to currentTime,
                        "name" to "Note",
                        "data" to JSONObject().apply {
                            put("content", content.trim())
                            put("position", nextPosition)
                        }
                    )

                    if (insertPosition != null) {
                        params["insert_position"] = insertPosition
                    }

                    val result = coordinator.processUserAction("tool_data.create", params)
                    if (result?.isSuccess == true) {
                        onSave()
                    } else {
                        LogManager.ui("Failed to create note", "ERROR")
                    }
                } catch (e: Exception) {
                    LogManager.ui("Error creating note: ${e.message}", "ERROR")
                } finally {
                    isSaving = false
                }
            }
        } else {
            onCancel() // Empty content = cancel
        }
    }

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UI.FormField(
                label = s.tool("field_content"),
                value = content,
                onChange = { content = it },
                fieldType = FieldType.TEXT_LONG,
                required = true,
                fieldModifier = FieldModifier.withFocus(focusRequester)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.ActionButton(
                    action = ButtonAction.CONFIRM,
                    size = Size.S,
                    enabled = !isSaving,
                    onClick = performSave
                )

                UI.ActionButton(
                    action = ButtonAction.CANCEL,
                    size = Size.S,
                    onClick = onCancel
                )

                if (isSaving) {
                    UI.LoadingIndicator(size = Size.XS)
                }
            }
        }
    }
}