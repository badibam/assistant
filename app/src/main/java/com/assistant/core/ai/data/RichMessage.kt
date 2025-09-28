package com.assistant.core.ai.data

/**
 * Rich message structure for user messages with enrichments
 */
data class RichMessage(
    val segments: List<MessageSegment>,
    val linearText: String,             // Computed at creation for AI consumption
    val dataCommands: List<DataCommand> // Computed at creation for prompt system
)

/**
 * Message segments - either text or enrichment blocks
 */
sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()

    data class EnrichmentBlock(
        val type: EnrichmentType,
        val config: String,       // JSON configuration of the block
        val preview: String       // Human-readable preview like "données nutrition zone Santé"
    ) : MessageSegment()
}

