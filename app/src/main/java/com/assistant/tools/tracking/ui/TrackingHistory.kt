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
import com.assistant.tools.tracking.ui.components.UniversalTrackingDialog
import com.assistant.tools.tracking.ui.components.ItemType
import com.assistant.tools.tracking.ui.components.InputType
import com.assistant.tools.tracking.ui.components.ActionType
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.tools.tracking.TrackingUtils
import com.assistant.core.utils.DateUtils
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

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
    
    // Date filter state - default to today
    var selectedDate by remember { 
        mutableStateOf(DateUtils.getTodayFormatted())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    
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
                        
                        val allEntries = entriesData.mapNotNull { entryMap ->
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
                        }
                        
                        // Filter by selected date and limit to 100 entries
                        val selectedDateMs = DateUtils.parseDateForFilter(selectedDate)
                        trackingData = allEntries
                            .filter { DateUtils.isOnSameDay(it.recorded_at, selectedDateMs) }
                            .sortedByDescending { it.recorded_at }
                            .take(100)
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
    
    // Update entry - quantity and timestamp can be changed
    val updateEntry = { entryId: String, name: String, properties: Map<String, Any>, newTimestamp: Long? ->
        scope.launch {
            try {
                val params = mutableMapOf<String, Any>(
                    "tool_type" to "tracking",
                    "operation" to "update",
                    "entry_id" to entryId,
                    "name" to name,
                    "type" to trackingType
                )
                
                // Add all properties from the tracking type
                params.putAll(properties)
                
                // Add timestamp if provided
                newTimestamp?.let { 
                    params["recorded_at"] = it.toString()
                }
                
                android.util.Log.d("TRACKING_DEBUG", "TrackingHistory - Updating entry with params: $params")
                
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
    
    // Load data on composition and when date changes
    LaunchedEffect(toolInstanceId, selectedDate) {
        loadData()
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with date selector and refresh button
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Date selector as title
            UI.FormField(
                label = "",
                value = selectedDate,
                onChange = { },
                fieldType = FieldType.TEXT,
                required = true,
                readonly = true,
                onClick = { 
                    showDatePicker = true
                }
            )
            
            if (!isLoading) {
                UI.ActionButton(
                    action = ButtonAction.REFRESH,
                    display = ButtonDisplay.ICON,
                    onClick = { loadData() }
                )
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date header (weight=3f)
                Box(
                    modifier = Modifier.weight(3f).padding(8.dp)
                ) {
                    UI.Text("Date", TextType.CAPTION)
                }
                
                // Nom header (weight=3f)
                Box(
                    modifier = Modifier.weight(3f).padding(8.dp)
                ) {
                    UI.Text("Item", TextType.CAPTION)
                }
                
                // Valeur header (weight=3f)
                Box(
                    modifier = Modifier.weight(3f).padding(8.dp)
                ) {
                    UI.Text("Valeur", TextType.CAPTION)
                }
                
                // Actions headers (weight=1f chaque)
                Box(
                    modifier = Modifier.weight(2f),
                    contentAlignment = Alignment.Center
                ) {
                    UI.Text("Actions", TextType.CAPTION)
                }
            }
        }
        
        // Data table - limit items and make it non-scrollable (parent page is scrollable)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            trackingData.forEach { entry ->
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
            
            // Show count info
            if (trackingData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                UI.Card(type = CardType.DEFAULT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        UI.Text(
                            "${trackingData.size} entrée(s) pour le $selectedDate" + 
                            if (trackingData.size == 100) " (limite atteinte)" else "",
                            TextType.CAPTION
                        )
                    }
                }
            }
        }
        
        // Edit dialog
        if (showEditDialog && editingEntry != null) {
            val entry = editingEntry!!
            
            // Parse JSON directly for each type instead of using limited ParsedValue
            val initialProperties = try {
                val json = JSONObject(entry.value)
                when (trackingType) {
                    "numeric" -> mapOf(
                        "quantity" to json.optString("quantity", ""),
                        "unit" to json.optString("unit", "")
                    )
                    "boolean" -> mapOf(
                        "state" to json.optBoolean("state", false),
                        "true_label" to json.optString("true_label", "Oui"),
                        "false_label" to json.optString("false_label", "Non")
                    )
                    "scale" -> mapOf(
                        "value" to json.optInt("value", 5),
                        "min_value" to json.optInt("min_value", 1),
                        "max_value" to json.optInt("max_value", 10),
                        "min_label" to json.optString("min_label", ""),
                        "max_label" to json.optString("max_label", "")
                    )
                    "text" -> mapOf(
                        "text" to json.optString("text", "")
                    )
                    "choice" -> {
                        val availableOptions = json.optJSONArray("available_options")?.let { array ->
                            (0 until array.length()).map { array.optString(it, "") }
                        } ?: emptyList<String>()
                        mapOf(
                            "selected_option" to json.optString("selected_option", ""),
                            "available_options" to availableOptions
                        )
                    }
                    "counter" -> mapOf(
                        "increment" to json.optInt("increment", 1)
                    )
                    "timer" -> mapOf(
                        "activity" to json.optString("activity", ""),
                        "duration_minutes" to json.optInt("duration_minutes", 0)
                    )
                    else -> emptyMap()
                }
            } catch (e: Exception) {
                emptyMap<String, Any>()
            }
            
            UniversalTrackingDialog(
                isVisible = showEditDialog,
                trackingType = trackingType,
                config = JSONObject(), // Empty config for editing existing entries
                itemType = null, // History editing - no itemType
                inputType = InputType.ENTRY,
                actionType = ActionType.UPDATE,
                initialName = entry.name,
                initialProperties = initialProperties,
                initialDate = DateUtils.formatDateForDisplay(entry.recorded_at),
                initialTime = DateUtils.formatTimeForDisplay(entry.recorded_at),
                onConfirm = { name, properties, _, date, time ->
                    android.util.Log.d("TRACKING_DEBUG", "TrackingHistory - onConfirm called: name='$name', properties=$properties, trackingType=$trackingType")
                    
                    // Update entry with new values and datetime
                    val newTimestamp = DateUtils.combineDateTime(date, time)
                    
                    android.util.Log.d("TRACKING_DEBUG", "TrackingHistory - calling updateEntry: id=${entry.id}, name='$name', properties=$properties, timestamp=$newTimestamp")
                    updateEntry(entry.id, name, properties, newTimestamp)
                    showEditDialog = false
                    editingEntry = null
                },
                onCancel = {
                    showEditDialog = false
                    editingEntry = null
                }
            )
        }
        
        // Date picker dialog
        if (showDatePicker) {
            UI.DatePicker(
                selectedDate = selectedDate,
                onDateSelected = { newDate ->
                    selectedDate = newDate
                },
                onDismiss = {
                    showDatePicker = false
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date (weight=3f)
        Box(
            modifier = Modifier.weight(3f).padding(8.dp)
        ) {
            UI.Text(
                text = DateUtils.formatSmartDateTime(entry.recorded_at),
                type = TextType.BODY
            )
        }
        
        // Nom (weight=3f)  
        Box(
            modifier = Modifier.weight(3f).padding(8.dp)
        ) {
            UI.Text(
                text = entry.name,
                type = TextType.BODY
            )
        }
        
        // Valeur (weight=3f)
        Box(
            modifier = Modifier.weight(3f).padding(8.dp)
        ) {
            UI.Text(
                text = formatTrackingValue(entry, trackingType),
                type = TextType.BODY
            )
        }
        
        // Modifier (weight=1f)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            UI.ActionButton(
                action = ButtonAction.EDIT,
                display = ButtonDisplay.ICON,
                size = Size.S,
                onClick = onEdit
            )
        }
        
        // Supprimer (weight=1f)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                display = ButtonDisplay.ICON,
                size = Size.S,
                requireConfirmation = true,
                onClick = onDelete
            )
        }
    }
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
            quantity = json.optDouble("quantity", 0.0),
            unit = json.optString("unit", "")
        )
    } catch (e: Exception) {
        ParsedValue(0.0, "")
    }
}


/**
 * Data class for parsed tracking values
 */
private data class ParsedValue(
    val quantity: Double,
    val unit: String
)