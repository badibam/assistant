package com.assistant.core.ai.domain

import com.assistant.core.ai.data.AIMessage
import com.assistant.core.ai.data.CommunicationModule
import com.assistant.core.ai.validation.ValidationContext

/**
 * Context data when AI execution is waiting for user interaction.
 *
 * Used by AIState to persist waiting state information.
 * This context is stored in DB as JSON for recovery after app restart.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Validation: Actions pending approval (WAITING_VALIDATION phase)
 * - Communication: User response needed (WAITING_COMMUNICATION_RESPONSE phase)
 * - CompletionConfirmation: System waiting before auto-confirming completion (WAITING_COMPLETION_CONFIRMATION phase)
 */
sealed class WaitingContext {
    /**
     * Waiting for user validation of actions.
     *
     * @param validationContext Full validation context with verbalized actions
     * @param cancelMessageId ID of the fallback VALIDATION_CANCELLED message created before suspension
     */
    data class Validation(
        val validationContext: ValidationContext,
        val cancelMessageId: String
    ) : WaitingContext()

    /**
     * Waiting for user response to communication module (CHAT only).
     *
     * @param communicationModule The module that requested user response
     * @param aiMessageId ID of the AI message containing the module (for reference)
     * @param cancelMessageId ID of the fallback COMMUNICATION_CANCELLED message created before suspension
     */
    data class Communication(
        val communicationModule: CommunicationModule,
        val aiMessageId: String,
        val cancelMessageId: String
    ) : WaitingContext()

    /**
     * Waiting for system auto-confirmation of completion (AUTOMATION only).
     *
     * This state is used to give a small delay (1s) before auto-confirming
     * completion when AI sets completed=true in AUTOMATION sessions.
     *
     * User can still click "Rejeter" during this window to reject completion
     * and allow AI to continue working.
     *
     * @param aiMessageId ID of the AI message that set completed=true
     * @param scheduledConfirmationTime Timestamp when auto-confirmation should trigger
     */
    data class CompletionConfirmation(
        val aiMessageId: String,
        val scheduledConfirmationTime: Long
    ) : WaitingContext()

    /**
     * Serialize waiting context to JSON string for DB storage.
     */
    fun toJson(): String {
        return when (this) {
            is Validation -> """
                {
                    "type": "Validation",
                    "validationContext": ${validationContextToJson(validationContext)},
                    "cancelMessageId": "$cancelMessageId"
                }
            """.trimIndent()

            is Communication -> """
                {
                    "type": "Communication",
                    "communicationModule": ${communicationModuleToJson(communicationModule)},
                    "aiMessageId": "$aiMessageId",
                    "cancelMessageId": "$cancelMessageId"
                }
            """.trimIndent()

            is CompletionConfirmation -> """
                {
                    "type": "CompletionConfirmation",
                    "aiMessageId": "$aiMessageId",
                    "scheduledConfirmationTime": $scheduledConfirmationTime
                }
            """.trimIndent()
        }
    }

    companion object {
        /**
         * Deserialize waiting context from JSON string.
         * Returns null if parsing fails.
         */
        fun fromJson(jsonString: String): WaitingContext? {
            // TODO: Implement JSON deserialization when needed for recovery
            // For now, waiting contexts are reconstructed from DB state on app restart
            return null
        }

        /**
         * Helper to serialize ValidationContext to JSON
         */
        private fun validationContextToJson(context: ValidationContext): String {
            // Simplified serialization - full reconstruction from DB on restart
            return """{"aiMessageId": "${context.aiMessageId}"}"""
        }

        /**
         * Helper to serialize CommunicationModule to JSON
         */
        private fun communicationModuleToJson(module: CommunicationModule): String {
            // Use AIMessage serialization logic
            return """{"type": "${module.type}", "data": ${dataToJson(module.data)}}"""
        }

        /**
         * Helper to serialize Map<String, Any> to JSON
         */
        private fun dataToJson(data: Map<String, Any>): String {
            val entries = data.entries.joinToString(", ") { (key, value) ->
                val valueStr = when (value) {
                    is String -> "\"$value\""
                    is List<*> -> "[${value.joinToString(", ") { "\"$it\"" }}]"
                    else -> value.toString()
                }
                "\"$key\": $valueStr"
            }
            return "{$entries}"
        }
    }
}
