package com.assistant.core.ui.Screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.tools.ToolTypeManager
import kotlinx.coroutines.launch

/**
 * Zone detail screen - shows zone information and its tools
 * Uses hybrid system: Compose layouts + UI.* visual components
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
    
    // State for showing/hiding available tools list
    var showAvailableTools by remember { mutableStateOf(false) }
    
    // State for tool configuration screen  
    var showingConfigFor by remember { mutableStateOf<String?>(null) }
    var editingTool by remember { mutableStateOf<ToolInstance?>(null) }
    
    // State for tool usage screen
    var selectedToolInstance by remember { mutableStateOf<ToolInstance?>(null) }
    
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
    
    // Configuration callbacks
    val onSaveConfig = { config: String ->
        editingTool?.let { tool ->
            // Update existing tool
            coroutineScope.launch {
                try {
                    val configMetadata = ToolTypeManager.getToolType(tool.tool_type)?.getConfigSchema() ?: "{}"
                    val result = coordinator.processUserAction("update->tool_instance", mapOf(
                        "tool_instance_id" to tool.id,
                        "config_json" to config,
                        "config_metadata_json" to configMetadata
                    ))
                    editingTool = null
                    showingConfigFor = null
                    reloadToolInstances()
                } catch (e: Exception) {
                    // TODO: Error handling
                }
            }
        } ?: showingConfigFor?.let { toolTypeId ->
            // Create new tool
            coroutineScope.launch {
                try {
                    val configMetadata = ToolTypeManager.getToolType(toolTypeId)?.getConfigSchema() ?: "{}"
                    val result = coordinator.processUserAction("create->tool_instance", mapOf(
                        "zone_id" to zone.id,
                        "tool_type" to toolTypeId,
                        "config_json" to config,
                        "config_metadata_json" to configMetadata
                    ))
                    showingConfigFor = null
                    reloadToolInstances()
                } catch (e: Exception) {
                    // TODO: Error handling
                }
            }
        }
    }
    
    val onCancelConfig = {
        showingConfigFor = null
        editingTool = null
    }
    
    // Show configuration screen if requested
    showingConfigFor?.let { toolTypeId ->
        ToolTypeManager.getToolType(toolTypeId)?.getConfigScreen(
            zoneId = zone.id,
            onSave = onSaveConfig,
            onCancel = onCancelConfig,
            existingConfig = editingTool?.config_json,
            existingToolId = editingTool?.id,
            onDelete = editingTool?.let { tool ->
                {
                    coroutineScope.launch {
                        try {
                            coordinator.processUserAction("delete->tool_instance", mapOf("tool_instance_id" to tool.id))
                            editingTool = null
                            showingConfigFor = null
                            reloadToolInstances()
                        } catch (e: Exception) {
                            // TODO: Error handling
                        }
                    }
                }
            }
        )
        return // Exit ZoneScreen composition when showing config
    }
    
    // Show tool usage screen if selected
    selectedToolInstance?.let { toolInstance ->
        ToolTypeManager.getToolType(toolInstance.tool_type)?.getUsageScreen(
            toolInstanceId = toolInstance.id,
            configJson = toolInstance.config_json,
            zoneName = zone.name,
            onNavigateBack = {
                selectedToolInstance = null
            },
            onLongClick = {
                editingTool = toolInstance
                showingConfigFor = toolInstance.tool_type
            }
        )
        return // Exit ZoneScreen composition when showing usage screen
    }
    
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        // Header with back button, zone title and add tool button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Button(
                type = ButtonType.DEFAULT,
                onClick = onBack
            ) {
                UI.Text(
                    text = "←",
                    type = TextType.LABEL
                )
            }
            
            // Zone title
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                type = ButtonType.PRIMARY,
                onClick = {
                    showAvailableTools = !showAvailableTools
                }
            ) {
                UI.Text(
                    text = "+",
                    type = TextType.LABEL
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Available tools list (shown conditionally)
        if (showAvailableTools) {
            UI.Card(
                type = CardType.DEFAULT
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.Text(
                        text = "Types d'outils disponibles",
                        type = TextType.SUBTITLE
                    )
                    
                    // List all available tool types
                    ToolTypeManager.getAllToolTypes().forEach { (toolTypeId, toolType) ->
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            onClick = {
                                showingConfigFor = toolTypeId
                                showAvailableTools = false
                            }
                        ) {
                            UI.Text(
                                text = toolType.getDisplayName(),
                                type = TextType.LABEL
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
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
                    context = context,
                    onClick = {
                        selectedToolInstance = toolInstance
                    },
                    onLongClick = {
                        editingTool = toolInstance
                        showingConfigFor = toolInstance.tool_type
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}