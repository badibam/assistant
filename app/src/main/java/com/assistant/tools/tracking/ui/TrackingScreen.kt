package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.ui.ThemeIconManager
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
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
    android.util.Log.d("CONFIGDEBUG", "TrackingScreen called with toolInstanceId: $toolInstanceId")
    
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    
    // State
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var historyRefreshTrigger by remember { mutableIntStateOf(0) }
    var configRefreshTrigger by remember { mutableIntStateOf(0) }
    
    // Load tool instance data
    LaunchedEffect(toolInstanceId, configRefreshTrigger) {
        try {
            val result = coordinator.processUserAction(
                "get->tool_instance",
                mapOf("tool_instance_id" to toolInstanceId)
            )
            
            when (result.status) {
                CommandStatus.SUCCESS -> {
                    val toolInstanceData = result.data?.get("tool_instance") as? Map<String, Any>
                    if (toolInstanceData != null) {
                        toolInstance = toolInstanceData
                    }
                }
                else -> {
                    errorMessage = result.error ?: "Impossible de charger l'outil"
                }
            }
        } catch (e: Exception) {
            errorMessage = "Erreur: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    // Parse configuration
    val config = remember(toolInstance) {
        val configJson = toolInstance?.get("config_json") as? String ?: "{}"
        try { 
            JSONObject(configJson) 
        } catch (e: Exception) { 
            JSONObject() 
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                UI.Text("Chargement...", TextType.BODY)
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
            val toolName = config.optString("name", "Suivi")
            val toolDescription = config.optString("description", "")
            val iconName = config.optString("icon_name", "activity")
            
            UI.PageHeader(
                title = toolName,
                subtitle = toolDescription.takeIf { it.isNotBlank() },
                icon = iconName,
                leftButton = ButtonAction.BACK,
                rightButton = ButtonAction.CONFIGURE,
                onLeftClick = onNavigateBack,
                onRightClick = onConfigureClick
            )
            
            // Input interface section
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UI.Text("Nouvelle entrée", TextType.SUBTITLE, fillMaxWidth = true, textAlign = TextAlign.Center)
                    
                    key(configRefreshTrigger) {
                        TrackingInputManager(
                            toolInstanceId = toolInstanceId,
                            config = config,
                            onEntrySaved = { 
                                historyRefreshTrigger++
                            },
                            onConfigChanged = {
                                configRefreshTrigger++
                            }
                        )
                    }
                }
            }
            
            // History section
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UI.Text("Historique", TextType.SUBTITLE, fillMaxWidth = true, textAlign = TextAlign.Center)
                    
                    TrackingHistory(
                        toolInstanceId = toolInstanceId,
                        trackingType = config.optString("type", "numeric"),
                        refreshTrigger = historyRefreshTrigger
                    )
                }
            }
            
        }
    }
}