package com.assistant.core.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
    // Load zones via command pattern
    var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Navigation states - persistent across orientation changes
    var showCreateZone by rememberSaveable { mutableStateOf(false) }
    var selectedZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var configZoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSeedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAIProviders by rememberSaveable { mutableStateOf(false) }
    var showTranscription by rememberSaveable { mutableStateOf(false) }
    var showFormat by rememberSaveable { mutableStateOf(false) }
    var showAILimits by rememberSaveable { mutableStateOf(false) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var showUI by rememberSaveable { mutableStateOf(false) }
    var showData by rememberSaveable { mutableStateOf(false) }
    var showAIChat by rememberSaveable { mutableStateOf(false) }
    
    // Derived states from IDs (recomputed after orientation change)
    val selectedZone = zones.find { it.id == selectedZoneId }
    val configZone = zones.find { it.id == configZoneId }
    
    // Load zones on first composition
    LaunchedEffect(Unit) {
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
                    updated_at = (map["updated_at"] as Number).toLong()
                )
            }
        }
    }

    // Observe data changes and reload zones automatically
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
                                updated_at = (map["updated_at"] as Number).toLong()
                            )
                        }
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
                        updated_at = (map["updated_at"] as Number).toLong()
                    )
                }
            }
        }
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
            onConfigureZone = { zoneId ->
                configZoneId = zoneId
            }
        )
        return // Exit MainScreen composition when showing ZoneScreen
    }
    
    // Show CreateZoneScreen when requested
    if (showCreateZone) {
        CreateZoneScreen(
            onCancel = {
                showCreateZone = false
            },
            onCreate = { name, description, color ->
                coroutineScope.launch {
                    try {
                        val result = coordinator.processUserAction(
                            "zones.create",
                            mapOf(
                                "name" to name,
                                "description" to (description ?: ""),
                                "color" to (color ?: "")
                            )
                        )
                        showCreateZone = false
                        reloadZones()
                    } catch (e: Exception) {
                    }
                }
            }
        )
    } else {
        // Main content using hybrid system: Compose layouts + UI.* components
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Header with configure and add zone buttons
            UI.PageHeader(
                title = s.shared("app_name"),
                subtitle = null,
                icon = null,
                leftButton = ButtonAction.CONFIGURE,
                rightButton = ButtonAction.ADD,
                onLeftClick = { showSettings = true },
                onRightClick = { showCreateZone = true }
            )
            
            // Zones section
            
            if (zones.isEmpty()) {
                // Show placeholder when no zones
                UI.Text(
                    text = s.shared("message_no_zones_created"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )

            } else {
                // Show zones list using UI.ZoneCard
                zones.forEach { zone ->
                    UI.ZoneCard(
                        zone = zone,
                        onClick = {
                            selectedZoneId = zone.id
                        },
                        onLongClick = {
                            configZoneId = zone.id
                        }
                    )
                }
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