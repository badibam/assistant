package com.assistant.core.ai.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.assistant.core.ai.ui.dialogs.data.DataEnrichmentState
import com.assistant.core.ai.ui.dialogs.data.SelectionStep
import com.assistant.core.ai.ui.dialogs.data.FieldSelectionType
import com.assistant.core.ai.ui.dialogs.data.TimestampSelection
import com.assistant.core.ai.ui.dialogs.data.NameSelection
import com.assistant.core.navigation.DataNavigator
import com.assistant.core.navigation.getAvailableFields
import com.assistant.core.navigation.getFieldChildrenFromCommonStructure
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.ContextualDataResult
import com.assistant.core.navigation.data.DataResultStatus
import com.assistant.core.ui.UI
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.TextType
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodSelector
import com.assistant.core.ui.components.normalizeTimestampWithConfig
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Creates current period with normalization according to configuration
 */
private fun createCurrentPeriod(type: PeriodType, dayStartHour: Int, weekStartDay: String): Period {
    val now = System.currentTimeMillis()
    val normalizedTimestamp = normalizeTimestampWithConfig(now, type, dayStartHour, weekStartDay)
    return Period(normalizedTimestamp, type)
}

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
    val coordinator = remember { Coordinator(context) }

    var state by remember { mutableStateOf(DataEnrichmentState()) }
    var isLoading by remember { mutableStateOf(false) }

    // App config state
    var dayStartHour by remember { mutableStateOf<Int?>(null) }
    var weekStartDay by remember { mutableStateOf<String?>(null) }
    var isConfigLoading by remember { mutableStateOf(true) }

    // Load app config and initial data
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            // Load app config first
            val configResult = coordinator.processUserAction("app_config.get", mapOf("category" to "format"))
            if (configResult.isSuccess) {
                val config = configResult.data?.get("settings") as? Map<String, Any>
                dayStartHour = (config?.get("day_start_hour") as? Number)?.toInt()
                weekStartDay = config?.get("week_start_day") as? String
            }

            // Load initial data (zones)
            val rootNodes = navigator.getRootNodes()
            state = state.copy(
                optionsByLevel = mapOf(0 to rootNodes),
                currentOptions = rootNodes,
                currentLevel = 0
            )
        } catch (e: Exception) {
            LogManager.coordination("Error loading initial data in DataEnrichmentDialog: ${e.message}", "ERROR", e)
        } finally {
            isLoading = false
            isConfigLoading = false
        }
    }

    // Show loading state while config is being loaded
    if (isConfigLoading || dayStartHour == null || weekStartDay == null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UI.Text(text = "Chargement de la configuration...", type = TextType.BODY)
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f) // Limit dialog height to 90% of screen
            .padding(16.dp)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState),
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

                                // Determine the level of the selected node to reset appropriately
                                val selectedLevel = when (selectedNode.type) {
                                    NodeType.ZONE -> 0
                                    NodeType.TOOL -> 1
                                    NodeType.FIELD -> 2
                                }

                                // Reset chain to the selected level and add new selection
                                val newStep = SelectionStep(
                                    label = getSelectionLabel(selectedNode.type, selectedLevel),
                                    selectedValue = selectedNode.displayName,
                                    selectedNode = selectedNode
                                )
                                val newChain = state.selectionChain.take(selectedLevel) + newStep

                                // Load next level options if node has children
                                val nextOptions = if (selectedNode.hasChildren) {
                                    when (selectedNode.type) {
                                        NodeType.ZONE -> navigator.getChildren(selectedNode.path)
                                        NodeType.TOOL -> {
                                            // Use the new common structure approach
                                            try {
                                                navigator.getAvailableFields(selectedNode.path.substringAfter("tools."), context)
                                            } catch (e: Exception) {
                                                LogManager.coordination("Error loading available fields for ${selectedNode.path}: ${e.message}", "ERROR", e)
                                                emptyList()
                                            }
                                        }
                                        NodeType.FIELD -> {
                                            // Fields can have sub-fields (nested structure)
                                            try {
                                                navigator.getFieldChildrenFromCommonStructure(selectedNode.path, context)
                                            } catch (e: Exception) {
                                                LogManager.coordination("Error loading field children for ${selectedNode.path}: ${e.message}", "ERROR", e)
                                                emptyList()
                                            }
                                        }
                                    }
                                } else {
                                    emptyList()
                                }

                                val isComplete = !selectedNode.hasChildren || nextOptions.isEmpty()

                                // Update optionsByLevel - keep options up to current level, add next level
                                val newOptionsByLevel = state.optionsByLevel.toMutableMap()

                                // Remove deeper levels than current selection
                                newOptionsByLevel.keys.filter { it > selectedLevel }.forEach { key ->
                                    newOptionsByLevel.remove(key)
                                }

                                // Add next level options if available
                                if (nextOptions.isNotEmpty()) {
                                    newOptionsByLevel[selectedLevel + 1] = nextOptions
                                }

                                // Determine field-specific selection type
                                val fieldSpecificType = if (isComplete && selectedNode.type == NodeType.FIELD) {
                                    when {
                                        selectedNode.path.endsWith(".timestamp") -> FieldSelectionType.TIMESTAMP
                                        selectedNode.path.endsWith(".name") -> FieldSelectionType.NAME
                                        else -> FieldSelectionType.NONE // data.* fields
                                    }
                                } else {
                                    FieldSelectionType.NONE
                                }

                                state = state.copy(
                                    selectionChain = newChain,
                                    selectedPath = selectedNode.path,
                                    currentOptions = nextOptions,
                                    currentLevel = selectedLevel,
                                    isComplete = isComplete,
                                    optionsByLevel = newOptionsByLevel,
                                    fieldSpecificType = fieldSpecificType,
                                    // Reset values when changing selection
                                    selectedValues = emptyList(),
                                    availableValues = emptyList(),
                                    // Reset field-specific selections
                                    timestampSelection = TimestampSelection(),
                                    nameSelection = NameSelection()
                                )

                                // Load contextual values only for data.* fields (NONE type)
                                if (isComplete && fieldSpecificType == FieldSelectionType.NONE) {
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

                // Section 2: Field-specific value selector (only if at field level and complete)
                if (state.isComplete && state.selectionChain.lastOrNull()?.selectedNode?.type == NodeType.FIELD) {
                    when (state.fieldSpecificType) {
                        FieldSelectionType.TIMESTAMP -> {
                            TimestampSelectorSection(
                                timestampSelection = state.timestampSelection,
                                onTimestampSelectionChange = { newTimestampSelection ->
                                    state = state.copy(timestampSelection = newTimestampSelection)
                                },
                                dayStartHour = dayStartHour!!,
                                weekStartDay = weekStartDay!!
                            )
                        }
                        FieldSelectionType.NAME -> {
                            NameSelectorSection(
                                nameSelection = state.nameSelection,
                                onNameSelectionChange = { newNameSelection ->
                                    state = state.copy(nameSelection = newNameSelection)
                                }
                            )
                        }
                        FieldSelectionType.NONE -> {
                            // Default data fields - original behavior
                            ValueSelectorSection(
                                availableValues = state.availableValues,
                                selectedValues = state.selectedValues,
                                contextualDataStatus = state.contextualDataStatus,
                                onSelectionChange = { selectedValues ->
                                    state = state.copy(selectedValues = selectedValues)
                                }
                            )
                        }
                    }
                }

                // Section 3: Query Preview (always show if we have a selection)
                if (state.selectionChain.isNotEmpty()) {
                    QueryPreviewSection(
                        selectionChain = state.selectionChain,
                        selectedValues = state.selectedValues,
                        fieldSpecificType = state.fieldSpecificType,
                        timestampSelection = state.timestampSelection,
                        nameSelection = state.nameSelection
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
                        enabled = state.selectionChain.isNotEmpty(),
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UI.Text(
            text = "Sélection des données",
            type = TextType.SUBTITLE
        )

        // Dynamic selectors for each available level
        state.optionsByLevel.keys.sorted().forEach { level ->
            val options = state.optionsByLevel[level] ?: emptyList()
            if (options.isNotEmpty()) {
                val label = getLevelLabel(level, options.firstOrNull()?.type)
                val selectedValue = state.selectionChain.getOrNull(level)?.selectedValue ?: ""

                UI.FormSelection(
                    label = label,
                    options = options.map { it.displayName },
                    selected = selectedValue,
                    onSelect = { selectedOption ->
                        val selectedNode = options.find { it.displayName == selectedOption }
                        if (selectedNode != null) {
                            onSelectionChange(selectedNode)
                        }
                    }
                )
            }
        }

        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    UI.Text(
                        text = "Chargement...",
                        type = TextType.LABEL
                    )
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
                // Use regular Column since parent is already scrollable
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableValues.take(10).forEach { value ->
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
                        UI.Text(
                            text = "... et ${availableValues.size - 10} autres valeurs",
                            type = TextType.LABEL
                        )
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
    selectedValues: List<String>,
    fieldSpecificType: FieldSelectionType = FieldSelectionType.NONE,
    timestampSelection: TimestampSelection = TimestampSelection(),
    nameSelection: NameSelection = NameSelection()
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = "Aperçu",
            type = TextType.SUBTITLE
        )

        // Description
        val description = buildQueryDescription(selectionChain, selectedValues, fieldSpecificType, timestampSelection, nameSelection)
        UI.Text(
            text = description,
            type = TextType.BODY
        )

        // SQL (placeholder for now)
        val sqlQuery = buildSqlQuery(selectionChain, selectedValues, fieldSpecificType, timestampSelection, nameSelection)
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
        NodeType.FIELD -> if (level <= 2) "Champ" else "Sous-champ ${level - 1}"
    }
}

private fun getLevelLabel(level: Int, nodeType: NodeType?): String {
    return when {
        level == 0 -> "Zone"
        level == 1 -> "Outil"
        level == 2 -> "Champ"
        nodeType == NodeType.FIELD -> "Sous-champ ${level - 1}"
        else -> "Niveau ${level + 1}"
    }
}

private fun getNextSelectionLabel(currentNodeType: NodeType): String {
    return when (currentNodeType) {
        NodeType.ZONE -> "Outil"
        NodeType.TOOL -> "Champ"
        NodeType.FIELD -> "Sous-champ"
    }
}

private fun buildQueryDescription(
    selectionChain: List<SelectionStep>,
    selectedValues: List<String>,
    fieldSpecificType: FieldSelectionType = FieldSelectionType.NONE,
    timestampSelection: TimestampSelection = TimestampSelection(),
    nameSelection: NameSelection = NameSelection()
): String {
    if (selectionChain.isEmpty()) return "Aucune sélection"

    // Use zone name and tool instance name instead of generic path
    val zoneName = selectionChain.find { it.selectedNode.type == NodeType.ZONE }?.selectedValue
    val toolInstanceName = selectionChain.find { it.selectedNode.type == NodeType.TOOL }?.selectedValue
    val fieldPath = selectionChain.filter { it.selectedNode.type == NodeType.FIELD }
        .joinToString(" → ") { it.selectedValue }

    val path = listOfNotNull(zoneName, toolInstanceName, fieldPath.takeIf { it.isNotEmpty() })
        .joinToString(" → ")

    // Extract zone_id and tool_instance_id for AI anchoring
    val zoneStep = selectionChain.find { it.selectedNode.type == NodeType.ZONE }
    val toolStep = selectionChain.find { it.selectedNode.type == NodeType.TOOL }

    val zoneId = zoneStep?.selectedNode?.path?.substringAfter("zones.")?.substringBefore(".")
    val toolInstanceId = toolStep?.selectedNode?.path?.substringAfter("tools.")?.substringBefore(".")

    // Adapt description based on selection level
    return when {
        // Zone only
        zoneName != null && toolInstanceName == null ->
            "Focus sur la zone '$zoneName' (zone_id: $zoneId)"

        // Zone + Tool
        zoneName != null && toolInstanceName != null && fieldPath.isEmpty() ->
            "Focus sur l'outil '$toolInstanceName' (tool_instance_id: $toolInstanceId, zone: $zoneName)"

        // Zone + Tool + Field(s)
        fieldPath.isNotEmpty() -> {
            val valuesPart = when (fieldSpecificType) {
                FieldSelectionType.TIMESTAMP -> {
                    when {
                        timestampSelection.isComplete -> {
                            val minDesc = formatTimestampForDescription(timestampSelection.minPeriodType, timestampSelection.minPeriod, timestampSelection.minCustomDateTime)
                            val maxDesc = formatTimestampForDescription(timestampSelection.maxPeriodType, timestampSelection.maxPeriod, timestampSelection.maxCustomDateTime)
                            "période du $minDesc au $maxDesc"
                        }
                        timestampSelection.minPeriodType != null || timestampSelection.maxPeriodType != null -> {
                            "sélection temporelle partielle"
                        }
                        else -> "toutes les données temporelles"
                    }
                }
                FieldSelectionType.NAME -> {
                    when {
                        nameSelection.selectedNames.isNotEmpty() -> "noms: ${nameSelection.selectedNames.joinToString(", ")}"
                        nameSelection.availableNames.isNotEmpty() -> "tous les noms disponibles"
                        else -> "tous les noms"
                    }
                }
                FieldSelectionType.NONE -> {
                    when {
                        selectedValues.isEmpty() -> "toutes les valeurs"
                        selectedValues.size == 1 -> "valeur: ${selectedValues.first()}"
                        else -> "${selectedValues.size} valeurs sélectionnées"
                    }
                }
            }
            "Focus sur '$path' pour $valuesPart (tool_instance_id: $toolInstanceId)"
        }

        else -> "Focus sur '$path'"
    }
}

private fun formatTimestampForDescription(periodType: PeriodType?, period: Period?, customDateTime: Long?): String {
    return when {
        period != null -> {
            // Use existing period system to format
            when (periodType) {
                PeriodType.DAY -> "jour du ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(period.timestamp))}"
                PeriodType.WEEK -> "semaine du ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(period.timestamp))}"
                PeriodType.MONTH -> "mois ${java.text.SimpleDateFormat("MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(period.timestamp))}"
                PeriodType.YEAR -> "année ${java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date(period.timestamp))}"
                else -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(period.timestamp))
            }
        }
        customDateTime != null -> {
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(customDateTime))
        }
        periodType != null -> {
            when (periodType) {
                PeriodType.DAY -> "jour sélectionné"
                PeriodType.WEEK -> "semaine sélectionnée"
                PeriodType.MONTH -> "mois sélectionné"
                PeriodType.YEAR -> "année sélectionnée"
                else -> "période sélectionnée"
            }
        }
        else -> "non défini"
    }
}

