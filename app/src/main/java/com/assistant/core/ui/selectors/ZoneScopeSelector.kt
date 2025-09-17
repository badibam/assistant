package com.assistant.core.ui.selectors

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
import com.assistant.core.ui.selectors.data.ZoneScopeState
import com.assistant.core.ui.selectors.data.SelectionStep
import com.assistant.core.ui.selectors.data.FieldSelectionType
import com.assistant.core.ui.selectors.data.TimestampSelection
import com.assistant.core.ui.selectors.data.NameSelection
import com.assistant.core.ui.selectors.data.NavigationConfig
import com.assistant.core.ui.selectors.data.SelectionResult
import com.assistant.core.ui.selectors.data.SelectionLevel
import com.assistant.core.ui.selectors.data.FieldSpecificData
import com.assistant.core.navigation.DataNavigator
import com.assistant.core.navigation.getAvailableFields
import com.assistant.core.navigation.getFieldChildrenFromCommonStructure
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.ContextualDataResult
import com.assistant.core.navigation.data.DataResultStatus
import com.assistant.core.strings.Strings
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

// Helper functions for SelectionLevel and validation
private fun getCurrentSelectionLevel(selectionChain: List<SelectionStep>): SelectionLevel {
    return when (selectionChain.lastOrNull()?.selectedNode?.type) {
        NodeType.ZONE -> SelectionLevel.ZONE
        NodeType.TOOL -> SelectionLevel.INSTANCE
        NodeType.FIELD -> SelectionLevel.FIELD
        else -> SelectionLevel.ZONE
    }
}

private fun getFieldSpecificData(state: ZoneScopeState): FieldSpecificData? {
    return when (state.fieldSpecificType) {
        FieldSelectionType.TIMESTAMP -> {
            val minTimestamp = state.timestampSelection.minCustomDateTime ?: state.timestampSelection.minPeriod?.timestamp
            val maxTimestamp = state.timestampSelection.maxCustomDateTime ?: state.timestampSelection.maxPeriod?.timestamp
            FieldSpecificData.TimestampData(
                minTimestamp = minTimestamp,
                maxTimestamp = maxTimestamp
            )
        }
        FieldSelectionType.NAME -> {
            FieldSpecificData.NameData(
                selectedNames = state.nameSelection.selectedNames,
                availableNames = state.nameSelection.availableNames
            )
        }
        FieldSelectionType.NONE -> {
            if (state.selectedValues.isNotEmpty()) {
                FieldSpecificData.DataValues(state.selectedValues)
            } else null
        }
    }
}



/**
 * Creates current period with normalization according to configuration
 */
private fun createCurrentPeriod(type: PeriodType, dayStartHour: Int, weekStartDay: String): Period {
    val now = System.currentTimeMillis()
    val normalizedTimestamp = normalizeTimestampWithConfig(now, type, dayStartHour, weekStartDay)
    return Period(normalizedTimestamp, type)
}

/**
 * Zone scope selector - Hierarchical navigation through zones, tool instances, and data fields
 * Configurable navigation depth and field-specific selection capabilities
 */
