package com.assistant.core.ui.screens

import com.assistant.core.utils.JsonUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.grid.ToolPositions
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.mapData
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Zone detail screen - shows zone information and its tools
 * Uses hybrid system: Compose layouts + UI.* visual components
 */
@Composable
fun ZoneScreen(
    zone: Zone,
    onBack: () -> Unit,
    opening: Pair<String, String?>? = null,
    onOpened: () -> Unit = {},
    onNavigateToSeedEditor: ((seedSessionId: String) -> Unit)? = null,
    onNavigateToAutomationHistory: ((automationId: String) -> Unit)? = null,
    onConfigureZone: ((zoneId: String) -> Unit)? = null,
    onAutomationStartChat: ((seedSessionId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load tool instances via command pattern
    var toolInstances by remember { mutableStateOf<List<ToolInstance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load automations for this zone
    var automations by remember { mutableStateOf<List<com.assistant.core.ai.data.Automation>>(emptyList()) }
    var isLoadingAutomations by remember { mutableStateOf(true) }

    // The zone shows "loading" until its tools and automations are first read. A later reload
    // (an automation switched on, a tool changed or duplicated) keeps the content on screen and
    // updates it when the new data arrives: replaced by "loading", the whole screen would blink.
    var loadedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading, isLoadingAutomations) {
        if (!isLoading && !isLoadingAutomations) loadedOnce = true
    }
    
    // State for showing/hiding available tools list - persiste orientation changes
    var showAvailableToolsForGroup by rememberSaveable { mutableStateOf<String?>(null) } // null = hidden, "" = ungrouped, "group_name" = specific group

    // State for tool configuration screen - persiste orientation changes
    var showingConfigFor by rememberSaveable { mutableStateOf<String?>(null) }
    var editingToolId by rememberSaveable { mutableStateOf<String?>(null) }

    // State for tool usage screen - persiste orientation changes
    var selectedToolInstanceId by rememberSaveable { mutableStateOf<String?>(null) }
    // The entry the tool opens on: the oldest waiting one when its tile was touched with one
    var openEntry by rememberSaveable(stateSaver = com.assistant.core.tools.EntryToOpen.Saver) { mutableStateOf<com.assistant.core.tools.EntryToOpen?>(null) }
    val waiting = com.assistant.core.ui.LocalWaiting.current
    // A tool asked for from outside (a notification), opened as its tile would be
    LaunchedEffect(opening) {
        val (toolId, entryId) = opening ?: return@LaunchedEffect
        openEntry = entryId?.let { com.assistant.core.tools.EntryToOpen.Existing(it) }
        selectedToolInstanceId = toolId
        onOpened()
    }

    // State for automation creation dialog - with pre-selected group
    var showCreateAutomationDialog by rememberSaveable { mutableStateOf(false) }
    var preSelectedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    // State for duplication dialogs
    var showDuplicateToolDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicateAutomationDialog by rememberSaveable { mutableStateOf(false) }

    // Derived states from IDs (recomputed after orientation change)
    val selectedToolInstance = toolInstances.find { it.id == selectedToolInstanceId }
    
    // Load tool instances on first composition and when zone changes
    LaunchedEffect(zone.id) {
        coordinator.executeWithLoading(
            operation = "tools.list",
            params = mapOf(
                "zone_id" to zone.id,
                "include_config" to true,
                "include_position" to true
            ),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            toolInstances = result.mapData("tool_instances") { toolInstanceOf(it) }
        }
    }

    // The zone's variables, reread whenever variables change anywhere (one may read another)
    var variables by remember { mutableStateOf<List<com.assistant.core.ui.variables.VariableRow>>(emptyList()) }
    var variablesVersion by remember { mutableStateOf(0) }
    LaunchedEffect(zone.id, variablesVersion) {
        val result = coordinator.processUserAction("variables.list", mapOf("zone_id" to zone.id))
        if (result.status == CommandStatus.SUCCESS) {
            variables = (result.data?.get("variables") as? List<*> ?: emptyList<Any>())
                .filterIsInstance<Map<*, *>>().map { com.assistant.core.ui.variables.VariableRow.of(it) }
        } else errorMessage = result.error
    }
    LaunchedEffect(zone.id) {
        DataChangeNotifier.changes.collect { event -> if (event is DataChangeEvent.VariablesChanged) variablesVersion++ }
    }
    // The variable open: its id, "" for a new one, null for none
    var openVariable by rememberSaveable { mutableStateOf<String?>(null) }

    // Load automations for this zone
    LaunchedEffect(zone.id) {
        coordinator.executeWithLoading(
            operation = "automations.list",
            params = mapOf("zone_id" to zone.id),
            onLoading = { isLoadingAutomations = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            @Suppress("UNCHECKED_CAST")
            val automationsList = result.data?.get("automations") as? List<Map<String, Any>> ?: emptyList()
            automations = automationsList.map { map ->
                com.assistant.core.ai.data.Automation.fromResult(map)
            }
        }
    }

    // Observe data changes and reload tools/automations automatically for this zone
    LaunchedEffect(zone.id) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolsChanged -> {
                    // Only reload if the change affects this zone
                    if (event.zoneId == zone.id) {
                        coordinator.executeWithLoading(
                            operation = "tools.list",
                            params = mapOf(
                                "zone_id" to zone.id,
                                "include_config" to true,
                                "include_position" to true
                            ),
                            onLoading = { isLoading = it },
                            onError = { error -> errorMessage = error }
                        )?.let { result ->
                            toolInstances = result.mapData("tool_instances") { toolInstanceOf(it) }
                        }
                    }
                }
                is DataChangeEvent.ZonesChanged -> {
                    // Reload automations when zones change (includes automation enable/disable/update/delete)
                    coordinator.executeWithLoading(
                        operation = "automations.list",
                        params = mapOf("zone_id" to zone.id),
                        onLoading = { isLoadingAutomations = it },
                        onError = { error -> errorMessage = error }
                    )?.let { result ->
                        @Suppress("UNCHECKED_CAST")
                        val automationsList = result.data?.get("automations") as? List<Map<String, Any>> ?: emptyList()
                        automations = automationsList.map { map ->
                            com.assistant.core.ai.data.Automation.fromResult(map)
                        }
                    }
                }
                else -> {} // Ignore other events
            }
        }
    }

    // Function to reload tool instances after operations
    val reloadToolInstances = {
        coroutineScope.launch {
            coordinator.executeWithLoading(
                operation = "tools.list",
                params = mapOf(
                    "zone_id" to zone.id,
                    "include_config" to true,
                    "include_position" to true
                ),
                onLoading = { isLoading = it },
                onError = { error -> errorMessage = error }
            )?.let { result ->
                toolInstances = result.mapData("tool_instances") { toolInstanceOf(it) }
            }
        }
    }
    
    val onCancelConfig = {
        showingConfigFor = null
        editingToolId = null
        preSelectedGroup = null
    }
    
    // Parse zone tool_groups from config
    val zoneToolGroups = remember(zone.tool_groups) {
        LogManager.ui("Parsing tool_groups for zone ${zone.id}: tool_groups = '${zone.tool_groups}'", "DEBUG")
        try {
            zone.tool_groups?.let {
                org.json.JSONArray(it).let { jsonArray ->
                    val groups = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                    LogManager.ui("Parsed ${groups.size} groups: $groups", "DEBUG")
                    groups
                }
            } ?: emptyList<String>().also {
                LogManager.ui("tool_groups is null, returning empty list", "DEBUG")
            }
        } catch (e: Exception) {
            LogManager.ui("Error parsing tool_groups: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // The edit mode of the sections' grids, a section named by its group, "" for the ungrouped one
    val gridEditor = com.assistant.core.ui.components.rememberGridEditor(
        placeOperation = "tools.place",
        placeParams = mapOf("zone_id" to zone.id),
        sectionTiles = { key -> ToolPositions.tiles(toolInstances, zoneToolGroups, key.ifEmpty { null }) },
        onError = { errorMessage = it }
    )

    // Show configuration screen if requested
    showingConfigFor?.let { toolTypeId ->
        ToolTypeManager.getToolType(toolTypeId)?.let { toolType ->
            com.assistant.core.tools.ui.ToolConfigScreen(
                toolType = toolType,
                tooltype = toolTypeId,
                zoneId = zone.id,
                // The saved id, not the tool resolved from the list: the list reloads after a
                // rotation, and the config screen, which loads the tool itself, would otherwise
                // be handed null meanwhile and switch to creation
                existingToolId = editingToolId,
                initialGroup = preSelectedGroup,
                onDone = {
                    onCancelConfig()
                    reloadToolInstances()
                },
                onCancel = onCancelConfig
            )
        }
        return // Exit ZoneScreen composition when showing config
    }
    
    // Show a variable's screen if one is open
    openVariable?.let { id ->
        com.assistant.core.ui.variables.VariableScreen(
            zoneId = zone.id,
            variableId = id.ifEmpty { null },
            group = preSelectedGroup,
            onDone = {
                openVariable = null
                preSelectedGroup = null
                variablesVersion++
            }
        )
        return
    }

    // Show tool usage screen if selected
    selectedToolInstance?.let { toolInstance ->
        com.assistant.core.ui.components.ShownScreen { ToolTypeManager.getToolType(toolInstance.tooltype)?.getUsageScreen(
            toolInstanceId = toolInstance.id,
            configJson = toolInstance.config_json,
            zoneName = zone.name,
            onNavigateBack = {
                selectedToolInstanceId = null
            },
            onLongClick = {
                editingToolId = toolInstance.id
                showingConfigFor = toolInstance.tooltype
            },
            openEntry = openEntry
        ) }
        return // Exit ZoneScreen composition when showing usage screen
    }

    com.assistant.core.ui.components.CloseEditOnLeave(gridEditor)
    
    // Edit mode: the grids of one section at a time, the bar under the screen while a tool moves
    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back button, zone title and configure zone button
        UI.PageHeader(
            title = zone.name,
            subtitle = zone.description?.takeIf { it.isNotBlank() },
            icon = zone.icon_name,
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onBack,
            onRightClick = { onConfigureZone?.invoke(zone.id) }
        )

        // Display sections by group
        if (!loadedOnce) {
            UI.Text(
                text = s.shared("message_loading"),
                type = TextType.BODY,
                fillMaxWidth = true,
                textAlign = TextAlign.Center
            )
        } else {
            // For each defined group, display its section (always shown, even if empty)
            zoneToolGroups.forEach { groupName ->
                GroupSection(
                    groupName = groupName,
                    zoneToolGroups = zoneToolGroups,
                    variables = variables.filter { it.group == groupName },
                    onOpenVariable = { openVariable = it },
                    onCreateVariable = {
                        preSelectedGroup = groupName
                        openVariable = ""
                        showAvailableToolsForGroup = null
                    },
                    toolInstances = toolInstances,
                    automations = automations,
                    showAvailableToolsForGroup = showAvailableToolsForGroup,
                    onToggleToolsList = { showAvailableToolsForGroup = if (showAvailableToolsForGroup == groupName) null else groupName },
                    onSelectToolType = { toolTypeId ->
                        showingConfigFor = toolTypeId
                        preSelectedGroup = groupName
                        showAvailableToolsForGroup = null
                    },
                    onCreateAutomation = {
                        preSelectedGroup = groupName
                        showCreateAutomationDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onDuplicateTool = {
                        preSelectedGroup = groupName
                        showDuplicateToolDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onDuplicateAutomation = {
                        preSelectedGroup = groupName
                        showDuplicateAutomationDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onToolClick = { toolId -> openEntry = waiting.oldest[toolId]?.let { com.assistant.core.tools.EntryToOpen.Existing(it) }; selectedToolInstanceId = toolId },
                    onOpenEntry = { tool, entry -> openEntry = entry; selectedToolInstanceId = tool.id },
                    editor = gridEditor,
                    onToolLongClick = { tool ->
                        editingToolId = tool.id
                        showingConfigFor = tool.tooltype
                    },
                    onAutomationEdit = { automation -> onNavigateToSeedEditor?.invoke(automation.seedSessionId) },
                    onAutomationTest = { automation ->
                        coroutineScope.launch {
                            try {
                                val result = coordinator.processUserAction(
                                    "automations.execute_manual",
                                    mapOf("automation_id" to automation.id)
                                )
                                if (result.status == CommandStatus.SUCCESS) {
                                    errorMessage = s.shared("automation_execution_triggered")
                                } else {
                                    errorMessage = result.error
                                }
                            } catch (e: Exception) {
                                errorMessage = s.shared("message_error").format(e.message ?: "")
                            }
                        }
                    },
                    onAutomationView = { automation -> onNavigateToAutomationHistory?.invoke(automation.id) },
                    onAutomationToggle = { automation, enabled ->
                        coroutineScope.launch {
                            try {
                                val operation = if (enabled) "automations.enable" else "automations.disable"
                                val result = coordinator.processUserAction(
                                    operation,
                                    mapOf("automation_id" to automation.id)
                                )
                                if (result.status != CommandStatus.SUCCESS) {
                                    errorMessage = result.error
                                }
                            } catch (e: Exception) {
                                errorMessage = s.shared("message_error").format(e.message ?: "")
                            }
                        }
                    },
                    onAutomationStartChat = onAutomationStartChat,
                    context = context
                )
            }

            // Ungrouped section: items with no group, or a group the zone does not have
            val ungroupedTools = toolInstances.filter { ToolPositions.section(it, zoneToolGroups) == null }
            val ungroupedAutomations = automations.filter { ToolPositions.section(it.group, zoneToolGroups) == null }

            // Always show ungrouped section (even if empty) to allow adding tools/automations
            UngroupedSection(
                    toolInstances = ungroupedTools,
                    variables = variables.filter { it.group == null || it.group !in zoneToolGroups },
                    onOpenVariable = { openVariable = it },
                    onCreateVariable = {
                        preSelectedGroup = null
                        openVariable = ""
                        showAvailableToolsForGroup = null
                    },
                    automations = ungroupedAutomations,
                    hasConfiguredGroups = zoneToolGroups.isNotEmpty(),
                    showAvailableToolsForGroup = showAvailableToolsForGroup,
                    onToggleToolsList = { showAvailableToolsForGroup = if (showAvailableToolsForGroup == "") null else "" },
                    onSelectToolType = { toolTypeId ->
                        showingConfigFor = toolTypeId
                        preSelectedGroup = null // No group for ungrouped section
                        showAvailableToolsForGroup = null
                    },
                    onCreateAutomation = {
                        preSelectedGroup = null
                        showCreateAutomationDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onDuplicateTool = {
                        preSelectedGroup = null
                        showDuplicateToolDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onDuplicateAutomation = {
                        preSelectedGroup = null
                        showDuplicateAutomationDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onToolClick = { toolId -> openEntry = waiting.oldest[toolId]?.let { com.assistant.core.tools.EntryToOpen.Existing(it) }; selectedToolInstanceId = toolId },
                    onOpenEntry = { tool, entry -> openEntry = entry; selectedToolInstanceId = tool.id },
                    editor = gridEditor,
                    onToolLongClick = { tool ->
                        editingToolId = tool.id
                        showingConfigFor = tool.tooltype
                    },
                    onAutomationEdit = { automation -> onNavigateToSeedEditor?.invoke(automation.seedSessionId) },
                    onAutomationTest = { automation ->
                        coroutineScope.launch {
                            try {
                                val result = coordinator.processUserAction(
                                    "automations.execute_manual",
                                    mapOf("automation_id" to automation.id)
                                )
                                if (result.status == CommandStatus.SUCCESS) {
                                    errorMessage = s.shared("automation_execution_triggered")
                                } else {
                                    errorMessage = result.error
                                }
                            } catch (e: Exception) {
                                errorMessage = s.shared("message_error").format(e.message ?: "")
                            }
                        }
                    },
                    onAutomationView = { automation -> onNavigateToAutomationHistory?.invoke(automation.id) },
                    onAutomationToggle = { automation, enabled ->
                        coroutineScope.launch {
                            try {
                                val operation = if (enabled) "automations.enable" else "automations.disable"
                                val result = coordinator.processUserAction(
                                    operation,
                                    mapOf("automation_id" to automation.id)
                                )
                                if (result.status != CommandStatus.SUCCESS) {
                                    errorMessage = result.error
                                }
                            } catch (e: Exception) {
                                errorMessage = s.shared("message_error").format(e.message ?: "")
                            }
                        }
                    },
                    onAutomationStartChat = onAutomationStartChat,
                    context = context
                )
        }
    }
    if (gridEditor.selectedId != null) com.assistant.core.ui.components.GridEditBar(gridEditor)
    }

    // The phone's back key leaves the zone; a move in progress is cancelled, once asked
    var confirmLeave by remember { mutableStateOf(false) }
    if (gridEditor.moving) androidx.activity.compose.BackHandler { confirmLeave = true }
    if (confirmLeave) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = { confirmLeave = false; gridEditor.cancel(); onBack() },
            onCancel = { confirmLeave = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UI.Text(s.shared("grid_leave_title"), TextType.SUBTITLE)
                UI.Text(s.shared("grid_leave_message"), TextType.BODY)
            }
        }
    }

    // Create automation dialog
    if (showCreateAutomationDialog) {
        com.assistant.core.ai.ui.automation.CreateAutomationDialog(
            zoneId = zone.id,
            zoneName = zone.name,
            preSelectedGroup = preSelectedGroup,
            onDismiss = {
                showCreateAutomationDialog = false
                preSelectedGroup = null
            },
            onSuccess = { seedSessionId ->
                showCreateAutomationDialog = false
                preSelectedGroup = null
                // Reload automations to show the new one
                coroutineScope.launch {
                    coordinator.executeWithLoading(
                        operation = "automations.list",
                        params = mapOf("zone_id" to zone.id),
                        onLoading = { isLoadingAutomations = it },
                        onError = { error -> errorMessage = error }
                    )?.let { result ->
                        @Suppress("UNCHECKED_CAST")
                        val automationsList = result.data?.get("automations") as? List<Map<String, Any>> ?: emptyList()
                        automations = automationsList.map { map ->
                            com.assistant.core.ai.data.Automation.fromResult(map)
                        }
                    }
                }
                // Navigate to SEED editor
                onNavigateToSeedEditor?.invoke(seedSessionId)
            }
        )
    }

    // Duplicate tool dialog
    if (showDuplicateToolDialog) {
        com.assistant.core.ui.selectors.DuplicateSelector(
            type = com.assistant.core.ui.selectors.DuplicateType.TOOL,
            onDismiss = {
                showDuplicateToolDialog = false
            },
            onConfirm = { sourceZoneId, toolInstanceId ->
                coroutineScope.launch {
                    isLoading = true
                    try {
                        val result = coordinator.processUserAction(
                            "tools.duplicate",
                            mapOf(
                                "tool_instance_id" to toolInstanceId,
                                "target_zone_id" to zone.id,
                                "target_group" to (preSelectedGroup ?: "")
                            )
                        )
                        if (result.status == CommandStatus.SUCCESS) {
                            UI.Toast(context, s.shared("duplicate_success_tool"))
                            showDuplicateToolDialog = false
                            // Reload tools to show the new one
                            coordinator.executeWithLoading(
                                operation = "tools.list",
                                params = mapOf(
                                    "zone_id" to zone.id,
                                    "include_config" to true,
                                    "include_position" to true
                                ),
                                onLoading = { isLoading = it },
                                onError = { error -> errorMessage = error }
                            )?.let { toolsResult ->
                                toolInstances = toolsResult.mapData("tool_instances") { toolInstanceOf(it) }
                            }
                        } else {
                            errorMessage = result.error ?: s.shared("duplicate_error").format("")
                        }
                    } catch (e: Exception) {
                        errorMessage = s.shared("duplicate_error").format(e.message ?: "")
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
    }

    // Duplicate automation dialog
    if (showDuplicateAutomationDialog) {
        com.assistant.core.ui.selectors.DuplicateSelector(
            type = com.assistant.core.ui.selectors.DuplicateType.AUTOMATION,
            onDismiss = {
                showDuplicateAutomationDialog = false
            },
            onConfirm = { sourceZoneId, automationId ->
                coroutineScope.launch {
                    isLoading = true
                    try {
                        val result = coordinator.processUserAction(
                            "automations.duplicate",
                            mapOf(
                                "automation_id" to automationId,
                                "target_zone_id" to zone.id,
                                "target_group" to (preSelectedGroup ?: "")
                            )
                        )
                        if (result.status == CommandStatus.SUCCESS) {
                            UI.Toast(context, s.shared("duplicate_success_automation"))
                            showDuplicateAutomationDialog = false
                            // Reload automations to show the new one
                            coordinator.executeWithLoading(
                                operation = "automations.list",
                                params = mapOf("zone_id" to zone.id),
                                onLoading = { isLoadingAutomations = it },
                                onError = { error -> errorMessage = error }
                            )?.let { result ->
                                val automationsList = result.data?.get("automations") as? List<*> ?: emptyList<Any>()
                                automations = automationsList.map { com.assistant.core.ai.data.Automation.fromResult(it as Map<*, *>) }
                            }
                        } else {
                            errorMessage = result.error ?: s.shared("duplicate_error").format("")
                        }
                    } catch (e: Exception) {
                        errorMessage = s.shared("duplicate_error").format(e.message ?: "")
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
    }

    // Error handling with Toast
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * Display a group section with its tools and automations
 */
@Composable
private fun GroupSection(
    groupName: String,
    zoneToolGroups: List<String>,
    variables: List<com.assistant.core.ui.variables.VariableRow>,
    onOpenVariable: (String) -> Unit,
    onCreateVariable: () -> Unit,
    toolInstances: List<ToolInstance>,
    automations: List<com.assistant.core.ai.data.Automation>,
    showAvailableToolsForGroup: String?,
    onToggleToolsList: () -> Unit,
    onSelectToolType: (String) -> Unit,
    onCreateAutomation: () -> Unit,
    onDuplicateTool: () -> Unit,
    onDuplicateAutomation: () -> Unit,
    onToolClick: (String) -> Unit,
    onOpenEntry: (ToolInstance, com.assistant.core.tools.EntryToOpen) -> Unit,
    editor: com.assistant.core.ui.components.GridEditor,
    onToolLongClick: (ToolInstance) -> Unit,
    onAutomationEdit: (com.assistant.core.ai.data.Automation) -> Unit,
    onAutomationTest: (com.assistant.core.ai.data.Automation) -> Unit,
    onAutomationView: (com.assistant.core.ai.data.Automation) -> Unit,
    onAutomationToggle: (com.assistant.core.ai.data.Automation, Boolean) -> Unit,
    onAutomationStartChat: ((String) -> Unit)?,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }
    // Filter tools and automations for this group
    val groupTools = toolInstances.filter { ToolPositions.section(it, zoneToolGroups) == groupName }
    val groupAutomations = automations.filter { it.group == groupName }

    // Section header - use SECTION_HEADER for subtle contrast with surfaceVariant
    UI.Card(type = CardType.SECTION_HEADER) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = groupName,
                type = TextType.SUBTITLE
            )

            com.assistant.core.ui.components.GridSectionButtons(groupName, groupTools.isNotEmpty(), editor, onToggleToolsList)
        }
    }

    // Available tools/automations list (shown conditionally)
    if (showAvailableToolsForGroup == groupName && !editor.anyEditing) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = s.shared("message_available_tool_types"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )

                // Duplicate automation button (SECONDARY, first)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        onClick = onDuplicateAutomation
                    ) {
                        UI.Text(
                            text = s.shared("action_duplicate_automation"),
                            type = TextType.LABEL
                        )
                    }
                }

                // Duplicate tool button (PRIMARY, second)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        onClick = onDuplicateTool
                    ) {
                        UI.Text(
                            text = s.shared("action_duplicate_tool"),
                            type = TextType.LABEL
                        )
                    }
                }

                // Variable button (SECONDARY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        onClick = onCreateVariable
                    ) {
                        UI.Text(
                            text = s.shared("variable_display_name"),
                            type = TextType.LABEL
                        )
                    }
                }

                // Automation button (SECONDARY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        onClick = onCreateAutomation
                    ) {
                        UI.Text(
                            text = s.shared("automation_display_name"),
                            type = TextType.LABEL
                        )
                    }
                }

                // List all available tool types (PRIMARY)
                ToolTypeManager.getAllToolTypes().forEach { (toolTypeId, toolType) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            onClick = { onSelectToolType(toolTypeId) }
                        ) {
                            UI.Text(
                                text = toolType.getDisplayName(context),
                                type = TextType.LABEL
                            )
                        }
                    }
                }
            }
        }
    }

    // The group's tools on their grid
    SectionGrid(groupName, groupTools, editor, onToolClick, onToolLongClick, onOpenEntry)

    // The group's variables, in one compact card
    com.assistant.core.ui.components.Faded(editor.anyEditing) { com.assistant.core.ui.variables.VariablesCard(variables, onOpenVariable) }

    // Display automations in this group
    groupAutomations.forEach { automation ->
        com.assistant.core.ui.components.Faded(editor.anyEditing) { com.assistant.core.ai.ui.automation.AutomationCard(
            automation = automation,
            onEdit = { onAutomationEdit(automation) },
            onTest = { onAutomationTest(automation) },
            onView = { onAutomationView(automation) },
            onToggleEnabled = { enabled -> onAutomationToggle(automation, enabled) },
            onStartChat = { onAutomationStartChat?.invoke(automation.seedSessionId) }
        ) }
    }

    // Empty state if no tools/automations in this group (only when list is not showing)
    if (groupTools.isEmpty() && groupAutomations.isEmpty() && showAvailableToolsForGroup != groupName) {
        UI.Text(
            text = s.shared("message_no_items_in_group"),
            type = TextType.CAPTION,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )
    }

    // Spacer after each group
    Spacer(modifier = Modifier.height(24.dp))
}