private fun buildSqlQuery(
    selectionChain: List<SelectionStep>,
    selectedValues: List<String>,
    fieldSpecificType: FieldSelectionType = FieldSelectionType.NONE,
    timestampSelection: TimestampSelection = TimestampSelection(),
    nameSelection: NameSelection = NameSelection()
): String {
    if (selectionChain.isEmpty()) return "-- Aucune sélection"

    val toolStep = selectionChain.find { it.selectedNode.type == NodeType.TOOL }
    val fieldStep = selectionChain.lastOrNull { it.selectedNode.type == NodeType.FIELD } // Last field in chain

    if (toolStep == null) return "-- Outil non sélectionné"

    // Use unified table structure
    val tableName = "tool_data"

    // Extract tool instance ID from path (e.g., "tools.instance_123" -> "instance_123")
    val toolInstanceId = toolStep.selectedNode.path.substringAfter("tools.").substringBefore(".")

    val (fieldName, isJsonField) = if (fieldStep != null) {
        val fieldPath = fieldStep.selectedNode.path
        when {
            fieldPath.endsWith(".name") -> Pair("name", false)
            fieldPath.endsWith(".timestamp") -> Pair("timestamp", false)
            fieldPath.contains(".data.") -> {
                // Extract JSON field path (e.g., "tools.weight_tracker.data.quantity" -> "quantity")
                val jsonFieldPath = fieldPath.substringAfter(".data.")
                Pair(jsonFieldPath, true)
            }
            else -> Pair("*", false)
        }
    } else {
        Pair("*", false)
    }

    val whereConditions = mutableListOf<String>()

    // Always filter by tool instance
    whereConditions.add("tool_instance_id = '$toolInstanceId'")

    // Add field-specific filters
    if (fieldStep != null) {
        when (fieldSpecificType) {
            FieldSelectionType.TIMESTAMP -> {
                // Add timestamp range conditions
                if (timestampSelection.minPeriod != null || timestampSelection.minCustomDateTime != null) {
                    val minTimestamp = timestampSelection.minCustomDateTime ?: timestampSelection.minPeriod?.timestamp ?: 0
                    whereConditions.add("timestamp >= $minTimestamp")
                }
                if (timestampSelection.maxPeriod != null || timestampSelection.maxCustomDateTime != null) {
                    val maxTimestamp = timestampSelection.maxCustomDateTime ?: timestampSelection.maxPeriod?.timestamp ?: Long.MAX_VALUE
                    whereConditions.add("timestamp <= $maxTimestamp")
                }
            }
            FieldSelectionType.NAME -> {
                // Add name filter
                if (nameSelection.selectedNames.isNotEmpty()) {
                    whereConditions.add("name IN (${nameSelection.selectedNames.joinToString(", ") { "'$it'" }})")
                }
            }
            FieldSelectionType.NONE -> {
                // Original data field logic
                if (selectedValues.isNotEmpty()) {
                    val valueCondition = if (isJsonField) {
                        "JSON_EXTRACT(data, '$.$fieldName') IN (${selectedValues.joinToString(", ") { "'$it'" }})"
                    } else {
                        "$fieldName IN (${selectedValues.joinToString(", ") { "'$it'" }})"
                    }
                    whereConditions.add(valueCondition)
                }
            }
        }
    }

    val whereClause = "WHERE ${whereConditions.joinToString(" AND ")}"

    // Select clause based on field type
    val selectClause = when {
        fieldName == "*" -> "SELECT *"
        isJsonField -> "SELECT JSON_EXTRACT(data, '$.$fieldName') as $fieldName"
        else -> "SELECT $fieldName"
    }

    return "$selectClause FROM $tableName $whereClause"
}

