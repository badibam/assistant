package com.assistant.core.ai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.assistant.core.ai.orchestration.QueuedSessionInfo
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.ai.ui.screens.AIScreen
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Floating AI Chat interface - Dialog wrapper with 4 states
 *
 * States:
 * - ÉTAT A: No active session → "Start Chat" button
 * - ÉTAT B: Automation active → Read-only view with Stop/Chat After/Interrupt buttons
 * - ÉTAT C: Chat active → Normal chat interface (AIScreen)
 * - ÉTAT D: Chat queued → "Waiting..." message with Cancel button
 *
 * Architecture:
 * - Observes activeSessionId and queuedSessions flows
 * - Determines state based on active session type and queue status
 * - Delegates to appropriate composable for each state
 */
@Composable
fun AIFloatingChat(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Observe orchestrator state
    val orchestratorSessionId by AIOrchestrator.activeSessionId.collectAsState()
    val queuedSessions by AIOrchestrator.queuedSessions.collectAsState()

    // Determine queued chat session
    val queuedChatSession = queuedSessions.firstOrNull { it.type == SessionType.CHAT }

    // Load active session type
    var activeSessionType by remember { mutableStateOf<SessionType?>(null) }

    LaunchedEffect(orchestratorSessionId) {
        if (orchestratorSessionId != null) {
            // Load session to get type
            val session = AIOrchestrator.loadSession(orchestratorSessionId!!)
            activeSessionType = session?.type
        } else {
            activeSessionType = null
        }
    }

    // State for errors
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f))
        ) {
            // Determine state and render appropriate UI
            when {
                // ÉTAT D: Chat queued (priority check)
                queuedChatSession != null && orchestratorSessionId != null -> {
                    ChatQueuedView(
                        queuedSession = queuedChatSession,
                        activeSessionId = orchestratorSessionId!!,
                        onCancel = {
                            AIOrchestrator.cancelQueuedSession(queuedChatSession.sessionId)
                        },
                        onClose = onDismiss
                    )
                }
                // ÉTAT C: Chat active
                orchestratorSessionId != null && activeSessionType == SessionType.CHAT -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        AIScreen(
                            sessionId = orchestratorSessionId!!,
                            onClose = onDismiss
                        )
                    }
                }
                // ÉTAT B: Automation active
                orchestratorSessionId != null && activeSessionType == SessionType.AUTOMATION -> {
                    AutomationActiveView(
                        sessionId = orchestratorSessionId!!,
                        onStop = {
                            scope.launch {
                                val result = AIOrchestrator.stopActiveSession()
                                if (!result.success) {
                                    errorMessage = result.error
                                }
                            }
                        },
                        onChatAfter = {
                            scope.launch {
                                try {
                                    val sessionId = AIOrchestrator.createSession(
                                        name = s.shared("ai_session_default_name"),
                                        type = SessionType.CHAT
                                    )
                                    if (sessionId.isNotEmpty()) {
                                        AIOrchestrator.requestSessionControl(sessionId, SessionType.CHAT)
                                    } else {
                                        errorMessage = s.shared("ai_error_create_session").format("")
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                }
                            }
                        },
                        onInterruptAndChat = {
                            scope.launch {
                                try {
                                    // Set endReason to SUSPENDED for current automation
                                    val coordinator = com.assistant.core.coordinator.Coordinator(context)
                                    coordinator.processUserAction("ai_sessions.set_end_reason", mapOf(
                                        "sessionId" to orchestratorSessionId!!,
                                        "endReason" to SessionEndReason.SUSPENDED.name
                                    ))

                                    // Create and activate chat session immediately
                                    val sessionId = AIOrchestrator.createSession(
                                        name = s.shared("ai_session_default_name"),
                                        type = SessionType.CHAT
                                    )
                                    if (sessionId.isNotEmpty()) {
                                        AIOrchestrator.requestSessionControl(sessionId, SessionType.CHAT)
                                    } else {
                                        errorMessage = s.shared("ai_error_create_session").format("")
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                }
                            }
                        },
                        onClose = onDismiss
                    )
                }
                // ÉTAT A: No active session
                else -> {
                    NoActiveSessionView(
                        onStartChat = {
                            scope.launch {
                                try {
                                    val sessionId = AIOrchestrator.createSession(
                                        name = s.shared("ai_session_default_name"),
                                        type = SessionType.CHAT
                                    )
                                    if (sessionId.isNotEmpty()) {
                                        AIOrchestrator.requestSessionControl(sessionId, SessionType.CHAT)
                                    } else {
                                        errorMessage = s.shared("ai_error_create_session").format("")
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                }
                            }
                        },
                        onClose = onDismiss
                    )
                }
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

// ========================================================================================
// STATE COMPOSABLES
// ========================================================================================

/**
 * ÉTAT A: No active session
 * Shows message and "Start Chat" button
 */
@Composable
private fun NoActiveSessionView(
    onStartChat: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var isCreatingSession by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                UI.Text(
                    text = s.shared("ai_chat_new"),
                    type = TextType.TITLE
                )
            }
            UI.ActionButton(
                action = ButtonAction.CANCEL,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onClose
            )
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                UI.Text(
                    text = s.shared("ai_chat_no_active_session"),
                    type = TextType.TITLE
                )

                if (isCreatingSession) {
                    UI.Text(
                        text = s.shared("ai_chat_creating_session"),
                        type = TextType.BODY
                    )
                } else {
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        size = Size.L,
                        onClick = {
                            isCreatingSession = true
                            onStartChat()
                        }
                    ) {
                        UI.Text(
                            text = s.shared("ai_chat_start_button"),
                            type = TextType.BODY
                        )
                    }
                }
            }
        }
    }
}

