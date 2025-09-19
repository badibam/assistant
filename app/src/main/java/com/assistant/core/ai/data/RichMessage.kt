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
 * DUAL MODE: Absolute vs Relative queries based on session type:
 *
 * CHAT (isRelative = false):
 * - "cette semaine" → "nutrition.week_2024_12_09_2024_12_15"
 * - Ensures conversation context remains stable across days
 * - Reproducible results when reviewing old conversations
 *
 * AUTOMATION (isRelative = true):
 * - "cette semaine" → "nutrition.current_week"
 * - Resolves to current week at execution time
 * - Allows automation queries to adapt to execution date
 *
 * Custom date fields are always absolute regardless of session type.
 */
data class DataQuery(
    val id: String,              // Format: "type.param1_value1.param2_value2"
    val type: String,            // Query type (ZONE_DATA, TOOL_ENTRIES, etc.)
    val params: Map<String, Any>, // Query parameters (absolute or relative)
    val isRelative: Boolean = false // true for automation, false for chat
)