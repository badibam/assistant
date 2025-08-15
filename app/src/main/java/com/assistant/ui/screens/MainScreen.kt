package com.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.debug.DebugManager
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
import com.assistant.R
import kotlinx.coroutines.launch

/**
 * Main screen - entry point of the application
 * Uses purely semantic UI components
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    var showCreateZone by remember { mutableStateOf(false) }
    var selectedZone by remember { mutableStateOf<Zone?>(null) }
    
    // Observe zones in real-time
    val database = remember { AppDatabase.getDatabase(context) }
    val zones by database.zoneDao().getAllZones().collectAsState(initial = emptyList())
    
    // Message debug initial
    LaunchedEffect(Unit) {
        DebugManager.debug("🚀 MainScreen chargé")
    }
    
    // Zone Debug - toujours en premier, visible partout
    UI.Card(
        type = CardType.SYSTEM,
        semantic = "debug-zone",
        modifier = Modifier.fillMaxWidth()
    ) {
        UI.Column {
            // Affichage des 3 derniers messages
            val messages by remember { derivedStateOf { DebugManager.debugMessages.take(3) } }
            
            repeat(3) { index ->
                UI.Text(
                    text = messages.getOrNull(index) ?: "",
                    type = TextType.CAPTION,
                    semantic = "debug-line-$index",
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
    
    UI.Spacer(modifier = Modifier.height(8.dp))
    
    // Show ZoneScreen when a zone is selected
    selectedZone?.let { zone ->
        ZoneScreen(
            zone = zone,
            onBack = {
                selectedZone = null
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
            onCreate = { name, description ->
                coroutineScope.launch {
                    try {
                        val result = coordinator.processUserAction(
                            "create->zone",
                            mapOf(
                                "name" to name,
                                "description" to (description ?: "")
                            )
                        )
                        DebugManager.debug("Zone création: ${result.status}")
                        showCreateZone = false
                    } catch (e: Exception) {
                        DebugManager.debug("Erreur création zone: ${e.message}")
                    }
                }
            }
        )
    } else {
        UI.Screen(type = ScreenType.MAIN) {
        
        // Top bar
        UI.TopBar(
            type = TopBarType.DEFAULT,
            title = stringResource(R.string.main_screen_title)
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Zones section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Text(
                text = stringResource(R.string.zones_section),
                type = TextType.TITLE,
                semantic = "section-title"
            )
            
            UI.Spacer(modifier = Modifier.height(12.dp))
            
            if (zones.isEmpty()) {
                // Show placeholder when no zones
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "placeholder",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        UI.Text(
                            text = stringResource(R.string.no_zones),
                            type = TextType.BODY,
                            semantic = "empty-state"
                        )
                        
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            semantic = "create-zone",
                            onClick = { 
                                DebugManager.debugButtonClick("Créer une zone")
                                showCreateZone = true
                            }
                        ) {
                            UI.Text(
                                text = stringResource(R.string.create_zone),
                                type = TextType.LABEL,
                                semantic = "button-label"
                            )
                        }
                    }
                }
            } else {
                // Show zones list
                zones.forEach { zone ->
                    UI.ZoneCard(
                        zoneName = zone.name,
                        onClick = {
                            DebugManager.debugButtonClick("Navigation vers zone: ${zone.name}")
                            selectedZone = zone
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
                
                UI.Spacer(modifier = Modifier.height(8.dp))
                
                // Add zone button
                UI.Button(
                    type = ButtonType.SECONDARY,
                    semantic = "add-zone",
                    onClick = { 
                        DebugManager.debugButtonClick("Ajouter une zone")
                        showCreateZone = true
                    }
                ) {
                    UI.Text(
                        text = stringResource(R.string.add_zone),
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(24.dp))
        
        // Terminal section
        UI.Container(type = ContainerType.SECONDARY) {
            UI.Text(
                text = stringResource(R.string.terminal_title),
                type = TextType.SUBTITLE,
                semantic = "section-title"
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            UI.Terminal(
                content = stringResource(R.string.assistant_ready),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        UI.Spacer(modifier = Modifier.height(24.dp))
        
        // AI Call Zone (permanent toggle)
        UI.Container(type = ContainerType.FLOATING) {
            UI.Button(
                type = ButtonType.SECONDARY,
                semantic = "ai-toggle",
                onClick = {
                    DebugManager.debugButtonClick("Appel IA")
                    // TODO: Toggle AI dialogue interface
                }
            ) {
                UI.Text(
                    text = stringResource(R.string.ai_call_zone),
                    type = TextType.LABEL,
                    semantic = "ai-call-label"
                )
            }
        }
        }
    }
}

// TODO: Add when database is ready
/*
@Composable
private fun ZonesList(zones: List<Zone>) {
    LazyColumn {
        items(zones) { zone ->
            UI.ZoneCard(
                zoneName = zone.name,
                onClick = {
                    // TODO: Navigate to zone detail
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}
*/