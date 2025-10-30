package com.assistant.core.ui.selectors

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.assistant.core.ui.selectors.data.*
import com.assistant.core.navigation.DataNavigator
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.strings.Strings
import com.assistant.core.ui.UI
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.TextType
import com.assistant.core.ui.components.PeriodRangeSelector
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Zone Scope Selector - Simplified for POINTER enrichments
 *
 * Navigation flow:
 * 1. Zone selection (if allowZoneSelection)
 * 2. Tool instance selection (if allowInstanceSelection)
 * 3. Context + Resources selection (unified UI section)
 * 4. Period selection (optional, shown based on context)
 *
 * Contexts:
 * - GENERIC: No automatic queries, optional reference period
 * - CONFIG: Configuration + schemas, no period
 * - DATA: Data + schemas, optional period on tool_data.timestamp
 * - EXECUTIONS: Executions + schemas, optional period on tool_executions.executionTime
 */
@Composable
fun ZoneScopeSelector(
    config: NavigationConfig,
    onDismiss: () -> Unit,
    onConfirm: (SelectionResult) -> Unit
) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    val dataNavigator = remember { DataNavigator(context) }

    // State management
    var state by remember { mutableStateOf(ZoneScopeState(selectedContext = config.defaultContext)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load initial zones on mount
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val rootNodes = dataNavigator.getRootNodes()
            val zoneNodes = rootNodes.filter { it.type == NodeType.ZONE }

            state = state.copy(
                currentOptions = zoneNodes,
                currentLevel = 0,
                optionsByLevel = mapOf(0 to zoneNodes)
            )

            LogManager.ui("ZoneScopeSelector: Loaded ${zoneNodes.size} zones", "DEBUG")
        } catch (e: Exception) {
            LogManager.ui("ZoneScopeSelector: Failed to load zones: ${e.message}", "ERROR", e)
            errorMessage = s.shared("error_loading_zones")
        } finally {
            isLoading = false
        }
    }

    // Error toast
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            UI.Toast(context, msg)
            errorMessage = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                UI.Text(
                    text = if (config.title.isNotEmpty()) config.title else s.shared("scope_selector_title"),
                    type = TextType.TITLE,
                    fillMaxWidth = true
                )

                // Loading indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        UI.LoadingIndicator()
                    }
                } else {
                    // Selection breadcrumb
                    if (state.selectionChain.isNotEmpty()) {
                        SelectionBreadcrumb(state.selectionChain)
                    }

                    // Current navigation level (Zone or Tool Instance)
                    if (state.currentLevel < 2) {
                        NavigationLevelSection(
                            state = state,
                            config = config,
                            onSelect = { selectedNode ->
                                scope.launch {
                                    handleLevelSelection(
                                        state = state,
                                        selectedNode = selectedNode,
                                        config = config,
                                        dataNavigator = dataNavigator,
                                        context = context,
                                        onStateUpdate = { newState -> state = newState },
                                        onError = { msg -> errorMessage = msg }
                                    )
                                }
                            }
                        )
                    }

                    // Context + Resources selection (shown after first selection)
                    if (state.selectionChain.isNotEmpty()) {
                        ContextResourcesSection(
                            state = state,
                            config = config,
                            onContextChange = { newContext ->
                                val currentLevel = getCurrentSelectionLevel(state.selectionChain)
                                state = state.copy(
                                    selectedContext = newContext,
                                    selectedResources = getDefaultResourcesForContext(newContext, currentLevel)
                                )
                            },
                            onResourceToggle = { resource ->
                                val newResources = if (state.selectedResources.contains(resource)) {
                                    state.selectedResources - resource
                                } else {
                                    state.selectedResources + resource
                                }
                                state = state.copy(selectedResources = newResources)
                            }
                        )
                    }

                    // Period selection (optional, shown after first selection and context-dependent)
                    if (state.selectionChain.isNotEmpty() && shouldShowPeriodSelector(state, config)) {
                        PeriodSelectionSection(
                            state = state,
                            config = config,
                            onTimestampSelectionChange = { newSelection ->
                                state = state.copy(timestampSelection = newSelection)
                            }
                        )
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.ActionButton(
                        action = ButtonAction.CANCEL,
                        onClick = onDismiss
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canConfirm = canConfirmSelection(state, config)
                    UI.ActionButton(
                        action = ButtonAction.CONFIRM,
                        onClick = {
                            val result = buildSelectionResult(state, config)
                            onConfirm(result)
                        },
                        enabled = canConfirm && !isLoading
                    )
                }
            }
        }
    }
}

