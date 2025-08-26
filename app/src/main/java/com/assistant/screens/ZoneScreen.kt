package com.assistant.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.launch

/**
 * Zone detail screen - shows zone information and its tools
 * Migrated to use new UI.* system
 */
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
    
    UI.Column {
        // Header with back button, zone title and add tool button
        UI.Row(spacing = 12.dp) {
            UI.Button(
                type = ButtonType.BACK,
                onClick = onBack
            ) {
                UI.Text(
                    text = "←",
                    type = TextType.LABEL
                )
            }
            
            // Zone title
            UI.Column(spacing = 4.dp) {
                UI.Text(
                    text = zone.name,
                    type = TextType.TITLE
                )
                
                zone.description?.let { description ->
                    UI.Text(
                        text = description,
                        type = TextType.BODY
                    )
                }
            }
            
            // Add tool button
            UI.Button(
                type = ButtonType.ADD,
                onClick = {
                    // TODO: Show available tools to add
                }
            ) {
                UI.Text(
                    text = "+",
                    type = TextType.LABEL
                )
            }
        }
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            UI.Text(
                text = "Chargement des outils...",
                type = TextType.BODY
            )
        } else if (toolInstances.isEmpty()) {
            // No tools placeholder
            UI.Text(
                text = "Aucun outil dans cette zone",
                type = TextType.BODY
            )
        } else {
            // Show tools using UI.ToolCard
            toolInstances.forEach { toolInstance ->
                UI.ToolCard(
                    tool = toolInstance,
                    displayMode = DisplayMode.LINE,
                    onClick = {
                        // TODO: Open tool screen
                    },
                    onLongClick = {
                        // TODO: Open tool configuration
                    }
                )
                
                UI.Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}