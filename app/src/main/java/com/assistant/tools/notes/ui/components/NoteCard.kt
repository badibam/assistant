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
import com.assistant.tools.notes.ui.NoteEntry

/**
 * Simplified note card component with dialog-based editing
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntry? = null, // null = placeholder mode
    toolInstanceId: String,
    showContextMenu: Boolean = false,
    contextMenuNoteId: String? = null,
    onNoteClick: () -> Unit = {}, // Opens edit dialog
    onContextMenuChanged: (Boolean) -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onAddAbove: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "notes", context = context) }

    // Determine card states
    val isPlaceholder = note == null
    val isMoving = false // Simplified - no complex moving state

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

                    else -> {
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
                        }
                    }
                }
            }

            // Context menu overlay
            if (showContextMenu && note != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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