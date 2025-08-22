package com.assistant.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
import com.assistant.R
import kotlinx.coroutines.launch

/**
 * Main screen - entry point of the application
 * Uses purely semantic UI components
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    var showCreateZone by remember { mutableStateOf(false) }
    var selectedZone by remember { mutableStateOf<Zone?>(null) }
    var configZone by remember { mutableStateOf<Zone?>(null) }
    
    // Observe zones in real-time
    val database = remember { AppDatabase.getDatabase(context) }
    val zones by database.zoneDao().getAllZones().collectAsState(initial = emptyList())
    
    
    
    // Show CreateZoneScreen in edit mode when zone config is requested
    configZone?.let { zone ->
        CreateZoneScreen(
            existingZone = zone,
            onCancel = {
                configZone = null
            },
            onUpdate = {
                configZone = null
            }
        )
        return // Exit MainScreen composition when showing zone config
    }
    
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
            onCreate = { name, description, color ->
                coroutineScope.launch {
                    try {
                        val result = coordinator.processUserAction(
                            "create->zone",
                            mapOf(
                                "name" to name,
                                "description" to (description ?: ""),
                                "color" to (color ?: "")
                            )
                        )
                        showCreateZone = false
                    } catch (e: Exception) {
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
                    UI.Column {
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
                                showCreateZone = true
                            },
                            enabled = true
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
                    UI.Card(
                        type = CardType.ZONE,
                        semantic = "zone-${zone.id}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    selectedZone = zone
                                },
                                onLongClick = {
                                    configZone = zone
                                }
                            )
                    ) {
                        UI.Column {
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
                        showCreateZone = true
                    },
                    enabled = true
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
                    // TODO: Toggle AI dialogue interface
                },
                enabled = true
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

