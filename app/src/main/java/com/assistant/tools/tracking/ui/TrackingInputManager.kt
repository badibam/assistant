package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.ui.*
import com.assistant.tools.tracking.ui.components.*
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Input orchestrator for tracking tools
 * Handles common logic (validation, saving, feedback) and routes to specialized input components
 */
@Composable
fun TrackingInputManager(
    toolInstanceId: String,
    zoneName: String,
    toolInstanceName: String,
    config: JSONObject,
    onEntrySaved: () -> Unit,
    onConfigChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    
    // Extract tracking configuration
    val trackingType = config.optString("type", "numeric")
    
    // State management
    var isLoading by remember { mutableStateOf(false) }
    
    // Save function with generalized Map signature for all tracking types
    val saveEntry: (String, Map<String, Any>) -> Unit = { itemName, properties ->
        android.util.Log.d("TRACKING_DEBUG", "saveEntry called: itemName=$itemName, properties=$properties, trackingType=$trackingType")
        scope.launch {
            isLoading = true
            
            try {
                // Build params with generalized properties
                val params = mutableMapOf<String, Any>(
                    "tool_type" to "tracking",
                    "operation" to "create",
                    "tool_instance_id" to toolInstanceId,
                    "zone_name" to zoneName,
                    "tool_instance_name" to toolInstanceName,
                    "name" to itemName,
                    "type" to trackingType
                )
                
                // Add type-specific properties
                params.putAll(properties)
                android.util.Log.d("TRACKING_DEBUG", "Final params being sent: $params")
                android.util.Log.d("CONFIGDEBUG", "ADDING ITEM - toolInstanceId: $toolInstanceId, params: $params")
                
                val result = coordinator.processUserAction("create->tool_data", params)
                
                android.util.Log.d("CONFIGDEBUG", "ADD ITEM RESULT - status: ${result.status}, error: ${result.error}")
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        android.util.Log.d("CONFIGDEBUG", "ITEM ADDED SUCCESSFULLY - calling onEntrySaved()")
                        
                        // Show success toast
                        android.widget.Toast.makeText(
                            context,
                            "Entrée sauvegardée",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        onEntrySaved()
                    }
                    else -> {
                        // Show error toast with detailed error
                        val errorMsg = result.error ?: "Erreur lors de la sauvegarde"
                        android.util.Log.e("TrackingInputManager", "Save failed: status=${result.status}, error=$errorMsg")
                        android.widget.Toast.makeText(
                            context,
                            errorMsg,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
            } catch (e: Exception) {
                // Show error toast
                android.util.Log.e("TrackingInputManager", "Exception during save", e)
                android.widget.Toast.makeText(
                    context,
                    "Erreur: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } finally {
                isLoading = false
            }
        }
    }
    
    // Dialog states
    var showDialog by remember { mutableStateOf(false) }
    var dialogItemType by remember { mutableStateOf<ItemType?>(null) }
    var dialogInputType by remember { mutableStateOf(InputType.ENTRY) }
    var dialogActionType by remember { mutableStateOf(ActionType.CREATE) }
    var dialogInitialName by remember { mutableStateOf("") }
    var dialogInitialProperties by remember { mutableStateOf(emptyMap<String, Any>()) }
    
    // Handler for adding items to predefined shortcuts
    val addToPredefined: (String, Map<String, Any>) -> Unit = { itemName, properties ->
        scope.launch {
            try {
                android.util.Log.d("TRACKING_DEBUG", "Adding to predefined: $itemName with $properties")
                
                // Get current items array from config
                val currentItems = config.optJSONArray("items") ?: JSONArray()
                
                // Create new item JSON object
                val newItem = JSONObject().apply {
                    put("name", itemName)
                    // Add all properties to the item
                    properties.forEach { (key, value) ->
                        put(key, value)
                    }
                }
                
                // Add new item to array
                currentItems.put(newItem)
                
                // Update tool instance configuration
                val params = JSONObject().apply {
                    put("tool_type", "tracking")
                    put("operation", "update_config")
                    put("tool_instance_id", toolInstanceId)
                    put("config", config.apply {
                        put("items", currentItems)
                    }.toString())
                }
                
                android.util.Log.d("TRACKING_DEBUG", "Updating config with new item: ${params.toString()}")
                
                val result = coordinator.processUserAction("update->tool_config", params)
                if (result.status == CommandStatus.SUCCESS) {
                    android.util.Log.d("TRACKING_DEBUG", "Successfully added item to predefined shortcuts")
                    onConfigChanged()
                } else {
                    android.util.Log.e("TRACKING_DEBUG", "Failed to add item to predefined: ${result.error}")
                }
            } catch (e: Exception) {
                android.util.Log.e("TRACKING_DEBUG", "Error adding to predefined: ${e.message}", e)
            }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Predefined items section for all types
        PredefinedItemsSection(
            config = config,
            trackingType = trackingType,
            isLoading = isLoading,
            toolInstanceId = toolInstanceId,
            onQuickSave = { name, properties ->
                saveEntry(name, properties)
            },
            onOpenDialog = { name, properties ->
                dialogInitialName = name
                dialogInitialProperties = properties
                dialogItemType = ItemType.PREDEFINED
                dialogInputType = InputType.ENTRY
                dialogActionType = ActionType.CREATE
                showDialog = true
            }
        )
        
        // Free input button (crayon/pencil icon) - except for TIMER which has no free input
        if (trackingType != "timer") {
            Box(modifier = Modifier.fillMaxWidth()) {
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.ICON,
                    onClick = {
                        dialogInitialName = ""
                        dialogInitialProperties = emptyMap()
                        dialogItemType = ItemType.FREE
                        dialogInputType = InputType.ENTRY
                        dialogActionType = ActionType.CREATE
                        showDialog = true
                    }
                )
            }
        }
    }
    
    // Universal tracking dialog
    UniversalTrackingDialog(
        isVisible = showDialog,
        trackingType = trackingType,
        config = config,
        itemType = dialogItemType,
        inputType = dialogInputType,
        actionType = dialogActionType,
        initialName = dialogInitialName,
        initialProperties = dialogInitialProperties,
        onConfirm = { name, properties, addToPredefinedFlag, date, time ->
            // Save the entry
            saveEntry(name, properties)
            
            // Add to predefined if requested
            if (addToPredefinedFlag && dialogItemType == ItemType.FREE) {
                addToPredefined(name, properties)
            }
            
            showDialog = false
        },
        onCancel = {
            showDialog = false
        }
    )
}