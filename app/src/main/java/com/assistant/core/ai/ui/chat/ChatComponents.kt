package com.assistant.core.ai.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
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
import com.assistant.core.utils.LogManager
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
 * Click on message copies content to clipboard
 *
 * @param previousAIMessage Optional previous AI message for displaying communication module question with response
 * @param freeReply Whether the user chose to answer the pending module by message
 * @param onFreeReply Frees the composer for that message
 */
@Composable
fun ChatMessageBubble(
    message: SessionMessage,
    aiState: com.assistant.core.ai.domain.AIState,
    isLastAIMessage: Boolean = false,
    previousAIMessage: SessionMessage? = null,
    freeReply: Boolean = false,
    onFreeReply: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Extract text content for clipboard copy
    // A user message's blocks name their targets as they are now
    val richText = message.richContent?.let { com.assistant.core.ai.ui.components.rememberDisplayText(it) }
    val textToCopy = remember(message, richText) {
        when {
            richText != null -> richText
            message.textContent != null -> message.textContent
            message.aiMessage != null -> message.aiMessage.preText
            message.systemMessage != null -> message.systemMessage.summary
            else -> ""
        }
    }

    // Copy to clipboard handler
    val onMessageClick = {
        if (textToCopy.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI Message", textToCopy)
            clipboard.setPrimaryClip(clip)
            UI.Toast(context, s.shared("message_copied"), Duration.SHORT)
        }
    }

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
        // Add clickable modifier for copy functionality
        Box(modifier = Modifier.clickable { onMessageClick() }) {
            UI.MessageBubble(sender = message.sender) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(UI.Space.S)
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
                            // Rich message with segments (use UI-friendly version without IDs)
                            UI.Text(
                                text = richText ?: "",
                                type = TextType.BODY
                            )
                        }
                        message.textContent != null -> {
                            // Check if this is a communication module response
                            val responsePrefix = s.shared("ai_module_response_prefix")
                            val isCommunicationResponse = message.sender == MessageSender.SYSTEM &&
                                message.textContent.startsWith(responsePrefix)

                            // If it's a response and we have the question from previous AI message, show it
                            val answered = previousAIMessage?.aiMessage?.communicationModule
                            if (isCommunicationResponse && answered != null) {
                                // The answer by the fields it answers, under the question the
                                // AI's preText asked
                                Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                                    UI.Text(
                                        text = previousAIMessage.aiMessage.preText,
                                        type = TextType.CAPTION
                                    )
                                    com.assistant.core.ai.ui.components.CommunicationAnswer(
                                        module = answered,
                                        answer = message.textContent.removePrefix(responsePrefix).trim()
                                    )
                                }
                            } else {
                                // Normal text content
                                UI.Text(
                                    text = message.textContent,
                                    type = TextType.BODY
                                )
                            }
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
                                    Spacer(modifier = Modifier.height(UI.Space.S))
                                    com.assistant.core.ai.ui.components.CommunicationModuleCard(
                                        module = module,
                                        onResponse = { response, note ->
                                            AIOrchestrator.resumeWithResponse(response, note)
                                        },
                                        onCancel = {
                                            AIOrchestrator.cancelCommunication()
                                        },
                                        freeReply = freeReply,
                                        onFreeReply = onFreeReply
                                    )
                                }
                            }

                            // Validation UI (inline, only on last AI message)
                            if (isLastAIMessage && aiState.waitingContext is com.assistant.core.ai.domain.WaitingContext.Validation) {
                                val validationCtx = aiState.waitingContext as com.assistant.core.ai.domain.WaitingContext.Validation
                                Spacer(modifier = Modifier.height(UI.Space.S))
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
                            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                                // Summary
                                UI.Text(
                                    text = message.systemMessage.summary,
                                    type = TextType.BODY
                                )

                                // Data above the size threshold waiting for the user's decision
                                val dataWaiting = aiState.waitingContext as? com.assistant.core.ai.domain.WaitingContext.DataConfirmation
                                if (dataWaiting != null && dataWaiting.messageId == message.id) {
                                    DataConfirmationCard(
                                        dataChars = dataWaiting.dataChars,
                                        maxDataChars = dataWaiting.maxDataChars,
                                        onSend = { AIOrchestrator.resumeWithDataConfirmation(true) },
                                        onRefuse = { AIOrchestrator.resumeWithDataConfirmation(false) }
                                    )
                                }

                                // Command results details (if present)
                                if (message.systemMessage.commandResults.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(UI.Space.XS))

                                    // The AI's commands these results answer, one per action result, in
                                    // order: an action that wrote entries shows them by their fields
                                    val results = message.systemMessage.commandResults
                                    val commands = previousAIMessage?.aiMessage?.actionCommands.orEmpty()
                                    val actionCount = results.count { it.isActionCommand }
                                    val commandOf: Map<com.assistant.core.ai.data.CommandResult, com.assistant.core.ai.data.DataCommand> =
                                        if (commands.size == actionCount) results.filter { it.isActionCommand }.zip(commands).toMap()
                                        else {
                                            if (actionCount > 0 && previousAIMessage != null) LogManager.aiUI("Action results ($actionCount) do not match the AI's commands (${commands.size})", "WARN")
                                            emptyMap()
                                        }

                                    results.forEach { commandResult ->
                                        UI.Card(type = CardType.DEFAULT) {
                                            Column(
                                                modifier = Modifier.padding(UI.Space.S),
                                                verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                                            ) {
                                                // Status icon + details
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    UI.Icon(
                                                        iconName = if (commandResult.status == CommandStatus.SUCCESS) "check" else "x",
                                                        size = 20.dp
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        // Verbalized description
                                                        commandResult.details?.let {
                                                            UI.Text(
                                                                text = it,
                                                                type = TextType.BODY
                                                            )
                                                        }

                                                        // What an action wrote: its entries by their fields, or else
                                                        // the little it returns (an id, a name). Query data is filtered
                                                        // and not displayed (already in formattedData)
                                                        val written = commandOf[commandResult]?.takeIf {
                                                            commandResult.status == CommandStatus.SUCCESS && it.params["entries"] != null
                                                        }
                                                        if (written != null) {
                                                            WrittenEntries(written)
                                                        } else if (commandResult.isActionCommand) {
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
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.M)
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
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.L)
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
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.L)
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

/** Asks whether data above the CHAT size threshold goes to the AI. */
@Composable
private fun DataConfirmationCard(
    dataChars: Int,
    maxDataChars: Int,
    onSend: () -> Unit,
    onRefuse: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(UI.Space.M),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            UI.Text(text = s.shared("ai_data_confirmation_title"), type = TextType.SUBTITLE)
            UI.Text(
                text = s.shared("ai_data_confirmation_text").format(dataChars, maxDataChars),
                type = TextType.BODY
            )
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Button(type = ButtonType.PRIMARY, size = Size.M, onClick = onSend) {
                    UI.Text(text = s.shared("ai_data_confirmation_send"), type = TextType.BODY)
                }
                UI.Button(type = ButtonType.DEFAULT, size = Size.M, onClick = onRefuse) {
                    UI.Text(text = s.shared("ai_data_confirmation_refuse"), type = TextType.BODY)
                }
            }
        }
    }
}

/**
 * The entries an action of the AI wrote, each value shown by its field as in the validation
 * request (ProposedEntries), read from the command with its tool's fields as they are now.
 */
@Composable
private fun WrittenEntries(action: com.assistant.core.ai.data.DataCommand) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // null while read; a tool that cannot be read (deleted since) says so
    var failed by remember(action) { mutableStateOf(false) }
    val entries by produceState<List<com.assistant.core.ai.validation.ProposedEntry>?>(initialValue = null, action) {
        value = try {
            com.assistant.core.ai.validation.ProposedEntries.read(action, context)
        } catch (e: Exception) {
            LogManager.aiUI("Written entries not read: ${e.message}", "WARN")
            failed = true
            emptyList()
        }
    }
    if (failed) {
        UI.Text(text = s.shared("ai_written_entries_unreadable"), type = TextType.CAPTION)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        entries?.forEach { entry -> com.assistant.core.ai.ui.ProposedEntryItem(entry) }
    }
}
