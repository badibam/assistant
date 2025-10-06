package com.assistant.core.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assistant.core.ai.data.*
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.orchestration.RoundReason
import com.assistant.core.ai.orchestration.WaitingState
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Floating AI Chat interface - 100% screen overlay
 * Basic implementation with RichComposer integration for testing
 */
@Composable
fun AIFloatingChat(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Session management - store ID and metadata, messages come from reactive flow
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activeSession by remember { mutableStateOf<AISession?>(null) }
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    // AIOrchestrator is now a singleton, no need to remember/create
    val aiOrchestrator = AIOrchestrator

    // Observe waiting state for user interactions
    val waitingState by aiOrchestrator.waitingState.collectAsState()

    // Observe round in progress for AI thinking indicator
    val isRoundInProgress by aiOrchestrator.isRoundInProgress.collectAsState()

    // Observe messages reactively from StateFlow (replaces polling)
    val messages by aiOrchestrator.activeSessionMessages.collectAsState()

    // Reload session every time dialog becomes visible (not just first mount)
    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect

        try {
            // Check for existing active session only, don't create one yet
            val existingSession = aiOrchestrator.getActiveSession()

            if (existingSession != null) {
                // Set active session (messages will flow reactively from StateFlow)
                activeSessionId = existingSession.id
                activeSession = existingSession
                LogManager.aiUI("AIFloatingChat using active session: ${existingSession.id}")
            } else {
                // No active session - will be created on first message send
                activeSessionId = null
                activeSession = null
                LogManager.aiUI("AIFloatingChat opened without active session - will create on first message")
            }
        } catch (e: Exception) {
            LogManager.aiUI("Failed to check for active session: ${e.message}", "ERROR")
            errorMessage = s.shared("ai_error_check_session")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // Full screen
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f)) // Semi-transparent overlay
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White) // Main chat background
            ) {
                // Header
                ChatHeader(
                    sessionName = activeSession?.name ?: s.shared("ai_chat_new"),
                    hasActiveSession = activeSessionId != null,
                    isLoading = isRoundInProgress,
                    onClose = onDismiss,
                    onStopSession = {
                        scope.launch {
                            try {
                                val result = aiOrchestrator.stopActiveSession()
                                if (result.success) {
                                    activeSessionId = null
                                    activeSession = null
                                    LogManager.aiUI("Session stopped successfully")
                                } else {
                                    LogManager.aiUI("Failed to stop session: ${result.error}", "ERROR")
                                    errorMessage = result.error
                                }
                            } catch (e: Exception) {
                                LogManager.aiUI("Error stopping session: ${e.message}", "ERROR")
                                errorMessage = s.shared("ai_error_stop_session")
                            }
                        }
                    }
                )

                // Messages area (scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ChatMessageList(
                        messages = messages,
                        isLoading = isRoundInProgress,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Rich composer
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        UI.RichComposer(
                            segments = segments,
                            onSegmentsChange = { segments = it },
                            onSend = { richMessage ->
                                LogManager.aiUI("AIFloatingChat received RichMessage from RichComposer")
                                LogManager.aiUI("RichMessage.linearText: '${richMessage.linearText}'")
                                LogManager.aiUI("RichMessage.dataCommands: ${richMessage.dataCommands.size} commands")

                                scope.launch {
                                    errorMessage = null

                                    try {
                                        // Get or create session ID
                                        val sessionId = activeSessionId ?: run {
                                            // Create new session on first message
                                            LogManager.aiUI("Creating new session for first message")
                                            val newSessionId = aiOrchestrator.createSession(s.shared("ai_session_default_name"), SessionType.CHAT)
                                            aiOrchestrator.setActiveSession(newSessionId)
                                            activeSessionId = newSessionId
                                            newSessionId
                                        }

                                        // Activate session if not already active
                                        if (aiOrchestrator.getActiveSessionId() != sessionId) {
                                            aiOrchestrator.requestSessionControl(sessionId, SessionType.CHAT)
                                        }

                                        // 1. Process user message (stores message + executes enrichments)
                                        val processResult = aiOrchestrator.processUserMessage(richMessage)

                                        if (!processResult.success) {
                                            errorMessage = processResult.error
                                            LogManager.aiUI("Failed to process message: ${processResult.error}", "ERROR")
                                            return@launch
                                        }

                                        // 2. Clear composer (messages will update reactively via StateFlow)
                                        segments = emptyList()
                                        LogManager.aiUI("User message sent")

                                        // 3. Execute AI round (messages will update reactively)
                                        val aiRoundResult = aiOrchestrator.executeAIRound(RoundReason.USER_MESSAGE)
                                        LogManager.aiUI("AI round finished")

                                        if (!aiRoundResult.success) {
                                            errorMessage = aiRoundResult.error
                                            LogManager.aiUI("Failed AI round: ${aiRoundResult.error}", "ERROR")
                                        } else {
                                            LogManager.aiUI("AI round completed successfully")
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = s.shared("ai_error_send_message").format(e.message ?: "")
                                        LogManager.aiUI("Exception sending message: ${e.message}", "ERROR")
                                    }
                                }
                            },
                            placeholder = s.shared("ai_composer_placeholder")
                        )
                    }
                }
            }
        }
    }

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            // TODO: Show proper error UI or toast
            LogManager.aiUI("Error: $message", "ERROR")
            // Clear error after showing
            errorMessage = null
        }
    }

    // User interaction dialogs based on waiting state
    when (val state = waitingState) {
        is WaitingState.WaitingValidation -> {
            ValidationDialog(
                request = state.request,
                onValidate = { validated ->
                    aiOrchestrator.resumeWithValidation(validated)
                }
            )
        }
        is WaitingState.WaitingResponse -> {
            CommunicationModuleDialog(
                module = state.module,
                onResponse = { response ->
                    aiOrchestrator.resumeWithResponse(response)
                }
            )
        }
        WaitingState.None -> {
            // No dialog needed
        }
    }
}

