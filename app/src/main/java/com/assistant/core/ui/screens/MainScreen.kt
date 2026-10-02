package com.assistant.core.ui.screens

import com.assistant.core.utils.JsonUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.mapData
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.strings.Strings
import com.assistant.core.database.entities.Zone
import com.assistant.core.ui.dialogs.SettingsDialog
import com.assistant.core.ui.screens.settings.*
import com.assistant.core.ai.ui.screens.AIProvidersScreen
import com.assistant.core.ai.ui.chat.AIFloatingChat
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
import kotlinx.coroutines.launch
import com.assistant.core.utils.LogManager

/**
 * Main screen - entry point of the application
 * Migrated to use new UI.* system
 */
@OptIn(ExperimentalFoundationApi::class)
/**
 * @param openToolId A tool to open, on its oldest entry that waits, the way its tile opens it: the
 *   one a touched notification is about; [onToolOpened] once its zone shows it
 */
@Composable
fun MainScreen(openToolId: String? = null, onToolOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    // Load zones and zone_groups via command pattern
    var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
    var zoneGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // The edit mode of the zone groups' grids, a section named by its group, "" for the ungrouped one
    val gridEditor = com.assistant.core.ui.components.rememberGridEditor(
        placeOperation = "zones.place",
        placeParams = emptyMap(),
        sectionTiles = { key -> com.assistant.core.grid.ZonePositions.tiles(zones, zoneGroups, key.ifEmpty { null }) },
        onError = { errorMessage = it }
    )

    // Navigation states - persistent across orientation changes
    var showCreateZone by rememberSaveable { mutableStateOf(false) }
    var preSelectedZoneGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var showMainScreenConfig by rememberSaveable { mutableStateOf(false) }
    var selectedZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var configZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSeedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAutomationId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedExecutionSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAIProviders by rememberSaveable { mutableStateOf(false) }
    var showFormat by rememberSaveable { mutableStateOf(false) }
    var showAILimits by rememberSaveable { mutableStateOf(false) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var showDemo by rememberSaveable { mutableStateOf(false) }
    var showUI by rememberSaveable { mutableStateOf(false) }
    var showData by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showAIChat by rememberSaveable { mutableStateOf(false) }

    // A chat asked for from any screen, with its content: the zone left for the chat, which lives here
    LaunchedEffect(Unit) {
        com.assistant.core.ai.orchestration.ChatRequests.requests.collect { prefill ->
            com.assistant.core.ai.orchestration.AIOrchestrator.startNewChatSession(prefill)
            selectedZoneId = null
            showAIChat = true
        }
    }
    // The tool a notification asked for, with its oldest entry that waits, handed to its zone
    var opening by remember { mutableStateOf<Pair<String, String?>?>(null) }
    LaunchedEffect(openToolId) {
        val toolId = openToolId ?: return@LaunchedEffect
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolId))
        val zoneId = (tool.data?.get("tool_instance") as? Map<*, *>)?.get("zone_id") as? String
        if (zoneId == null) {
            // Deleted since the notification: the app opens as it is
            LogManager.ui("MainScreen: tool $toolId of a notification not found: ${tool.error}", "WARN")
            onToolOpened()
            return@LaunchedEffect
        }
        val waiting = coordinator.processUserAction("tools.waiting", mapOf("zone_id" to zoneId))
        opening = toolId to ((waiting.data?.get("oldest") as? Map<*, *>)?.get(toolId) as? String)
        selectedZoneId = zoneId
        onToolOpened()
    }
    // A tool asked for from any screen, on one of its entries: handed to its zone as above
    LaunchedEffect(Unit) {
        com.assistant.core.tools.ToolRequests.requests.collect { (toolId, entryId) ->
            val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolId))
            val zoneId = (tool.data?.get("tool_instance") as? Map<*, *>)?.get("zone_id") as? String
            if (zoneId == null) {
                LogManager.ui("MainScreen: tool $toolId asked for not found: ${tool.error}", "WARN")
                errorMessage = tool.error ?: s.shared("service_error_tool_instance_not_found")
                return@collect
            }
            opening = toolId to entryId
            selectedZoneId = zoneId
        }
    }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    
    // Derived states from IDs (recomputed after orientation change)
    val selectedZone = zones.find { it.id == selectedZoneId }
    val configZone = zones.find { it.id == configZoneId }
    
    // Load zones and zone_groups on first composition
    LaunchedEffect(Unit) {
        // Load zones
        coordinator.executeWithLoading(
            operation = "zones.list",
            params = mapOf("include_position" to true),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            zones = result.mapData("zones") { map ->
                zoneFrom(map).also { zone ->
                    LogManager.ui("MainScreen - Loaded zone '${zone.name}' with group: '${zone.group}'", "DEBUG")
                }
            }
        }

        // Load zone_groups
        coordinator.executeWithLoading(
            operation = "app_config.get",
            params = mapOf("category" to com.assistant.core.database.entities.AppSettingCategories.MAIN_SCREEN),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            zoneGroups = ((result.data?.get("settings") as Map<*, *>)["zone_groups"] as List<*>).map { it as String }
        }
    }

    // Observe data changes and reload zones and zone_groups automatically
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ZonesChanged -> {
                    coordinator.executeWithLoading(
                        operation = "zones.list",
                        params = mapOf("include_position" to true),
                        onLoading = { isLoading = it },
                        onError = { error -> errorMessage = error }
                    )?.let { result ->
                        zones = result.mapData("zones") { map ->
                            zoneFrom(map)
                        }
                    }
                }
                is DataChangeEvent.AppConfigChanged -> {
                    // Reload zone_groups when app config changes
                    coordinator.executeWithLoading(
                        operation = "app_config.get",
                        params = mapOf("category" to com.assistant.core.database.entities.AppSettingCategories.MAIN_SCREEN),
                        onLoading = { isLoading = it },
                        onError = { error -> errorMessage = error }
                    )?.let { result ->
                        zoneGroups = ((result.data?.get("settings") as Map<*, *>)["zone_groups"] as List<*>).map { it as String }
                    }
                }
                else -> {} // Ignore other events
            }
        }
    }

    // Function to reload zones after operations
    val reloadZones = {
        coroutineScope.launch {
            coordinator.executeWithLoading(
                operation = "zones.list",
                params = mapOf("include_position" to true),
                onLoading = { isLoading = it },
                onError = { error -> errorMessage = error }
            )?.let { result ->
                zones = result.mapData("zones") { map ->
                    zoneFrom(map)
                }
            }
        }
    }

    // Show MainScreenConfigScreen when requested
    if (showMainScreenConfig) {
        com.assistant.core.ui.screens.settings.AppSettingsScreen(
            category = com.assistant.core.database.entities.AppSettingCategories.MAIN_SCREEN,
            onBack = { showMainScreenConfig = false }
        )
        return // Exit MainScreen composition when showing config
    }

    // Show History screen when requested
    if (showHistory) {
        HistoryScreen(
            onNavigateBack = {
                showHistory = false
            },
            onResumeSession = { sessionId ->
                showHistory = false
                showAIChat = true
            }
        )
        return // Exit MainScreen composition when showing History
    }

    // Show AI Providers screen when requested
    if (showAIProviders) {
        AIProvidersScreen(
            onBack = {
                showAIProviders = false
            }
        )
        return // Exit MainScreen composition when showing AI Providers
    }

    // Show Format settings screen when requested
    if (showFormat) {
        com.assistant.core.ui.screens.settings.AppSettingsScreen(
            category = com.assistant.core.database.entities.AppSettingCategories.FORMAT,
            onBack = { showFormat = false }
        )
        return // Exit MainScreen composition when showing Format settings
    }

    // Show AI Limits settings screen when requested
    if (showAILimits) {
        com.assistant.core.ui.screens.settings.AppSettingsScreen(
            category = com.assistant.core.database.entities.AppSettingCategories.AI_LIMITS,
            onBack = { showAILimits = false }
        )
        return // Exit MainScreen composition when showing AI Limits settings
    }

    // Show Validation settings screen when requested
    if (showValidation) {
        com.assistant.core.ui.screens.settings.AppSettingsScreen(
            category = com.assistant.core.database.entities.AppSettingCategories.VALIDATION_CONFIG,
            onBack = { showValidation = false }
        )
        return // Exit MainScreen composition when showing Validation settings
    }

    if (showDemo) {
        com.assistant.core.ui.screens.settings.DemoSettingsScreen(onBack = { showDemo = false })
        return
    }

    // Show UI settings screen when requested
    if (showUI) {
        com.assistant.core.ui.screens.settings.AppSettingsScreen(
            category = com.assistant.core.database.entities.AppSettingCategories.UI,
            onBack = { showUI = false }
        )
        return // Exit MainScreen composition when showing UI settings
    }

    // Show Data settings screen when requested
    if (showData) {
        DataSettingsScreen(
            onBack = {
                showData = false
            }
        )
        return // Exit MainScreen composition when showing Data settings
    }

    // Show Logs screen when requested
    if (showLogs) {
        LogsScreen(
            onBack = {
                showLogs = false
            }
        )
        return // Exit MainScreen composition when showing Logs screen
    }

    // Show AIScreen for SEED session editing when requested
    selectedSeedSessionId?.let { seedSessionId ->
        com.assistant.core.ai.ui.screens.AIScreen(
            sessionId = seedSessionId,
            onClose = {
                selectedSeedSessionId = null
            }
        )
        return // Exit MainScreen composition when showing SEED editor
    }

    // Show ExecutionDetailScreen when an execution is selected
    selectedExecutionSessionId?.let { sessionId ->
        com.assistant.core.ai.ui.automation.ExecutionDetailScreen(
            sessionId = sessionId,
            onNavigateBack = {
                selectedExecutionSessionId = null
            }
        )
        return // Exit MainScreen composition when showing execution detail
    }

    // Show AutomationScreen when an automation is selected
    selectedAutomationId?.let { automationId ->
        com.assistant.core.ai.ui.automation.AutomationScreen(
            automationId = automationId,
            onNavigateBack = {
                selectedAutomationId = null
            },
            onNavigateToExecution = { sessionId ->
                selectedExecutionSessionId = sessionId
            }
        )
        return // Exit MainScreen composition when showing automation history
    }

    // Show CreateZoneScreen in edit mode when zone config is requested
    configZone?.let { zone ->
        CreateZoneScreen(
            existingZone = zone,
            onCancel = {
                configZoneId = null
            },
            onUpdate = {
                configZoneId = null
                reloadZones()
            },
            onDelete = {
                configZoneId = null
                reloadZones()
            }
        )
        return // Exit MainScreen composition when showing zone config
    }

    // Show ZoneScreen when a zone is selected
    selectedZone?.let { zone ->
        com.assistant.core.ui.components.ShownScreen { ZoneScreen(
            zone = zone,
            opening = opening,
            onOpened = { opening = null },
            onBack = {
                selectedZoneId = null
            },
            onNavigateToSeedEditor = { seedSessionId ->
                selectedSeedSessionId = seedSessionId
            },
            onNavigateToAutomationHistory = { automationId ->
                selectedAutomationId = automationId
            },
            onConfigureZone = { zoneId ->
                configZoneId = zoneId
            },
            onAutomationStartChat = { seedSessionId ->
                coroutineScope.launch {
                    // The chat opens with the automation's starting message, to send or change
                    val seed = com.assistant.core.ai.orchestration.AIOrchestrator.loadSeedMessages(seedSessionId)
                    val prefill = seed.firstOrNull { it.sender == com.assistant.core.ai.data.MessageSender.USER }?.richContent?.segments ?: emptyList()
                    com.assistant.core.ai.orchestration.ChatRequests.open(prefill)
                }
            }
        ) }
        return // Exit MainScreen composition when showing ZoneScreen
    }
    
    // Show CreateZoneScreen when requested
    if (showCreateZone) {
        CreateZoneScreen(
            preSelectedGroup = preSelectedZoneGroup,
            onCancel = {
                showCreateZone = false
                preSelectedZoneGroup = null
            },
            onCreate = {
                showCreateZone = false
                preSelectedZoneGroup = null
                reloadZones()
            }
        )
    } else {
        // The home screen is the root: the back key leaves the app, and asks first
        BackHandler { showExitConfirm = true }
        com.assistant.core.ui.components.CloseEditOnLeave(gridEditor)

        // Main content using hybrid system: Compose layouts + UI.* components
        Box(modifier = Modifier.fillMaxSize()) {
          // The bar of a zone being moved goes under the scrolling content
          Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.L)
            ) {
            // Header with Settings and Config buttons
            UI.PageHeader(
                title = s.shared("app_name"),
                subtitle = null,
                icon = null,
                leftButton = ButtonAction.CONFIGURE,
                rightButton = ButtonAction.CONFIGURE,
                onLeftClick = { showSettings = true },
                onRightClick = { showMainScreenConfig = true }
            )

            // Display zones by group sections
            if (isLoading) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            } else {
                // For each defined group, display its section (always shown, even if empty)
                zoneGroups.forEach { groupName ->
                    ZoneGroupSection(
                        groupName = groupName,
                        zones = zones,
                        configuredGroups = zoneGroups,
                        editor = gridEditor,
                        onZoneClick = { zone -> selectedZoneId = zone.id },
                        onZoneLongClick = { zone -> configZoneId = zone.id },
                        onAddZone = {
                            preSelectedZoneGroup = groupName
                            showCreateZone = true
                        },
                        context = context
                    )
                }

                // "Ungrouped" section (always shown, even if empty)
                // Includes zones with null group OR zones with group not in zoneGroups list
                ZoneGroupSection(
                    groupName = null, // null = ungrouped
                    zones = zones,
                    hasConfiguredGroups = zoneGroups.isNotEmpty(),
                    configuredGroups = zoneGroups, // Pass list to filter orphaned zones
                    editor = gridEditor,
                    onZoneClick = { zone -> selectedZoneId = zone.id },
                    onZoneLongClick = { zone -> configZoneId = zone.id },
                    onAddZone = {
                        preSelectedZoneGroup = null
                        showCreateZone = true
                    },
                    context = context
                )
            }
        } // Close Column
            if (gridEditor.selectedId != null) com.assistant.core.ui.components.GridEditBar(gridEditor)
          }

        // AI Chat floating button (in Box, not Column), away while a zone moves
        if (gridEditor.selectedId == null) Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(UI.Space.L)
        ) {
            UI.FloatingButton(action = ButtonAction.AI_CHAT, onClick = { showAIChat = true })
        }
        }
    }
    
    // Show Settings dialog when requested
    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onOptionSelected = { optionId ->
                when (optionId) {
                    "history" -> showHistory = true
                    "ai_providers" -> showAIProviders = true
                    "format" -> showFormat = true
                    "ai_limits" -> showAILimits = true
                    "validation" -> showValidation = true
                    "demo" -> showDemo = true
                    "ui" -> showUI = true
                    "data" -> showData = true
                    "logs" -> showLogs = true
                }
                showSettings = false
            }
        )
    }

    if (showExitConfirm) {
        UI.ConfirmDialog(
            title = s.shared("action_quit"),
            message = s.shared("exit_confirm_message"),
            confirmText = s.shared("action_quit"),
            cancelText = s.shared("action_cancel"),
            onConfirm = { (context as? android.app.Activity)?.finish() },
            onDismiss = { showExitConfirm = false }
        )
    }

    // AI Floating Chat
    AIFloatingChat(
        isVisible = showAIChat,
        onDismiss = { showAIChat = false }
    )

    // Error handling with Toast
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * Zone group section component
 * Displays zones belonging to a specific group (or ungrouped zones if groupName is null)
 * with a header and an add button
 */