/**
 * Component for timestamp field selection (date range)
 */
@Composable
private fun TimestampSelectorSection(
    timestampSelection: TimestampSelection,
    onTimestampSelectionChange: (TimestampSelection) -> Unit,
    dayStartHour: Int,
    weekStartDay: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UI.Text(
            text = "Sélection temporelle",
            type = TextType.SUBTITLE
        )

        // Date minimum
        TimestampRangeSelector(
            label = "Date minimum",
            periodType = timestampSelection.minPeriodType,
            period = timestampSelection.minPeriod,
            customDateTime = timestampSelection.minCustomDateTime,
            onPeriodTypeChange = { newPeriodType ->
                onTimestampSelectionChange(
                    timestampSelection.copy(minPeriodType = newPeriodType, minPeriod = null, minCustomDateTime = null)
                )
            },
            onPeriodChange = { newPeriod ->
                onTimestampSelectionChange(
                    timestampSelection.copy(minPeriod = newPeriod, minCustomDateTime = null)
                )
            },
            onCustomDateTimeChange = { newDateTime ->
                onTimestampSelectionChange(
                    timestampSelection.copy(minCustomDateTime = newDateTime, minPeriod = null)
                )
            },
            dayStartHour = dayStartHour,
            weekStartDay = weekStartDay
        )

        // Date maximum
        TimestampRangeSelector(
            label = "Date maximum",
            periodType = timestampSelection.maxPeriodType,
            period = timestampSelection.maxPeriod,
            customDateTime = timestampSelection.maxCustomDateTime,
            onPeriodTypeChange = { newPeriodType ->
                onTimestampSelectionChange(
                    timestampSelection.copy(maxPeriodType = newPeriodType, maxPeriod = null, maxCustomDateTime = null)
                )
            },
            onPeriodChange = { newPeriod ->
                onTimestampSelectionChange(
                    timestampSelection.copy(maxPeriod = newPeriod, maxCustomDateTime = null)
                )
            },
            onCustomDateTimeChange = { newDateTime ->
                onTimestampSelectionChange(
                    timestampSelection.copy(maxCustomDateTime = newDateTime, maxPeriod = null)
                )
            },
            dayStartHour = dayStartHour,
            weekStartDay = weekStartDay
        )
    }
}

