package com.assistant.tools.tracking.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.ui.components.TrackingItemDialog
import com.assistant.tools.tracking.ui.components.TrackingDialogMode
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Responsive table display for tracking data history with CRUD operations
 * Shows chronological list of entries with edit/delete functionality
 */
@Composable
fun TrackingHistory(
    toolInstanceId: String,
    trackingType: String = "numeric",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    
    // State
    var trackingData by remember { mutableStateOf<List<TrackingData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<TrackingData?>(null) }
    
    // Load data
    val loadData = {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val result = coordinator.processUserAction(
                    "get->tool_data",
                    mapOf(
                        "tool_type" to "tracking",
                        "operation" to "get_entries",
                        "tool_instance_id" to toolInstanceId
                    )
                )
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                        
                        trackingData = entriesData.mapNotNull { entryMap ->
                            if (entryMap is Map<*, *>) {
                                try {
                                    TrackingData(
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
                                } catch (e: Exception) {
                                    Log.e("TrackingHistory", "Failed to map entry", e)
                                    null
                                }
                            } else null
                        }.sortedByDescending { it.recorded_at }
                    }
                    else -> {
                        errorMessage = result.error ?: "Erreur lors du chargement"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Erreur: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    // Update entry
    val updateEntry = { entryId: String, newValue: String, newName: String ->
        scope.launch {
            try {
                val params = mapOf(
                    "tool_type" to "tracking",
                    "operation" to "update",
                    "entry_id" to entryId,
                    "name" to newName,
                    "value" to newValue
                )
                android.util.Log.d("TrackingHistory", "Updating entry with params: $params")
                
                val result = coordinator.processUserAction("update->tool_data", params)
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        android.widget.Toast.makeText(context, "Entrée modifiée", android.widget.Toast.LENGTH_SHORT).show()
                        loadData() // Reload data to show changes
                    }
                    else -> {
                        android.widget.Toast.makeText(context, result.error ?: "Erreur lors de la modification", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Erreur: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Delete entry
    val deleteEntry = { entryId: String ->
        scope.launch {
            try {
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
                        android.widget.Toast.makeText(context, "Entrée supprimée", android.widget.Toast.LENGTH_SHORT).show()
                        trackingData = trackingData.filter { it.id != entryId }
                    }
                    else -> {
                        android.widget.Toast.makeText(context, result.error ?: "Erreur lors de la suppression", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Erreur: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Load data on composition
    LaunchedEffect(toolInstanceId) {
        loadData()
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with refresh button
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            UI.Text("Historique des entrées", TextType.SUBTITLE)
            
            if (!isLoading) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = { loadData() }
                ) {
                    UI.Text("↻", TextType.LABEL)
                }
            }
        }
        
        // Error message
        if (errorMessage != null) {
            UI.Card(type = CardType.DEFAULT) {
                UI.Text(errorMessage!!, TextType.BODY)
            }
        }
        
        // Loading state
        if (isLoading && trackingData.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                UI.Text("Chargement...", TextType.BODY)
            }
        }
        
        // Empty state
        if (!isLoading && trackingData.isEmpty() && errorMessage == null) {
            UI.Card(type = CardType.DEFAULT) {
                UI.Text("Aucune entrée enregistrée", TextType.BODY)
            }
        }
        
        // Table header
        if (trackingData.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                UI.Text("Date", TextType.CAPTION)
                UI.Text("Item", TextType.CAPTION) 
                UI.Text("Valeur", TextType.CAPTION)
                UI.Text("Actions", TextType.CAPTION)
            }
        }
        
        // Data table
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(trackingData) { entry ->
                TrackingHistoryRow(
                    entry = entry,
                    trackingType = trackingType,
                    onEdit = {
                        editingEntry = entry
                        showEditDialog = true
                    },
                    onDelete = { deleteEntry(entry.id) }
                )
            }
        }
        
        // Edit dialog
        if (showEditDialog && editingEntry != null) {
            val entry = editingEntry!!
            val parsedValue = parseTrackingValue(entry.value)
            
            TrackingItemDialog(
                isVisible = showEditDialog,
                trackingType = trackingType,
                mode = TrackingDialogMode.PREDEFINED_INPUT,
                initialName = entry.name,
                initialUnit = parsedValue.unit,
                initialDefaultValue = parsedValue.amount.toString(),
                onConfirm = { name, unit, defaultValue, _ ->
                    val numericValue = com.assistant.core.utils.NumberFormatting.parseUserInput(defaultValue)
                    if (numericValue != null) {
                        val jsonValue = JSONObject().apply {
                            put("amount", numericValue)
                            put("unit", unit)
                            put("type", trackingType)
                            put("raw", formatDisplayValue(numericValue, unit))
                        }
                        
                        updateEntry(entry.id, jsonValue.toString(), name)
                        showEditDialog = false
                        editingEntry = null
                    } else {
                        android.util.Log.w("TrackingHistory", "Invalid numeric value for update: '$defaultValue'")
                    }
                },
                onCancel = {
                    showEditDialog = false
                    editingEntry = null
                }
            )
        }
    }
}

/**
 * Individual table row for tracking data
 */
@Composable
private fun TrackingHistoryRow(
    entry: TrackingData,
    trackingType: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Date column  
            UI.Text(formatSmartDate(entry.recorded_at), TextType.BODY)
            
            // Item column
            UI.Text(entry.name, TextType.BODY)
            
            // Value column
            UI.Text(formatTrackingValue(entry, trackingType), TextType.BODY)
            
            // Actions column
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = onEdit
                ) {
                    UI.Text("Modifier", TextType.CAPTION)
                }
                
                UI.Button(
                    type = ButtonType.SECONDARY,
                    onClick = onDelete
                ) {
                    UI.Text("×", TextType.CAPTION)
                }
            }
        }
    }

/**
 * Format date with smart relative display
 */
private fun formatSmartDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val dayInMs = 24 * 60 * 60 * 1000L
    
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    
    return when {
        diff < dayInMs && isSameDay(timestamp, now) -> "Aujourd'hui ${timeFormat.format(Date(timestamp))}"
        diff < 2 * dayInMs && isYesterday(timestamp, now) -> "Hier ${timeFormat.format(Date(timestamp))}"
        else -> dateFormat.format(Date(timestamp))
    }
}

/**
 * Check if two timestamps are on the same day
 */
private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
    
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

/**
 * Check if timestamp is yesterday
 */
private fun isYesterday(timestamp: Long, now: Long): Boolean {
    val yesterday = now - 24 * 60 * 60 * 1000L
    return isSameDay(timestamp, yesterday)
}

/**
 * Format tracking value for display based on type
 */
private fun formatTrackingValue(entry: TrackingData, trackingType: String): String {
    return try {
        val valueJson = JSONObject(entry.value)
        valueJson.optString("raw", entry.value)
    } catch (e: Exception) {
        entry.value
    }
}

/**
 * Parse tracking value JSON for editing
 */
private fun parseTrackingValue(valueJson: String): ParsedValue {
    return try {
        val json = JSONObject(valueJson)
        ParsedValue(
            amount = json.optDouble("amount", 0.0),
            unit = json.optString("unit", "")
        )
    } catch (e: Exception) {
        ParsedValue(0.0, "")
    }
}

/**
 * Format display value for consistent presentation
 */
private fun formatDisplayValue(value: Double, unit: String): String {
    val formattedValue = if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        value.toString()
    }
    
    return if (unit.isNotBlank()) {
        "$formattedValue $unit"
    } else {
        formattedValue
    }
}

/**
 * Data class for parsed tracking values
 */
private data class ParsedValue(
    val amount: Double,
    val unit: String
)