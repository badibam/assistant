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
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.ui.screens.AIScreen
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import kotlinx.coroutines.launch

/**
 * Floating AI Chat interface - Dialog wrapper with simplified V2 architecture
 *
 * States (simplified from V1):
 * - IDLE: No active session → "Start Chat" button
 * - CHAT: Chat active → Normal chat interface (AIScreen)
 * - AUTOMATION: Automation active → Read-only view with interrupt/stop buttons
 *
 * V2 Simplifications:
 * - No queue management (handled internally by scheduler)
 * - Single state observation via currentState
 * - Direct actions via requestChatSession()
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

    // Observe orchestrator state (V2)
    val aiState by AIOrchestrator.currentState.collectAsState()

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
                // CHAT: Chat active
                aiState.sessionId != null && aiState.sessionType == SessionType.CHAT -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        AIScreen(
                            sessionId = aiState.sessionId!!,
                            onClose = onDismiss
                        )
                    }
                }
                // AUTOMATION: Automation active
                aiState.sessionId != null && aiState.sessionType == SessionType.AUTOMATION -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        AutomationActiveView(
                            sessionId = aiState.sessionId!!,
                            phase = aiState.phase,
                            onStop = {
                                scope.launch {
                                    try {
                                        AIOrchestrator.stopActiveSession()
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                    }
                                }
                            },
                            onInterruptAndChat = {
                                scope.launch {
                                    try {
                                        // Request chat session - scheduler will handle interruption
                                        AIOrchestrator.requestChatSession()
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                    }
                                }
                            },
                            onClose = onDismiss
                        )
                    }
                }
                // IDLE: No active session
                else -> {
                    NoActiveSessionView(
                        onStartChat = {
                            scope.launch {
                                try {
                                    AIOrchestrator.requestChatSession()
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
 * IDLE: No active session
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
 * AUTOMATION: Automation active
 * Shows read-only automation messages with interrupt/stop buttons
 *
 * V2 changes:
 * - No "Chat After" option (would require queue management)
 * - Only "Interrupt & Chat" and "Stop"
 * - Messages and state observed from AIOrchestrator
 */
@Composable
private fun AutomationActiveView(
    sessionId: String,
    phase: Phase,
    onStop: () -> Unit,
    onInterruptAndChat: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Observe state from orchestrator
    val aiState by AIOrchestrator.currentState.collectAsState()

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
            Column(modifier = Modifier.weight(1f)) {
                UI.Text(
                    text = s.shared("ai_automation_running"),
                    type = TextType.TITLE
                )
            }

            // Stop button (SECONDARY red)
            UI.ActionButton(
                action = ButtonAction.STOP,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onStop
            )

            // Interrupt & Chat button (PRIMARY green)
            UI.ActionButton(
                action = ButtonAction.AI_CHAT,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onInterruptAndChat
            )

            // Close button
            UI.ActionButton(
                action = ButtonAction.CANCEL,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onClose
            )
        }

        // Messages display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            com.assistant.core.ai.ui.screens.ChatMessageList(
                aiState = aiState,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Status bar (always visible at bottom)
        com.assistant.core.ai.ui.components.SessionStatusBar(
            phase = aiState.phase,
            sessionType = SessionType.AUTOMATION,
            context = context
        )
    }
}
