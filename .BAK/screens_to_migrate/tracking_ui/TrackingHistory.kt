package com.assistant.tools.tracking.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.ui.core.*
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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
    
    // Coordinator
    val coordinator = remember { Coordinator(context) }
    
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
                // Use coordinator to get tracking entries
                val result = coordinator.processUserAction(
                    "get->tool_data",
                    mapOf(
                        "tool_type" to "tracking",
                        "operation" to "get_entries",
                        "tool_instance_id" to toolInstanceId
                    )
                )
                
                // Debug: log result status and data
                Log.d("TrackingHistory", "Command result status = ${result.status}")
                Log.d("TrackingHistory", "Command result data = ${result.data}")
                Log.d("TrackingHistory", "Command result error = ${result.error}")
                Log.d("TrackingHistory", "Tool instance ID = $toolInstanceId")
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                        Log.d("TrackingHistory", "Found ${entriesData.size} entries")
                        
                        trackingData = entriesData.mapNotNull { entryMap ->
                            if (entryMap is Map<*, *>) {
                                try {
                                    val data = TrackingData(
                                        id = entryMap["id"] as? String ?: "",
                                        tool_instance_id = entryMap["tool_instance_id"] as? String ?: "",
                                        zone_name = entryMap["zone_name"] as? String ?: "",
                                        tool_instance_name = entryMap["tool_instance_name"] as? String ?: "",
                                        name = entryMap["name"] as? String ?: "",
                                        value = entryMap["value"] as? String ?: "",
                                        recorded_at = (entryMap["recorded_at"] as? Number)?.toLong() ?: 0L,
                                        created_at = (entryMap["created_at"] as? Number)?.toLong() ?: 0L,
                                        updated_at = (entryMap["updated_at"] as? Number)?.toLong() ?: 0L
                                    )
                                    Log.d("TrackingHistory", "Mapped entry: ${data.name} = ${data.value}")
                                    data
                                } catch (e: Exception) {
                                    Log.e("TrackingHistory", "Failed to map entry", e)
                                    null
                                }
                            } else null
                        }.sortedByDescending { it.recorded_at }
                        
                        Log.d("TrackingHistory", "Final trackingData size = ${trackingData.size}")
                    }
                    else -> {
                        errorMessage = "Status: ${result.status}, Error: ${result.error ?: "Erreur lors du chargement"}"
                        Log.e("TrackingHistory", "Command failed - $errorMessage")
                    }
                }
                
            } catch (e: Exception) {
                errorMessage = "Erreur lors du chargement: ${e.message}"
                Log.e("TrackingHistory", "Exception during data loading", e)
            } finally {
                isLoading = false
            }
        }
    }
    
    // Delete entry
    val deleteEntry = { entryId: String ->
        scope.launch {
            try {
                // Use coordinator to delete tracking entry
                val result = coordinator.processUserAction(
                    "delete->tool_data",
                    mapOf(
                        "tool_type" to "tracking",
                        "operation" to "delete",
                        "entry_id" to entryId
                    )
                )
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        // Remove from local state
                        trackingData = trackingData.filter { it.id != entryId }
                        Log.d("TrackingHistory", "Deleted entry $entryId successfully")
                    }
                    else -> {
                        errorMessage = result.error ?: "Erreur lors de la suppression"
                        Log.e("TrackingHistory", "Delete failed: $errorMessage")
                    }
                }
                
            } catch (e: Exception) {
                errorMessage = "Erreur lors de la suppression: ${e.message}"
                Log.e("TrackingHistory", "Exception during delete", e)
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
                onDelete = { deleteEntry(entry.id) }
            )
        }
    }
}

/**
 * Individual history item component - Compact single-line display
 */
@Composable
private fun TrackingHistoryItem(
    entry: TrackingData,
    trackingType: String,
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
            // Compact entry info on single line: name + value + date
            UI.Text(
                text = buildCompactEntryText(entry, trackingType),
                type = TextType.BODY,
                semantic = "entry-compact",
                modifier = Modifier.weight(1f)
            )
            
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
 * Build compact single-line text: "nom: valeur unité - JJ/MM/AA H:M"
 */
private fun buildCompactEntryText(entry: TrackingData, trackingType: String): String {
    val name = if (entry.name.isNotBlank()) entry.name else "entrée"
    val value = formatTrackingValue(entry, trackingType)
    val date = formatCompactDate(entry.recorded_at)
    
    return "$name: $value - $date"
}

/**
 * Format tracking value for display based on type
 */
private fun formatTrackingValue(entry: TrackingData, trackingType: String): String {
    // Just get the "raw" field from JSON - that's exactly what user typed
    try {
        val valueJson = org.json.JSONObject(entry.value)
        return valueJson.optString("raw", entry.value)
    } catch (e: Exception) {
        return entry.value // Fallback to raw value
    }
}

/**
 * Format date for compact display (JJ/MM/AA H:M)
 */
private fun formatCompactDate(timestamp: Long): String {
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
    return dateFormat.format(java.util.Date(timestamp))
}