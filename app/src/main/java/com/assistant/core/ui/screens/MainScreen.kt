package com.assistant.core.ui.screens

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

/**
 * Main screen - entry point of the application
 * Migrated to use new UI.* system
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    // Load zones and zone_groups via command pattern
    var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
    var zoneGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
    var showTranscription by rememberSaveable { mutableStateOf(false) }
    var showFormat by rememberSaveable { mutableStateOf(false) }
    var showAILimits by rememberSaveable { mutableStateOf(false) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var showUI by rememberSaveable { mutableStateOf(false) }
    var showData by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showAIChat by rememberSaveable { mutableStateOf(false) }
    
    // Derived states from IDs (recomputed after orientation change)
    val selectedZone = zones.find { it.id == selectedZoneId }
    val configZone = zones.find { it.id == configZoneId }
    
    // Load zones and zone_groups on first composition
    LaunchedEffect(Unit) {
        // Load zones
        coordinator.executeWithLoading(
            operation = "zones.list",
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            zones = result.mapData("zones") { map ->
                Zone(
                    id = map["id"] as String,
                    name = map["name"] as String,
                    description = map["description"] as? String,
                    order_index = (map["order_index"] as Number).toInt(),
                    created_at = (map["created_at"] as Number).toLong(),
                    updated_at = (map["updated_at"] as Number).toLong(),
                    tool_groups = map["tool_groups"] as? String,
                    group = map["group"] as? String
                ).also { zone ->
                    com.assistant.core.utils.LogManager.ui("MainScreen - Loaded zone '${zone.name}' with group: '${zone.group}'", "DEBUG")
                }
            }
        }

        // Load zone_groups
        coordinator.executeWithLoading(
            operation = "app_config.get_zone_groups",
            params = emptyMap(),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            zoneGroups = (result.data?.get("zone_groups") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()
        }
    }

    // Observe data changes and reload zones and zone_groups automatically
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ZonesChanged -> {
                    coordinator.executeWithLoading(
                        operation = "zones.list",
                        onLoading = { isLoading = it },
                        onError = { error -> errorMessage = error }
                    )?.let { result ->
                        zones = result.mapData("zones") { map ->
                            Zone(
                                id = map["id"] as String,
                                name = map["name"] as String,
                                description = map["description"] as? String,
                                order_index = (map["order_index"] as Number).toInt(),
                                created_at = (map["created_at"] as Number).toLong(),
                                updated_at = (map["updated_at"] as Number).toLong(),
                                tool_groups = map["tool_groups"] as? String,
                                group = map["group"] as? String
                            )
                        }
                    }
                }
                is DataChangeEvent.AppConfigChanged -> {
                    // Reload zone_groups when app config changes
                    coordinator.executeWithLoading(
                        operation = "app_config.get_zone_groups",
                        params = emptyMap(),
                        onLoading = { isLoading = it },
                        onError = { error -> errorMessage = error }
                    )?.let { result ->
                        zoneGroups = (result.data?.get("zone_groups") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
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
                onLoading = { isLoading = it },
                onError = { error -> errorMessage = error }
            )?.let { result ->
                zones = result.mapData("zones") { map ->
                    Zone(
                        id = map["id"] as String,
                        name = map["name"] as String,
                        description = map["description"] as? String,
                        order_index = (map["order_index"] as Number).toInt(),
                        created_at = (map["created_at"] as Number).toLong(),
                        updated_at = (map["updated_at"] as Number).toLong(),
                        tool_groups = map["tool_groups"] as? String,
                        group = map["group"] as? String
                    )
                }
            }
        }
    }

    // Show MainScreenConfigScreen when requested
    if (showMainScreenConfig) {
        MainScreenConfigScreen(
            onBack = {
                showMainScreenConfig = false
            }
        )
        return // Exit MainScreen composition when showing config
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

    // Show Transcription settings screen when requested
    if (showTranscription) {
        TranscriptionProvidersScreen(
            onBack = {
                showTranscription = false
            }
        )
        return // Exit MainScreen composition when showing Transcription settings
    }

    // Show Format settings screen when requested
    if (showFormat) {
        FormatSettingsScreen(
            onBack = {
                showFormat = false
            }
        )
        return // Exit MainScreen composition when showing Format settings
    }

    // Show AI Limits settings screen when requested
    if (showAILimits) {
        AILimitsSettingsScreen(
            onBack = {
                showAILimits = false
            }
        )
        return // Exit MainScreen composition when showing AI Limits settings
    }

    // Show Validation settings screen when requested
    if (showValidation) {
        ValidationSettingsScreen(
            onBack = {
                showValidation = false
            }
        )
        return // Exit MainScreen composition when showing Validation settings
    }

    // Show UI settings screen when requested
    if (showUI) {
        UISettingsScreen(
            onBack = {
                showUI = false
            }
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
        ZoneScreen(
            zone = zone,
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
            }
        )
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
        // Main content using hybrid system: Compose layouts + UI.* components
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

        // AI Chat floating button (in Box, not Column)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            UI.ActionButton(
                action = ButtonAction.AI_CHAT,
                display = ButtonDisplay.ICON,
                size = Size.L,
                onClick = { showAIChat = true }
            )
        }
        }
    }
    
    // Show Settings dialog when requested
    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onOptionSelected = { optionId ->
                when (optionId) {
                    "ai_providers" -> showAIProviders = true
                    "transcription" -> showTranscription = true
                    "format" -> showFormat = true
                    "ai_limits" -> showAILimits = true
                    "validation" -> showValidation = true
                    "ui" -> showUI = true
                    "data" -> showData = true
                    "logs" -> showLogs = true
                }
                showSettings = false
            }
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
    configuredGroups: List<String> = emptyList(),
    onZoneClick: (Zone) -> Unit,
    onZoneLongClick: (Zone) -> Unit,
    onAddZone: () -> Unit,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }

    // Filter zones for this group
    val groupZones = zones.filter { zone ->
        val matches = if (groupName != null) {
            // Match specific group
            zone.group == groupName
        } else {
            // Ungrouped section: null group OR orphaned groups (not in configured list)
            zone.group == null || (zone.group?.isNotBlank() == true && zone.group !in configuredGroups)
        }
        com.assistant.core.utils.LogManager.ui("ZoneGroupSection - Zone '${zone.name}' (group='${zone.group}') matches groupName='$groupName': $matches", "DEBUG")
        matches
    }

    // Determine section label
    val sectionLabel = when {
        groupName != null -> groupName
        hasConfiguredGroups -> s.shared("label_ungrouped")
        else -> s.shared("label_zones")
    }

    // Section header with add button
    UI.Card(type = CardType.DEFAULT) {
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

            // Add button
            UI.ActionButton(
                action = ButtonAction.ADD,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = onAddZone
            )
        }
    }

    // Display zones or empty message
    if (groupZones.isEmpty()) {
        UI.Card(type = CardType.DEFAULT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
        // Display zone cards
        groupZones.forEach { zone ->
            UI.ZoneCard(
                zone = zone,
                onClick = { onZoneClick(zone) },
                onLongClick = { onZoneLongClick(zone) }
            )
        }
    }
}