// ================================================================================================
// UI Sections
// ================================================================================================

/**
 * Breadcrumb showing current selection path
 */
@Composable
private fun SelectionBreadcrumb(chain: List<SelectionStep>) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        UI.Text(
            text = s.shared("scope_current_selection"),
            type = TextType.SUBTITLE
        )

        chain.forEach { step ->
            UI.Text(
                text = "${step.label}: ${step.selectedValue}",
                type = TextType.BODY
            )
        }
    }
}

/**
 * Navigation level section (Zone or Tool Instance selection)
 */
@Composable
private fun NavigationLevelSection(
    state: ZoneScopeState,
    config: NavigationConfig,
    onSelect: (SchemaNode) -> Unit
) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)

    val levelLabel = when (state.currentLevel) {
        0 -> s.shared("scope_select_zone")
        1 -> s.shared("scope_select_tool")
        else -> s.shared("scope_select_item")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UI.Text(
            text = levelLabel,
            type = TextType.SUBTITLE
        )

        if (state.currentOptions.isEmpty()) {
            UI.Text(
                text = s.shared("scope_no_options"),
                type = TextType.BODY
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.currentOptions) { node ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                UI.Text(
                                    text = node.displayName,
                                    type = TextType.BODY
                                )
                            }

                            UI.ActionButton(
                                action = ButtonAction.SELECT,
                                onClick = { onSelect(node) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Context + Resources unified section
 */
@Composable
private fun ContextResourcesSection(
    state: ZoneScopeState,
    config: NavigationConfig,
    onContextChange: (PointerContext) -> Unit,
    onResourceToggle: (String) -> Unit
) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)

    // Filter allowed contexts based on tool type
    val allowedContexts = remember(state.selectionChain) {
        filterAllowedContexts(state, config, context)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UI.Text(
            text = s.shared("scope_select_context"),
            type = TextType.SUBTITLE
        )

        // Context selector (FormSelection for radio-like behavior)
        val contextOptions = allowedContexts.map { getContextLabel(it, s) }
        val selectedContextLabel = getContextLabel(state.selectedContext, s)

        UI.FormSelection(
            label = s.shared("label_context"),
            options = contextOptions,
            selected = selectedContextLabel,
            onSelect = { selectedLabel ->
                val newContext = allowedContexts.find { getContextLabel(it, s) == selectedLabel }
                newContext?.let { onContextChange(it) }
            },
            required = true
        )

        // Resources toggles (dynamic based on selected context)
        val availableResources = getAvailableResourcesForContext(state.selectedContext)

        if (availableResources.isNotEmpty()) {
            UI.Text(
                text = s.shared("scope_select_resources"),
                type = TextType.BODY
            )

            availableResources.forEach { resource ->
                UI.ToggleField(
                    label = getResourceLabel(resource, s),
                    checked = state.selectedResources.contains(resource),
                    onCheckedChange = { onResourceToggle(resource) },
                    required = false
                )
            }
        }
    }
}

/**
 * Period selection section (optional)
 */
@Composable
private fun PeriodSelectionSection(
    state: ZoneScopeState,
    config: NavigationConfig,
    onTimestampSelectionChange: (TimestampSelection) -> Unit
) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UI.Text(
            text = getPeriodSectionLabel(state.selectedContext, s),
            type = TextType.SUBTITLE
        )

        PeriodRangeSelector(
            startPeriodType = state.timestampSelection.minPeriodType,
            endPeriodType = state.timestampSelection.maxPeriodType,
            startPeriod = state.timestampSelection.minPeriod,
            endPeriod = state.timestampSelection.maxPeriod,
            startCustomDate = state.timestampSelection.minCustomDateTime,
            endCustomDate = state.timestampSelection.maxCustomDateTime,
            startRelativePeriod = state.timestampSelection.minRelativePeriod,
            endRelativePeriod = state.timestampSelection.maxRelativePeriod,
            onStartTypeChange = { type ->
                // When type changes, PeriodRangeSelector will automatically call onStartPeriodChange
                // We just need to update the type
                onTimestampSelectionChange(state.timestampSelection.copy(minPeriodType = type))
            },
            onEndTypeChange = { type ->
                // When type changes, PeriodRangeSelector will automatically call onEndPeriodChange
                // We just need to update the type
                onTimestampSelectionChange(state.timestampSelection.copy(maxPeriodType = type))
            },
            onStartPeriodChange = { period ->
                // Update both period and ensure type is consistent
                val newType = period?.type ?: state.timestampSelection.minPeriodType
                onTimestampSelectionChange(state.timestampSelection.copy(
                    minPeriodType = newType,
                    minPeriod = period,
                    minCustomDateTime = null, // Clear custom date when using period
                    minRelativePeriod = null // Clear relative period when using absolute
                ))
            },
            onEndPeriodChange = { period ->
                // Update both period and ensure type is consistent
                val newType = period?.type ?: state.timestampSelection.maxPeriodType
                onTimestampSelectionChange(state.timestampSelection.copy(
                    maxPeriodType = newType,
                    maxPeriod = period,
                    maxCustomDateTime = null, // Clear custom date when using period
                    maxRelativePeriod = null // Clear relative period when using absolute
                ))
            },
            onStartCustomDateChange = { dateTime ->
                // When using custom date, clear period fields
                onTimestampSelectionChange(state.timestampSelection.copy(
                    minPeriodType = null, // Custom mode
                    minPeriod = null,
                    minCustomDateTime = dateTime,
                    minRelativePeriod = null
                ))
            },
            onEndCustomDateChange = { dateTime ->
                // When using custom date, clear period fields
                onTimestampSelectionChange(state.timestampSelection.copy(
                    maxPeriodType = null, // Custom mode
                    maxPeriod = null,
                    maxCustomDateTime = dateTime,
                    maxRelativePeriod = null
                ))
            },
            onStartRelativePeriodChange = { relative ->
                // Update relative period and ensure type is consistent
                val newType = relative?.type ?: state.timestampSelection.minPeriodType
                onTimestampSelectionChange(state.timestampSelection.copy(
                    minPeriodType = newType,
                    minPeriod = null, // Clear absolute period when using relative
                    minCustomDateTime = null, // Clear custom date when using relative
                    minRelativePeriod = relative
                ))
            },
            onEndRelativePeriodChange = { relative ->
                // Update relative period and ensure type is consistent
                val newType = relative?.type ?: state.timestampSelection.maxPeriodType
                onTimestampSelectionChange(state.timestampSelection.copy(
                    maxPeriodType = newType,
                    maxPeriod = null, // Clear absolute period when using relative
                    maxCustomDateTime = null, // Clear custom date when using relative
                    maxRelativePeriod = relative
                ))
            },
            useOnlyRelativeLabels = config.useRelativeLabels, // Use relative labels for AUTOMATION
            returnRelative = config.useRelativeLabels // Return relative periods for AUTOMATION
        )
    }
}

