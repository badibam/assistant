package com.assistant.core.ai.domain

import com.assistant.core.ai.data.AIMessage
import com.assistant.core.ai.data.CommunicationModule
import com.assistant.core.ai.validation.ValidationContext

/**
 * Context data when AI execution is waiting for user interaction.
 *
 * Held in AIState only, never stored: it derives entirely from the last AI message (its
 * action commands, or its communication module) and the validation rules. A session restored
 * in a waiting phase gets it back from AIEventProcessor, which builds it on entering that
 * phase exactly as it did the first time.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Validation: Actions pending approval (WAITING_VALIDATION phase)
 * - Communication: User response needed (WAITING_COMMUNICATION_RESPONSE phase)
 */
sealed class WaitingContext {
    /**
     * Waiting for user validation of actions.
     *
     * @param validationContext Full validation context with verbalized actions
     */
    data class Validation(
        val validationContext: ValidationContext
    ) : WaitingContext()

    /**
     * Waiting for user response to communication module (CHAT only).
     *
     * @param communicationModule The module that requested user response
     * @param aiMessageId ID of the AI message containing the module (for reference)
     */
    data class Communication(
        val communicationModule: CommunicationModule,
        val aiMessageId: String
    ) : WaitingContext()
}
