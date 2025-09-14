package com.assistant.tools.notes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.tools.notes.ui.NoteEntry

/**
 * Individual note card component
 * Displays full note content with interaction capabilities
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntry,
    isMoving: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onStartMoving: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onStopMoving: () -> Unit = {},
    onAddAbove: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "notes", context = context) }

    var showContextMenu by remember { mutableStateOf(false) }

    UI.Card(type = CardType.DEFAULT) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (!isMoving) {
                                onClick()
                            }
                        },
                        onLongClick = {
                            if (!isMoving) {
                                showContextMenu = true
                                onLongClick()
                            }
                        }
                    )
                    .padding(16.dp)
            ) {
                UI.Text(
                    text = note.content,
                    type = TextType.BODY,
                    fillMaxWidth = true
                )
            }

            // Context menu overlay
            if (showContextMenu && !isMoving) {
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
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            size = Size.S,
                            onClick = {
                                showContextMenu = false
                                onAddAbove()
                            }
                        ) {
                            UI.Text(s.tool("action_add_above"), TextType.LABEL)
                        }

                        UI.Button(
                            type = ButtonType.DEFAULT,
                            size = Size.S,
                            onClick = {
                                showContextMenu = false
                                onStartMoving()
                            }
                        ) {
                            UI.Text(s.tool("action_move"), TextType.LABEL)
                        }

                        UI.ActionButton(
                            action = ButtonAction.CANCEL,
                            size = Size.S,
                            onClick = {
                                showContextMenu = false
                            }
                        )
                    }
                }
            }

            // Movement controls overlay
            if (isMoving) {
                MovementOverlay(
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onStop = {
                        onStopMoving()
                    }
                )
            }
        }
    }
}

/**
 * Movement controls overlay component
 */
@Composable
private fun MovementOverlay(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "notes", context = context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.ActionButton(
                action = ButtonAction.UP,
                size = Size.S,
                onClick = onMoveUp
            )

            UI.ActionButton(
                action = ButtonAction.DOWN,
                size = Size.S,
                onClick = onMoveDown
            )

            UI.Button(
                type = ButtonType.PRIMARY,
                size = Size.S,
                onClick = onStop
            ) {
                UI.Text(s.tool("move_ok"), TextType.LABEL)
            }
        }
    }
}