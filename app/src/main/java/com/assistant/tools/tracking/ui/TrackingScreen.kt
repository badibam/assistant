package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.themes.base.ThemeIconManager
import com.assistant.ui.components.ToolIcon
import com.assistant.tools.tracking.ui.inputs.TrackingInput
import org.json.JSONObject

/**
 * Dedicated page for tracking tool instance
 * Displays tool info, input interface, and data history
 */
@Composable
fun TrackingScreen(
    toolInstanceId: String,
    configJson: String,
    onNavigateBack: () -> Unit,
    onLongClick: () -> Unit // For configuration access
) {
    val context = LocalContext.current
    
    // Parse configuration
    val config = remember(configJson) { 
        try { JSONObject(configJson) } catch (e: Exception) { JSONObject() }
    }
    
    // Extract tool info
    val toolName = config.optString("name", "Suivi")
    val toolDescription = config.optString("description", "")
    val iconName = config.optString("icon_name", "activity")
    val trackingType = config.optString("type", "numeric")
    
    // State for refreshing history
    var historyRefreshTrigger by remember { mutableStateOf(0) }
    
    // Get icon resource
    val iconResource = try {
        ThemeIconManager.getIconResource(context, "default", iconName)
    } catch (e: IllegalArgumentException) {
        ThemeIconManager.getIconResource(context, "default", "activity")
    }
    
    UI.Screen(type = ScreenType.TOOL_INSTANCE) {
        // Top bar with tool info
        UI.TopBar(
            type = TopBarType.TOOL,
            title = toolName
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Tool header (non-editable info)
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Tool icon
                ToolIcon(
                    iconResource = iconResource,
                    size = 48.dp
                )
                
                // Tool info
                UI.Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UI.Text(
                        text = toolName,
                        type = TextType.TITLE,
                        semantic = "tool-name"
                    )
                    
                    if (toolDescription.isNotBlank()) {
                        UI.Text(
                            text = toolDescription,
                            type = TextType.BODY,
                            semantic = "tool-description"
                        )
                    }
                    
                    UI.Text(
                        text = "Type: $trackingType", // TODO: Internationalization - use stringResource
                        type = TextType.CAPTION,
                        semantic = "tool-type"
                    )
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(24.dp))
        
        // Input interface section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = "Nouvelle entrée", // TODO: Internationalization - use stringResource
                    type = TextType.TITLE,
                    semantic = "input-section-title"
                )
                
                // Input interface
                TrackingInput(
                    toolInstanceId = toolInstanceId,
                    config = config,
                    onEntrySaved = { 
                        // Trigger history refresh
                        historyRefreshTrigger++
                    }
                )
            }
        }
        
        UI.Spacer(modifier = Modifier.height(24.dp))
        
        // History section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = "Historique", // TODO: Internationalization - use stringResource
                    type = TextType.TITLE,
                    semantic = "history-section-title"
                )
                
                // History display
                key(historyRefreshTrigger) {
                    TrackingHistory(
                        toolInstanceId = toolInstanceId,
                        trackingType = trackingType
                    )
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Navigation actions
        UI.Container(type = ContainerType.FLOATING) {
            UI.Button(
                type = ButtonType.SECONDARY,
                semantic = "back-button",
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Text(
                    text = "Retour", // TODO: Internationalization - use stringResource
                    type = TextType.LABEL,
                    semantic = "back-label"
                )
            }
        }
    }
}

// TODO: Internationalization - Create strings.xml entries for tracking types and all UI text