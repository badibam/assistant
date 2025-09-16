package com.assistant.core.ai.ui.dialogs.data

import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.DataResultStatus

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

    // Value selection state
    val availableValues: List<String> = emptyList(),
    val selectedValues: List<String> = emptyList(),
    val contextualDataStatus: DataResultStatus = DataResultStatus.OK
)

/**
 * Étape de sélection dans la chaîne de navigation
 */
data class SelectionStep(
    val label: String,           // "Zone", "Outil", "Champ", etc.
    val selectedValue: String,   // "Santé", "Suivi Poids", "value", etc.
    val selectedNode: SchemaNode // Nœud sélectionné pour cette étape
)