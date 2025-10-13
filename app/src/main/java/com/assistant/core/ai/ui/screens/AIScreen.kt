package com.assistant.core.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.*
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.orchestration.RoundReason
import com.assistant.core.ai.orchestration.WaitingState
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * AI Screen - Unified interface for all session types (CHAT, SEED, AUTOMATION)
 *
 * Architecture:
 * - CHAT: Interactive conversation with user, message sending, validation toggles
 * - SEED: Template editor for automations (message + schedule/triggers config)
 * - AUTOMATION: Read-only viewer for automation executions with interrupt capability
 *
 * This is a pure Composable (not a Dialog) for maximum flexibility:
 * - Used by AIFloatingChat as Dialog wrapper for active sessions
 * - Used directly by ZoneScreen for SEED editing (fullscreen)
 * - Can be embedded anywhere in the app
 */
@Composable
fun AIScreen(
    sessionId: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Load session (once per sessionId)
    var session by remember(sessionId) { mutableStateOf<AISession?>(null) }
    var isLoadingSession by remember(sessionId) { mutableStateOf(true) }
    var errorMessage by remember(sessionId) { mutableStateOf<String?>(null) }

    // Load session data
    LaunchedEffect(sessionId) {
        try {
            isLoadingSession = true
            session = AIOrchestrator.loadSession(sessionId)
            LogManager.aiUI("AIScreen loaded session: ${session?.name} (type=${session?.type})")
        } catch (e: Exception) {
            LogManager.aiUI("Failed to load session $sessionId: ${e.message}", "ERROR")
            errorMessage = s.shared("ai_error_load_session")
        } finally {
            isLoadingSession = false
        }
    }

    // For CHAT/AUTOMATION: observe activeSessionId for eviction detection
    val orchestratorSessionId by AIOrchestrator.activeSessionId.collectAsState()
    LaunchedEffect(orchestratorSessionId) {
        if (orchestratorSessionId == sessionId && session != null) {
            // Session reactivated or state changed - reload
            try {
                session = AIOrchestrator.loadSession(sessionId)
                LogManager.aiUI("AIScreen reloaded session after activation change")
            } catch (e: Exception) {
                LogManager.aiUI("Failed to reload session: ${e.message}", "ERROR")
            }
        }
    }

    // Loading state
    if (isLoadingSession) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(
                text = s.shared("message_loading"),
                type = TextType.BODY
            )
        }
        return
    }

    // Error state
    if (session == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = errorMessage ?: s.shared("ai_error_session_not_found"),
                    type = TextType.BODY
                )
                UI.ActionButton(
                    action = ButtonAction.BACK,
                    display = ButtonDisplay.LABEL,
                    onClick = onClose
                )
            }
        }
        return
    }

    // Route according to session type
    when (session!!.type) {
        SessionType.CHAT -> {
            ChatMode(
                session = session!!,
                onClose = onClose
            )
        }
        SessionType.SEED -> {
            SeedMode(
                session = session!!,
                onClose = onClose
            )
        }
        SessionType.AUTOMATION -> {
            AutomationMode(
                session = session!!,
                onClose = onClose
            )
        }
    }
}

/**
 * CHAT mode - Interactive conversation
 */
@Composable
private fun ChatMode(
    session: AISession,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Local states
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStats by remember { mutableStateOf(false) }

    // Observe orchestrator states
    val waitingState by AIOrchestrator.waitingState.collectAsState()
    val isRoundInProgress by AIOrchestrator.isRoundInProgress.collectAsState()
    val messages by AIOrchestrator.activeSessionMessages.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        ChatHeader(
            session = session,
            isLoading = isRoundInProgress,
            onClose = onClose,
            onShowStats = { showStats = true },
            onStopSession = {
                scope.launch {
                    AIOrchestrator.stopActiveSession()
                }
            },
            onToggleValidation = { requireValidation ->
                scope.launch {
                    AIOrchestrator.toggleValidation(session.id, requireValidation)
                }
            }
        )

        // Stats dialog
        if (showStats) {
            com.assistant.core.ai.ui.chat.SessionStatsDialog(
                sessionId = session.id,
                sessionName = session.name,
                onDismiss = { showStats = false }
            )
        }

        // Messages area
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

        // Composer
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                UI.RichComposer(
                    segments = segments,
                    onSegmentsChange = { segments = it },
                    onSend = { richMessage ->
                        errorMessage = null
                        AIOrchestrator.sendMessageAsync(
                            richMessage = richMessage,
                            onComposerClear = { segments = emptyList() },
                            onError = { error -> errorMessage = error }
                        )
                    },
                    placeholder = s.shared("ai_composer_placeholder")
                )
            }
        }
    }

    // Error display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * SEED mode - Automation template editor
 * TODO: Implement full editor with AutomationEditorFooter
 */