@Composable
private fun ZoneGroupSection(
    groupName: String?,
    zones: List<Zone>,
    hasConfiguredGroups: Boolean = true,
    configuredGroups: List<String>,
    editor: com.assistant.core.ui.components.GridEditor,
    onZoneClick: (Zone) -> Unit,
    onZoneLongClick: (Zone) -> Unit,
    onAddZone: () -> Unit,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }

    // The zones shown here: of this group, or for the ungrouped section of none the home screen has
    val groupZones = zones.filter { com.assistant.core.grid.ZonePositions.section(it.group, configuredGroups) == groupName }
    val key = groupName ?: ""

    // Determine section label
    val sectionLabel = when {
        groupName != null -> groupName
        hasConfiguredGroups -> s.shared("label_ungrouped")
        else -> s.shared("label_zones")
    }

    // Section header with its buttons: its title over a divider (SECTION_HEADER)
    UI.Card(type = CardType.SECTION_HEADER) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.M),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = sectionLabel,
                type = TextType.HEADING
            )

            com.assistant.core.ui.components.GridSectionButtons(key, groupZones.isNotEmpty(), editor, onAddZone)
        }
    }

    // Display zones or empty message
    if (groupZones.isEmpty()) {
        UI.Card(type = CardType.DEFAULT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                contentAlignment = Alignment.Center
            ) {
                UI.Text(
                    text = if (groupName != null) {
                        s.shared("message_no_zones_in_group")
                    } else {
                        s.shared("message_no_ungrouped_zones")
                    },
                    type = TextType.CAPTION,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // The zones on their grid, in edit mode when it is this section's, faded while another's is
        com.assistant.core.ui.components.Faded(editor.anyEditing && !editor.isEditing(key)) {
            com.assistant.core.ui.components.GridLayout(groupZones.map { com.assistant.core.grid.ZonePositions.tile(it) }, groupZones.map { false }, editor.gridEdit(key)) { i ->
                val zone = groupZones[i]
                UI.ZoneCard(
                    zone = zone,
                    onClick = { onZoneClick(zone) },
                    onLongClick = { onZoneLongClick(zone) }
                )
            }
        }
    }
}

/**
 * A zone from one entry of zones.list. Every field the list returns is read here, and only
 * here: three copies of this used to drop whatever they did not name, the icon among them.
 */
private fun zoneFrom(map: Map<String, Any?>): Zone = Zone(
    id = map["id"] as String,
    name = map["name"] as String,
    description = map["description"] as? String,
    icon_name = map["icon_name"] as? String,
    display_mode = map["display_mode"] as String,
    grid_x = (map["grid_x"] as Number).toInt(),
    grid_y = (map["grid_y"] as Number).toInt(),
    created_at = (map["created_at"] as Number).toLong(),
    updated_at = (map["updated_at"] as Number).toLong(),
    tool_groups = (map["tool_groups"] as? List<*>)?.let { JsonUtils.toJSONArray(it).toString() },
    group = map["group"] as? String
)
