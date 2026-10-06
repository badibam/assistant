package com.assistant.core.ai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.ai.data.*
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.ui.screens.AIScreen
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.ui.components.FullScreenDialog
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
    // The past chats, opened over the chat: resuming one shows it here, leaving comes back
    var showHistory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    FullScreenDialog(onDismiss = onDismiss) {
        when {
            showHistory -> {
                com.assistant.core.ui.screens.HistoryScreen(
                    onNavigateBack = { showHistory = false },
                    onResumeSession = { showHistory = false }
                )
            }
            // CHAT or AUTOMATION: Session active → Use AIScreen
            aiState.sessionId != null -> {
                AIScreen(
                    sessionId = aiState.sessionId!!,
                    onClose = onDismiss,
                    onOpenHistory = { showHistory = true }
                )
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
                    onOpenHistory = { showHistory = true },
                    onClose = onDismiss
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
    onOpenHistory: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var isCreatingSession by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        UI.HeaderBar {
            Box(modifier = Modifier.weight(1f)) {
                UI.Text(
                    text = s.shared("ai_chat_new"),
                    type = TextType.TITLE
                )
            }
            UI.ActionButton(
                action = ButtonAction.HISTORY,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onOpenHistory
            )
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
                verticalArrangement = Arrangement.spacedBy(UI.Space.XL),
                modifier = Modifier.padding(UI.Space.XL)
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