/**
 * Component for a single timestamp range selector (min or max)
 */
@Composable
private fun TimestampRangeSelector(
    label: String,
    periodType: PeriodType?,
    period: Period?,
    customDateTime: Long?,
    onPeriodTypeChange: (PeriodType?) -> Unit,
    onPeriodChange: (Period) -> Unit,
    onCustomDateTimeChange: (Long) -> Unit,
    dayStartHour: Int,
    weekStartDay: String
) {
    // State for date/time pickers
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = label,
            type = TextType.LABEL
        )

        // Period type selector
        UI.FormSelection(
            label = "Type de période",
            options = listOf("Heure", "Jour", "Semaine", "Mois", "Année", "Date personnalisée"),
            selected = when (periodType) {
                PeriodType.HOUR -> "Heure"
                PeriodType.DAY -> "Jour"
                PeriodType.WEEK -> "Semaine"
                PeriodType.MONTH -> "Mois"
                PeriodType.YEAR -> "Année"
                null -> "Date personnalisée" // Fix: always show "Date personnalisée" when periodType is null
            },
            onSelect = { selected ->
                val newPeriodType = when (selected) {
                    "Heure" -> PeriodType.HOUR
                    "Jour" -> PeriodType.DAY
                    "Semaine" -> PeriodType.WEEK
                    "Mois" -> PeriodType.MONTH
                    "Année" -> PeriodType.YEAR
                    "Date personnalisée" -> null // Will show custom picker
                    else -> null
                }
                onPeriodTypeChange(newPeriodType)
            }
        )

        // Period or custom selector based on type
        when {
            periodType != null && period != null -> {
                // Show period selector with existing period system
                PeriodSelector(
                    period = period,
                    onPeriodChange = onPeriodChange,
                    dayStartHour = dayStartHour,
                    weekStartDay = weekStartDay
                )
            }
            periodType != null -> {
                // Initialize period for this type
                LaunchedEffect(periodType) {
                    val initialPeriod = createCurrentPeriod(periodType, dayStartHour, weekStartDay)
                    onPeriodChange(initialPeriod)
                }
            }
            customDateTime == null -> {
                // Show custom date/time picker button
                UI.FormField(
                    label = "Date et heure personnalisée",
                    value = "Sélectionner...",
                    onChange = { },
                    readonly = true,
                    onClick = {
                        // Initialize with current date/time
                        onCustomDateTimeChange(System.currentTimeMillis())
                    }
                )
            }
            else -> {
                // Show selected custom date/time with edit option
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Date field
                        Box(modifier = Modifier.weight(1f)) {
                            UI.FormField(
                                label = "Date",
                                value = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(customDateTime!!)),
                                onChange = { },
                                readonly = true,
                                onClick = { showDatePicker = true }
                            )
                        }

                        // Time field
                        Box(modifier = Modifier.weight(1f)) {
                            UI.FormField(
                                label = "Heure",
                                value = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(customDateTime)),
                                onChange = { },
                                readonly = true,
                                onClick = { showTimePicker = true }
                            )
                        }
                    }
                }
            }
        }

        // Date picker dialog
        if (showDatePicker && customDateTime != null) {
            UI.DatePicker(
                selectedDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(customDateTime)),
                onDateSelected = { newDateString ->
                    // Combine new date with existing time
                    try {
                        val existingTime = java.util.Calendar.getInstance().apply {
                            timeInMillis = customDateTime
                        }
                        val newDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                            .parse(newDateString)

                        val combined = java.util.Calendar.getInstance().apply {
                            time = newDate!!
                            set(java.util.Calendar.HOUR_OF_DAY, existingTime.get(java.util.Calendar.HOUR_OF_DAY))
                            set(java.util.Calendar.MINUTE, existingTime.get(java.util.Calendar.MINUTE))
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }

                        onCustomDateTimeChange(combined.timeInMillis)
                    } catch (e: Exception) {
                        // Fallback: keep original datetime
                    }
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }

        // Time picker dialog
        if (showTimePicker && customDateTime != null) {
            UI.TimePicker(
                selectedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(customDateTime)),
                onTimeSelected = { newTimeString ->
                    // Combine existing date with new time
                    try {
                        val existingDate = java.util.Calendar.getInstance().apply {
                            timeInMillis = customDateTime
                        }
                        val timeParts = newTimeString.split(":")
                        val hour = timeParts[0].toInt()
                        val minute = timeParts[1].toInt()

                        val combined = java.util.Calendar.getInstance().apply {
                            timeInMillis = customDateTime
                            set(java.util.Calendar.HOUR_OF_DAY, hour)
                            set(java.util.Calendar.MINUTE, minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }

                        onCustomDateTimeChange(combined.timeInMillis)
                    } catch (e: Exception) {
                        // Fallback: keep original datetime
                    }
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false }
            )
        }
    }
}

