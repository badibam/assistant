package com.assistant.tools.notes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.assistant.tools.notes.ui.NoteEntry
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Individual note card component with inline editing
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntry,
    toolInstanceId: String,
    isMoving: Boolean = false,
    onNoteUpdated: (NoteEntry) -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onAddAbove: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // State
    var isEditing by remember { mutableStateOf(false) }
    var content by remember(note.content) { mutableStateOf(note.content) }
    var isSaving by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    // Auto-focus when entering edit mode
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    // Save function
    val performSave = {
        if (content.trim() != note.content && content.trim().isNotEmpty()) {
            coroutineScope.launch {
                isSaving = true
                try {
                    val params = mapOf(
                        "entryId" to note.id,
                        "toolInstanceId" to toolInstanceId,
                        "data" to JSONObject().apply {
                            put("content", content.trim())
                            put("position", note.position)
                        }
                    )

                    val result = coordinator.processUserAction("tool_data.update", params)
                    if (result?.isSuccess == true) {
                        val updatedNote = note.copy(content = content.trim())
                        onNoteUpdated(updatedNote)
                        isEditing = false
                    } else {
                        isEditing = true // Stay in edit mode on error
                    }
                } catch (e: Exception) {
                    LogManager.ui("Error updating note: ${e.message}", "ERROR")
                    isEditing = true
                } finally {
                    isSaving = false
                }
            }
        } else {
            // No changes or empty content - just exit edit mode
            isEditing = false
            content = note.content // Restore original content if empty
        }
    }

    UI.Card(type = CardType.DEFAULT) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (!isMoving && !showContextMenu) {
                                if (isEditing) {
                                    performSave()
                                } else {
                                    isEditing = true
                                }
                            }
                        },
                        onLongClick = {
                            if (!isMoving && !isEditing) {
                                showContextMenu = true
                            }
                        }
                    )
                    .padding(16.dp)
            ) {
                // Content
                if (isEditing) {
                    UI.FormField(
                        label = "",
                        value = content,
                        onChange = { content = it },
                        fieldType = FieldType.TEXT_LONG,
                        fieldModifier = FieldModifier.withFocus(focusRequester)
                    )

                    // Edit mode buttons
                    if (!isSaving) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UI.ActionButton(
                                action = ButtonAction.CONFIRM,
                                size = Size.XS,
                                onClick = performSave
                            )
                            UI.ActionButton(
                                action = ButtonAction.CANCEL,
                                size = Size.XS,
                                onClick = {
                                    content = note.content // Restore original
                                    isEditing = false
                                }
                            )
                        }
                    }

                    if (isSaving) {
                        Spacer(modifier = Modifier.height(8.dp))
                        UI.LoadingIndicator(size = Size.XS)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UI.Text(
                            text = content,
                            type = TextType.BODY,
                            fillMaxWidth = true
                        )

                        if (isSaving) {
                            UI.LoadingIndicator(size = Size.XS)
                        }
                    }
                }
            }

            // Context menu
            if (showContextMenu && !isMoving && !isEditing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        UI.ActionButton(
                            action = ButtonAction.UP,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                showContextMenu = false
                                onMoveUp()
                            }
                        )

                        UI.ActionButton(
                            action = ButtonAction.DOWN,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                showContextMenu = false
                                onMoveDown()
                            }
                        )

                        UI.ActionButton(
                            action = ButtonAction.ADD,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                showContextMenu = false
                                onAddAbove()
                            }
                        )

                        UI.ActionButton(
                            action = ButtonAction.DELETE,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            requireConfirmation = true,
                            confirmMessage = s.tool("delete_confirm_template").format(
                                note.content.take(30) + if (note.content.length > 30) "..." else ""
                            ),
                            onClick = {
                                showContextMenu = false
                                onDelete()
                            }
                        )

                        UI.ActionButton(
                            action = ButtonAction.CONFIRM,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                showContextMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}