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
import com.assistant.core.utils.DateUtils
import com.assistant.tools.tracking.ui.components.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

/**
 * Input orchestrator for tracking tools
 * Handles common logic (validation, saving, feedback) and routes to specialized input components
 */
@Composable
fun TrackingInputManager(
    toolInstanceId: String,
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
    
    // Save function with new valueJson signature
    val saveEntry: (String, String, Long) -> Unit = { itemName, valueJson, recordedAt ->
        android.util.Log.d("VALDEBUG", "=== SAVEENTRY START ===")
        android.util.Log.d("VALDEBUG", "saveEntry called: itemName=$itemName, valueJson=$valueJson, recordedAt=$recordedAt, trackingType=$trackingType")
        scope.launch {
            isLoading = true
            
            try {
                // Build params pour nouvelle structure tool_data
                val params = mutableMapOf<String, Any>(
                    "toolInstanceId" to toolInstanceId,
                    "tooltype" to "tracking", 
                    "timestamp" to recordedAt,
                    "name" to itemName,
                    "data" to JSONObject(valueJson)
                )
                android.util.Log.d("VALDEBUG", "Final params being sent: $params")
                
                val result = coordinator.processUserAction("create->tool_data", params)
                
                android.util.Log.d("VALDEBUG", "=== COORDINATOR RESULT ===")
                android.util.Log.d("VALDEBUG", "Result status: ${result.status}")
                android.util.Log.d("VALDEBUG", "Result error: ${result.error}")
                android.util.Log.d("VALDEBUG", "Result data: ${result.data}")
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        android.util.Log.d("VALDEBUG", "=== SAVE SUCCESS ===")
                        
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
                        android.util.Log.e("VALDEBUG", "=== SAVE FAILED ===")
                        android.util.Log.e("VALDEBUG", "Save failed: status=${result.status}, error=$errorMsg")
                        android.widget.Toast.makeText(
                            context,
                            errorMsg,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
            } catch (e: Exception) {
                // Show error toast
                android.util.Log.e("VALDEBUG", "=== SAVE EXCEPTION ===")
                android.util.Log.e("VALDEBUG", "Exception during save", e)
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
                        when (value) {
                            is String -> put(key, value)
                            is Int -> put(key, value)
                            is Double -> put(key, value)
                            is Boolean -> put(key, value)
                            else -> put(key, value.toString())
                        }
                    }
                }
                
                // Add new item to array
                currentItems.put(newItem)
                
                // Update tool instance configuration
                val updatedConfigJson = config.apply {
                    put("items", currentItems)
                }.toString()
                
                val params = mapOf(
                    "tool_instance_id" to toolInstanceId,
                    "config_json" to updatedConfigJson
                )
                
                android.util.Log.d("TRACKING_DEBUG", "Updating config with new item: $params")
                
                val result = coordinator.processUserAction("update->tool_instance", params)
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
                // Convert properties to valueJson for quick save
                val initialValueJson = when (trackingType) {
                    "numeric" -> JSONObject().apply {
                        put("type", "numeric")
                        put("quantity", properties["quantity"] ?: properties["default_quantity"])
                        put("unit", properties["unit"] ?: "")
                        val qty = (properties["quantity"] ?: properties["default_quantity"])?.toString()
                        val unit = properties["unit"]?.toString() ?: ""
                        put("raw", if (unit.isNotBlank()) "$qty $unit" else qty ?: "")
                    }.toString()
                    "counter" -> JSONObject().apply {
                        put("type", "counter") 
                        put("increment", properties["default_increment"] ?: 1)
                        put("raw", properties["default_increment"]?.toString() ?: "1")
                    }.toString()
                    else -> JSONObject().apply {
                        put("type", trackingType)
                        put("raw", name) // Default for other types
                    }.toString()
                }
                
                // Convert to validation format with proper types
                val validationObject = com.assistant.tools.tracking.TrackingUtils.convertToValidationFormat(initialValueJson, trackingType)
                val finalValueJson = JSONObject().apply {
                    for ((key, value) in validationObject) {
                        put(key, value)
                    }
                }.toString()
                
                saveEntry(name, finalValueJson, System.currentTimeMillis())
            },
            onOpenDialog = { name, properties ->
                dialogInitialName = name
                dialogInitialProperties = properties
                dialogItemType = ItemType.PREDEFINED
                dialogActionType = ActionType.CREATE
                showDialog = true
            }
        )
        
        // Free input button (plus icon) - except for TIMER which has no free input
        if (trackingType != "timer") {
            Box(modifier = Modifier.fillMaxWidth()) {
                UI.ActionButton(
                    action = ButtonAction.ADD,
                    display = ButtonDisplay.ICON,
                    onClick = {
                        dialogInitialName = ""
                        dialogInitialProperties = emptyMap()
                        dialogItemType = ItemType.FREE
                        dialogActionType = ActionType.CREATE
                        showDialog = true
                    }
                )
            }
        }
    }
    
    // Tracking entry dialog
    TrackingEntryDialog(
        isVisible = showDialog,
        trackingType = trackingType,
        config = config,
        itemType = dialogItemType,
        actionType = dialogActionType,
        toolInstanceId = toolInstanceId,
        zoneName = zoneName,
        toolInstanceName = toolInstanceName,
        initialName = dialogInitialName,
        initialValue = dialogInitialProperties,
        initialRecordedAt = System.currentTimeMillis(),
        onConfirm = { name, valueJson, addToPredefinedFlag, recordedAt ->
            // Save the entry with the user-selected date and time
            saveEntry(name, valueJson, recordedAt)
            
            // Add to predefined if requested
            if (addToPredefinedFlag && dialogItemType == ItemType.FREE) {
                // Parse valueJson back to properties for addToPredefined
                try {
                    val valueObj = JSONObject(valueJson)
                    val properties = mutableMapOf<String, Any>()
                    when (trackingType) {
                        "numeric" -> {
                            if (valueObj.has("quantity")) properties["default_quantity"] = valueObj.getDouble("quantity")
                            if (valueObj.has("unit")) properties["unit"] = valueObj.getString("unit")
                        }
                        "counter" -> {
                            if (valueObj.has("increment")) properties["default_increment"] = valueObj.getInt("increment")
                        }
                        // Other types don't have default properties typically
                    }
                    addToPredefined(name, properties)
                } catch (e: Exception) {
                    android.util.Log.e("TRACKING_DEBUG", "Error parsing value JSON for predefined: ${e.message}")
                }
            }
            
            showDialog = false
        },
        onCancel = {
            showDialog = false
        }
    )
}