// ================================================================================================
// Business Logic
// ================================================================================================

/**
 * Handle selection at current navigation level (Zone or Tool Instance)
 */
private suspend fun handleLevelSelection(
    state: ZoneScopeState,
    selectedNode: SchemaNode,
    config: NavigationConfig,
    dataNavigator: DataNavigator,
    context: android.content.Context,
    onStateUpdate: (ZoneScopeState) -> Unit,
    onError: (String) -> Unit
) {
    val s = Strings.`for`(context = context)

    LogManager.ui("ZoneScopeSelector: Level ${state.currentLevel} selected: ${selectedNode.displayName}", "DEBUG")

    // Update selection chain
    val newChain = state.selectionChain + SelectionStep(
        label = when (selectedNode.type) {
            NodeType.ZONE -> s.shared("label_zone")
            NodeType.TOOL -> s.shared("label_tool")
            else -> s.shared("label_item")
        },
        selectedValue = selectedNode.displayName,
        selectedNode = selectedNode
    )

    // Build path - node.path already contains the full path
    val newPath = selectedNode.path
    val newLevel = state.currentLevel + 1

    // Check if we should load next level
    val currentLevel = getCurrentSelectionLevel(newChain)
    val shouldContinue = config.shouldNavigateToNextLevel(currentLevel, hasChildren = true)

    // Initialize default resources for first selection (zone level)
    val defaultResources = if (state.selectionChain.isEmpty()) {
        getDefaultResourcesForContext(state.selectedContext, currentLevel)
    } else {
        state.selectedResources
    }

    if (shouldContinue && newLevel < 2) {
        // Load next level options
        try {
            val children = dataNavigator.getChildren(newPath)
            val nextLevelOptions = children.filter {
                when (newLevel) {
                    1 -> it.type == NodeType.TOOL  // After zone, show tools
                    else -> false
                }
            }

            onStateUpdate(state.copy(
                selectionChain = newChain,
                selectedPath = newPath,
                currentOptions = nextLevelOptions,
                currentLevel = newLevel,
                selectedResources = defaultResources,
                optionsByLevel = state.optionsByLevel + (newLevel to nextLevelOptions)
            ))

            LogManager.ui("ZoneScopeSelector: Loaded ${nextLevelOptions.size} options for level $newLevel", "DEBUG")
        } catch (e: Exception) {
            LogManager.ui("ZoneScopeSelector: Failed to load next level: ${e.message}", "ERROR", e)
            onError(s.shared("error_loading_options"))
        }
    } else {
        // Just update selection, don't load next level
        onStateUpdate(state.copy(
            selectionChain = newChain,
            selectedPath = newPath,
            currentLevel = newLevel,
            selectedResources = defaultResources
        ))
    }
}

