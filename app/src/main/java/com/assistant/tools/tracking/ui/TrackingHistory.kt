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
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.tools.tracking.ui.components.TrackingEntryDialog
import com.assistant.tools.tracking.ui.components.ItemType
import com.assistant.tools.tracking.ui.components.ActionType
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.strings.Strings
import com.assistant.tools.tracking.TrackingUtils
import com.assistant.tools.tracking.timer.TimerManager
import com.assistant.core.utils.DateUtils
import com.assistant.core.ui.components.PeriodFilterType
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.PeriodSelector
import com.assistant.core.ui.components.normalizeTimestampWithConfig
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

/**
 * Crée une période actuelle avec normalisation selon la configuration
 */
private fun createCurrentPeriod(type: PeriodType, dayStartHour: Int, weekStartDay: String): Period {
    val now = System.currentTimeMillis()
    val normalizedTimestamp = normalizeTimestampWithConfig(now, type, dayStartHour, weekStartDay)
    return Period(normalizedTimestamp, type)
}

/**
 * Responsive table display for tracking data history with CRUD operations
 * Shows chronological list of entries with edit/delete functionality
 */
@Composable
fun TrackingHistory(
    toolInstanceId: String,
    trackingType: String,
    refreshTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    
    // State
    var trackingData by remember { mutableStateOf<List<ToolDataEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<ToolDataEntity?>(null) }
    
    // New filter system state
    var periodFilter by remember { mutableStateOf(PeriodFilterType.DAY) }
    var currentPeriod by remember { mutableStateOf(Period.now(PeriodType.DAY)) }
    var entriesLimit by remember { mutableStateOf(100) }
    
    // Pagination state
    var currentPage by remember { mutableStateOf(1) }
    var totalEntries by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    
    // App config state - null jusqu'au chargement
    var dayStartHour by remember { mutableStateOf<Int?>(null) }
    var weekStartDay by remember { mutableStateOf<String?>(null) }
    
    // Legacy state for compatibility during transition
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
                // Préparer les paramètres selon le filtre de période
                val params = mutableMapOf<String, Any>(
                    "operation" to "get_entries",
                    "toolInstanceId" to toolInstanceId,
                    "limit" to entriesLimit,
                    "page" to currentPage
                )
                
                // Ajouter les filtres temporels selon le type de période
                when (periodFilter) {
                    PeriodFilterType.ALL -> {
                        // Pas de filtre temporel, juste la limite
                    }
                    PeriodFilterType.HOUR -> {
                        val periodStart = currentPeriod.timestamp
                        val periodEnd = periodStart + (60 * 60 * 1000L) // +1 hour
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.DAY -> {
                        val periodStart = currentPeriod.timestamp
                        val periodEnd = periodStart + (24 * 60 * 60 * 1000L) // +1 day
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.WEEK -> {
                        val periodStart = currentPeriod.timestamp
                        val periodEnd = periodStart + (7 * 24 * 60 * 60 * 1000L) // +1 week
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.MONTH -> {
                        val periodStart = currentPeriod.timestamp
                        // Pour les mois, on calcule le début du mois suivant
                        val periodEnd = Calendar.getInstance().apply {
                            timeInMillis = periodStart
                            add(Calendar.MONTH, 1)
                        }.timeInMillis
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.YEAR -> {
                        val periodStart = currentPeriod.timestamp
                        // Pour les années, on calcule le début de l'année suivante
                        val periodEnd = Calendar.getInstance().apply {
                            timeInMillis = periodStart
                            add(Calendar.YEAR, 1)
                        }.timeInMillis
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                }
                
                val result = coordinator.processUserAction("get->tool_data", params)
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                        val paginationData = result.data?.get("pagination") as? Map<*, *>
                        
                        // Mise à jour des données de pagination
                        paginationData?.let { pagination ->
                            totalPages = (pagination["totalPages"] as? Number)?.toInt() ?: 1
                            totalEntries = (pagination["totalEntries"] as? Number)?.toInt() ?: 0
                            currentPage = (pagination["currentPage"] as? Number)?.toInt() ?: 1
                        }
                        
                        // Obtenir l'ID de l'entrée timer en cours pour l'exclure
                        val activeTimerEntryId = TimerManager.getInstance().timerState.value.entryId
                        
                        trackingData = entriesData.mapNotNull { entryMap ->
                            if (entryMap is Map<*, *>) {
                                try {
                                    val entryId = entryMap["id"] as? String ?: ""
                                    
                                    // Exclure l'entrée du timer en cours (durée = 0)
                                    if (activeTimerEntryId.isNotEmpty() && entryId == activeTimerEntryId) {
                                        return@mapNotNull null
                                    }
                                    
                                    val timestamp = (entryMap["timestamp"] as? Number)?.toLong()
                                    Log.d("TIMESTAMP_DEBUG", "Entry ${entryMap["id"]}: timestamp=$timestamp (${DateUtils.formatFullDateTime(timestamp ?: 0)})")
                                    ToolDataEntity(
                                        id = entryId,
                                        toolInstanceId = entryMap["toolInstanceId"] as? String ?: "",
                                        tooltype = entryMap["tooltype"] as? String ?: "tracking",
                                        timestamp = timestamp,
                                        name = entryMap["name"] as? String,
                                        data = entryMap["data"] as? String ?: "",
                                        createdAt = (entryMap["createdAt"] as? Number)?.toLong() ?: 0L,
                                        updatedAt = (entryMap["updatedAt"] as? Number)?.toLong() ?: 0L
                                    )
                                } catch (e: Exception) {
                                    Log.e("TrackingHistory", "Failed to map entry", e)
                                    null
                                }
                            } else null
                        }
                    }
                    else -> {
                        errorMessage = result.error ?: s.shared("tools_error_loading")
                    }
                }
            } catch (e: Exception) {
                errorMessage = s.shared("message_error").format(e.message ?: "")
            } finally {
                isLoading = false
            }
        }
    }
    
    // Update entry - name, data and timestamp can be changed
    val updateEntry = { entryId: String, name: String, dataJson: String, newTimestamp: Long? ->
        android.util.Log.d("VALDEBUG", "=== UPDATEENTRY START ===")
        android.util.Log.d("VALDEBUG", "updateEntry called: entryId=$entryId, name=$name, dataJson=$dataJson, newTimestamp=$newTimestamp")
        scope.launch {
            try {
                val params = mutableMapOf<String, Any>(
                    "id" to entryId,
                    "name" to name,
                    "data" to JSONObject(dataJson)
                )
                
                // Add timestamp if provided
                newTimestamp?.let { 
                    params["timestamp"] = it
                }
                
                android.util.Log.d("VALDEBUG", "Final update params: $params")
                
                val result = coordinator.processUserAction("update->tool_data", params)
                
                android.util.Log.d("VALDEBUG", "=== UPDATE RESULT ===")
                android.util.Log.d("VALDEBUG", "Result status: ${result.status}")
                android.util.Log.d("VALDEBUG", "Result error: ${result.error}")
                android.util.Log.d("VALDEBUG", "Result data: ${result.data}")
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        android.widget.Toast.makeText(context, s.tool("usage_entry_updated"), android.widget.Toast.LENGTH_SHORT).show()
                        loadData() // Reload data to show changes
                    }
                    else -> {
                        android.widget.Toast.makeText(context, result.error ?: s.tool("error_entry_update"), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, s.shared("message_error").format(e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
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
                        "id" to entryId
                    )
                )
                
                when (result.status) {
                    CommandStatus.SUCCESS -> {
                        android.widget.Toast.makeText(context, s.tool("usage_entry_deleted"), android.widget.Toast.LENGTH_SHORT).show()
                        trackingData = trackingData.filter { it.id != entryId }
                    }
                    else -> {
                        android.widget.Toast.makeText(context, result.error ?: s.tool("error_entry_deletion"), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, s.shared("message_error").format(e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Load app config on composition
    LaunchedEffect(Unit) {
        try {
            val configResult = coordinator.processUserAction("get->app_config", mapOf("category" to "temporal"))
            if (configResult.status == CommandStatus.SUCCESS) {
                val config = configResult.data?.get("settings") as? Map<String, Any>
                dayStartHour = (config?.get("day_start_hour") as? Number)?.toInt()
                weekStartDay = config?.get("week_start_day") as? String
            }
        } catch (e: Exception) {
            // Config loading failed
        }
    }
    
    // Attendre que la config soit chargée
    if (dayStartHour == null || weekStartDay == null) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            UI.Text(text = s.shared("tools_loading_config"), type = TextType.BODY)
        }
        return
    }

    // Reset page when filters change
    LaunchedEffect(periodFilter, currentPeriod, entriesLimit) {
        currentPage = 1
    }
    
    // Load data on composition and when filters or pagination change
    LaunchedEffect(toolInstanceId, periodFilter, currentPeriod, entriesLimit, currentPage, refreshTrigger) {
        loadData()
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Level 1: Global filters
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Period filter dropdown
            Box(modifier = Modifier.weight(1f)) {
                UI.FormSelection(
                    label = "",
                    options = listOf(s.shared("period_all"), s.shared("period_hour"), s.shared("period_day"), s.shared("period_week"), s.shared("period_month"), s.shared("period_year")),
                    selected = when(periodFilter) {
                        PeriodFilterType.ALL -> s.shared("period_all")
                        PeriodFilterType.HOUR -> s.shared("period_hour")
                        PeriodFilterType.DAY -> s.shared("period_day")
                        PeriodFilterType.WEEK -> s.shared("period_week")
                        PeriodFilterType.MONTH -> s.shared("period_month")
                        PeriodFilterType.YEAR -> s.shared("period_year")
                    },
                    onSelect = { selection ->
                        periodFilter = when(selection) {
                            s.shared("period_all") -> PeriodFilterType.ALL
                            s.shared("period_hour") -> PeriodFilterType.HOUR
                            s.shared("period_day") -> PeriodFilterType.DAY
                            s.shared("period_week") -> PeriodFilterType.WEEK
                            s.shared("period_month") -> PeriodFilterType.MONTH
                            s.shared("period_year") -> PeriodFilterType.YEAR
                            else -> PeriodFilterType.DAY
                        }
                        // Update current period when filter changes
                        currentPeriod = when(periodFilter) {
                            PeriodFilterType.ALL -> createCurrentPeriod(PeriodType.DAY, dayStartHour!!, weekStartDay!!) // Default for ALL
                            PeriodFilterType.HOUR -> createCurrentPeriod(PeriodType.HOUR, dayStartHour!!, weekStartDay!!)
                            PeriodFilterType.DAY -> createCurrentPeriod(PeriodType.DAY, dayStartHour!!, weekStartDay!!)
                            PeriodFilterType.WEEK -> createCurrentPeriod(PeriodType.WEEK, dayStartHour!!, weekStartDay!!)
                            PeriodFilterType.MONTH -> createCurrentPeriod(PeriodType.MONTH, dayStartHour!!, weekStartDay!!)
                            PeriodFilterType.YEAR -> createCurrentPeriod(PeriodType.YEAR, dayStartHour!!, weekStartDay!!)
                        }
                    }
                )
            }
            
            // Entries limit dropdown
            Box(modifier = Modifier.weight(1f)) {
                UI.FormSelection(
                    label = "",
                    options = listOf("10", "25", "100", "250", "1000"),
                    selected = entriesLimit.toString(),
                    onSelect = { selection -> 
                        entriesLimit = selection.toInt()
                    }
                )
            }
            
            // Refresh button
            Box(contentAlignment = Alignment.Center) {
                if (!isLoading) {
                    UI.ActionButton(
                        action = ButtonAction.REFRESH,
                        display = ButtonDisplay.ICON,
                        onClick = { loadData() }
                    )
                } else {
                    UI.CenteredText("...", TextType.BODY)
                }
            }
        }
        
        // Level 2: Period selector (hidden for ALL filter)
        if (periodFilter != PeriodFilterType.ALL) {
            PeriodSelector(
                period = currentPeriod,
                onPeriodChange = { newPeriod ->
                    currentPeriod = newPeriod
                    loadData()
                },
                dayStartHour = dayStartHour!!,
                weekStartDay = weekStartDay!!
            )
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
                UI.CenteredText(s.shared("tools_loading"), TextType.BODY)
            }
        }
        
        // Empty state
        if (!isLoading && trackingData.isEmpty() && errorMessage == null) {
            UI.Card(type = CardType.DEFAULT) {
                UI.Text(s.tool("usage_no_entries"), TextType.BODY)
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
                    UI.Text(s.shared("label_date"), TextType.CAPTION)
                }
                
                // Nom header (weight=3f)
                Box(
                    modifier = Modifier.weight(3f).padding(8.dp)
                ) {
                    UI.Text(s.shared("label_name"), TextType.CAPTION)
                }
                
                // Valeur header (weight=3f)
                Box(
                    modifier = Modifier.weight(3f).padding(8.dp)
                ) {
                    UI.Text(s.tool("usage_label_value"), TextType.CAPTION)
                }
                
                // Actions headers (weight=1f chaque)
                Box(
                    modifier = Modifier.weight(2f),
                    contentAlignment = Alignment.Center
                ) {
                    UI.CenteredText(s.shared("label_actions"), TextType.CAPTION)
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
            
            // Pagination controls
            if (trackingData.isNotEmpty() && totalPages > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                UI.Pagination(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onPageChange = { newPage -> currentPage = newPage }
                )
            }
        }
        
        // Edit dialog
        if (showEditDialog && editingEntry != null) {
            val entry = editingEntry!!
            
            // Parse JSON directly for each type instead of using limited ParsedValue
            val initialProperties = try {
                val json = JSONObject(entry.data)
                when (trackingType) {
                    "numeric" -> mapOf(
                        "quantity" to json.optString("quantity", ""),
                        "unit" to json.optString("unit", "")
                    )
                    "boolean" -> mapOf(
                        "state" to json.optBoolean("state", false),
                        "true_label" to json.optString("true_label", s.tool("config_default_true_label")),
                        "false_label" to json.optString("false_label", s.tool("config_default_false_label"))
                    )
                    "scale" -> {
                        android.util.Log.d("SCALE_DEBUG", "Données JSON scale: $json")
                        mapOf(
                            "rating" to json.optInt("rating"),
                            "min_value" to json.optInt("min_value"),
                            "max_value" to json.optInt("max_value"),
                            "min_label" to json.optString("min_label"),
                            "max_label" to json.optString("max_label")
                        ).also { 
                            android.util.Log.d("SCALE_DEBUG", "InitialProperties créées: $it")
                        }
                    }
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
                        "duration_seconds" to json.optInt("duration_seconds", 0)
                    )
                    else -> emptyMap()
                }
            } catch (e: Exception) {
                emptyMap<String, Any>()
            }
            
            TrackingEntryDialog(
                isVisible = showEditDialog,
                trackingType = trackingType,
                config = JSONObject(), // Empty config for editing existing entries
                itemType = null, // History editing - no itemType
                actionType = ActionType.UPDATE,
                toolInstanceId = toolInstanceId,
                initialName = entry.name ?: "",
                initialData = initialProperties,
                initialTimestamp = entry.timestamp ?: System.currentTimeMillis(),
                onConfirm = { name, dataJson, _, timestamp ->
                    android.util.Log.d("TRACKING_DEBUG", "TrackingHistory - onConfirm called: name='$name', dataJson=$dataJson, trackingType=$trackingType")
                    
                    android.util.Log.d("TRACKING_DEBUG", "TrackingHistory - calling updateEntry: id=${entry.id}, name='$name', dataJson=$dataJson, timestamp=$timestamp")
                    updateEntry(entry.id, name, dataJson, timestamp)
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
    entry: ToolDataEntity,
    trackingType: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date (weight=3f)
        Box(
            modifier = Modifier.weight(3f).padding(8.dp)
        ) {
            UI.Text(
                text = DateUtils.formatFullDateTime(entry.timestamp ?: System.currentTimeMillis()),
                type = TextType.BODY
            )
        }
        
        // Nom (weight=3f)  
        Box(
            modifier = Modifier.weight(3f).padding(8.dp)
        ) {
            UI.Text(
                text = entry.name ?: "",
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
private fun formatTrackingValue(entry: ToolDataEntity, trackingType: String): String {
    return try {
        val dataJson = JSONObject(entry.data)
        dataJson.optString("raw", entry.data)
    } catch (e: Exception) {
        entry.data
    }
}

/**
 * Parse tracking value JSON for editing
 */
private fun parseTrackingValue(dataJson: String): ParsedValue {
    return try {
        val json = JSONObject(dataJson)
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