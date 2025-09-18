package com.assistant.core.ai.data

/**
 * Rich message structure for user messages with enrichments
 */
data class RichMessage(
    val segments: List<MessageSegment>,
    val linearText: String,           // Computed at creation for AI consumption
    val dataQueries: List<DataQuery>  // Computed at creation for prompt system
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

/**
 * Data query for prompt system integration
 *
 * IMPORTANT: Queries use ABSOLUTE timestamps/periods for consistency:
 * - "cette semaine" → "nutrition.week_2024_12_09_2024_12_15"
 * - "aujourd'hui" → "tracking.day_2024_12_15"
 *
 * This ensures:
 * - Conversation context remains stable across days
 * - Reproducible results when reviewing old conversations
 * - Clear traceability of what data was actually used
 *
 * Enrichment blocks must calculate absolute timestamps at creation time.
 */
data class DataQuery(
    val id: String,              // Format: "type.param1_value1.param2_value2"
    val type: String,            // Query type (ZONE_DATA, TOOL_ENTRIES, etc.)
    val params: Map<String, Any> // Query parameters with absolute values
)