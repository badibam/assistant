package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.DateUtils

/**
 * SessionCard - Display CHAT session summary for History feature
 *
 * Shows:
 * - Session name (title)
 * - First user message preview (truncated to 60 chars)
 * - Created date
 * - Message count
 * - Action buttons: Resume, Rename, Delete
 *
 * Layout: Vertical card with title, preview, metadata row, and action buttons
 *
 * Usage: In HistoryScreen session list
 */
@Composable
fun SessionCard(
    sessionId: String,
    name: String,
    createdAt: Long,
    messageCount: Int,
    firstUserMessage: String,
    onResumeClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    UI.Card(
        type = CardType.DEFAULT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Session name (title)
            UI.Text(
                text = name,
                type = TextType.SUBTITLE,
                fillMaxWidth = true
            )

            // Row 2: Preview message
            if (firstUserMessage.isNotEmpty()) {
                UI.Text(
                    text = firstUserMessage,
                    type = TextType.CAPTION,
                    fillMaxWidth = true
                )
            }

            Divider()

            // Row 3: Metadata - Created date | Message count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Created date
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = DateUtils.formatFullDateTime(createdAt),
                        type = TextType.CAPTION
                    )
                }

                // Right column: Message count
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    UI.Text(
                        text = s.shared("history_session_messages").format(messageCount),
                        type = TextType.CAPTION
                    )
                }
            }

            Divider()

            // Row 4: Action buttons (Resume, Rename, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Resume button
                UI.ActionButton(
                    action = ButtonAction.RESUME,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onResumeClick
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Rename button
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onRenameClick
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Delete button
                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onDeleteClick
                )
            }
        }
    }
}
