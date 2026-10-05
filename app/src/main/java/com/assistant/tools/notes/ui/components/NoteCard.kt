package com.assistant.tools.notes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.fields.CustomFieldsDisplay
import com.assistant.tools.notes.ui.NoteEntry

/**
 * Simplified note card component with dialog-based editing
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntry? = null, // null = placeholder mode
    toolInstanceId: String,
    config: org.json.JSONObject, // The tool's config, for how its fields show
    titles: Boolean = false, // Whether the tool's notes take a title, shown above the text
    showContextMenu: Boolean = false,
    contextMenuNoteId: String? = null,
    onNoteClick: () -> Unit = {}, // Opens edit dialog
    onContextMenuChanged: (Boolean) -> Unit = {},
    dragHandle: (@Composable () -> Unit)? = null, // The grip the note is reordered by
    onAddAbove: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "notes", context = context) }

    // Determine card states
    val isPlaceholder = note == null
    val isMoving = false // Simplified - no complex moving state
    val title = note?.title?.takeIf { titles }

    UI.Card(type = CardType.DEFAULT) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .let { modifier ->
                            if (isPlaceholder) {
                                modifier.clickable { onAddAbove() } // Placeholder click creates note
                            } else {
                                modifier.combinedClickable(
                                    onClick = {
                                        if (!isMoving && !showContextMenu) {
                                            onNoteClick() // Open edit dialog
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMoving && note != null) {
                                            onContextMenuChanged(true)
                                        }
                                    }
                                )
                            }
                        }
                        .padding(UI.Space.L)
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

                        else -> {
                            title?.let {
                                UI.Text(text = it, type = TextType.SUBTITLE)
                                Spacer(modifier = Modifier.height(UI.Space.XS))
                            }

                            // Note content display
                            val displayContent = note?.content?.trim() ?: ""

                            if (displayContent.isBlank()) {
                                // Empty note
                                Box(
                                    modifier = Modifier.height(60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UI.Text(
                                        text = s.tool("content_empty"),
                                        type = TextType.CAPTION
                                    )
                                }
                            } else {
                                // Note with content
                                UI.Text(
                                    text = displayContent,
                                    type = TextType.BODY
                                )

                                // Custom fields display (always shown for alwaysVisible fields)
                                Spacer(modifier = Modifier.height(UI.Space.S))
                                CustomFieldsDisplay(
                                    toolType = com.assistant.tools.notes.NotesToolType,
                                    config = config,
                                    values = note?.extra ?: emptyMap(),
                                    layout = com.assistant.core.fields.FieldsLayout.COMPACT,
                                    context = context
                                )
                            }
                        }
                    }
                }

                dragHandle?.invoke()
            }

            // Context menu overlay
            if (showContextMenu && note != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UI.Space.S),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)
                ) {
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
                            title ?: (note.content.take(30) + if (note.content.length > 30) "..." else "")
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