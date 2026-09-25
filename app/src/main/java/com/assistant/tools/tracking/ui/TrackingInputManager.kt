package com.assistant.tools.tracking.ui

import com.assistant.core.utils.JsonUtils
import com.assistant.core.ui.PropertiesSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.ui.*
import com.assistant.core.utils.DateUtils
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
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
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    
    // Extract tracking configuration
    val trackingType = config.optString("type", "numeric")
    
    // State management
    var isLoading by remember { mutableStateOf(false) }
    
    // The custom date chosen above the shortcuts, or null for "now". Now is read when it is
    // used — at the save, or when a dialog opens — never kept from the arrival on the screen
    var customTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }
    
    // Save function with new dataJson signature
    val saveEntry: (String, String, Long) -> Unit = { itemName, dataJson, timestamp ->
        LogManager.tracking("=== SaveEntry start ===")
        LogManager.tracking("saveEntry called: itemName=$itemName, dataJson=$dataJson, timestamp=$timestamp, trackingType=$trackingType")
        scope.launch {
            isLoading = true
            
            try {
                // Parse dataJson and extract custom_fields
                val dataObject = JSONObject(dataJson)
                val customFields = dataObject.optJSONObject("extra")
                if (customFields != null) {
                    dataObject.remove("extra") // Remove from data object
                }

                // Build the params for the current tool_data structure
                val params = mutableMapOf<String, Any>(
                    "tool_instance_id" to toolInstanceId,
                    "tooltype" to "tracking",
                    "timestamp" to timestamp,
                    "name" to itemName,
                    "schema_id" to "tracking_data_$trackingType", // Add schema_id for validation
                    "data" to dataObject
                )

                // Add custom_fields as separate parameter if present
                if (customFields != null) {
                    params["extra"] = customFields
                }

                LogManager.tracking("Final params being sent: $params")
                
                val result = coordinator.processUserAction("tool_data.create", params)
                
                LogManager.tracking("=== Coordinator result ===")
                LogManager.tracking("Result status: ${result.status}")
                LogManager.tracking("Result error: ${result.error}")
                LogManager.tracking("Result data: ${result.data}")
                
                when {
                    result.isSuccess -> {
                        LogManager.tracking("=== Save success ===")
                        
                        // Show success toast
                        UI.Toast(context, s.tool("usage_entry_saved"), Duration.SHORT)
                        
                        onEntrySaved()
                    }
                    else -> {
                        // Show error toast with detailed error
                        val errorMsg = result.error ?: s.tool("error_entry_saving")
                        LogManager.tracking("=== Save failed ===", "ERROR")
                        LogManager.tracking("Save failed: status=${result.status}, error=$errorMsg", "ERROR")
                        UI.Toast(context, errorMsg, Duration.LONG)
                    }
                }
                
            } catch (e: Exception) {
                // Show error toast
                LogManager.tracking("=== Save exception ===", "ERROR")
                LogManager.tracking("Exception during save", "ERROR", e)
                UI.Toast(context, s.shared("message_error").format(e.message ?: ""), Duration.LONG)
            } finally {
                isLoading = false
            }
        }
    }
    
    // Dialog states
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var dialogItemType by rememberSaveable { mutableStateOf<ItemType?>(null) }
    var dialogActionType by rememberSaveable { mutableStateOf(ActionType.CREATE) }
    var dialogInitialName by rememberSaveable { mutableStateOf("") }
    var dialogInitialProperties by rememberSaveable(stateSaver = PropertiesSaver) { mutableStateOf(emptyMap<String, Any>()) }
    // Fixed when the dialog opens, so the date it shows does not move while it is open
    var dialogInitialTimestamp by rememberSaveable { mutableStateOf(0L) }
    
    // Handler for adding items to predefined shortcuts
    val addToPredefined: (String, Map<String, Any>) -> Unit = { itemName, properties ->
        scope.launch {
            try {
                LogManager.tracking("Adding to predefined: $itemName with $properties")
                
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
                val updatedConfig = JsonUtils.toMap(config.apply {
                    put("items", currentItems)
                })

                val params = mapOf(
                    "tool_instance_id" to toolInstanceId,
                    "config" to updatedConfig
                )
                
                LogManager.tracking("Updating config with new item: $params")
                
                val result = coordinator.processUserAction("tools.update", params)
                if (result.isSuccess) {
                    LogManager.tracking("Successfully added item to predefined shortcuts")
                    onConfigChanged()
                } else {
                    LogManager.tracking("Failed to add item to predefined: ${result.error}", "ERROR")
                }
            } catch (e: Exception) {
                LogManager.tracking("Error adding to predefined: ${e.message}", "ERROR", e)
            }
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Predefined items section for all types
        PredefinedItemsSection(
            config = config,
            trackingType = trackingType,
            isLoading = isLoading,
            toolInstanceId = toolInstanceId,
            onEntrySaved = onEntrySaved,
            onCustomTimestampChange = { customTimestamp = it },
            onQuickSave = { name, properties ->
                // Convert properties to dataJson for quick save
                // Note: 'raw' field is auto-generated by ToolDataService via enrichData()
                val initialDataJson = when (trackingType) {
                    "numeric" -> JSONObject().apply {
                        put("type", "numeric")
                        put("quantity", properties["quantity"] ?: properties["default_quantity"])
                        put("unit", properties["unit"] ?: "")
                    }.toString()
                    "counter" -> JSONObject().apply {
                        put("type", "counter")
                        put("increment", properties.getValue("increment")) // +n or -n, as the button sent it
                    }.toString()
                    "boolean" -> JSONObject().apply {
                        put("type", "boolean")
                        put("state", properties["state"] ?: true)
                        val trueLabel = properties["true_label"]?.toString() ?: s.tool("config_default_true_label")
                        val falseLabel = properties["false_label"]?.toString() ?: s.tool("config_default_false_label")
                        put("true_label", trueLabel)
                        put("false_label", falseLabel)
                    }.toString()
                    "timer" -> JSONObject().apply {
                        put("type", "timer")
                        put("duration_seconds", properties["duration_seconds"] ?: 0)
                    }.toString()
                    else -> JSONObject().apply {
                        put("type", trackingType)
                    }.toString()
                }
                
                // Convert to validation format with proper types
                val validationObject = com.assistant.tools.tracking.TrackingUtils.convertToValidationFormat(initialDataJson, trackingType)
                val finalDataJson = JSONObject().apply {
                    for ((key, value) in validationObject) {
                        put(key, value)
                    }
                }.toString()
                
                saveEntry(name, finalDataJson, customTimestamp ?: System.currentTimeMillis())
            },
            onOpenDialog = { name, properties ->
                dialogInitialName = name
                dialogInitialProperties = properties
                dialogItemType = ItemType.PREDEFINED
                dialogActionType = ActionType.CREATE
                dialogInitialTimestamp = customTimestamp ?: System.currentTimeMillis()
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
                        dialogInitialTimestamp = customTimestamp ?: System.currentTimeMillis()
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
        initialName = dialogInitialName,
        initialData = dialogInitialProperties,
        initialTimestamp = dialogInitialTimestamp,
        onConfirm = { name, dataJson, addToPredefinedFlag, timestamp ->
            // Save the entry with the user-selected date and time
            saveEntry(name, dataJson, timestamp)
            
            // Add to predefined if requested
            if (addToPredefinedFlag && dialogItemType == ItemType.FREE) {
                // Parse dataJson back to properties for addToPredefined
                try {
                    val dataObj = JSONObject(dataJson)
                    val properties = mutableMapOf<String, Any>()
                    when (trackingType) {
                        "numeric" -> {
                            if (dataObj.has("quantity")) properties["default_quantity"] = dataObj.getDouble("quantity")
                            if (dataObj.has("unit")) properties["unit"] = dataObj.getString("unit")
                        }
                        "counter" -> {
                            // The shortcut keeps the amount; its two buttons give the sign
                            val step = Math.abs(dataObj.getInt("increment"))
                            if (step > 0) properties["default_increment"] = step
                        }
                        // Other types don't have default properties typically
                    }
                    addToPredefined(name, properties)
                } catch (e: Exception) {
                    LogManager.tracking("Error parsing data JSON for predefined: ${e.message}", "ERROR", e)
                }
            }
            
            showDialog = false
        },
        onCancel = {
            showDialog = false
        }
    )
}