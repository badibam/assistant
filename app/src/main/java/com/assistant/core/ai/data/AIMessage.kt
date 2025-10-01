package com.assistant.core.ai.data

/**
 * AI message structure with validation, data commands and action commands
 * Important constraint: Either dataCommands OR actionCommands, never both simultaneously
 */
data class AIMessage(
    val preText: String,                              // Required introduction/analysis
    val validationRequest: ValidationRequest?,        // Optional - before actions
    val dataCommands: List<DataCommand>?,             // Optional - AI data queries
    val actionCommands: List<DataCommand>?,           // Optional - actions to execute
    val postText: String?,                            // Optional, only if actions
    val communicationModule: CommunicationModule?     // Optional, always last
)

/**
 * Validation request from AI before executing actions
 */
data class ValidationRequest(
    val message: String,
    val status: ValidationStatus? = null // PENDING, CONFIRMED, REFUSED
)


/**
 * Communication modules for user interaction
 * Sealed class for controlled set of core modules
 *
 * Pattern analogous to tool_data: common type + variable data in Map
 * Data validated via CommunicationModuleSchemas
 *
 * Exclusive with dataCommands/actionCommands/postText:
 * - If communicationModule present → only preText + communicationModule (+ optional validationRequest if related)
 * - User response stored as simple SessionMessage with textContent
 */
sealed class CommunicationModule {
    abstract val type: String
    abstract val data: Map<String, Any>

    /**
     * Multiple choice question
     * Data: question (String), options (List<String>)
     */
    data class MultipleChoice(
        override val type: String = "MultipleChoice",
        override val data: Map<String, Any>
    ) : CommunicationModule()

    /**
     * Validation/confirmation request
     * Data: message (String)
     */
    data class Validation(
        override val type: String = "Validation",
        override val data: Map<String, Any>
    ) : CommunicationModule()

    // TODO: Add Slider, DataSelector modules when needed
}