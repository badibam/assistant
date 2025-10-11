package com.assistant.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.mapData
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
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
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load tool instances via command pattern
    var toolInstances by remember { mutableStateOf<List<ToolInstance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // State for showing/hiding available tools list - persiste orientation changes
    var showAvailableTools by rememberSaveable { mutableStateOf(false) }
    
    // State for tool configuration screen - persiste orientation changes  
    var showingConfigFor by rememberSaveable { mutableStateOf<String?>(null) }
    var editingToolId by rememberSaveable { mutableStateOf<String?>(null) }
    
    // State for tool usage screen - persiste orientation changes
    var selectedToolInstanceId by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Derived states from IDs (recomputed after orientation change)
    val editingTool = toolInstances.find { it.id == editingToolId }
    val selectedToolInstance = toolInstances.find { it.id == selectedToolInstanceId }
    
    // Load tool instances on first composition and when zone changes
    LaunchedEffect(zone.id) {
        coordinator.executeWithLoading(
            operation = "tools.list",
            params = mapOf("zone_id" to zone.id),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            toolInstances = result.mapData("tool_instances") { map ->
                ToolInstance(
                    id = map["id"] as String,
                    zone_id = map["zone_id"] as String,
                    tool_type = map["tool_type"] as String,
                    config_json = map["config_json"] as String,
                    order_index = (map["order_index"] as Number).toInt(),
                    created_at = (map["created_at"] as Number).toLong(),
                    updated_at = (map["updated_at"] as Number).toLong()
                )
            }
        }
    }

    // Observe data changes and reload tools automatically for this zone
    LaunchedEffect(zone.id) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolsChanged -> {
                    // Only reload if the change affects this zone
                    if (event.zoneId == zone.id) {
                        coordinator.executeWithLoading(
                            operation = "tools.list",
                            params = mapOf("zone_id" to zone.id),
                            onLoading = { isLoading = it },
                            onError = { error -> errorMessage = error }
                        )?.let { result ->
                            toolInstances = result.mapData("tool_instances") { map ->
                                ToolInstance(
                                    id = map["id"] as String,
                                    zone_id = map["zone_id"] as String,
                                    tool_type = map["tool_type"] as String,
                                    config_json = map["config_json"] as String,
                                    order_index = (map["order_index"] as Number).toInt(),
                                    created_at = (map["created_at"] as Number).toLong(),
                                    updated_at = (map["updated_at"] as Number).toLong()
                                )
                            }
                        }
                    }
                }
                else -> {} // Ignore other events
            }
        }
    }

    // Function to reload tool instances after operations
    val reloadToolInstances = {
        coroutineScope.launch {
            coordinator.executeWithLoading(
                operation = "tools.list",
                params = mapOf("zone_id" to zone.id),
                onLoading = { isLoading = it },
                onError = { error -> errorMessage = error }
            )?.let { result ->
                toolInstances = result.mapData("tool_instances") { map ->
                    ToolInstance(
                        id = map["id"] as String,
                        zone_id = map["zone_id"] as String,
                        tool_type = map["tool_type"] as String,
                        config_json = map["config_json"] as String,
                        order_index = (map["order_index"] as Number).toInt(),
                        created_at = (map["created_at"] as Number).toLong(),
                        updated_at = (map["updated_at"] as Number).toLong()
                    )
                }
            }
        }
    }
    
    // Configuration callbacks
    val onSaveConfig = { config: String ->
        editingTool?.let { tool ->
            // Update existing tool
            coroutineScope.launch {
                try {
                    val result = coordinator.processUserAction("tools.update", mapOf(
                        "tool_instance_id" to tool.id,
                        "config_json" to config
                    ))
                    editingToolId = null
                    showingConfigFor = null
                    reloadToolInstances()
                } catch (e: Exception) {
                    errorMessage = "Update tool error: ${e.message}"
                }
            }
        } ?: showingConfigFor?.let { toolTypeId ->
            // Create new tool
            coroutineScope.launch {
                try {
                    val result = coordinator.processUserAction("tools.create", mapOf(
                        "zone_id" to zone.id,
                        "tool_type" to toolTypeId,
                        "config_json" to config
                    ))
                    showingConfigFor = null
                    reloadToolInstances()
                } catch (e: Exception) {
                    errorMessage = "Create tool error: ${e.message}"
                }
            }
        }
    }
    
    val onCancelConfig = {
        showingConfigFor = null
        editingToolId = null
    }
    
    // Show configuration screen if requested
    showingConfigFor?.let { toolTypeId ->
        ToolTypeManager.getToolType(toolTypeId)?.getConfigScreen(
            zoneId = zone.id,
            onSave = onSaveConfig,
            onCancel = onCancelConfig,
            existingToolId = editingTool?.id,
            onDelete = editingTool?.let { tool ->
                {
                    coroutineScope.launch {
                        try {
                            coordinator.processUserAction("tools.delete", mapOf("tool_instance_id" to tool.id))
                            editingToolId = null
                            showingConfigFor = null
                            reloadToolInstances()
                        } catch (e: Exception) {
                            errorMessage = "Delete tool error: ${e.message}"
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
                selectedToolInstanceId = null
            },
            onLongClick = {
                editingToolId = toolInstance.id
                showingConfigFor = toolInstance.tool_type
            }
        )
        return // Exit ZoneScreen composition when showing usage screen
    }
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back button, zone title and add tool button
        UI.PageHeader(
            title = zone.name,
            subtitle = zone.description?.takeIf { it.isNotBlank() },
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.ADD,
            onLeftClick = onBack,
            onRightClick = { showAvailableTools = !showAvailableTools }
        )
        
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
                        text = s.shared("message_available_tool_types"),
                        type = TextType.BODY,
                        fillMaxWidth = true,
                        textAlign = TextAlign.Center

                    )
                    
                    // List all available tool types - centered
                    ToolTypeManager.getAllToolTypes().forEach { (toolTypeId, toolType) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            UI.Button(
                                type = ButtonType.PRIMARY,
                                onClick = {
                                    showingConfigFor = toolTypeId
                                    showAvailableTools = false
                                }
                            ) {
                                UI.Text(
                                    text = toolType.getDisplayName(context),
                                    type = TextType.LABEL
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (isLoading) {
            UI.Text(
                text = s.shared("message_loading_tools"),
                type = TextType.BODY
            )
        } else if (toolInstances.isEmpty()) {
            // No tools placeholder
            UI.Text(
                text = s.shared("message_no_tools_in_zone"),
                type = TextType.BODY,
                fillMaxWidth = true,
                textAlign = TextAlign.Center
            )
        } else {
            // Show tools using UI.ToolCard
            toolInstances.forEach { toolInstance ->
                UI.ToolCard(
                    tool = toolInstance,
                    displayMode = DisplayMode.LINE,
                    context = context,
                    onClick = {
                        selectedToolInstanceId = toolInstance.id
                    },
                    onLongClick = {
                        editingToolId = toolInstance.id
                        showingConfigFor = toolInstance.tool_type
                    }
                )
            }
        }
    }
    
    // Error handling with Toast
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}