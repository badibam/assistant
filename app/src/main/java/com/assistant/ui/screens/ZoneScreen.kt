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
import com.assistant.core.database.entities.ToolInstance
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
    
    // Debug message
    LaunchedEffect(Unit) {
        DebugManager.debug("🏷️ ZoneScreen chargé: ${zone.name}")
    }
    
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
                    text = "Informations de la zone",
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
                            text = "Nom",
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
                                text = "Description",
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
                    text = "Outils de cette zone",
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
                                text = "Aucun outil configuré dans cette zone",
                                type = TextType.BODY,
                                semantic = "empty-state"
                            )
                            
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            UI.Button(
                                type = ButtonType.PRIMARY,
                                semantic = "add-tool",
                                onClick = { 
                                    DebugManager.debugButtonClick("Ajouter un outil à ${zone.name}")
                                    // TODO: Implement tool creation/addition
                                }
                            ) {
                                UI.Text(
                                    text = "Ajouter un outil",
                                    type = TextType.LABEL,
                                    semantic = "button-label"
                                )
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
                                    text = formatToolType(toolInstance.tool_type),
                                    type = TextType.SUBTITLE,
                                    semantic = "tool-type"
                                )
                                
                                UI.Spacer(modifier = Modifier.height(4.dp))
                                
                                // Tool configuration preview (first 100 chars)
                                val configPreview = toolInstance.config_json.take(100) + 
                                    if (toolInstance.config_json.length > 100) "..." else ""
                                UI.Text(
                                    text = "Configuration: $configPreview",
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
                            // TODO: Implement tool creation/addition
                        }
                    ) {
                        UI.Text(
                            text = "Ajouter un outil",
                            type = TextType.LABEL,
                            semantic = "button-label"
                        )
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
                    text = "Retour",
                    type = TextType.LABEL,
                    semantic = "button-label"
                )
            }
        }
    }
}

/**
 * Format tool type for display
 */
private fun formatToolType(toolType: String): String {
    return when (toolType) {
        "tracking" -> "Suivi"
        "objective" -> "Objectif"
        "calculation" -> "Calcul"
        "statistic" -> "Statistique"
        "graphic" -> "Graphique"
        "list" -> "Liste"
        "journal" -> "Journal"
        "note" -> "Note"
        "message" -> "Message"
        "alert" -> "Alerte"
        "analysis" -> "Analyse"
        "auto_action" -> "Action automatique"
        else -> toolType.replaceFirstChar { it.uppercase() }
    }
}