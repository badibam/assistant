package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.ui.core.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.database.AppDatabase
import org.json.JSONObject
import kotlinx.coroutines.launch

/**
 * Base tracking input component with common logic
 * Handles validation, saving, and error states for all tracking types
 */
@Composable
fun TrackingInput(
    toolInstanceId: String,
    config: JSONObject,
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    
    // Extract tracking configuration
    val trackingType = config.optString("type", "numeric")
    
    // Common state
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Clear messages after some time
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            kotlinx.coroutines.delay(3000)
            successMessage = null
        }
    }
    
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(5000)
            errorMessage = null
        }
    }
    
    // Save function with common logic
    val saveEntry = { value: Any, name: String? ->
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                // Get tool instance info via coordinator
                val toolInstanceResult = coordinator.processUserAction(
                    "get->tool_instance",
                    mapOf("tool_instance_id" to toolInstanceId)
                )
                
                if (toolInstanceResult.status != CommandStatus.SUCCESS) {
                    errorMessage = "Impossible de récupérer les informations de l'outil"
                    return@launch
                }
                
                val toolInstanceData = toolInstanceResult.data?.get("tool_instance") as? Map<String, Any>
                val zoneId = toolInstanceData?.get("zone_id") as? String
                
                if (zoneId == null) {
                    errorMessage = "Zone introuvable pour cet outil"
                    return@launch
                }
                
                val zoneResult = coordinator.processUserAction(
                    "get->zone",
                    mapOf("zone_id" to zoneId)
                )
                
                if (zoneResult.status != CommandStatus.SUCCESS) {
                    errorMessage = "Impossible de récupérer les informations de la zone"
                    return@launch
                }
                
                val zoneData = zoneResult.data?.get("zone") as? Map<String, Any>
                val zoneName = zoneData?.get("name") as? String
                
                if (zoneName == null) {
                    errorMessage = "Nom de zone introuvable"
                    return@launch
                }
                
                val toolInstanceName = config.optString("name", "Suivi")
                
                // Use value as-is (it's already the correct JSON from NumericTrackingInput)
                val valueJson = value.toString()
                
                // Use coordinator to save tracking entry
                val result = coordinator.processUserAction(
                    "create->tool_data",
                    mapOf(
                        "tool_type" to "tracking",
                        "operation" to "create",
                        "tool_instance_id" to toolInstanceId,
                        "zone_name" to zoneName,
                        "tool_instance_name" to toolInstanceName,
                        "name" to (name ?: "entrée"),
                        "value" to valueJson
                    )
                )
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        successMessage = "Entrée sauvegardée"
                        onEntrySaved()
                    }
                    else -> {
                        errorMessage = result.error ?: "Erreur lors de la sauvegarde"
                    }
                }
                
            } catch (e: Exception) {
                errorMessage = "Erreur lors de la sauvegarde: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    UI.Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status messages
        if (successMessage != null) {
            UI.Card(
                type = CardType.SYSTEM,
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Text(
                    text = successMessage!!,
                    type = TextType.BODY
                )
            }
        }
        
        if (errorMessage != null) {
            UI.Card(
                type = CardType.SYSTEM,
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Text(
                    text = errorMessage!!,
                    type = TextType.BODY
                )
            }
        }
        
        // Render appropriate input component based on tracking type
        when (trackingType) {
            "numeric" -> NumericTrackingInput(
                config = config,
                onSave = saveEntry,
                isLoading = isLoading
            )
//            "text" -> TextTrackingInput(
//                config = config,
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "scale" -> ScaleTrackingInput(
//                config = config,
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "boolean" -> BooleanTrackingInput(
//                config = config,
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "duration" -> DurationTrackingInput(
//                config = config,
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "choice" -> ChoiceTrackingInput(
//                config = config,
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
//            "counter" -> CounterTrackingInput(
//                config = config,
//                config = config,
//                onSave = saveEntry,
//                isLoading = isLoading
//            )
            else -> {
                UI.Text(
                    text = "Type de suivi non supporté: $trackingType", // TODO: Internationalization
                    type = TextType.BODY,
                    semantic = "unsupported-type"
                )
            }
        }
    }
}

/**
 * Common interface for specialized tracking input components
 */
interface TrackingInputComponent {
    @Composable
    fun Content(
        config: JSONObject,
        onSave: (value: Any, itemName: String?) -> Unit,
        isLoading: Boolean
    )
}