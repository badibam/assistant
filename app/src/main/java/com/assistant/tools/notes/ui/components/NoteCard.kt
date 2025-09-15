package com.assistant.tools.notes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.ui.components.WithSpotlight
import com.assistant.core.strings.Strings
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import com.assistant.tools.notes.ui.NoteEntry
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Unified note card component supporting placeholder, creation, and editing
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntry? = null, // null = placeholder mode
    toolInstanceId: String,
    isCreating: Boolean = false, // true = creation mode
    insertPosition: Int? = null, // position for new note creation
    isMoving: Boolean = false,
    showContextMenu: Boolean = false,
    editingNoteId: String? = null, // État global d'édition
    contextMenuNoteId: String? = null, // État global de menu contextuel
    onEditingChanged: (Boolean) -> Unit = {},
    onContextMenuChanged: (Boolean) -> Unit = {},
    onNoteUpdated: (NoteEntry) -> Unit = {},
    onNoteCreated: () -> Unit = {}, // callback for creation completion
    onCreationCancelled: () -> Unit = {}, // callback for creation cancellation
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

    // Determine card state
    val isPlaceholder = note == null && !isCreating
    val isThisCardActive = when {
        isCreating -> true
        isPlaceholder -> false
        note != null && editingNoteId == note.id -> true
        note != null && contextMenuNoteId == note.id -> true
        else -> false
    }

    // State
    var isEditing by remember { mutableStateOf(isCreating) } // Auto-edit when creating
    var content by remember(note?.content, isCreating) {
        mutableStateOf(if (isCreating) "" else (note?.content ?: ""))
    }
    var isSaving by remember { mutableStateOf(false) }

    // Extracted save function to avoid duplication
    val saveNote: (Boolean) -> Unit = { exitEditMode ->
        when {
            note != null && content.trim() != note.content && content.trim().isNotEmpty() -> {
                coroutineScope.launch {
                    isSaving = true
                    try {
                        val params = mapOf(
                            "id" to note.id,
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
                            if (exitEditMode) {
                                isEditing = false
                            }
                        } else {
                            isEditing = true // Stay in edit mode on error
                        }
                    } catch (e: Exception) {
                        LogManager.ui("Error saving note: ${e.message}", "ERROR")
                        isEditing = true
                    } finally {
                        isSaving = false
                    }
                }
            }
            exitEditMode -> {
                // No changes or empty content - just exit edit mode
                isEditing = false
                if (note != null) {
                    content = note.content // Restore original content if empty
                }
            }
        }
    }

    // Sync local isEditing with global editingNoteId
    LaunchedEffect(editingNoteId, note?.id) {
        if (note != null) {
            val shouldBeEditing = editingNoteId == note.id
            if (isEditing != shouldBeEditing) {
                LogManager.ui("🔵 SYNC: isEditing $isEditing -> $shouldBeEditing for note ${note.id}", "DEBUG")

                // Auto-save if exiting edit mode (was editing, now not)
                if (isEditing && !shouldBeEditing) {
                    LogManager.ui("🔵 AUTO-SAVE: Exiting edit mode, saving note ${note.id}", "DEBUG")
                    saveNote(false) // Don't set isEditing here, will be set below
                }

                isEditing = shouldBeEditing
            }
        }
    }

    // Auto-focus when entering edit mode or creating
    LaunchedEffect(isEditing, isCreating) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
        if (note != null) {
            onEditingChanged(isEditing)
        }
    }

    // Save function (handles both creation and update)
    val performSave: () -> Unit = {
        when {
            isCreating -> {
                // Creation logic
                if (content.trim().isNotEmpty()) {
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            val currentTime = System.currentTimeMillis()
                            val nextPosition = insertPosition ?: Int.MAX_VALUE

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
                                onNoteCreated()
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
                    onCreationCancelled() // Empty content = cancel
                }
            }
            note != null -> {
                // Update logic for existing notes - use shared function
                saveNote(true) // Exit edit mode after save
            }
        }
    }

    WithSpotlight(
        isActive = isThisCardActive,
        editingNoteId = editingNoteId,
        contextMenuNoteId = contextMenuNoteId,
        onCloseSpotlight = {
            if (!isCreating) { // Don't close creation from other cards
                // Check if THIS note is being edited
                val isThisNoteEditing = note != null && editingNoteId == note.id
                LogManager.ui("🔴 onCloseSpotlight called: isThisNoteEditing=$isThisNoteEditing, editingNoteId=$editingNoteId, note=${note?.id}", "DEBUG")

                // If this note is being edited, save it
                if (isThisNoteEditing) {
                    LogManager.ui("🔴 Triggering performSave from onCloseSpotlight", "DEBUG")
                    performSave()
                } else {
                    // If ANY note is being edited (not this one), just close spotlight
                    // The edited note will handle its own save when its state changes
                    LogManager.ui("🔴 Just closing spotlight (this note not editing, but editingNoteId=$editingNoteId)", "DEBUG")
                    onEditingChanged(false)
                    onContextMenuChanged(false)
                }
            }
        }
    ) {
        UI.Card(type = CardType.DEFAULT) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { modifier ->
                            if (isPlaceholder) {
                                modifier.clickable { onAddAbove() } // Placeholder click creates note
                            } else {
                                modifier.combinedClickable(
                                    onClick = {
                                        if (!isMoving && !showContextMenu && !isCreating) {
                                            if (isEditing) {
                                                performSave()
                                            } else {
                                                isEditing = true
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMoving && !isEditing && !isCreating && note != null) {
                                            onContextMenuChanged(true)
                                        }
                                    }
                                )
                            }
                        }
                        .padding(16.dp)
                ) {
                    // Content display logic
                    when {
                        isPlaceholder -> {
                            // Placeholder display
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                UI.Icon("add", size = 48.dp)
                            }
                        }

                        isEditing || isCreating -> {
                            // Edit/Creation mode
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
                                            if (isCreating) {
                                                onCreationCancelled()
                                            } else if (note != null) {
                                                content = note.content // Restore original
                                                isEditing = false
                                            }
                                        }
                                    )
                                }
                            }

                            if (isSaving) {
                                Spacer(modifier = Modifier.height(8.dp))
                                UI.LoadingIndicator(size = Size.XS)
                            }
                        }

                        else -> {
                            // Display mode
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UI.Text(
                                    text = if (content.isEmpty()) s.tool("content_empty") else content,
                                    type = TextType.BODY,
                                    fillMaxWidth = true
                                )

                                if (isSaving) {
                                    UI.LoadingIndicator(size = Size.XS)
                                }
                            }
                        }
                    }
                }

            }

            // Context menu (only for existing notes)
            if (showContextMenu && !isMoving && !isEditing && !isCreating && note != null) {
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
                                onMoveUp()
                                // Keep context menu visible after move
                            }
                        )

                        UI.ActionButton(
                            action = ButtonAction.DOWN,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                onMoveDown()
                                // Keep context menu visible after move
                            }
                        )

                        UI.ActionButton(
                            action = ButtonAction.ADD,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                onContextMenuChanged(false)
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
                                onContextMenuChanged(false)
                                onDelete()
                            }
                        )

                        UI.ActionButton(
                            action = ButtonAction.CONFIRM,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                onContextMenuChanged(false)
                            }
                        )
                    }
                }
            }
        }
    }
}