package com.assistant.core.ui.Screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.entities.Zone
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.launch

/**
 * Main screen - entry point of the application
 * Migrated to use new UI.* system
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
    
    // Load zones via command pattern
    var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load zones on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val result = coordinator.processUserAction("get->zones")
            if (result.status == CommandStatus.SUCCESS && result.data != null) {
                val zonesData = result.data["zones"] as? List<Map<String, Any>> ?: emptyList()
                zones = zonesData.mapNotNull { zoneMap ->
                    try {
                        Zone(
                            id = zoneMap["id"] as String,
                            name = zoneMap["name"] as String,
                            description = zoneMap["description"] as? String,
                            order_index = (zoneMap["order_index"] as Number).toInt(),
                            created_at = (zoneMap["created_at"] as Number).toLong(),
                            updated_at = (zoneMap["updated_at"] as Number).toLong()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            // TODO: Error handling
        } finally {
            isLoading = false
        }
    }
    
    // Function to reload zones after operations
    val reloadZones = {
        coroutineScope.launch {
            isLoading = true
            try {
                val result = coordinator.processUserAction("get->zones")
                if (result.status == CommandStatus.SUCCESS && result.data != null) {
                    val zonesData = result.data["zones"] as? List<Map<String, Any>> ?: emptyList()
                    zones = zonesData.mapNotNull { zoneMap ->
                        try {
                            Zone(
                                id = zoneMap["id"] as String,
                                name = zoneMap["name"] as String,
                                description = zoneMap["description"] as? String,
                                order_index = (zoneMap["order_index"] as Number).toInt(),
                                created_at = (zoneMap["created_at"] as Number).toLong(),
                                updated_at = (zoneMap["updated_at"] as Number).toLong()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                // TODO: Error handling
            } finally {
                isLoading = false
            }
        }
    }
    
    // Show CreateZoneScreen in edit mode when zone config is requested
    configZone?.let { zone ->
        CreateZoneScreen(
            existingZone = zone,
            onCancel = {
                configZone = null
            },
            onUpdate = {
                configZone = null
                reloadZones()
            },
            onDelete = {
                configZone = null
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
                        reloadZones()
                    } catch (e: Exception) {
                    }
                }
            }
        )
    } else {
        // Main content using hybrid system: Compose layouts + UI.* components
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title - centré
            UI.Text(
                text = "Assistant",
                type = TextType.TITLE,
                fillMaxWidth = true,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Zones section
            UI.Text(
                text = "Mes Zones",
                type = TextType.SUBTITLE
            )
            
            if (zones.isEmpty()) {
                // Show placeholder when no zones
                UI.Text(
                    text = "Aucune zone créée",
                    type = TextType.BODY
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Test RowScope avec Box + weight
                    Box(
                        modifier = Modifier.weight(0.3f)
                    ) {
                        UI.Text(
                            text = "Test weight",
                            type = TextType.CAPTION
                        )
                    }
                    
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        onClick = { 
                            showCreateZone = true
                        }
                    ) {
                        UI.Text(
                            text = "Créer une zone",
                            type = TextType.LABEL
                        )
                    }
                }
            } else {
                // Show zones list using UI.ZoneCard
                zones.forEach { zone ->
                    UI.ZoneCard(
                        zone = zone,
                        onClick = {
                            selectedZone = zone
                        },
                        onLongClick = {
                            configZone = zone
                        }
                    )
                }
                
                // Add zone button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        onClick = { 
                            showCreateZone = true
                        }
                    ) {
                        UI.Text(
                            text = "Ajouter une zone",
                            type = TextType.LABEL
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Terminal section
            UI.Text(
                text = "Terminal",
                type = TextType.SUBTITLE
            )
            
            UI.Text(
                text = "Assistant prêt",
                type = TextType.BODY
            )
        }
    }
}