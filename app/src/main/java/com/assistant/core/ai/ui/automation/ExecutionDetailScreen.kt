package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.domain.AIState
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.data.MessageSender
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.ui.chat.ChatMessageBubble
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*

/**
 * ExecutionDetailScreen - Display automation execution message history
 *
 * Shows:
 * - Page header with BACK button
 * - Full conversation history in read-only mode
 * - All messages displayed with ChatMessageBubble
 *
 * Read-only mode:
 * - No RichComposer (no user input)
 * - No actions on messages
 * - No validation/communication module interactions
 * - Messages displayed for audit and transparency
 *
 * Usage: Accessed from ExecutionCard VIEW button in AutomationScreen
 */
@Composable
fun ExecutionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Observe messages for this session
    val messages by AIOrchestrator.observeMessages(sessionId).collectAsState(initial = emptyList())

    // Create a static AIState for read-only display (IDLE, no interactions)
    val readOnlyAIState = remember {
        AIState(
            sessionId = sessionId,
            phase = Phase.CLOSED,
            sessionType = null,
            automationId = null,
            totalRoundtrips = 0,
            lastEventTime = 0L,
            lastUserInteractionTime = 0L,
            waitingContext = null
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("automation_execution_detail_title"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onNavigateBack
        )

        // Messages list (read-only)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(messages) { index, message ->
                // Determine if this is the last AI message (for styling)
                val isLastAI = message.sender == MessageSender.AI &&
                              index == messages.indexOfLast { it.sender == MessageSender.AI }

                // Get previous AI message for context
                val previousAI = if (index > 0) {
                    messages.subList(0, index).lastOrNull { it.sender == MessageSender.AI }
                } else null

                ChatMessageBubble(
                    message = message,
                    aiState = readOnlyAIState,  // Static state, no interactions
                    isLastAIMessage = isLastAI,
                    previousAIMessage = previousAI
                )
            }
        }
    }
}
