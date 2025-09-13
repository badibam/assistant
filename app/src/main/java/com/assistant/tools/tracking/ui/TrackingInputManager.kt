package com.assistant.tools.tracking.ui

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
    
    // Default timestamp management: custom > now
    var defaultTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Save function with new dataJson signature
    val saveEntry: (String, String, Long) -> Unit = { itemName, dataJson, timestamp ->
        LogManager.tracking("=== SaveEntry start ===")
        LogManager.tracking("saveEntry called: itemName=$itemName, dataJson=$dataJson, timestamp=$timestamp, trackingType=$trackingType")
        scope.launch {
            isLoading = true
            
            try {
                // Build params pour nouvelle structure tool_data
                val params = mutableMapOf<String, Any>(
                    "toolInstanceId" to toolInstanceId,
                    "tooltype" to "tracking", 
                    "timestamp" to timestamp,
                    "name" to itemName,
                    "data" to JSONObject(dataJson)
                )
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
    var showDialog by remember { mutableStateOf(false) }
    var dialogItemType by remember { mutableStateOf<ItemType?>(null) }
    var dialogActionType by remember { mutableStateOf(ActionType.CREATE) }
    var dialogInitialName by remember { mutableStateOf("") }
    var dialogInitialProperties by remember { mutableStateOf(emptyMap<String, Any>()) }
    
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
                val updatedConfigJson = config.apply {
                    put("items", currentItems)
                }.toString()
                
                val params = mapOf(
                    "tool_instance_id" to toolInstanceId,
                    "config_json" to updatedConfigJson
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
            defaultTimestamp = defaultTimestamp,
            onDefaultTimestampChange = { newTimestamp ->
                defaultTimestamp = newTimestamp
            },
            onQuickSave = { name, properties ->
                // Convert properties to dataJson for quick save
                val initialDataJson = when (trackingType) {
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
                    "boolean" -> JSONObject().apply {
                        put("type", "boolean")
                        put("state", properties["state"] ?: true)
                        val trueLabel = properties["true_label"]?.toString() ?: s.tool("config_default_true_label")
                        val falseLabel = properties["false_label"]?.toString() ?: s.tool("config_default_false_label")
                        put("true_label", trueLabel)
                        put("false_label", falseLabel)
                        val state = properties["state"] as? Boolean ?: true
                        put("raw", if (state) trueLabel else falseLabel)
                    }.toString()
                    "timer" -> JSONObject().apply {
                        put("type", "timer")
                        put("duration_seconds", properties["duration_seconds"] ?: 0)
                        val seconds = properties["duration_seconds"] as? Int ?: 0
                        val h = seconds / 3600
                        val m = (seconds % 3600) / 60
                        val s = seconds % 60
                        val rawText = buildString {
                            if (h > 0) append("${h}h ")
                            if (m > 0) append("${m}m ")
                            if (s > 0 || (h == 0 && m == 0)) append("${s}s")
                        }.trim()
                        put("raw", "$name: $rawText")
                    }.toString()
                    else -> JSONObject().apply {
                        put("type", trackingType)
                        put("raw", name) // Default for other types
                    }.toString()
                }
                
                // Convert to validation format with proper types
                val validationObject = com.assistant.tools.tracking.TrackingUtils.convertToValidationFormat(initialDataJson, trackingType)
                val finalDataJson = JSONObject().apply {
                    for ((key, value) in validationObject) {
                        put(key, value)
                    }
                }.toString()
                
                saveEntry(name, finalDataJson, defaultTimestamp)
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
        initialName = dialogInitialName,
        initialData = dialogInitialProperties,
        initialTimestamp = defaultTimestamp,
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
                            if (dataObj.has("increment")) properties["default_increment"] = dataObj.getInt("increment")
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