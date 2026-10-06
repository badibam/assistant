package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Dedicated page for tracking tool instance
 * Displays tool info, input interface, and data history
 */
@Composable
fun TrackingScreen(
    toolInstanceId: String,
    zoneName: String,
    onNavigateBack: () -> Unit,
    onConfigureClick: () -> Unit = {}
) {
    LogManager.tracking("TrackingScreen called with toolInstanceId: $toolInstanceId")
    
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    
    // State
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var historyRefreshTrigger by remember { mutableIntStateOf(0) }
    var configRefreshTrigger by remember { mutableIntStateOf(0) }
    
    // Load tool instance data
    LaunchedEffect(toolInstanceId, configRefreshTrigger) {
        coordinator.executeWithLoading(
            operation = "tools.get",
            params = mapOf("tool_instance_id" to toolInstanceId),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            toolInstance = result.mapSingleData("tool_instance") { map -> map }
        }
    }

    // Observe data changes and refresh history automatically
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> {
                    // Only refresh if the change affects this tool instance
                    if (event.toolInstanceId == toolInstanceId) {
                        historyRefreshTrigger++
                    }
                }
                // A tool of this zone changed its config, perhaps this one from an entry that
                // brought a new unit or option: the config is read again without the loading
                // state, which would clear the screen and what it holds
                is DataChangeEvent.ToolsChanged -> {
                    if (event.zoneId == toolInstance?.get("zone_id")) {
                        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
                        if (result.isSuccess) toolInstance = result.mapSingleData("tool_instance") { map -> map }
                    }
                }
                else -> {} // Ignore other events
            }
        }
    }

    // Parse configuration
    val config = remember(toolInstance) {
        JsonUtils.toJSONObject(toolInstance?.get("config") as? Map<String, Any?> ?: emptyMap())
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                UI.CenteredText(s.shared("tools_loading"), TextType.BODY)
            }
        } else if (errorMessage != null) {
            UI.Card(type = CardType.DEFAULT) {
                UI.Text(errorMessage!!, TextType.BODY)
            }
            
            UI.ActionButton(
                action = ButtonAction.BACK,
                onClick = onNavigateBack
            )
        } else if (toolInstance != null) {
            // Tool header with UI.PageHeader
            val settings = com.assistant.core.tools.ToolConfigSettings.read(com.assistant.tools.tracking.TrackingToolType, config, context)
            val toolName = settings.string("name")!!
            val toolDescription = settings.string("description").orEmpty()
            val iconName = settings.string("icon_name")!!
            
            UI.PageHeader(
                title = toolName,
                subtitle = toolDescription.takeIf { it.isNotBlank() },
                icon = iconName,
                iconColor = com.assistant.core.themes.IconColor.of(settings.string(com.assistant.core.themes.IconColor.KEY)),
                leftButton = ButtonAction.BACK,
                rightButton = ButtonAction.CONFIGURE,
                onLeftClick = onNavigateBack,
                onRightClick = onConfigureClick
            )
            
            // Input interface section
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(UI.Space.L),
                    verticalArrangement = Arrangement.spacedBy(UI.Space.L)
                ) {
                    UI.Text(s.tool("usage_section_new_entry"), TextType.SUBTITLE, fillMaxWidth = true, textAlign = TextAlign.Center)
                    
                    key(configRefreshTrigger) {
                        TrackingQuickEntry(
                            toolInstanceId = toolInstanceId,
                            config = config,
                            refreshTrigger = historyRefreshTrigger,
                            onConfigChanged = { configRefreshTrigger++ }
                        )
                    }
                }
            }

            // History section
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(UI.Space.L),
                    verticalArrangement = Arrangement.spacedBy(UI.Space.L)
                ) {
                    UI.Text(s.tool("usage_section_history"), TextType.SUBTITLE, fillMaxWidth = true, textAlign = TextAlign.Center)
                    
                    TrackingHistory(
                        toolInstanceId = toolInstanceId,
                        refreshTrigger = historyRefreshTrigger
                    )
                }
            }
            
        }
    }
}