/**
 * Chat header with session info and controls
 */
@Composable
private fun ChatHeader(
    sessionName: String,
    hasActiveSession: Boolean,
    isLoading: Boolean,
    onClose: () -> Unit,
    onStopSession: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var showStopConfirmation by remember { mutableStateOf(false) }

    // Stop session confirmation dialog
    if (showStopConfirmation) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                showStopConfirmation = false
                onStopSession()
            },
            onCancel = { showStopConfirmation = false }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = s.shared("ai_session_stop_title"),
                    type = TextType.TITLE
                )
                UI.Text(
                    text = s.shared("ai_session_stop_message"),
                    type = TextType.BODY
                )
            }
        }
    }

    UI.PageHeader(
        title = sessionName,
        subtitle = when {
            isLoading -> s.shared("ai_status_processing")
            hasActiveSession -> s.shared("ai_status_ready")
            sessionName == s.shared("ai_chat_new") -> s.shared("ai_status_send_to_start")
            else -> s.shared("ai_status_inactive")
        },
        leftButton = if (hasActiveSession) ButtonAction.DELETE else null, // Stop session button (only if has active session)
        rightButton = ButtonAction.CANCEL, // Close chat button
        onLeftClick = if (hasActiveSession) { { showStopConfirmation = true } } else null,
        onRightClick = onClose
    )
}

/**
 * Message timeline display
 */
@Composable
private fun ChatMessageList(
    messages: List<SessionMessage>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    UI.Text(
                        text = s.shared("ai_chat_start_conversation"),
                        type = TextType.BODY
                    )
                }
            }
        } else {
            items(messages) { message ->
                ChatMessageBubble(message = message)
            }

            // AI thinking indicator
            if (isLoading) {
                item {
                    UI.AIThinkingIndicator()
                }
            }
        }
    }
}

/**
 * Individual message bubble
 */
@Composable
private fun ChatMessageBubble(
    message: SessionMessage
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    val alignment = when (message.sender) {
        MessageSender.USER -> Alignment.CenterEnd
        MessageSender.AI -> Alignment.CenterStart
        MessageSender.SYSTEM -> Alignment.Center
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                        UI.Text(
                            text = message.aiMessage.preText,
                            type = TextType.BODY
                        )
                    }
                    message.systemMessage != null -> {
                        UI.Text(
                            text = message.systemMessage.summary,
                            type = TextType.BODY
                        )
                    }
                }
            }
        }
    }
}

/**
 * Validation dialog for AI action requests
 * Displays when AI asks user to validate proposed actions
 */
@Composable
private fun ValidationDialog(
    request: ValidationRequest,
    onValidate: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    UI.Dialog(
        type = DialogType.CONFIRM,
        onConfirm = { onValidate(true) },
        onCancel = { onValidate(false) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UI.Text(
                text = s.shared("ai_validation_title"),
                type = TextType.TITLE
            )
            UI.Text(
                text = request.message,
                type = TextType.BODY
            )
            request.status?.let { status ->
                UI.Text(
                    text = "${s.shared("ai_validation_status")}: ${status.name}",
                    type = TextType.CAPTION
                )
            }
        }
    }
}

/**
 * Communication module dialog for AI questions
 * Handles different module types (MultipleChoice, Validation, etc.)
 */
@Composable
private fun CommunicationModuleDialog(
    module: CommunicationModule,
    onResponse: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    when (module) {
        is CommunicationModule.MultipleChoice -> {
            val question = module.data["question"] as? String ?: s.shared("ai_module_no_question")
            val options = (module.data["options"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            var selectedOption by remember { mutableStateOf<String?>(null) }

            UI.Dialog(
                type = DialogType.CONFIRM,
                onConfirm = {
                    selectedOption?.let { onResponse(it) }
                },
                onCancel = {
                    onResponse("") // Empty response = cancelled
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UI.Text(
                        text = s.shared("ai_module_multiple_choice_title"),
                        type = TextType.TITLE
                    )
                    UI.Text(
                        text = question,
                        type = TextType.BODY
                    )

                    // Options as radio buttons
                    options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UI.Button(
                                type = if (selectedOption == option) ButtonType.PRIMARY else ButtonType.DEFAULT,
                                size = Size.M,
                                onClick = { selectedOption = option }
                            ) {
                                UI.Text(
                                    text = option,
                                    type = TextType.BODY
                                )
                            }
                        }
                    }
                }
            }
        }

        is CommunicationModule.Validation -> {
            val message = module.data["message"] as? String ?: s.shared("ai_module_no_message")

            UI.Dialog(
                type = DialogType.CONFIRM,
                onConfirm = { onResponse("confirmed") },
                onCancel = { onResponse("cancelled") }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UI.Text(
                        text = s.shared("ai_module_validation_title"),
                        type = TextType.TITLE
                    )
                    UI.Text(
                        text = message,
                        type = TextType.BODY
                    )
                }
            }
        }
    }
}

