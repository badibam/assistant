package com.assistant.core.ai.data

/**
 * Unified command structure for data queries and actions in the AI system
 *
 * Replaces DataQuery as part of the command system restructure to provide
 * a unified interface for both data retrieval and action execution.
 *
 * DUAL MODE: Absolute vs Relative commands based on session type:
 *
 * CHAT (isRelative = false):
 * - "cette semaine" → "nutrition.week_2024_12_09_2024_12_15"
 * - Ensures conversation context remains stable across days
 * - Reproducible results when reviewing old conversations
 *
 * AUTOMATION (isRelative = true):
 * - "cette semaine" → "nutrition.current_week"
 * - Resolves to current week at execution time
 * - Allows automation commands to adapt to execution date
 *
 * Custom date fields are always absolute regardless of session type.
 */
data class DataCommand(
    val id: String,              // Hash deterministic of (type + params + isRelative)
    val type: String,            // Standardized command type (see available types in AI.md)
    val params: Map<String, Any>, // Absolute or relative parameters
    val isRelative: Boolean = false // true for automation, false for chat
)