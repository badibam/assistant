package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
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
                // TODO: Call TrackingService to save entry
                // val result = TrackingService.saveEntry(toolInstanceId, value, name)
                
                // Simulate save for now
                kotlinx.coroutines.delay(500)
                
                successMessage = "Entrée sauvegardée" // TODO: Internationalization
                onEntrySaved()
                
            } catch (e: Exception) {
                errorMessage = "Erreur lors de la sauvegarde: ${e.message}" // TODO: Internationalization
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