/**
 * Get current selection level from chain
 */
private fun getCurrentSelectionLevel(chain: List<SelectionStep>): SelectionLevel {
    return when (chain.lastOrNull()?.selectedNode?.type) {
        NodeType.ZONE -> SelectionLevel.ZONE
        NodeType.TOOL -> SelectionLevel.INSTANCE
        else -> SelectionLevel.ZONE
    }
}

/**
 * Filter allowed contexts based on selection level and tool type support
 */
private fun filterAllowedContexts(
    state: ZoneScopeState,
    config: NavigationConfig,
    context: android.content.Context
): List<PointerContext> {
    val currentLevel = if (state.selectionChain.isNotEmpty()) {
        getCurrentSelectionLevel(state.selectionChain)
    } else {
        null
    }

    return when (currentLevel) {
        SelectionLevel.ZONE -> {
            // Zone level: only GENERIC and CONFIG
            // DATA and EXECUTIONS are instance-specific
            config.allowedContexts.filter {
                it == PointerContext.GENERIC || it == PointerContext.CONFIG
            }
        }
        SelectionLevel.INSTANCE -> {
            // Instance level: filter based on tool type support for executions
            val toolNode = state.selectionChain.lastOrNull { it.selectedNode.type == NodeType.TOOL }

            if (toolNode != null) {
                val tooltype = toolNode.selectedNode.toolType

                if (tooltype != null) {
                    val toolType = ToolTypeManager.getToolType(tooltype)
                    val supportsExecutions = toolType?.supportsExecutions() == true

                    if (supportsExecutions) {
                        config.allowedContexts
                    } else {
                        config.allowedContexts.filter { it != PointerContext.EXECUTIONS }
                    }
                } else {
                    // No tooltype available, show all except EXECUTIONS to be safe
                    config.allowedContexts.filter { it != PointerContext.EXECUTIONS }
                }
            } else {
                config.allowedContexts
            }
        }
        else -> {
            // No selection yet, show all contexts
            config.allowedContexts
        }
    }
}

/**
 * Get available resources for a given context
 */
private fun getAvailableResourcesForContext(context: PointerContext): List<String> {
    return when (context) {
        PointerContext.GENERIC -> emptyList() // No resources for GENERIC
        PointerContext.CONFIG -> listOf("config", "config_schema")
        PointerContext.DATA -> listOf("data", "data_schema")
        PointerContext.EXECUTIONS -> listOf("executions", "executions_schema")
    }
}

