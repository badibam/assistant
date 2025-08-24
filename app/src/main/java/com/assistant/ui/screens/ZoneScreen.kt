package com.assistant.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.themes.base.ThemeIconManager
import com.assistant.ui.components.ToolInstanceDisplayComponent
import com.assistant.ui.components.ToolIcon
import com.assistant.ui.components.DisplayMode
import org.json.JSONObject
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.commands.CommandStatus
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
    
    // Load tool instances via command pattern
    var toolInstances by remember { mutableStateOf<List<ToolInstance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load tool instances on first composition and when zone changes
    LaunchedEffect(zone.id) {
        isLoading = true
        try {
            val result = coordinator.processUserAction("get->tool_instances", mapOf("zone_id" to zone.id))
            if (result.status == CommandStatus.SUCCESS && result.data != null) {
                val instancesData = result.data["tool_instances"] as? List<Map<String, Any>> ?: emptyList()
                toolInstances = instancesData.mapNotNull { instanceMap ->
                    try {
                        ToolInstance(
                            id = instanceMap["id"] as String,
                            zone_id = instanceMap["zone_id"] as String,
                            tool_type = instanceMap["tool_type"] as String,
                            config_json = instanceMap["config_json"] as String,
                            config_metadata_json = instanceMap["config_metadata_json"] as String,
                            order_index = (instanceMap["order_index"] as Number).toInt(),
                            created_at = (instanceMap["created_at"] as Number).toLong(),
                            updated_at = (instanceMap["updated_at"] as Number).toLong()
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
    
    // Function to reload tool instances after operations
    val reloadToolInstances = {
        coroutineScope.launch {
            isLoading = true
            try {
                val result = coordinator.processUserAction("get->tool_instances", mapOf("zone_id" to zone.id))
                if (result.status == CommandStatus.SUCCESS && result.data != null) {
                    val instancesData = result.data["tool_instances"] as? List<Map<String, Any>> ?: emptyList()
                    toolInstances = instancesData.mapNotNull { instanceMap ->
                        try {
                            ToolInstance(
                                id = instanceMap["id"] as String,
                                zone_id = instanceMap["zone_id"] as String,
                                tool_type = instanceMap["tool_type"] as String,
                                config_json = instanceMap["config_json"] as String,
                                config_metadata_json = instanceMap["config_metadata_json"] as String,
                                order_index = (instanceMap["order_index"] as Number).toInt(),
                                created_at = (instanceMap["created_at"] as Number).toLong(),
                                updated_at = (instanceMap["updated_at"] as Number).toLong()
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
    
    // State for showing/hiding available tools list
    var showAvailableTools by remember { mutableStateOf(false) }
    
    // State for tool configuration screen
    var showingConfigFor by remember { mutableStateOf<String?>(null) }
    var editingToolInstance by remember { mutableStateOf<ToolInstance?>(null) }
    
    // State for tool dedicated screen
    var showingToolScreen by remember { mutableStateOf<ToolInstance?>(null) }
    
    // Show tool usage screen via discovery (before main UI)
    showingToolScreen?.let { toolInstance ->
        val toolType = ToolTypeManager.getToolType(toolInstance.tool_type)
        toolType?.getUsageScreen(
            toolInstanceId = toolInstance.id,
            configJson = toolInstance.config_json,
            onNavigateBack = { showingToolScreen = null },
            onLongClick = { 
                editingToolInstance = toolInstance
                showingToolScreen = null
            }
        )
        return // Exit ZoneScreen composition when showing tool screen
    }
    
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
                    reloadToolInstances()
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
                    reloadToolInstances()
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
                    reloadToolInstances()
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
            
            // À partir de ligne 3 : Les outils avec système de grille
            BoxWithConstraints {
                val availableWidth = maxWidth - 32.dp // Moins padding container
                val gridUnit = availableWidth / 4 // Base : 1/4 d'écran
                val currentTheme = "default" // TODO: val currentTheme = ThemeManager.getCurrentThemeName()
                
                // Grid layout pour les outils
                UI.Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    toolInstances.forEach { toolInstance ->
                        // Récupérer le mode d'affichage depuis la config
                        val config = try {
                            JSONObject(toolInstance.config_json)
                        } catch (e: Exception) {
                            JSONObject()
                        }
                        
                        val displayModeStr = config.optString("display_mode", "Icône")
                        val displayMode = when (displayModeStr) {
                            "Icône" -> DisplayMode.ICON
                            "Minimal" -> DisplayMode.MINIMAL
                            "Ligne" -> DisplayMode.LINE
                            "Condensé" -> DisplayMode.CONDENSED
                            "Étendu" -> DisplayMode.EXTENDED
                            "Carré" -> DisplayMode.SQUARE
                            "Complet" -> DisplayMode.FULL
                            else -> DisplayMode.ICON
                        }
                        
                        // Calculer la taille selon le mode
                        val toolSize = when (displayMode) {
                            DisplayMode.ICON -> DpSize(gridUnit, gridUnit) // 1/4 × 1/4
                            DisplayMode.MINIMAL -> DpSize(gridUnit * 2, gridUnit) // 1/2 × 1/4
                            DisplayMode.LINE -> DpSize(availableWidth, gridUnit) // 1 × 1/4
                            DisplayMode.CONDENSED -> DpSize(gridUnit * 2, gridUnit * 2) // 1/2 × 1/2
                            DisplayMode.EXTENDED -> DpSize(availableWidth, gridUnit * 2) // 1 × 1/2
                            DisplayMode.SQUARE -> DpSize(availableWidth, gridUnit * 4) // 1 × 1
                            DisplayMode.FULL -> DpSize(availableWidth, gridUnit * 6) // 1 × ∞ (pour test, 6 unités)
                        }
                        
                        // Récupérer le nom de l'outil et l'icône depuis la config
                        val toolName = config.optString("name").ifEmpty { 
                            ToolTypeManager.getToolTypeName(toolInstance.tool_type)
                        }
                        
                        // Récupérer le nom de l'icône (config ou défaut du ToolType)
                        val iconName = config.optString("icon_name").ifEmpty {
                            ToolTypeManager.getToolType(toolInstance.tool_type)?.getDefaultIconName() ?: "activity"
                        }
                        
                        // Récupérer l'icône depuis le thème
                        val iconResource = try {
                            ThemeIconManager.getIconResource(context, currentTheme, iconName)
                        } catch (e: IllegalArgumentException) {
                            // Log error et utiliser l'icône par défaut du tool type
                            println("Icon not found: $iconName for theme $currentTheme, using default")
                            ThemeIconManager.getIconResource(context, currentTheme, "activity")
                        }
                        
                        ToolInstanceDisplayComponent(
                            displayMode = displayMode,
                            size = toolSize,
                            icon = {
                                ToolIcon(
                                    iconResource = iconResource,
                                    size = 32.dp
                                )
                            },
                            title = toolName,
                            onClick = {
                                // Navigate to tool dedicated screen via discovery
                                showingToolScreen = toolInstance
                            },
                            onLongClick = {
                                editingToolInstance = toolInstance
                            }
                        )
                    }
                }
            }
        } // End of normal zone screen UI.Screen
    } // End of run block
}

