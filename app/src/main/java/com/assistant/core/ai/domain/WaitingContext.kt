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

    /**
     * Waiting for the user to send or refuse data above the size threshold (CHAT only).
     *
     * @param messageId The SYSTEM message holding the data, kept out of the prompt meanwhile
     * @param dataChars Size of its data text, as the AI would receive it
     * @param maxDataChars The threshold it went over
     * @param alwaysSend Whether the data is that of the tools sent always, asked once for the
     *   whole session before the AI is called (docs/design/always-send.md), rather than data
     *   the AI asked for
     */
    data class DataConfirmation(
        val messageId: String,
        val dataChars: Int,
        val maxDataChars: Int,
        val alwaysSend: Boolean = false
    ) : WaitingContext()
}
