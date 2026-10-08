package app.treelune.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import app.treelune.core.utils.DateUtils

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
                .padding(UI.Space.M),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
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

            UI.Divider()

            // Row 3: Metadata - Created date | Message count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: Created date
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = app.treelune.core.utils.DateTimeFormatter.formatForDisplay(
                            createdAt,
                            androidx.compose.ui.platform.LocalContext.current
                        ),
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

            UI.Divider()

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

                Spacer(modifier = Modifier.width(UI.Space.S))

                // Rename button
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onRenameClick
                )

                Spacer(modifier = Modifier.width(UI.Space.S))

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
