package com.assistant.core.ui.selectors.data

import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.DataResultStatus
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.RelativePeriod

/**
 * State for zone scope navigation and selection
 * Simplified for POINTER enrichments (ZONE → INSTANCE → CONTEXT → RESOURCES → PERIOD)
 */
data class ZoneScopeState(
    // Navigation state (ZONE and INSTANCE levels only)
    val selectionChain: List<SelectionStep> = emptyList(),
    val selectedPath: String = "",
    val currentOptions: List<SchemaNode> = emptyList(),
    val currentLevel: Int = 0,

    // Options available at each level for allowing changes (flexible depth)
    val optionsByLevel: Map<Int, List<SchemaNode>> = emptyMap(),

    // Context selection (GENERIC, CONFIG, DATA, EXECUTIONS)
    val selectedContext: PointerContext = PointerContext.GENERIC,

    // Resources selection (context-specific checkable items)
    val selectedResources: List<String> = emptyList(),

    // Period selection (for DATA and EXECUTIONS contexts, optional for GENERIC)
    val timestampSelection: TimestampSelection = TimestampSelection(),

    // Completion state
    val isComplete: Boolean = false
)

/**
 * Étape de sélection dans la chaîne de navigation
 */
data class SelectionStep(
    val label: String,           // "Zone", "Outil", "Champ", etc.
    val selectedValue: String,   // "Santé", "Suivi Poids", "value", etc.
    val selectedNode: SchemaNode // Nœud sélectionné pour cette étape
)

/**
 * Period selection for temporal filtering (date range)
 * Supports both absolute periods (CHAT) and relative periods (AUTOMATION)
 *
 * Used for:
 * - DATA context: filters tool_data.timestamp
 * - EXECUTIONS context: filters tool_executions.executionTime
 * - GENERIC context: reference period for AI (optional)
 */
data class TimestampSelection(
    // Date minimum - absolute mode (CHAT)
    val minPeriodType: PeriodType? = null,          // jour, semaine, mois, année, personnalisée
    val minPeriod: Period? = null,                  // période absolue sélectionnée pour min
    val minCustomDateTime: Long? = null,            // date/heure personnalisée pour min (always absolute)

    // Date maximum - absolute mode (CHAT)
    val maxPeriodType: PeriodType? = null,          // jour, semaine, mois, année, personnalisée
    val maxPeriod: Period? = null,                  // période absolue sélectionnée pour max
    val maxCustomDateTime: Long? = null,            // date/heure personnalisée pour max (always absolute)

    // Relative mode (AUTOMATION) - mutually exclusive with Period fields
    val minRelativePeriod: RelativePeriod? = null,  // période relative pour min
    val maxRelativePeriod: RelativePeriod? = null   // période relative pour max
) {
    val isComplete: Boolean
        get() = (minPeriodType != null || maxPeriodType != null) &&
                (minPeriod != null || minCustomDateTime != null || maxPeriod != null || maxCustomDateTime != null ||
                 minRelativePeriod != null || maxRelativePeriod != null)
}