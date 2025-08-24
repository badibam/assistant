package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.tools.tracking.entities.TrackingData
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Component to display tracking data history with CRUD operations
 * Shows chronological list of entries with delete functionality
 */
@Composable
fun TrackingHistory(
    toolInstanceId: String,
    trackingType: String = "numeric",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var trackingData by remember { mutableStateOf<List<TrackingData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Date formatter
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    
    // Load data
    val loadData = {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                // TODO: Call TrackingService or Repository to load data - use coordinator
                // val data = ...
                // trackingData = data.sortedByDescending { it.recorded_at }
                
                // Simulate data for now
                kotlinx.coroutines.delay(500)
                trackingData = emptyList() // Replace with actual data
                
            } catch (e: Exception) {
                errorMessage = "Erreur lors du chargement: ${e.message}" // TODO: Internationalization
            } finally {
                isLoading = false
            }
        }
    }
    
    // Delete entry
    val deleteEntry = { entryId: String ->
        scope.launch {
            try {
                // TODO: Call TrackingService to delete entry - use coordinator
                // TrackingService.deleteEntry(entryId)
                
                // Simulate delete
                trackingData = trackingData.filter { it.id != entryId }
                
            } catch (e: Exception) {
                errorMessage = "Erreur lors de la suppression: ${e.message}" // TODO: Internationalization
            }
        }
    }
    
    // Load data on first composition
    LaunchedEffect(toolInstanceId) {
        loadData()
    }
    
    UI.Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with refresh button
        UI.Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            UI.Text(
                text = "Historique des entrées", // TODO: Internationalization
                type = TextType.TITLE,
                semantic = "history-title"
            )
            
            UI.Button(
                type = ButtonType.GHOST,
                semantic = "refresh-history",
                onClick = { loadData() },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    UI.LoadingIndicator(type = LoadingType.MINIMAL)
                } else {
                    UI.Text(
                        text = "↻", // TODO: Use proper refresh icon
                        type = TextType.LABEL,
                        semantic = "refresh-icon"
                    )
                }
            }
        }
        
        // Error message
        if (errorMessage != null) {
            UI.Card(
                type = CardType.SYSTEM, // TODO: Add ERROR card type
                semantic = "error-message",
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Text(
                    text = errorMessage!!,
                    type = TextType.BODY,
                    semantic = "error-text"
                )
            }
        }
        
        // Loading state
        if (isLoading && trackingData.isEmpty()) {
            UI.LoadingIndicator(
                type = LoadingType.DEFAULT,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Empty state
        if (!isLoading && trackingData.isEmpty() && errorMessage == null) {
            UI.Card(
                type = CardType.SYSTEM,
                semantic = "empty-state",
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Text(
                    text = "Aucune entrée enregistrée", // TODO: Internationalization
                    type = TextType.BODY,
                    semantic = "empty-text"
                )
            }
        }
        
        // Data list
        trackingData.forEach { entry ->
            TrackingHistoryItem(
                entry = entry,
                trackingType = trackingType,
                dateFormatter = dateFormatter,
                onDelete = { deleteEntry(entry.id) }
            )
        }
    }
}

/**
 * Individual history item component
 */
@Composable
private fun TrackingHistoryItem(
    entry: TrackingData,
    trackingType: String,
    dateFormatter: SimpleDateFormat,
    onDelete: () -> Unit
) {
    UI.Card(
        type = CardType.DATA_ENTRY,
        semantic = "history-item-${entry.id}",
        modifier = Modifier.fillMaxWidth()
    ) {
        UI.Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Entry info
            UI.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Value display
                UI.Text(
                    text = formatTrackingValue(entry, trackingType),
                    type = TextType.BODY,
                    semantic = "entry-value"
                )
                
                // Item name if present
                if (entry.name.isNotBlank()) {
                    UI.Text(
                        text = entry.name,
                        type = TextType.CAPTION,
                        semantic = "entry-item-name"
                    )
                }
                
                // Timestamp
                UI.Text(
                    text = dateFormatter.format(Date(entry.recorded_at)),
                    type = TextType.CAPTION,
                    semantic = "entry-timestamp"
                )
            }
            
            // Delete button
            UI.Button(
                type = ButtonType.DANGER,
                semantic = "delete-entry",
                onClick = onDelete
            ) {
                UI.Text(
                    text = "×",
                    type = TextType.LABEL,
                    semantic = "delete-icon"
                )
            }
        }
    }
}

/**
 * Format tracking value for display based on type
 */
private fun formatTrackingValue(entry: TrackingData, trackingType: String): String {
    return when (trackingType) {
        "numeric" -> {
            // Parse JSON value to extract numeric value and unit
            try {
                val valueJson = org.json.JSONObject(entry.value)
                val numericValue = valueJson.optDouble("value", 0.0)
                val unit = valueJson.optString("unit", "")
                
                if (unit.isNotBlank()) {
                    "${numericValue} $unit"
                } else {
                    numericValue.toString()
                }
            } catch (e: Exception) {
                entry.value // Fallback to raw value
            }
        }
        // TODO: Add other tracking types when implemented
        else -> entry.value
    }
}