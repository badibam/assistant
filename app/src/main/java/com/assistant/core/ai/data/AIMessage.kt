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
 */
sealed class CommunicationModule {
    abstract val id: String
    abstract val metadata: ModuleMetadata

    data class MultipleChoice(
        override val id: String,
        override val metadata: ModuleMetadata,
        val question: String,
        val options: List<String>
    ) : CommunicationModule()

    data class Validation(
        override val id: String,
        override val metadata: ModuleMetadata,
        val message: String
    ) : CommunicationModule()

    // TODO: Add Slider, DataSelector modules when needed
}

/**
 * Metadata for communication modules
 */
data class ModuleMetadata(
    val displayName: String,
    val description: String,
    val paramsSchema: String,     // JSON Schema for params
    val responseSchema: String    // JSON Schema for response
)