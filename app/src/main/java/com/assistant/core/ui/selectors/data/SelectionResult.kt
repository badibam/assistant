package com.assistant.core.ui.selectors.data

/**
 * Result of hierarchical scope selection
 */
data class SelectionResult(
    val selectedPath: String,                       // Full path of selection (e.g., "zones.zone1.tools.instance1.data.field")
    val selectionLevel: SelectionLevel,             // Level where selection stopped

    // Context-aware selection (new for tool_executions support)
    val selectedContext: PointerContext = PointerContext.DATA,  // Selected context (GENERIC, CONFIG, DATA, EXECUTIONS)
    val selectedResources: List<String> = emptyList(),          // Resources checked: ["data", "data_schema"] or ["executions", "executions_schema"] etc.

    // Period selection (from ZoneScopeSelector)
    val timestampSelection: TimestampSelection = TimestampSelection(),  // Period range selected for temporal filtering

    // Field-level selection (existing)
    val selectedValues: List<String> = emptyList(), // Selected values for the path
    val fieldSpecificData: FieldSpecificData? = null, // Additional data for specific field types
    val displayChain: List<String> = emptyList()    // Human-readable labels for display (e.g., ["Zone: Santé", "Outil: Suivi Poids"])
)

/**
 * Field-specific data for specialized field types
 */
sealed class FieldSpecificData {

    /**
     * Timestamp field selection data (date ranges)
     */
    data class TimestampData(
        val minTimestamp: Long? = null,
        val maxTimestamp: Long? = null,
        val description: String = ""
    ) : FieldSpecificData()

    /**
     * Name field selection data (entry names)
     */
    data class NameData(
        val selectedNames: List<String> = emptyList(),
        val availableNames: List<String> = emptyList()
    ) : FieldSpecificData()

    /**
     * Generic data field selection
     */
    data class DataValues(
        val values: List<String> = emptyList()
    ) : FieldSpecificData()
}