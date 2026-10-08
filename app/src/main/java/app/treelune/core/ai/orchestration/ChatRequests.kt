package app.treelune.core.ai.orchestration

import app.treelune.core.ai.data.MessageSegment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Asks for a chat opened with a content — text and pointers in its composer, sent by the user
 * when they choose — from any screen: an automation's card, a questionnaire's « Avec l'IA ». The
 * main screen, which holds the chat, opens it (AIOrchestrator.startNewChatSession).
 */
object ChatRequests {
    private val _requests = MutableSharedFlow<List<MessageSegment>>(extraBufferCapacity = 1)
    val requests: SharedFlow<List<MessageSegment>> = _requests.asSharedFlow()

    fun open(prefill: List<MessageSegment>) {
        _requests.tryEmit(prefill)
    }
}
