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
     *
     * @param cancelMessageId ID of the VALIDATION_CANCELLED system message to delete if user validates
     */
    data class WaitingValidation(
        val context: ValidationContext,
        val cancelMessageId: String
    ) : WaitingState()

    /**
     * Waiting for user response to communication module
     *
     * @param cancelMessageId ID of the COMMUNICATION_CANCELLED system message to delete if user responds
     */
    data class WaitingResponse(
        val module: CommunicationModule,
        val cancelMessageId: String
    ) : WaitingState()
}
