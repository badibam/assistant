package com.assistant.core.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Switch
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
import com.assistant.core.ai.ui.SessionCostDisplay
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
    var showStats by remember { mutableStateOf(false) }

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
                    session = activeSession,
                    onClose = onDismiss,
                    onShowStats = { showStats = true },
                    onStartSession = {
                        scope.launch {
                            try {
                                val newSessionId = aiOrchestrator.createSession(
                                    s.shared("ai_session_default_name"),
                                    SessionType.CHAT
                                )
                                // Request control to properly activate session in orchestrator
                                aiOrchestrator.requestSessionControl(newSessionId, SessionType.CHAT)
                                // Update local state
                                activeSessionId = newSessionId
                                activeSession = aiOrchestrator.getActiveSession()
                                LogManager.aiUI("New session created and activated: $newSessionId")
                            } catch (e: Exception) {
                                LogManager.aiUI("Error creating session: ${e.message}", "ERROR")
                                errorMessage = s.shared("ai_error_create_session").format(e.message ?: "")
                            }
                        }
                    },
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
                    },
                    onToggleValidation = { requireValidation ->
                        scope.launch {
                            try {
                                activeSessionId?.let { sessionId ->
                                    aiOrchestrator.toggleValidation(sessionId, requireValidation)
                                    // Refresh session to update UI
                                    activeSession = aiOrchestrator.getActiveSession()
                                    LogManager.aiUI("Validation toggled: $requireValidation")
                                }
                            } catch (e: Exception) {
                                LogManager.aiUI("Error toggling validation: ${e.message}", "ERROR")
                                errorMessage = s.shared("ai_error_toggle_validation")
                            }
                        }
                    }
                )

                // Stats dialog
                if (showStats && activeSessionId != null) {
                    SessionStatsDialog(
                        sessionId = activeSessionId!!,
                        sessionName = activeSession?.name ?: s.shared("ai_chat_new"),
                        onDismiss = { showStats = false }
                    )
                }

                // Messages area (scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ChatMessageList(
                        messages = messages,
                        isLoading = isRoundInProgress,
                        waitingState = waitingState,
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
}

/**
 * Chat header with session info and controls
 */
@Composable
private fun ChatHeader(
    sessionName: String,
    hasActiveSession: Boolean,
    isLoading: Boolean,
    session: AISession?,
    onClose: () -> Unit,
    onShowStats: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleValidation: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var showStopConfirmation by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSessionSettings by remember { mutableStateOf(false) }

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

    // Settings menu dialog
    if (showSettingsMenu && hasActiveSession) {
        SettingsMenuDialog(
            session = session,
            onDismiss = { showSettingsMenu = false },
            onShowCosts = {
                showSettingsMenu = false
                onShowStats()
            },
            onShowSessionSettings = {
                LogManager.aiUI("Opening session settings, session=$session", "DEBUG")
                showSettingsMenu = false
                showSessionSettings = true
            }
        )
    }

    // Session settings dialog
    if (showSessionSettings) {
        LogManager.aiUI("Showing session settings dialog: session=$session", "DEBUG")
        if (session != null) {
            SessionSettingsDialog(
                session = session,
                onDismiss = { showSessionSettings = false },
                onToggleValidation = onToggleValidation
            )
        } else {
            LogManager.aiUI("ERROR: session is null, cannot show settings dialog", "ERROR")
            // Reset state if session is null
            showSessionSettings = false
        }
    }

    // Header: single horizontal row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with status
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = sessionName,
                type = TextType.TITLE
            )
            UI.Text(
                text = when {
                    isLoading -> s.shared("ai_status_processing")
                    hasActiveSession -> s.shared("ai_status_ready")
                    sessionName == s.shared("ai_chat_new") -> s.shared("ai_status_send_to_start")
                    else -> s.shared("ai_status_inactive")
                },
                type = TextType.CAPTION
            )
        }

        // Start session button (only if NO active session)
        if (!hasActiveSession) {
            UI.ActionButton(
                action = ButtonAction.START,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onStartSession
            )
        }

        // Stop session button (only if has active session)
        if (hasActiveSession) {
            UI.ActionButton(
                action = ButtonAction.STOP,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = { showStopConfirmation = true }
            )
        }

        // Settings button (only if has active session)
        if (hasActiveSession) {
            UI.ActionButton(
                action = ButtonAction.CONFIGURE,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = { showSettingsMenu = true }
            )
        }

        // Close button
        UI.ActionButton(
            action = ButtonAction.CANCEL,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onClose
        )
    }
}

/**
 * Settings menu dialog - lists settings options
 */
@Composable
private fun SettingsMenuDialog(
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
private fun SessionSettingsDialog(
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
 * Message timeline display
 */
@Composable
private fun ChatMessageList(
    messages: List<SessionMessage>,
    isLoading: Boolean,
    waitingState: WaitingState,
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

    // Determine which message should show active module (last AI message if waiting for response)
    val lastAIMessageIndex = messages.indexOfLast { it.sender == MessageSender.AI }

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
            itemsIndexed(messages) { index, message ->
                ChatMessageBubble(
                    message = message,
                    waitingState = waitingState,
                    isLastAIMessage = index == lastAIMessageIndex
                )
            }

            // Validation UI (inline in flow, after messages)
            // Show when waiting for validation
            if (waitingState is WaitingState.WaitingValidation) {
                item {
                    com.assistant.core.ai.ui.ValidationUI(
                        context = waitingState.context,
                        onValidate = {
                            AIOrchestrator.resumeWithValidation(true)
                        },
                        onRefuse = {
                            AIOrchestrator.resumeWithValidation(false)
                        }
                    )
                }
            }

            // AI thinking indicator with interrupt button
            // Exception: don't show during communication module or validation (have their own buttons)
            if (isLoading && waitingState !is WaitingState.WaitingResponse && waitingState !is WaitingState.WaitingValidation) {
                item {
                    ChatLoadingIndicator()
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
    message: SessionMessage,
    waitingState: WaitingState,
    isLastAIMessage: Boolean = false
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
                        // AI message with preText
                        UI.Text(
                            text = message.aiMessage.preText,
                            type = TextType.BODY
                        )

                        // Communication module (inline in flow, after preText)
                        // Show only if this is the last AI message AND we're waiting for a response
                        message.aiMessage.communicationModule?.let { module ->
                            val shouldShowModule = isLastAIMessage &&
                                                 waitingState is WaitingState.WaitingResponse

                            if (shouldShowModule) {
                                Spacer(modifier = Modifier.height(8.dp))
                                com.assistant.core.ai.ui.components.CommunicationModuleCard(
                                    module = module,
                                    onResponse = { response ->
                                        AIOrchestrator.resumeWithResponse(response)
                                    },
                                    onCancel = {
                                        AIOrchestrator.resumeWithResponse(null)
                                    }
                                )
                            }
                        }
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
 * TODO Phase 5: Reimplement with ValidationContext and ValidationUI
 * REMOVED: Old implementation using ValidationRequest (which no longer exists)
 */
// @Composable
// private fun ValidationDialog(...) { ... }
// Will be replaced with ValidationUI in Phase 5


/**
 * Session stats dialog - displays cost breakdown
 */
@Composable
private fun SessionStatsDialog(
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
                SessionCostDisplay(sessionId = sessionId)
            }
        }
    }
}

/**
 * Loading indicator with interrupt button
 * Shows "Interrupting..." message after user clicks interrupt
 */
@Composable
private fun ChatLoadingIndicator() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var isInterrupting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UI.AIThinkingIndicator()

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
                    AIOrchestrator.interruptActiveRound()
                }
            )
        }
    }
}
