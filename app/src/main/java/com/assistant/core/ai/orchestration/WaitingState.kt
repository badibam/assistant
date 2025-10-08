package com.assistant.core.ai.orchestration

import com.assistant.core.ai.data.CommunicationModule
import com.assistant.core.ai.validation.ValidationContext

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
     *
     * The ValidationContext contains all information needed for the UI:
     * - aiMessageId for persistence/reconstruction
     * - actions list (all actions, validated and non-validated)
     * - verbalized actions with descriptions, warnings, and reasons
     */
    data class WaitingValidation(val context: ValidationContext) : WaitingState()

    /**
     * Waiting for user response to communication module
     */
    data class WaitingResponse(val module: CommunicationModule) : WaitingState()
}
