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

    // Session management
    var activeSession by remember { mutableStateOf<AISession?>(null) }
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val aiOrchestrator = remember { AIOrchestrator(context) }

    // Initialize session on first load
    LaunchedEffect(Unit) {
        try {
            // Check for active session first, create one if none exists
            val existingSession = aiOrchestrator.getActiveSession()

            if (existingSession != null) {
                // Reuse existing active session
                activeSession = existingSession
                LogManager.aiUI("AIFloatingChat reusing active session: ${existingSession.id}")
            } else {
                // Create new session if none exists
                val sessionId = aiOrchestrator.createSession("New Chat", SessionType.CHAT)
                aiOrchestrator.setActiveSession(sessionId)
                val session = aiOrchestrator.loadSession(sessionId)
                activeSession = session
                LogManager.aiUI("AIFloatingChat created new session: $sessionId")
            }
        } catch (e: Exception) {
            LogManager.aiUI("Failed to initialize AI session: ${e.message}", "ERROR")
            errorMessage = "Failed to initialize chat session"
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
                    sessionName = activeSession?.name ?: "Loading...",
                    isActive = activeSession?.isActive ?: false,
                    isLoading = isLoading,
                    onClose = onDismiss
                )

                // Messages area (scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ChatMessageList(
                        messages = activeSession?.messages ?: emptyList(),
                        isLoading = isLoading,
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
                                LogManager.aiUI("RichMessage.dataQueries: ${richMessage.dataQueries.size} queries")

                                activeSession?.let { session ->
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null

                                        try {
                                            // Send via AISessionManager (complete flow)
                                            val result = aiOrchestrator.sendMessage(richMessage, session.id)

                                            if (result.success) {
                                                // Reload session with new messages
                                                val updatedSession = aiOrchestrator.loadSession(session.id)
                                                activeSession = updatedSession
                                                segments = emptyList() // Clear composer
                                                LogManager.aiUI("Message sent successfully, session updated")
                                            } else {
                                                errorMessage = result.error
                                                LogManager.aiUI("Failed to send message: ${result.error}", "ERROR")
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to send message: ${e.message}"
                                            LogManager.aiUI("Exception sending message: ${e.message}", "ERROR")
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                } ?: run {
                                    errorMessage = "No active session"
                                    LogManager.aiUI("Cannot send message: no active session", "ERROR")
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
    isActive: Boolean,
    isLoading: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    UI.PageHeader(
        title = sessionName,
        subtitle = when {
            isLoading -> "Processing..."
            isActive -> "Ready"
            else -> "Inactive"
        },
        rightButton = ButtonAction.CANCEL, // Use cancel for close
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
                        text = "Start a conversation...",
                        type = TextType.BODY
                    )
                }
            }
        } else {
            items(messages) { message ->
                ChatMessageBubble(message = message)
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        UI.Text(
                            text = "AI is thinking...",
                            type = TextType.CAPTION
                        )
                    }
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
                        MessageSender.USER -> "You"
                        MessageSender.AI -> "AI"
                        MessageSender.SYSTEM -> "System"
                    },
                    type = TextType.LABEL
                )

                // Message content
                when {
                    message.richContent != null -> {
                        // Rich message with segments
                        UI.Text(
                            text = "Rich: ${message.richContent.linearText}",
                            type = TextType.BODY
                        )
                        if (message.richContent.segments.filterIsInstance<MessageSegment.EnrichmentBlock>().isNotEmpty()) {
                            UI.Text(
                                text = "Enrichments: ${message.richContent.segments.filterIsInstance<MessageSegment.EnrichmentBlock>().size}",
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

