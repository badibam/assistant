package com.assistant.core.ai.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.assistant.core.ai.ui.dialogs.data.DataEnrichmentState
import com.assistant.core.ai.ui.dialogs.data.SelectionStep
import com.assistant.core.navigation.DataNavigator
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.ContextualDataResult
import com.assistant.core.navigation.data.DataResultStatus
import com.assistant.core.ui.UI
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.TextType
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Dialog pour configurer un enrichissement de données (🔍)
 * Permet de sélectionner une donnée via navigation hiérarchique
 * et de spécifier les critères de filtrage
 */
@Composable
fun DataEnrichmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (selectedPath: String, selectedValues: List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = remember { DataNavigator(context) }

    var state by remember { mutableStateOf(DataEnrichmentState()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load initial data (zones)
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val rootNodes = navigator.getRootNodes()
            state = state.copy(
                currentOptions = rootNodes,
                currentLevel = 0
            )
        } catch (e: Exception) {
            LogManager.coordination("Error loading root nodes in DataEnrichmentDialog: ${e.message}", "ERROR", e)
        } finally {
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Header
                UI.Text(
                    text = "Enrichissement Données",
                    type = TextType.TITLE
                )

                // Section 1: Metadata Selector (Conditional Dropdowns)
                MetadataSelectorSection(
                    state = state,
                    isLoading = isLoading,
                    onSelectionChange = { selectedNode ->
                        scope.launch {
                            try {
                                isLoading = true

                                // Add selection to chain
                                val newStep = SelectionStep(
                                    label = getSelectionLabel(selectedNode.type, state.currentLevel),
                                    selectedValue = selectedNode.displayName,
                                    selectedNode = selectedNode
                                )
                                val newChain = state.selectionChain + newStep

                                // Load next level options if node has children
                                val nextOptions = if (selectedNode.hasChildren) {
                                    when (selectedNode.type) {
                                        NodeType.ZONE -> navigator.getChildren(selectedNode.path)
                                        NodeType.TOOL -> navigator.getFieldChildren(selectedNode.path.substringAfter("tools."))
                                        NodeType.FIELD -> emptyList() // Fields are leaf nodes for now
                                    }
                                } else {
                                    emptyList()
                                }

                                state = state.copy(
                                    selectionChain = newChain,
                                    selectedPath = selectedNode.path,
                                    currentOptions = nextOptions,
                                    currentLevel = if (nextOptions.isNotEmpty()) state.currentLevel + 1 else state.currentLevel,
                                    isComplete = !selectedNode.hasChildren || nextOptions.isEmpty()
                                )

                                // Load contextual values if selection is complete
                                if (state.isComplete) {
                                    val contextualData = navigator.getDistinctValues(selectedNode.path)
                                    state = state.copy(
                                        availableValues = contextualData.data.map { it.toString() },
                                        contextualDataStatus = contextualData.status
                                    )
                                }

                            } catch (e: Exception) {
                                LogManager.coordination("Error in selection change: ${e.message}", "ERROR", e)
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )

                // Section 2: Value Selector (only if selection is complete)
                if (state.isComplete) {
                    ValueSelectorSection(
                        availableValues = state.availableValues,
                        selectedValues = state.selectedValues,
                        contextualDataStatus = state.contextualDataStatus,
                        onSelectionChange = { selectedValues ->
                            state = state.copy(selectedValues = selectedValues)
                        }
                    )
                }

                // Section 3: Query Preview (only if selection is complete)
                if (state.isComplete) {
                    QueryPreviewSection(
                        selectionChain = state.selectionChain,
                        selectedValues = state.selectedValues
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    UI.ActionButton(
                        action = ButtonAction.CANCEL,
                        onClick = onDismiss
                    )

                    UI.ActionButton(
                        action = ButtonAction.CONFIRM,
                        enabled = state.isComplete,
                        onClick = {
                            onConfirm(state.selectedPath, state.selectedValues)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataSelectorSection(
    state: DataEnrichmentState,
    isLoading: Boolean,
    onSelectionChange: (SchemaNode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = "Sélection des données",
            type = TextType.SUBTITLE
        )

        // Display selection chain (breadcrumb)
        if (state.selectionChain.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.selectionChain) { step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(80.dp)) {
                            UI.Text(
                                text = "${step.label}:",
                                type = TextType.LABEL
                            )
                        }
                        UI.Text(
                            text = step.selectedValue,
                            type = TextType.BODY
                        )
                    }
                }
            }
        }

        // Current level dropdown
        if (state.currentOptions.isNotEmpty()) {
            val currentLabel = if (state.selectionChain.isEmpty()) {
                "Zone"
            } else {
                getNextSelectionLabel(state.selectionChain.last().selectedNode.type)
            }

            Column {
                UI.Text(
                    text = "$currentLabel:",
                    type = TextType.LABEL
                )

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    // Simple list for now - could be replaced with proper dropdown
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.currentOptions) { option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = false,
                                        onClick = { onSelectionChange(option) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UI.Text(
                                        text = option.displayName,
                                        type = TextType.BODY
                                    )
                                    if (option.hasChildren) {
                                        UI.Text(
                                            text = " →",
                                            type = TextType.LABEL
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueSelectorSection(
    availableValues: List<String>,
    selectedValues: List<String>,
    contextualDataStatus: DataResultStatus,
    onSelectionChange: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = "Valeurs disponibles",
            type = TextType.SUBTITLE
        )

        when (contextualDataStatus) {
            DataResultStatus.OK -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableValues.take(10)) { value ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = value in selectedValues,
                                onCheckedChange = { checked ->
                                    val newSelection = if (checked) {
                                        selectedValues + value
                                    } else {
                                        selectedValues - value
                                    }
                                    onSelectionChange(newSelection)
                                }
                            )
                            Box(modifier = Modifier.padding(start = 8.dp)) {
                                UI.Text(
                                    text = value,
                                    type = TextType.BODY
                                )
                            }
                        }
                    }

                    if (availableValues.size > 10) {
                        item {
                            UI.Text(
                                text = "... et ${availableValues.size - 10} autres valeurs",
                                type = TextType.CAPTION
                            )
                        }
                    }
                }
            }
            DataResultStatus.ERROR -> {
                UI.Text(
                    text = "Erreur lors du chargement des valeurs",
                    type = TextType.BODY
                )
            }
            else -> {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun QueryPreviewSection(
    selectionChain: List<SelectionStep>,
    selectedValues: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = "Aperçu",
            type = TextType.SUBTITLE
        )

        // Description
        val description = buildQueryDescription(selectionChain, selectedValues)
        UI.Text(
            text = description,
            type = TextType.BODY
        )

        // SQL (placeholder for now)
        val sqlQuery = buildSqlQuery(selectionChain, selectedValues)
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                UI.Text(
                    text = "Requête SQL:",
                    type = TextType.LABEL
                )
                UI.Text(
                    text = sqlQuery,
                    type = TextType.CAPTION
                )
            }
        }
    }
}

// Helper functions

private fun getSelectionLabel(nodeType: NodeType, level: Int): String {
    return when (nodeType) {
        NodeType.ZONE -> "Zone"
        NodeType.TOOL -> "Outil"
        NodeType.FIELD -> "Champ"
    }
}

private fun getNextSelectionLabel(currentNodeType: NodeType): String {
    return when (currentNodeType) {
        NodeType.ZONE -> "Outil"
        NodeType.TOOL -> "Champ"
        NodeType.FIELD -> "Sous-champ"
    }
}

private fun buildQueryDescription(selectionChain: List<SelectionStep>, selectedValues: List<String>): String {
    if (selectionChain.isEmpty()) return "Aucune sélection"

    val path = selectionChain.joinToString(" → ") { it.selectedValue }
    val valuesPart = when {
        selectedValues.isEmpty() -> "toutes les valeurs"
        selectedValues.size == 1 -> "valeur: ${selectedValues.first()}"
        else -> "${selectedValues.size} valeurs sélectionnées"
    }

    return "Données de '$path' pour $valuesPart"
}

private fun buildSqlQuery(selectionChain: List<SelectionStep>, selectedValues: List<String>): String {
    // Placeholder SQL generation
    if (selectionChain.isEmpty()) return "-- Aucune sélection"

    val toolStep = selectionChain.find { it.selectedNode.type == NodeType.TOOL }
    val fieldStep = selectionChain.find { it.selectedNode.type == NodeType.FIELD }

    val tableName = toolStep?.selectedNode?.toolType ?: "unknown_table"
    val fieldName = fieldStep?.selectedNode?.path?.substringAfterLast(".") ?: "*"

    val whereClause = if (selectedValues.isNotEmpty()) {
        "WHERE $fieldName IN (${selectedValues.joinToString(", ") { "'$it'" }})"
    } else {
        ""
    }

    return "SELECT $fieldName FROM ${tableName}_data $whereClause"
}