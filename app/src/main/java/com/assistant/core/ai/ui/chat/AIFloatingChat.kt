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
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager

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

    // Mock session for testing
    var mockSession by remember { mutableStateOf(createMockSession()) }
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) }

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
                    sessionName = mockSession.name,
                    isActive = mockSession.isActive,
                    onClose = onDismiss
                )

                // Messages area (scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ChatMessageList(
                        messages = mockSession.messages,
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
                                LogManager.service("RichMessage sent: ${richMessage.linearText}")
                                LogManager.service("DataQueries: ${richMessage.dataQueries.size}")
                                LogManager.service("Segments: ${richMessage.segments.size}")

                                // Add message to mock session
                                val newMessage = SessionMessage(
                                    id = "msg_${System.currentTimeMillis()}",
                                    timestamp = System.currentTimeMillis(),
                                    sender = MessageSender.USER,
                                    richContent = richMessage,
                                    textContent = null,
                                    aiMessage = null,
                                    aiMessageJson = null,
                                    systemMessage = null
                                )
                                mockSession = mockSession.copy(
                                    messages = mockSession.messages + newMessage
                                )

                                // Clear composer
                                segments = emptyList()

                                // TODO: Send to AI service
                            },
                            placeholder = s.shared("ai_composer_placeholder")
                        )
                    }
                }
            }
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    UI.PageHeader(
        title = sessionName,
        subtitle = if (isActive) "Active" else "Inactive",
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

/**
 * Create mock session for testing
 */
private fun createMockSession(): AISession {
    return AISession(
        id = "mock_session",
        name = "Test Chat",
        type = SessionType.CHAT,
        providerId = "mock",
        providerSessionId = "mock_provider_session",
        schedule = null,
        createdAt = System.currentTimeMillis(),
        lastActivity = System.currentTimeMillis(),
        messages = listOf(
            SessionMessage(
                id = "welcome",
                timestamp = System.currentTimeMillis(),
                sender = MessageSender.SYSTEM,
                richContent = null,
                textContent = null,
                aiMessage = null,
                aiMessageJson = null,
                systemMessage = SystemMessage(
                    type = SystemMessageType.DATA_ADDED,
                    commandResults = emptyList(),
                    summary = "Welcome to AI Chat! Try adding enrichments to your messages."
                )
            )
        ),
        isActive = true
    )
}