/**
 * Get default selected resources for a given context and selection level
 */
private fun getDefaultResourcesForContext(context: PointerContext, level: SelectionLevel): List<String> {
    return when (context) {
        PointerContext.GENERIC -> emptyList() // No default for GENERIC
        PointerContext.CONFIG -> {
            when (level) {
                SelectionLevel.ZONE -> listOf("config") // Zone config checked by default
                SelectionLevel.INSTANCE -> listOf("config") // Tool config checked by default
                else -> emptyList()
            }
        }
        PointerContext.DATA -> listOf("data") // Data checked by default
        PointerContext.EXECUTIONS -> listOf("executions") // Executions checked by default
    }
}

/**
 * Should show period selector based on context
 */
private fun shouldShowPeriodSelector(state: ZoneScopeState, config: NavigationConfig): Boolean {
    // Show for DATA, EXECUTIONS (always), and GENERIC (if user wants to specify reference period)
    return when (state.selectedContext) {
        PointerContext.DATA, PointerContext.EXECUTIONS -> true
        PointerContext.GENERIC -> true // Optional reference period
        PointerContext.CONFIG -> false // No period for configs
    }
}

/**
 * Check if current selection can be confirmed
 */
private fun canConfirmSelection(state: ZoneScopeState, config: NavigationConfig): Boolean {
    // Must have at least one selection
    if (state.selectionChain.isEmpty()) return false

    // Get current selection level
    val currentLevel = getCurrentSelectionLevel(state.selectionChain)

    // Check if we can confirm at this level
    val canConfirmAtThisLevel = config.isValidSelection(currentLevel)

    if (!canConfirmAtThisLevel) return false

    // GENERIC context has no resource requirements
    if (state.selectedContext == PointerContext.GENERIC) return true

    // Other contexts require at least one resource selected
    return state.selectedResources.isNotEmpty()
}

/**
 * Build final SelectionResult from current state
 */
private fun buildSelectionResult(state: ZoneScopeState, config: NavigationConfig): SelectionResult {
    val displayChain = state.selectionChain.map { step ->
        "${step.label}: ${step.selectedValue}"
    }

    val currentLevel = getCurrentSelectionLevel(state.selectionChain)

    return SelectionResult(
        selectedPath = state.selectedPath,
        selectionLevel = currentLevel,
        selectedContext = state.selectedContext,
        selectedResources = state.selectedResources,
        timestampSelection = state.timestampSelection,  // Pass period selection from ZoneScopeSelector
        selectedValues = emptyList(), // Not used for POINTER (no field-level selection)
        fieldSpecificData = null, // Not used for POINTER
        displayChain = displayChain
    )
}

// ================================================================================================
// String Helpers
// ================================================================================================

private fun getContextLabel(context: PointerContext, s: com.assistant.core.strings.StringsContext): String {
    return when (context) {
        PointerContext.GENERIC -> s.shared("label_context_generic")
        PointerContext.CONFIG -> s.shared("label_context_config")
        PointerContext.DATA -> s.shared("label_context_data")
        PointerContext.EXECUTIONS -> s.shared("label_context_executions")
    }
}

private fun getResourceLabel(resource: String, s: com.assistant.core.strings.StringsContext): String {
    return when (resource) {
        "config" -> s.shared("label_resource_config")
        "config_schema" -> s.shared("label_resource_config_schema")
        "data" -> s.shared("label_resource_data")
        "data_schema" -> s.shared("label_resource_data_schema")
        "executions" -> s.shared("label_resource_executions")
        "executions_schema" -> s.shared("label_resource_executions_schema")
        else -> resource
    }
}

private fun getPeriodSectionLabel(context: PointerContext, s: com.assistant.core.strings.StringsContext): String {
    return when (context) {
        PointerContext.GENERIC -> s.shared("label_period_reference")
        PointerContext.DATA -> s.shared("label_period_data")
        PointerContext.EXECUTIONS -> s.shared("label_period_execution")
        PointerContext.CONFIG -> "" // Not shown
    }
}
