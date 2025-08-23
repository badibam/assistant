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
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.R
import kotlinx.coroutines.launch

/**
 * Zone detail screen - shows zone information and its tools
 * Follows established UI patterns from MainScreen and CreateZoneScreen
 */
@OptIn(ExperimentalFoundationApi::class)
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
    var editingToolInstance by remember { mutableStateOf<ToolInstance?>(null) }
    
    // Configuration callbacks (shared logic for all tool types)
    val onSaveConfig = { config: String ->
        editingToolInstance?.let { existingTool ->
            // Update existing tool
            coroutineScope.launch {
                try {
                    val configMetadata = ToolTypeManager.getToolType(existingTool.tool_type)?.getConfigSchema() ?: "{}"
                    coordinator.processUserAction("update->tool_instance", mapOf(
                        "tool_instance_id" to existingTool.id,
                        "config_json" to config,
                        "config_metadata_json" to configMetadata
                    ))
                    editingToolInstance = null
                } catch (e: Exception) {
                    // TODO: Gestion d'erreur
                }
            }
        } ?: showingConfigFor?.let { toolTypeId ->
            // Create new tool
            coroutineScope.launch {
                try {
                    val configMetadata = ToolTypeManager.getToolType(toolTypeId)?.getConfigSchema() ?: "{}"
                    coordinator.processUserAction("create->tool_instance", mapOf(
                        "zone_id" to zone.id,
                        "tool_type" to toolTypeId,
                        "config_json" to config,
                        "config_metadata_json" to configMetadata
                    ))
                    showingConfigFor = null
                } catch (e: Exception) {
                    // TODO: Gestion d'erreur
                }
            }
        }
    }
    
    val onCancelConfig = {
        showingConfigFor = null
        editingToolInstance = null
    }
    
    val onDeleteConfig = {
        editingToolInstance?.let { toolToDelete ->
            coroutineScope.launch {
                try {
                    coordinator.processUserAction("delete->tool_instance", mapOf(
                        "tool_instance_id" to toolToDelete.id
                    ))
                    editingToolInstance = null
                } catch (e: Exception) {
                    // TODO: Gestion d'erreur
                }
            }
        }
    }
    
    
    // Tool type manager is automatically initialized through annotation processing
    
    // Show configuration screen if requested, otherwise show zone screen
    editingToolInstance?.let { toolInstance ->
        // Edit existing tool
        ToolTypeManager.getToolType(toolInstance.tool_type)?.getConfigScreen(
            zoneId = zone.id,
            onSave = onSaveConfig,
            onCancel = onCancelConfig,
            existingConfig = toolInstance.config_json,
            existingToolId = toolInstance.id,
            onDelete = onDeleteConfig
        )
    } ?: showingConfigFor?.let { toolTypeId ->
        // Create new tool
        ToolTypeManager.getToolType(toolTypeId)?.getConfigScreen(
            zoneId = zone.id,
            onSave = onSaveConfig,
            onCancel = onCancelConfig,
            existingConfig = null,
            existingToolId = null,
            onDelete = null
        )
    } ?: run {
        // Normal zone screen
        UI.Screen(type = ScreenType.MAIN) {
            // Ligne 1 : Bouton retour + Titre
            UI.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Colonne 1 : Bouton retour
                UI.Button(
                    type = ButtonType.GHOST,
                    semantic = "back-button",
                    onClick = { onBack() }
                ) {
                    UI.Text(
                        text = "←",
                        type = TextType.LABEL,
                        semantic = "back-icon"
                    )
                }
                
                // Colonne 2 : Titre de la zone (espace restant)
                UI.Text(
                    text = zone.name,
                    type = TextType.TITLE,
                    semantic = "zone-title",
                    modifier = Modifier.weight(1f)
                )
            }
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            // Ligne 2 : Sous-titre + bouton ajout outil
            UI.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sous-titre (prend l'espace restant)
                zone.description?.let { description ->
                    UI.Text(
                        text = description,
                        type = TextType.BODY,
                        semantic = "zone-description",
                        modifier = Modifier.weight(1f)
                    )
                } ?: UI.Spacer(modifier = Modifier.weight(1f))
                
                // Bouton ajout outil (taille minimale)
                UI.Button(
                    type = ButtonType.GHOST,
                    semantic = "add-tool-button",
                    onClick = { showAvailableTools = !showAvailableTools }
                ) {
                    UI.Text(
                        text = "+",
                        type = TextType.LABEL,
                        semantic = "add-icon"
                    )
                }
            }
            
            // Available tools list (shown conditionally juste après ligne 2)
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
            
            UI.Spacer(modifier = Modifier.height(16.dp))
            
            // À partir de ligne 3 : Les outils directement
            toolInstances.forEach { toolInstance ->
                UI.Card(
                    type = CardType.ZONE,
                    semantic = "tool-${toolInstance.id}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            onClick = {
                                // TODO: Naviguer vers l'écran d'utilisation de l'outil
                            },
                            onLongClick = {
                                editingToolInstance = toolInstance
                            }
                        )
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
        } // End of normal zone screen UI.Screen
    } // End of run block
}

