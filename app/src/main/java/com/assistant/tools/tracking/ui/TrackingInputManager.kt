package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.tools.tracking.ui.inputs.NumericTrackingInput
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
    
    // Save function with common logic
    val saveEntry: (String, String) -> Unit = { valueJson, itemName ->
        scope.launch {
            isLoading = true
            
            try {
                // Use coordinator to save tracking entry
                val params = mapOf(
                    "tool_type" to "tracking",
                    "operation" to "create",
                    "tool_instance_id" to toolInstanceId,
                    "zone_name" to zoneName,
                    "tool_instance_name" to toolInstanceName,
                    "name" to itemName,
                    "value" to valueJson
                )
                android.util.Log.d("TrackingInputManager", "Saving with params: $params")
                
                val result = coordinator.processUserAction("create->tool_data", params)
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
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
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Route to appropriate input component based on tracking type
        when (trackingType) {
            "numeric" -> NumericTrackingInput(
                config = config,
                onSave = saveEntry,
                onAddToPredefined = { itemName, unit, defaultValue ->
                    scope.launch {
                        try {
                            // Get current tool instance to modify config
                            android.util.Log.d("TrackingInputManager", "Getting tool instance for adding to predefined: $toolInstanceId")
                            val getResult = coordinator.processUserAction(
                                "get->tool_instance",
                                mapOf("tool_instance_id" to toolInstanceId)
                            )
                            
                            android.util.Log.d("TrackingInputManager", "Get result: status=${getResult.status}, data=${getResult.data}")
                            
                            if (getResult.status != CommandStatus.SUCCESS) {
                                android.util.Log.e("TrackingInputManager", "Failed to get tool instance: ${getResult.error}")
                                android.widget.Toast.makeText(
                                    context,
                                    "Impossible de récupérer la configuration: ${getResult.error}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                return@launch
                            }
                            
                            val toolInstanceData = getResult.data?.get("tool_instance") as? Map<String, Any>
                            val currentConfig = toolInstanceData?.get("config_json") as? String ?: "{}"
                            
                            // Parse and modify config to add item
                            val configJson = JSONObject(currentConfig)
                            val itemsArray = configJson.optJSONArray("items") ?: org.json.JSONArray()
                            
                            // Add new item
                            val newItem = JSONObject().apply {
                                put("name", itemName)
                                if (unit.isNotBlank()) put("unit", unit)
                                if (defaultValue.isNotBlank()) put("default_value", defaultValue)
                            }
                            itemsArray.put(newItem)
                            configJson.put("items", itemsArray)
                            
                            // Update tool instance with modified config
                            val result = coordinator.processUserAction(
                                "update->tool_instance",
                                mapOf(
                                    "tool_instance_id" to toolInstanceId,
                                    "config_json" to configJson.toString()
                                )
                            )
                            
                            when (result.status) {
                                CommandStatus.SUCCESS -> {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Ajouté aux raccourcis",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    
                                    // Notify parent to reload config
                                    onConfigChanged()
                                }
                                else -> {
                                    android.widget.Toast.makeText(
                                        context,
                                        result.error ?: "Erreur lors de l'ajout aux raccourcis",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Erreur: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                isLoading = isLoading
            )
//            "text" -> TextTrackingInput(
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "scale" -> ScaleTrackingInput(
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "boolean" -> BooleanTrackingInput(
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "duration" -> DurationTrackingInput(
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "choice" -> ChoiceTrackingInput(
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "counter" -> CounterTrackingInput(
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
            else -> {
                // Fallback for unsupported types
                android.widget.Toast.makeText(
                    context,
                    "Type de suivi non supporté: $trackingType",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}