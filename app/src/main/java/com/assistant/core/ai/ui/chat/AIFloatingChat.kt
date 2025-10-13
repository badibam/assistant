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
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.ai.ui.screens.AIScreen
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Floating AI Chat interface - Dialog wrapper around AIScreen
 *
 * Purpose:
 * - Provides Dialog overlay for active session (CHAT or AUTOMATION)
 * - Auto-creates CHAT session on first message if none active
 * - Observes activeSessionId to detect session changes (eviction)
 *
 * Architecture:
 * - Simple Dialog wrapper that delegates all logic to AIScreen
 * - Shows "new chat" prompt if no active session
 * - Automatically switches to AIScreen when session becomes active
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

    // Observe orchestrator active session ID (reactive to eviction)
    val orchestratorSessionId by AIOrchestrator.activeSessionId.collectAsState()

    // Local state for new chat creation
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            if (orchestratorSessionId != null) {
                // Active session exists - show AIScreen
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
            } else {
                // No active session - show new chat prompt
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    // Header
                    NewChatHeader(onClose = onDismiss)

                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            UI.Text(
                                text = s.shared("ai_chat_start_conversation"),
                                type = TextType.TITLE
                            )
                            UI.Text(
                                text = s.shared("ai_status_send_to_start"),
                                type = TextType.BODY
                            )
                        }
                    }

                    // Composer for first message
                    UI.Card(type = CardType.DEFAULT) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            UI.RichComposer(
                                segments = segments,
                                onSegmentsChange = { segments = it },
                                onSend = { richMessage ->
                                    LogManager.aiUI("AIFloatingChat: Creating new session with first message")
                                    errorMessage = null

                                    // Use sendMessageAsync which will create session automatically
                                    AIOrchestrator.sendMessageAsync(
                                        richMessage = richMessage,
                                        onSessionCreated = { sessionId ->
                                            LogManager.aiUI("New session created: $sessionId")
                                        },
                                        onComposerClear = {
                                            segments = emptyList()
                                        },
                                        onError = { error ->
                                            errorMessage = error
                                        }
                                    )
                                },
                                placeholder = s.shared("ai_composer_placeholder")
                            )
                        }
                    }
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

/**
 * Header for new chat state (no active session)
 */
@Composable
private fun NewChatHeader(onClose: () -> Unit) {
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
        // Title
        Box(modifier = Modifier.weight(1f)) {
            UI.Text(
                text = s.shared("ai_chat_new"),
                type = TextType.TITLE
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