@Composable
fun ZoneScopeSelector(
    config: NavigationConfig,
    onDismiss: () -> Unit,
    onConfirm: (SelectionResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = remember { DataNavigator(context) }
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(context = context) }

    var state by remember { mutableStateOf(ZoneScopeState()) }
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
            LogManager.coordination("Error loading initial data in ZoneScopeSelector: ${e.message}", "ERROR", e)
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
                    UI.Text(text = s.shared("scope_loading_config"), type = TextType.BODY)
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
                    text = if (config.title.isNotEmpty()) config.title else s.shared("scope_selector_title"),
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
                                            // Skip loading fields for "all tools" selection
                                            if (selectedNode.path == "tools.all") {
                                                emptyList()
                                            } else {
                                                // Use the new common structure approach
                                                try {
                                                    navigator.getAvailableFields(selectedNode.path.substringAfter("tools."), context)
                                                } catch (e: Exception) {
                                                    LogManager.coordination("Error loading available fields for ${selectedNode.path}: ${e.message}", "ERROR", e)
                                                    emptyList()
                                                }
                                            }
                                        }
                                        NodeType.FIELD -> {
                                            // Skip loading sub-fields for "all fields" selection
                                            if (selectedNode.path == "fields.all") {
                                                emptyList()
                                            } else {
                                                // Fields can have sub-fields (nested structure)
                                                try {
                                                    navigator.getFieldChildrenFromCommonStructure(selectedNode.path, context)
                                                } catch (e: Exception) {
                                                    LogManager.coordination("Error loading field children for ${selectedNode.path}: ${e.message}", "ERROR", e)
                                                    emptyList()
                                                }
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
                                        selectedNode.path == "fields.all" -> FieldSelectionType.NONE // All fields = no specific type
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

                                // Load contextual values for data.* fields when field is complete and value selection is allowed
                                if (config.allowValueSelection && isComplete && fieldSpecificType == FieldSelectionType.NONE &&
                                    selectedNode.path != "tools.all" && selectedNode.path != "fields.all") {
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
                    },
                    s = s
                )

                // Section 2: Field-specific value selector (only if allowValueSelection is true)
                if (config.allowValueSelection && state.isComplete && state.selectionChain.lastOrNull()?.selectedNode?.type == NodeType.FIELD) {
                    when (state.fieldSpecificType) {
                        FieldSelectionType.TIMESTAMP -> {
                            TimestampSelectorSection(
                                timestampSelection = state.timestampSelection,
                                onTimestampSelectionChange = { newTimestampSelection ->
                                    state = state.copy(timestampSelection = newTimestampSelection)
                                },
                                dayStartHour = dayStartHour!!,
                                weekStartDay = weekStartDay!!,
                                s = s
                            )
                        }
                        FieldSelectionType.NAME -> {
                            NameSelectorSection(
                                nameSelection = state.nameSelection,
                                onNameSelectionChange = { newNameSelection ->
                                    state = state.copy(nameSelection = newNameSelection)
                                },
                                s = s,
                                selectionChain = state.selectionChain,
                                coordinator = coordinator
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
                                },
                                s = s
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
                        nameSelection = state.nameSelection,
                        s = s
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
                        enabled = config.isValidSelection(getCurrentSelectionLevel(state.selectionChain)),
                        onClick = {
                            val result = SelectionResult(
                                selectedPath = state.selectedPath,
                                selectedValues = state.selectedValues,
                                selectionLevel = getCurrentSelectionLevel(state.selectionChain),
                                fieldSpecificData = getFieldSpecificData(state)
                            )
                            onConfirm(result)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataSelectorSection(
    state: ZoneScopeState,
    isLoading: Boolean,
    onSelectionChange: (SchemaNode) -> Unit,
    s: com.assistant.core.strings.StringsContext
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UI.Text(
            text = s.shared("scope_navigation_selection"),
            type = TextType.SUBTITLE
        )

        // Dynamic selectors for each available level
        state.optionsByLevel.keys.sorted().forEach { level ->
            val options = state.optionsByLevel[level] ?: emptyList()
            if (options.isNotEmpty()) {
                val label = getLevelLabel(level, options.firstOrNull()?.type)
                val selectedValue = state.selectionChain.getOrNull(level)?.selectedValue ?: ""

                // Add "All" option for tools and fields levels
                val allOptions = when (level) {
                    1 -> listOf(s.shared("scope_all_instances")) + options.map { it.displayName }
                    2 -> listOf(s.shared("scope_all_fields")) + options.map { it.displayName }
                    else -> options.map { it.displayName }
                }

                // Set default selection to "All" option for tools and fields
                val effectiveSelected = if (selectedValue.isEmpty() && (level == 1 || level == 2)) {
                    when (level) {
                        1 -> s.shared("scope_all_instances")
                        2 -> s.shared("scope_all_fields")
                        else -> selectedValue
                    }
                } else {
                    selectedValue
                }

                UI.FormSelection(
                    label = label,
                    options = allOptions,
                    selected = effectiveSelected,
                    onSelect = { selectedOption ->
                        // Handle "All" selections
                        if (selectedOption == s.shared("scope_all_instances") || selectedOption == s.shared("scope_all_fields")) {
                            // Create a virtual node for "All" selection
                            val allNode = SchemaNode(
                                path = when (level) {
                                    1 -> "tools.all"
                                    2 -> "fields.all"
                                    else -> "all"
                                },
                                displayName = selectedOption,
                                type = when (level) {
                                    1 -> NodeType.TOOL
                                    2 -> NodeType.FIELD
                                    else -> NodeType.ZONE
                                },
                                hasChildren = false
                            )
                            onSelectionChange(allNode)
                        } else {
                            // Handle regular node selection
                            val selectedNode = options.find { it.displayName == selectedOption }
                            if (selectedNode != null) {
                                onSelectionChange(selectedNode)
                            }
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
                        text = s.shared("scope_loading"),
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
    onSelectionChange: (List<String>) -> Unit,
    s: com.assistant.core.strings.StringsContext
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("scope_available_values"),
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
                            Box(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                                UI.Text(
                                    text = if (value.length > 50) "${value.take(47)}..." else value,
                                    type = TextType.BODY
                                )
                            }
                        }
                    }

                    if (availableValues.size > 10) {
                        UI.Text(
                            text = String.format(s.shared("scope_more_values"), availableValues.size - 10),
                            type = TextType.LABEL
                        )
                    }
                }
            }
            DataResultStatus.ERROR -> {
                UI.Text(
                    text = s.shared("scope_error_loading_values"),
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
    nameSelection: NameSelection = NameSelection(),
    s: com.assistant.core.strings.StringsContext
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("scope_preview"),
            type = TextType.SUBTITLE
        )

        // Description
        val description = buildQueryDescription(selectionChain, selectedValues, fieldSpecificType, timestampSelection, nameSelection)
        UI.Text(
            text = if (description.length > 200) "${description.take(197)}..." else description,
            type = TextType.BODY
        )

        // SQL Query Preview
        val sqlQuery = buildSqlQuery(selectionChain, selectedValues, fieldSpecificType, timestampSelection, nameSelection)
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                UI.Text(
                    text = s.shared("scope_sql_query"),
                    type = TextType.LABEL
                )
                UI.Text(
                    text = if (sqlQuery.length > 300) "${sqlQuery.take(297)}..." else sqlQuery,
                    type = TextType.CAPTION
                )
            }
        }
    }
}

// Helper functions

private fun getSelectionLabel(nodeType: NodeType, level: Int): String {
    return when (nodeType) {
        NodeType.ZONE -> "Zone" // Keep hardcoded for now - used internally
        NodeType.TOOL -> "Outil" // Keep hardcoded for now - used internally
        NodeType.FIELD -> if (level <= 2) "Champ" else "Sous-champ ${level - 1}" // Keep hardcoded for now - used internally
    }
}

private fun getLevelLabel(level: Int, nodeType: NodeType?): String {
    return when {
        level == 0 -> "Zone" // Keep hardcoded for now - used internally
        level == 1 -> "Outil" // Keep hardcoded for now - used internally
        level == 2 -> "Champ" // Keep hardcoded for now - used internally
        nodeType == NodeType.FIELD -> "Sous-champ ${level - 1}" // Keep hardcoded for now - used internally
        else -> "Niveau ${level + 1}" // Keep hardcoded for now - used internally
    }
}

private fun getNextSelectionLabel(currentNodeType: NodeType): String {
    return when (currentNodeType) {
        NodeType.ZONE -> "Outil" // Keep hardcoded for now - used internally
        NodeType.TOOL -> "Champ" // Keep hardcoded for now - used internally
        NodeType.FIELD -> "Sous-champ" // Keep hardcoded for now - used internally
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
    val toolInstanceId = toolStep?.selectedNode?.path?.let { path ->
        when {
            path == "tools.all" -> "all"
            else -> path.substringAfter("tools.").substringBefore(".")
        }
    }

    // Adapt description based on selection level
    return when {
        // Zone only
        zoneName != null && toolInstanceName == null ->
            "Focus sur la zone '$zoneName' (zone_id: $zoneId)"

        // Zone + Tool
        zoneName != null && toolInstanceName != null && fieldPath.isEmpty() -> {
            if (toolInstanceId == "all") {
                "Focus sur tous les outils de la zone '$zoneName'"
            } else {
                "Focus sur l'outil '$toolInstanceName' (tool_instance_id: $toolInstanceId, zone: $zoneName)"
            }
        }

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
                        nameSelection.selectedNames.isNotEmpty() -> {
                            if (nameSelection.selectedNames.size <= 3) {
                                val truncatedNames = nameSelection.selectedNames.map { name ->
                                    if (name.length > 20) "${name.take(17)}..." else name
                                }
                                "noms: ${truncatedNames.joinToString(", ")}"
                            } else {
                                "${nameSelection.selectedNames.size} noms sélectionnés"
                            }
                        }
                        nameSelection.availableNames.isNotEmpty() -> "tous les noms disponibles"
                        else -> "tous les noms"
                    }
                }
                FieldSelectionType.NONE -> {
                    when {
                        selectedValues.isEmpty() -> "toutes les valeurs"
                        selectedValues.size == 1 -> {
                            val value = selectedValues.first()
                            "valeur: ${if (value.length > 30) "${value.take(27)}..." else value}"
                        }
                        selectedValues.size <= 3 -> {
                            val truncatedValues = selectedValues.map { value ->
                                if (value.length > 20) "${value.take(17)}..." else value
                            }
                            "valeurs: ${truncatedValues.joinToString(", ")}"
                        }
                        else -> "${selectedValues.size} valeurs sélectionnées"
                    }
                }
            }
            if (toolInstanceId == "all") {
                "Focus sur '$path' pour $valuesPart (tous les outils)"
            } else {
                "Focus sur '$path' pour $valuesPart (tool_instance_id: $toolInstanceId)"
            }
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
    val toolInstanceId = toolStep.selectedNode.path.let { path ->
        when {
            path == "tools.all" -> "all"
            else -> path.substringAfter("tools.").substringBefore(".")
        }
    }

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

    // Filter by tool instance (skip for "all" tools)
    if (toolInstanceId != "all") {
        whereConditions.add("tool_instance_id = '$toolInstanceId'")
    }

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

    val whereClause = if (whereConditions.isNotEmpty()) {
        "WHERE ${whereConditions.joinToString(" AND ")}"
    } else {
        ""
    }

    // Select clause based on field type
    val selectClause = when {
        fieldName == "*" -> "SELECT *"
        isJsonField -> "SELECT JSON_EXTRACT(data, '$.$fieldName') as $fieldName"
        else -> "SELECT $fieldName"
    }

    return "$selectClause FROM $tableName $whereClause".trim()
}

/**
 * Component for timestamp field selection (date range)
 */
@Composable
private fun TimestampSelectorSection(
    timestampSelection: TimestampSelection,
    onTimestampSelectionChange: (TimestampSelection) -> Unit,
    dayStartHour: Int,
    weekStartDay: String,
    s: com.assistant.core.strings.StringsContext
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UI.Text(
            text = s.shared("scope_temporal_selection"),
            type = TextType.SUBTITLE
        )

        // Date minimum
        TimestampRangeSelector(
            label = s.shared("scope_date_start"),
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
            weekStartDay = weekStartDay,
            s = s
        )

        // Date maximum
        TimestampRangeSelector(
            label = s.shared("scope_date_end"),
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
            weekStartDay = weekStartDay,
            s = s
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
    weekStartDay: String,
    s: com.assistant.core.strings.StringsContext
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
            label = s.shared("scope_period_type"),
            options = listOf(s.shared("period_hour"), s.shared("period_day"), s.shared("period_week"), s.shared("period_month"), s.shared("period_year"), s.shared("scope_period_custom")),
            selected = when (periodType) {
                PeriodType.HOUR -> s.shared("period_hour")
                PeriodType.DAY -> s.shared("period_day")
                PeriodType.WEEK -> s.shared("period_week")
                PeriodType.MONTH -> s.shared("period_month")
                PeriodType.YEAR -> s.shared("period_year")
                null -> s.shared("scope_period_custom") // Fix: always show "Date personnalisée" when periodType is null
            },
            onSelect = { selected ->
                val newPeriodType = when (selected) {
                    s.shared("period_hour") -> PeriodType.HOUR
                    s.shared("period_day") -> PeriodType.DAY
                    s.shared("period_week") -> PeriodType.WEEK
                    s.shared("period_month") -> PeriodType.MONTH
                    s.shared("period_year") -> PeriodType.YEAR
                    s.shared("scope_period_custom") -> null // Will show custom picker
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
                    label = s.shared("scope_custom_datetime"),
                    value = s.shared("scope_select_datetime"),
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
                                label = s.shared("label_date"),
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
                                label = s.shared("label_time"),
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
    onNameSelectionChange: (NameSelection) -> Unit,
    s: com.assistant.core.strings.StringsContext,
    selectionChain: List<SelectionStep>,
    coordinator: Coordinator
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("scope_name_selection"),
            type = TextType.SUBTITLE
        )

        // Load available names from tool data via coordinator
        LaunchedEffect(selectionChain) {
            try {
                // Extract tool instance ID from selection chain
                val toolStep = selectionChain.find { it.selectedNode.type == NodeType.TOOL }
                val toolInstanceId = toolStep?.selectedNode?.path?.let { path ->
                    when {
                        path == "tools.all" -> null // Skip loading for "all tools"
                        else -> path.substringAfter("tools.").substringBefore(".")
                    }
                }

                if (toolInstanceId != null) {
                    // Load tool data to get available names
                    val result = coordinator.processUserAction(
                        "tool_data.get",
                        mapOf("toolInstanceId" to toolInstanceId)
                    )

                    if (result.isSuccess) {
                        val entries = result.data?.get("entries") as? List<Map<String, Any>> ?: emptyList()
                        val names = entries.mapNotNull { entry ->
                            (entry["name"] as? String)?.takeIf { it.isNotBlank() }
                        }.distinct().sorted()

                        onNameSelectionChange(nameSelection.copy(availableNames = names))
                    } else {
                        LogManager.coordination("Failed to load names for tool $toolInstanceId: ${result.message}", "ERROR")
                        onNameSelectionChange(nameSelection.copy(availableNames = emptyList()))
                    }
                } else {
                    // No specific tool selected or "all tools" - clear names
                    onNameSelectionChange(nameSelection.copy(availableNames = emptyList()))
                }
            } catch (e: Exception) {
                LogManager.coordination("Error loading available names: ${e.message}", "ERROR", e)
                onNameSelectionChange(nameSelection.copy(availableNames = emptyList()))
            }
        }

        if (nameSelection.availableNames.isNotEmpty()) {
            UI.Text(
                text = s.shared("scope_available_entry_names"),
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
                        Box(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                            UI.Text(
                                text = if (name.length > 50) "${name.take(47)}..." else name,
                                type = TextType.BODY
                            )
                        }
                    }
                }

                if (nameSelection.availableNames.size > 10) {
                    UI.Text(
                        text = String.format(s.shared("scope_more_names"), nameSelection.availableNames.size - 10),
                        type = TextType.CAPTION
                    )
                }
            }
        } else {
            UI.Text(
                text = s.shared("scope_loading_names"),
                type = TextType.BODY
            )
        }
    }
}