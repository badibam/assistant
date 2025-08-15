package com.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.debug.DebugManager
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
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
    
    // Observe zones in real-time
    val database = remember { AppDatabase.getDatabase(context) }
    val zones by database.zoneDao().getAllZones().collectAsState(initial = emptyList())
    
    // Message debug initial
    LaunchedEffect(Unit) {
        DebugManager.debug("🚀 MainScreen chargé")
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
        // Zone Debug - en haut pour visibilité
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
        
        // Top bar
        UI.TopBar(
            type = TopBarType.DEFAULT,
            title = "Assistant Personnel"
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Zones section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Text(
                text = "Zones",
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
                            text = "Aucune zone configurée",
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
                                text = "Créer une zone",
                                type = TextType.LABEL,
                                semantic = "button-label"
                            )
                        }
                    }
                }
            } else {
                // Show zones list
                zones.forEach { zone ->
                    UI.Card(
                        type = CardType.ZONE,
                        semantic = "zone-${zone.id}",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column {
                            UI.Text(
                                text = zone.name,
                                type = TextType.SUBTITLE,
                                semantic = "zone-name"
                            )
                            zone.description?.let { description ->
                                UI.Spacer(modifier = Modifier.height(4.dp))
                                UI.Text(
                                    text = description,
                                    type = TextType.CAPTION,
                                    semantic = "zone-description"
                                )
                            }
                        }
                    }
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
                        text = "Ajouter une zone",
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
                text = "Terminal",
                type = TextType.SUBTITLE,
                semantic = "section-title"
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            UI.Terminal(
                content = "Assistant démarré. En attente de commandes...",
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
                    text = "Appel IA",
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