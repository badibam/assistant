package com.assistant.core.ai.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assistant.core.ai.data.*
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import kotlinx.coroutines.launch

/**
 * Shared chat components extracted from AIFloatingChat
 * Used by both AIScreen and AIFloatingChat
 */

/**
 * Individual message bubble - themed UI.MessageBubble
 *
 * V2: Uses aiState.waitingContext for inline validation/communication display
 */
@Composable
fun ChatMessageBubble(
    message: SessionMessage,
    aiState: com.assistant.core.ai.domain.AIState,
    isLastAIMessage: Boolean = false
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Preserve original alignment logic
    val alignment = when (message.sender) {
        MessageSender.USER -> Alignment.CenterEnd
        MessageSender.AI -> Alignment.CenterStart
        MessageSender.SYSTEM -> Alignment.Center
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        // Delegate to themed MessageBubble for appearance
        UI.MessageBubble(sender = message.sender) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sender indicator
                UI.Text(
                    text = when (message.sender) {
                        MessageSender.USER -> s.shared("ai_sender_user")
                        MessageSender.AI -> s.shared("ai_sender_ai")
                        MessageSender.SYSTEM -> s.shared("ai_sender_system")
                    },
                    type = TextType.LABEL
                )

                // Message content
                when {
                    message.richContent != null -> {
                        // Rich message with segments
                        UI.Text(
                            text = "${s.shared("ai_message_rich_prefix")} ${message.richContent.linearText}",
                            type = TextType.BODY
                        )
                        if (message.richContent.segments.filterIsInstance<MessageSegment.EnrichmentBlock>().isNotEmpty()) {
                            UI.Text(
                                text = s.shared("ai_message_enrichments_count").format(
                                    message.richContent.segments.filterIsInstance<MessageSegment.EnrichmentBlock>().size
                                ),
                                type = TextType.CAPTION
                            )
                        }
                    }
                    message.textContent != null -> {
                        UI.Text(
                            text = message.textContent,
                            type = TextType.BODY
                        )
                    }
                    message.aiMessage != null -> {
                        // AI message with preText
                        UI.Text(
                            text = message.aiMessage.preText,
                            type = TextType.BODY
                        )

                        // Communication module (inline, only on last AI message)
                        message.aiMessage.communicationModule?.let { module ->
                            val shouldShow = isLastAIMessage &&
                                aiState.waitingContext is com.assistant.core.ai.domain.WaitingContext.Communication

                            if (shouldShow) {
                                Spacer(modifier = Modifier.height(8.dp))
                                com.assistant.core.ai.ui.components.CommunicationModuleCard(
                                    module = module,
                                    onResponse = { response ->
                                        AIOrchestrator.resumeWithResponse(response)
                                    },
                                    onCancel = {
                                        AIOrchestrator.cancelCommunication()
                                    }
                                )
                            }
                        }

                        // Validation UI (inline, only on last AI message)
                        if (isLastAIMessage && aiState.waitingContext is com.assistant.core.ai.domain.WaitingContext.Validation) {
                            val validationCtx = aiState.waitingContext as com.assistant.core.ai.domain.WaitingContext.Validation
                            Spacer(modifier = Modifier.height(8.dp))
                            com.assistant.core.ai.ui.ValidationUI(
                                context = validationCtx.validationContext,
                                onValidate = {
                                    AIOrchestrator.resumeWithValidation(true)
                                },
                                onRefuse = {
                                    AIOrchestrator.resumeWithValidation(false)
                                }
                            )
                        }
                    }
                    message.systemMessage != null -> {
                        // System message: show summary + command details
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Summary
                            UI.Text(
                                text = message.systemMessage.summary,
                                type = TextType.BODY
                            )

                            // Command results details (if present)
                            if (message.systemMessage.commandResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))

                                message.systemMessage.commandResults.forEach { commandResult ->
                                    UI.Card(type = CardType.DEFAULT) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Status icon + details
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                UI.Text(
                                                    text = if (commandResult.status == CommandStatus.SUCCESS) "✓" else "✗",
                                                    type = TextType.BODY
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    // Verbalized description
                                                    commandResult.details?.let {
                                                        UI.Text(
                                                            text = it,
                                                            type = TextType.BODY
                                                        )
                                                    }

                                                    // Data (if success)
                                                    commandResult.data?.let { data ->
                                                        if (data.isNotEmpty()) {
                                                            val dataText = data.entries.joinToString(", ") { (k, v) ->
                                                                "$k: $v"
                                                            }
                                                            UI.Text(
                                                                text = dataText,
                                                                type = TextType.CAPTION
                                                            )
                                                        }
                                                    }

                                                    // Error (if failed)
                                                    commandResult.error?.let { error ->
                                                        UI.Text(
                                                            text = s.shared("ai_error_label").format(error),
                                                            type = TextType.CAPTION
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI loading spinner (just the spinner, no buttons)
 * Can be used in both CHAT and AUTOMATION contexts
 */
@Composable
fun AILoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        UI.AIThinkingIndicator()
    }
}

/**
 * Chat interrupt button with state management
 * Shows "Interrupting..." message after user clicks interrupt
 * CHAT only - not for AUTOMATION
 */
@Composable
fun ChatInterruptButton() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    var isInterrupting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isInterrupting) {
            // Show "Interrupting..." message after button clicked
            UI.Text(
                text = s.shared("ai_status_interrupting"),
                type = TextType.CAPTION
            )
        } else {
            // Show interrupt button
            UI.ActionButton(
                action = ButtonAction.INTERRUPT,
                display = ButtonDisplay.LABEL,
                size = Size.S,
                onClick = {
                    isInterrupting = true
                    scope.launch {
                        AIOrchestrator.interruptActiveRound()
                    }
                }
            )
        }
    }
}

/**
 * Settings menu dialog - lists settings options
 */
@Composable
fun SettingsMenuDialog(
    session: AISession?,
    onDismiss: () -> Unit,
    onShowCosts: () -> Unit,
    onShowSessionSettings: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                UI.Text(
                    text = s.shared("ai_settings_menu_title"),
                    type = TextType.TITLE
                )

                // Option 1: Costs
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = onShowCosts
                ) {
                    UI.Text(
                        text = s.shared("ai_settings_costs"),
                        type = TextType.BODY
                    )
                }

                // Option 2: Session settings
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = onShowSessionSettings
                ) {
                    UI.Text(
                        text = s.shared("ai_settings_session"),
                        type = TextType.BODY
                    )
                }
            }
        }
    }
}

/**
 * Session settings dialog - validation toggle and other session parameters
 */
@Composable
fun SessionSettingsDialog(
    session: AISession,
    onDismiss: () -> Unit,
    onToggleValidation: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.Text(
                        text = s.shared("ai_settings_session"),
                        type = TextType.TITLE
                    )
                    UI.ActionButton(
                        action = ButtonAction.CANCEL,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = onDismiss
                    )
                }

                // Validation toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.Text(
                        text = s.shared("label_validation"),
                        type = TextType.BODY
                    )
                    Switch(
                        checked = session.requireValidation,
                        onCheckedChange = onToggleValidation
                    )
                }
            }
        }
    }
}

/**
 * Session stats dialog - displays cost breakdown
 */
@Composable
fun SessionStatsDialog(
    sessionId: String,
    sessionName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.Text(
                        text = sessionName,
                        type = TextType.TITLE
                    )
                    UI.ActionButton(
                        action = ButtonAction.CANCEL,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = onDismiss
                    )
                }

                // Cost display
                com.assistant.core.ai.ui.SessionCostDisplay(sessionId = sessionId)
            }
        }
    }
}