/**
 * ÉTAT B: Automation active
 * Shows read-only automation messages with action buttons
 */
@Composable
private fun AutomationActiveView(
    sessionId: String,
    onStop: () -> Unit,
    onChatAfter: () -> Unit,
    onInterruptAndChat: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header with 3 action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                UI.Text(
                    text = s.shared("ai_automation_running"),
                    type = TextType.TITLE
                )
            }

            // Stop button
            UI.Button(
                type = ButtonType.SECONDARY,
                size = Size.S,
                onClick = onStop
            ) {
                UI.Text(
                    text = s.shared("action_stop"),
                    type = TextType.BODY
                )
            }

            // Chat After button
            UI.Button(
                type = ButtonType.DEFAULT,
                size = Size.S,
                onClick = onChatAfter
            ) {
                UI.Text(
                    text = s.shared("ai_chat_after_button"),
                    type = TextType.BODY
                )
            }

            // Interrupt & Chat button
            UI.Button(
                type = ButtonType.PRIMARY,
                size = Size.S,
                onClick = onInterruptAndChat
            ) {
                UI.Text(
                    text = s.shared("ai_chat_interrupt_and_chat_button"),
                    type = TextType.BODY
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

        // Read-only automation messages (AIScreen will detect AUTOMATION type automatically)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AIScreen(
                sessionId = sessionId,
                onClose = onClose
            )
        }
    }
}

/**
 * ÉTAT D: Chat queued
 * Shows waiting message with automation view and cancel button
 */
@Composable
private fun ChatQueuedView(
    queuedSession: QueuedSessionInfo,
    activeSessionId: String,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header with queued message
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3CD)) // Warning yellow
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = s.shared("ai_chat_queued_message"),
                        type = TextType.SUBTITLE
                    )
                }

                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.S,
                    onClick = onCancel
                ) {
                    UI.Text(
                        text = s.shared("action_cancel"),
                        type = TextType.BODY
                    )
                }

                UI.ActionButton(
                    action = ButtonAction.CANCEL,
                    display = ButtonDisplay.ICON,
                    size = Size.M,
                    onClick = onClose
                )
            }
        }

        // Show automation in progress (AIScreen will detect AUTOMATION type automatically)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AIScreen(
                sessionId = activeSessionId,
                onClose = onClose
            )
        }
    }
}