@Composable
private fun SeedMode(
    session: AISession,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // TODO Phase 2: Full SEED editor implementation
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Temporary header
        SeedHeader(
            session = session,
            onClose = onClose,
            onConfigureAutomation = {
                // TODO: Open automation config dialog
            }
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(
                text = "SEED editor - Coming soon",
                type = TextType.TITLE
            )
        }
    }
}

/**
 * AUTOMATION mode - Execution viewer (read-only)
 */
@Composable
private fun AutomationMode(
    session: AISession,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Observe orchestrator states
    val messages by AIOrchestrator.activeSessionMessages.collectAsState()
    val isRoundInProgress by AIOrchestrator.isRoundInProgress.collectAsState()
    val waitingState by AIOrchestrator.waitingState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        AutomationHeader(
            session = session,
            isRunning = isRoundInProgress,
            onClose = onClose,
            onInterrupt = {
                AIOrchestrator.interruptActiveRound()
            }
        )

        // Messages area (read-only)
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            ChatMessageList(
                messages = messages,
                isLoading = isRoundInProgress,
                waitingState = waitingState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ================================================================================================
// Headers (3 variants)
// ================================================================================================

/**
 * Chat header - Session controls, validation toggle, stats
 */
@Composable
private fun ChatHeader(
    session: AISession,
    isLoading: Boolean,
    onClose: () -> Unit,
    onShowStats: () -> Unit,
    onStopSession: () -> Unit,
    onToggleValidation: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var showStopConfirmation by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSessionSettings by remember { mutableStateOf(false) }

    // Stop confirmation dialog
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
    if (showSettingsMenu) {
        com.assistant.core.ai.ui.chat.SettingsMenuDialog(
            session = session,
            onDismiss = { showSettingsMenu = false },
            onShowCosts = {
                showSettingsMenu = false
                onShowStats()
            },
            onShowSessionSettings = {
                showSettingsMenu = false
                showSessionSettings = true
            }
        )
    }

    // Session settings dialog
    if (showSessionSettings) {
        com.assistant.core.ai.ui.chat.SessionSettingsDialog(
            session = session,
            onDismiss = { showSessionSettings = false },
            onToggleValidation = onToggleValidation
        )
    }

    // Header row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with status
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
            UI.Text(
                text = when {
                    isLoading -> s.shared("ai_status_processing")
                    else -> s.shared("ai_status_ready")
                },
                type = TextType.CAPTION
            )
        }

        // Stop button
        UI.ActionButton(
            action = ButtonAction.STOP,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = { showStopConfirmation = true }
        )

        // Settings button
        UI.ActionButton(
            action = ButtonAction.CONFIGURE,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = { showSettingsMenu = true }
        )

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
 * Seed header - Automation name and config button
 */
@Composable
private fun SeedHeader(
    session: AISession,
    onClose: () -> Unit,
    onConfigureAutomation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title
        Box(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
        }

        // Config button
        UI.ActionButton(
            action = ButtonAction.CONFIGURE,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onConfigureAutomation
        )

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
 * Automation header - Execution status and interrupt button
 */
@Composable
private fun AutomationHeader(
    session: AISession,
    isRunning: Boolean,
    onClose: () -> Unit,
    onInterrupt: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title with status
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = session.name,
                type = TextType.TITLE
            )
            UI.Text(
                text = if (isRunning) s.shared("ai_status_processing") else s.shared("ai_status_completed"),
                type = TextType.CAPTION
            )
        }

        // Interrupt button (only if running)
        if (isRunning) {
            UI.ActionButton(
                action = ButtonAction.INTERRUPT,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onInterrupt
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

// ================================================================================================
// Shared Components (copied from AIFloatingChat, will be extracted to separate file later)
// ================================================================================================

/**
 * Message list - shared by CHAT and AUTOMATION modes
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
                com.assistant.core.ai.ui.chat.ChatMessageBubble(
                    message = message,
                    waitingState = waitingState,
                    isLastAIMessage = index == lastAIMessageIndex
                )
            }

            // AI thinking indicator (don't show during communication module or validation)
            if (isLoading && waitingState !is WaitingState.WaitingResponse && waitingState !is WaitingState.WaitingValidation) {
                item {
                    com.assistant.core.ai.ui.chat.ChatLoadingIndicator()
                }
            }
        }
    }
}

