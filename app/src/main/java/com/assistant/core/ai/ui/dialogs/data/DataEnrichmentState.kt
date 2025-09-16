package com.assistant.core.ai.ui.dialogs.data

import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.DataResultStatus
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.Period

/**
 * État du dialog d'enrichissement de données
 */
data class DataEnrichmentState(
    // Navigation state
    val selectionChain: List<SelectionStep> = emptyList(),
    val selectedPath: String = "",
    val currentOptions: List<SchemaNode> = emptyList(),
    val currentLevel: Int = 0,
    val isComplete: Boolean = false,

    // Options available at each level for allowing changes (flexible depth)
    val optionsByLevel: Map<Int, List<SchemaNode>> = emptyMap(),

    // Value selection state
    val availableValues: List<String> = emptyList(),
    val selectedValues: List<String> = emptyList(),
    val contextualDataStatus: DataResultStatus = DataResultStatus.OK,

    // Field-specific selection state
    val fieldSpecificType: FieldSelectionType = FieldSelectionType.NONE,

    // Timestamp field selection (date range)
    val timestampSelection: TimestampSelection = TimestampSelection(),

    // Name field selection
    val nameSelection: NameSelection = NameSelection()
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
 * Types de sélection spécifique aux champs
 */
enum class FieldSelectionType {
    NONE,        // Pas de sélection spécifique (champs data.*)
    TIMESTAMP,   // Sélection de plage temporelle
    NAME         // Sélection de noms d'entrées
}

/**
 * Sélection pour champ timestamp (plage temporelle)
 */
data class TimestampSelection(
    // Date minimum
    val minPeriodType: PeriodType? = null,          // jour, semaine, mois, année, personnalisée
    val minPeriod: Period? = null,                  // période sélectionnée pour min
    val minCustomDateTime: Long? = null,            // date/heure personnalisée pour min

    // Date maximum
    val maxPeriodType: PeriodType? = null,          // jour, semaine, mois, année, personnalisée
    val maxPeriod: Period? = null,                  // période sélectionnée pour max
    val maxCustomDateTime: Long? = null             // date/heure personnalisée pour max
) {
    val isComplete: Boolean
        get() = (minPeriodType != null || maxPeriodType != null) &&
                (minPeriod != null || minCustomDateTime != null || maxPeriod != null || maxCustomDateTime != null)
}

/**
 * Sélection pour champ name (noms d'entrées)
 */
data class NameSelection(
    val availableNames: List<String> = emptyList(),  // Noms d'entrées disponibles
    val selectedNames: List<String> = emptyList()    // Noms sélectionnés
) {
    val isComplete: Boolean
        get() = true // Always complete - can select 0 or more names
}