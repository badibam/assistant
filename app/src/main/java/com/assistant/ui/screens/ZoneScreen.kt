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
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.R
import kotlinx.coroutines.launch

/**
 * Zone detail screen - shows zone information and its tools
 * Follows established UI patterns from MainScreen and CreateZoneScreen
 */
@Composable
fun ZoneScreen(
    zone: Zone,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Observe tool instances for this zone in real-time
    val database = remember { AppDatabase.getDatabase(context) }
    val toolInstances by database.toolInstanceDao().getToolInstancesByZone(zone.id)
        .collectAsState(initial = emptyList())
    
    // State for showing/hiding available tools list
    var showAvailableTools by remember { mutableStateOf(false) }
    
    // State for tool configuration screen
    var showingConfigFor by remember { mutableStateOf<String?>(null) }
    
    // Configuration callbacks (shared logic for all tool types)
    val onSaveConfig = { config: String ->
        showingConfigFor?.let { toolTypeId ->
            coroutineScope.launch {
                try {
                    val toolInstance = ToolInstance(
                        zone_id = zone.id,
                        tool_type = toolTypeId,
                        config_json = config,
                        config_metadata_json = ToolTypeManager.getToolType(toolTypeId)?.getConfigSchema() ?: "{}"
                    )
                    database.toolInstanceDao().insertToolInstance(toolInstance)
                    DebugManager.debug("✅ Outil $toolTypeId créé dans zone ${zone.name}")
                    showingConfigFor = null
                } catch (e: Exception) {
                    DebugManager.debug("❌ Erreur création outil: ${e.message}")
                }
            }
        }
    }
    
    val onCancelConfig = {
        DebugManager.debug("❌ Configuration annulée")
        showingConfigFor = null
    }
    
    // Debug message
    LaunchedEffect(Unit) {
        DebugManager.debug("🏷️ ZoneScreen chargé: ${zone.name}")
    }
    
    // Tool type manager is automatically initialized through annotation processing
    
    // Show configuration screen if requested, otherwise show zone screen
    showingConfigFor?.let { toolTypeId ->
        ToolTypeManager.getToolType(toolTypeId)?.getConfigScreen(
            zoneId = zone.id,
            onSave = onSaveConfig,
            onCancel = onCancelConfig
        )
    } ?: run {
        // Normal zone screen
        UI.Screen(type = ScreenType.MAIN) {
            // Top bar with back navigation
            UI.TopBar(
                type = TopBarType.DEFAULT,
                title = zone.name
            )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Zone information section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(
                    text = stringResource(R.string.zone_info_section),
                    type = TextType.TITLE,
                    semantic = "section-title"
                )
                
                // Zone name
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "zone-info",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = stringResource(R.string.zone_info_name),
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(4.dp))
                        UI.Text(
                            text = zone.name,
                            type = TextType.BODY,
                            semantic = "zone-name"
                        )
                    }
                }
                
                // Zone description (if exists)
                zone.description?.let { description ->
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "zone-description-card",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Column {
                            UI.Text(
                                text = stringResource(R.string.zone_info_description),
                                type = TextType.SUBTITLE,
                                semantic = "field-label"
                            )
                            UI.Spacer(modifier = Modifier.height(4.dp))
                            UI.Text(
                                text = description,
                                type = TextType.BODY,
                                semantic = "zone-description"
                            )
                        }
                    }
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(24.dp))
        
        // Tools section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(
                    text = stringResource(R.string.zone_tools_section),
                    type = TextType.TITLE,
                    semantic = "section-title"
                )
                
                if (toolInstances.isEmpty()) {
                    // Show placeholder when no tools
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "placeholder",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Column {
                            UI.Text(
                                text = stringResource(R.string.no_tools_configured),
                                type = TextType.BODY,
                                semantic = "empty-state"
                            )
                            
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            UI.Button(
                                type = ButtonType.PRIMARY,
                                semantic = "add-tool",
                                onClick = { 
                                    DebugManager.debugButtonClick("Ajouter un outil à ${zone.name}")
                                    showAvailableTools = !showAvailableTools
                                }
                            ) {
                                UI.Text(
                                    text = stringResource(R.string.add_tool),
                                    type = TextType.LABEL,
                                    semantic = "button-label"
                                )
                            }
                            
                            // Available tools list (shown conditionally)
                            if (showAvailableTools) {
                                UI.Spacer(modifier = Modifier.height(12.dp))
                                
                                UI.Card(
                                    type = CardType.SYSTEM,
                                    semantic = "available-tools-list",
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UI.Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // List all available tool types
                                        ToolTypeManager.getAllToolTypes().forEach { (toolTypeId, toolType) ->
                                            UI.Button(
                                                type = ButtonType.GHOST,
                                                semantic = "select-tool-$toolTypeId",
                                                onClick = {
                                                    DebugManager.debugButtonClick("Sélectionner outil: ${toolType.getDisplayName()}")
                                                    showingConfigFor = toolTypeId
                                                    showAvailableTools = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                UI.Card(
                                                    type = CardType.ZONE,
                                                    semantic = "available-tool-$toolTypeId",
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    UI.Text(
                                                        text = toolType.getDisplayName(),
                                                        type = TextType.BODY,
                                                        semantic = "tool-name"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show tools list
                    toolInstances.forEach { toolInstance ->
                        UI.Card(
                            type = CardType.ZONE,
                            semantic = "tool-${toolInstance.id}",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            UI.Column {
                                UI.Text(
                                    text = ToolTypeManager.getToolTypeName(toolInstance.tool_type),
                                    type = TextType.SUBTITLE,
                                    semantic = "tool-type"
                                )
                                
                                UI.Spacer(modifier = Modifier.height(4.dp))
                                
                                // Tool configuration preview (first 100 chars)
                                val configPreview = toolInstance.config_json.take(100) + 
                                    if (toolInstance.config_json.length > 100) "..." else ""
                                UI.Text(
                                    text = stringResource(R.string.tool_config_preview, configPreview),
                                    type = TextType.CAPTION,
                                    semantic = "tool-config"
                                )
                            }
                        }
                    }
                    
                    UI.Spacer(modifier = Modifier.height(8.dp))
                    
                    // Add tool button
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        semantic = "add-tool",
                        onClick = { 
                            DebugManager.debugButtonClick("Ajouter un outil à ${zone.name}")
                            showAvailableTools = !showAvailableTools
                        }
                    ) {
                        UI.Text(
                            text = stringResource(R.string.add_tool),
                            type = TextType.LABEL,
                            semantic = "button-label"
                        )
                    }
                    
                    // Available tools list (shown conditionally)
                    if (showAvailableTools) {
                        UI.Spacer(modifier = Modifier.height(12.dp))
                        
                        UI.Card(
                            type = CardType.SYSTEM,
                            semantic = "available-tools-list",
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UI.Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // List all available tool types
                                ToolTypeManager.getAllToolTypes().forEach { (toolTypeId, toolType) ->
                                    UI.Button(
                                        type = ButtonType.GHOST,
                                        semantic = "select-tool-$toolTypeId",
                                        onClick = {
                                            DebugManager.debugButtonClick("Sélectionner outil: ${toolType.getDisplayName()}")
                                            showingConfigFor = toolTypeId
                                            showAvailableTools = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        UI.Card(
                                            type = CardType.ZONE,
                                            semantic = "available-tool-$toolTypeId",
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            UI.Text(
                                                text = toolType.getDisplayName(),
                                                type = TextType.BODY,
                                                semantic = "tool-name"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(32.dp))
        
        // Action buttons
        UI.Container(type = ContainerType.FLOATING) {
            UI.Button(
                type = ButtonType.SECONDARY,
                semantic = "back-button",
                onClick = {
                    DebugManager.debugButtonClick("Retour depuis zone ${zone.name}")
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Text(
                    text = stringResource(R.string.back),
                    type = TextType.LABEL,
                    semantic = "button-label"
                )
            }
        }
    } // End of normal zone screen UI.Screen
    } // End of run block
}

