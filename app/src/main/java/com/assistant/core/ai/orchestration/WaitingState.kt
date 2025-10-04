package com.assistant.core.ai.orchestration

import com.assistant.core.ai.data.ValidationRequest
import com.assistant.core.ai.data.CommunicationModule

/**
 * Sealed class representing AI waiting states for user interaction
 * Used with StateFlow for UI observation and reconnection
 */
sealed class WaitingState {
    /**
     * No waiting - normal flow
     */
    object None : WaitingState()

    /**
     * Waiting for user validation of proposed actions
     */
    data class WaitingValidation(val request: ValidationRequest) : WaitingState()

    /**
     * Waiting for user response to communication module
     */
    data class WaitingResponse(val module: CommunicationModule) : WaitingState()
}