/**
 * Display the ungrouped section with tools and automations without a group
 */
@Composable
private fun UngroupedSection(
    toolInstances: List<ToolInstance>,
    variables: List<com.assistant.core.ui.variables.VariableRow>,
    onOpenVariable: (String) -> Unit,
    onCreateVariable: () -> Unit,
    automations: List<com.assistant.core.ai.data.Automation>,
    hasConfiguredGroups: Boolean,
    showAvailableToolsForGroup: String?,
    onToggleToolsList: () -> Unit,
    onSelectToolType: (String) -> Unit,
    onCreateAutomation: () -> Unit,
    onDuplicateTool: () -> Unit,
    onDuplicateAutomation: () -> Unit,
    onToolClick: (String) -> Unit,
    onOpenEntry: (ToolInstance, com.assistant.core.tools.EntryToOpen) -> Unit,
    editor: com.assistant.core.ui.components.GridEditor,
    onToolLongClick: (ToolInstance) -> Unit,
    onAutomationEdit: (com.assistant.core.ai.data.Automation) -> Unit,
    onAutomationTest: (com.assistant.core.ai.data.Automation) -> Unit,
    onAutomationView: (com.assistant.core.ai.data.Automation) -> Unit,
    onAutomationToggle: (com.assistant.core.ai.data.Automation, Boolean) -> Unit,
    onAutomationStartChat: ((String) -> Unit)?,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }

    // Determine section label
    val sectionLabel = if (hasConfiguredGroups) {
        s.shared("label_ungrouped")
    } else {
        s.shared("label_tools_and_automations")
    }

    // Section header - use SECTION_HEADER for subtle contrast with surfaceVariant
    UI.Card(type = CardType.SECTION_HEADER) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = sectionLabel,
                type = TextType.SUBTITLE
            )

            com.assistant.core.ui.components.GridSectionButtons("", toolInstances.isNotEmpty(), editor, onToggleToolsList)
        }
    }

    // Available tools/automations list (shown conditionally)
    if (showAvailableToolsForGroup == "" && !editor.anyEditing) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = s.shared("message_available_tool_types"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )

                // Duplicate automation button (SECONDARY, first)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        onClick = onDuplicateAutomation
                    ) {
                        UI.Text(
                            text = s.shared("action_duplicate_automation"),
                            type = TextType.LABEL
                        )
                    }
                }

                // Duplicate tool button (PRIMARY, second)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        onClick = onDuplicateTool
                    ) {
                        UI.Text(
                            text = s.shared("action_duplicate_tool"),
                            type = TextType.LABEL
                        )
                    }
                }

                // Variable button (SECONDARY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        onClick = onCreateVariable
                    ) {
                        UI.Text(
                            text = s.shared("variable_display_name"),
                            type = TextType.LABEL
                        )
                    }
                }

                // Automation button (SECONDARY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        onClick = onCreateAutomation
                    ) {
                        UI.Text(
                            text = s.shared("automation_display_name"),
                            type = TextType.LABEL
                        )
                    }
                }

                // List all available tool types (PRIMARY)
                ToolTypeManager.getAllToolTypes().forEach { (toolTypeId, toolType) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            onClick = { onSelectToolType(toolTypeId) }
                        ) {
                            UI.Text(
                                text = toolType.getDisplayName(context),
                                type = TextType.LABEL
                            )
                        }
                    }
                }
            }
        }
    }

    // The ungrouped tools on their grid
    SectionGrid("", toolInstances, editor, onToolClick, onToolLongClick, onOpenEntry)

    // The ungrouped variables, in one compact card
    com.assistant.core.ui.components.Faded(editor.anyEditing) { com.assistant.core.ui.variables.VariablesCard(variables, onOpenVariable) }

    // Display ungrouped automations
    automations.forEach { automation ->
        com.assistant.core.ui.components.Faded(editor.anyEditing) { com.assistant.core.ai.ui.automation.AutomationCard(
            automation = automation,
            onEdit = { onAutomationEdit(automation) },
            onTest = { onAutomationTest(automation) },
            onView = { onAutomationView(automation) },
            onToggleEnabled = { enabled -> onAutomationToggle(automation, enabled) },
            onStartChat = { onAutomationStartChat?.invoke(automation.seedSessionId) }
        ) }
    }

    // Empty state (only when list is not showing)
    if (toolInstances.isEmpty() && automations.isEmpty() && showAvailableToolsForGroup != "") {
        UI.Text(
            text = s.shared("message_no_items_in_group"),
            type = TextType.CAPTION,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )
    }
}

/** A tool of a tools.list result asked with its config and its place. */
@Suppress("UNCHECKED_CAST")
private fun toolInstanceOf(map: Map<String, Any?>) = ToolInstance(
    id = map["id"] as String,
    zone_id = map["zone_id"] as String,
    tooltype = map["tooltype"] as String,
    config_json = JsonUtils.toJSONObject(map["config"] as Map<String, Any?>).toString(),
    grid_x = (map["grid_x"] as Number).toInt(),
    grid_y = (map["grid_y"] as Number).toInt(),
    created_at = (map["created_at"] as Number).toLong(),
    updated_at = (map["updated_at"] as Number).toLong()
)

/** A section's grid: in edit mode when it is the section edited, faded while another is. */
@Composable
private fun SectionGrid(
    key: String,
    tools: List<ToolInstance>,
    editor: com.assistant.core.ui.components.GridEditor,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, com.assistant.core.tools.EntryToOpen) -> Unit
) {
    com.assistant.core.ui.components.Faded(editor.anyEditing && !editor.isEditing(key)) {
        com.assistant.core.ui.components.ToolGrid(tools, { onToolClick(it.id) }, onToolLongClick, onOpenEntry, editor.gridEdit(key))
    }
}
