package com.assistant.core.ui.selectors.data

/**
 * Result of hierarchical scope selection
 */
data class SelectionResult(
    val selectedPath: String,                       // Full path of selection (e.g., "zones.zone1.tools.instance1.data.field")
    val selectedValues: List<String>,               // Selected values for the path
    val selectionLevel: SelectionLevel,             // Level where selection stopped
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