/**
 * Component for name field selection
 */
@Composable
private fun NameSelectorSection(
    nameSelection: NameSelection,
    onNameSelectionChange: (NameSelection) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = "Sélection par nom",
            type = TextType.SUBTITLE
        )

        // TODO: Load available names from database
        LaunchedEffect(Unit) {
            // Placeholder - would load from DataNavigator
            val mockNames = listOf("Entrée 1", "Entrée 2", "Entrée 3", "Entrée du matin", "Entrée du soir")
            onNameSelectionChange(nameSelection.copy(availableNames = mockNames))
        }

        if (nameSelection.availableNames.isNotEmpty()) {
            UI.Text(
                text = "Noms d'entrées disponibles",
                type = TextType.LABEL
            )

            // Show available names with checkboxes
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                nameSelection.availableNames.take(10).forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = name in nameSelection.selectedNames,
                            onCheckedChange = { checked ->
                                val newSelection = if (checked) {
                                    nameSelection.selectedNames + name
                                } else {
                                    nameSelection.selectedNames - name
                                }
                                onNameSelectionChange(nameSelection.copy(selectedNames = newSelection))
                            }
                        )
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            UI.Text(
                                text = name,
                                type = TextType.BODY
                            )
                        }
                    }
                }

                if (nameSelection.availableNames.size > 10) {
                    UI.Text(
                        text = "... et ${nameSelection.availableNames.size - 10} autres",
                        type = TextType.CAPTION
                    )
                }
            }
        } else {
            UI.Text(
                text = "Chargement des noms...",
                type = TextType.BODY
            )
        }
    }
}