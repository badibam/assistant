package com.assistant.tools.tracking.ui

import com.assistant.core.utils.LogManager
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
import com.assistant.core.coordinator.isSuccess
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
 * Creates current period with normalization according to configuration
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
    var currentPeriod by remember { mutableStateOf<Period?>(null) }
    var entriesLimit by remember { mutableStateOf(100) }
    
    // Pagination state
    var currentPage by remember { mutableStateOf(1) }
    var totalEntries by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    
    // App config state
    var dayStartHour by remember { mutableStateOf<Int?>(null) }
    var weekStartDay by remember { mutableStateOf<String?>(null) }
    var isConfigLoading by remember { mutableStateOf(true) }
    
    
    // Load data
    val loadData = {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                // Prepare parameters according to period filter
                val params = mutableMapOf<String, Any>(
                    "operation" to "get_entries",
                    "toolInstanceId" to toolInstanceId,
                    "limit" to entriesLimit,
                    "page" to currentPage
                )
                
                // Add temporal filters according to period type
                when (periodFilter) {
                    PeriodFilterType.ALL -> {
                        // No temporal filter, only limit
                    }
                    PeriodFilterType.HOUR -> {
                        val periodStart = currentPeriod!!.timestamp
                        val periodEnd = periodStart + (60 * 60 * 1000L) // +1 hour
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.DAY -> {
                        val periodStart = currentPeriod!!.timestamp
                        val periodEnd = periodStart + (24 * 60 * 60 * 1000L) // +1 day
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.WEEK -> {
                        val periodStart = currentPeriod!!.timestamp
                        val periodEnd = periodStart + (7 * 24 * 60 * 60 * 1000L) // +1 week
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.MONTH -> {
                        val periodStart = currentPeriod!!.timestamp
                        // For months, calculate start of next month
                        val periodEnd = Calendar.getInstance().apply {
                            timeInMillis = periodStart
                            add(Calendar.MONTH, 1)
                        }.timeInMillis
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                    PeriodFilterType.YEAR -> {
                        val periodStart = currentPeriod!!.timestamp
                        // For years, calculate start of next year
                        val periodEnd = Calendar.getInstance().apply {
                            timeInMillis = periodStart
                            add(Calendar.YEAR, 1)
                        }.timeInMillis
                        params["startTime"] = periodStart
                        params["endTime"] = periodEnd
                    }
                }
                
                val result = coordinator.processUserAction("tool_data.get", params)
                
                when {
                    result.isSuccess -> {
                        val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                        val paginationData = result.data?.get("pagination") as? Map<*, *>
                        
                        // Update pagination data
                        paginationData?.let { pagination ->
                            totalPages = (pagination["totalPages"] as? Number)?.toInt() ?: 1
                            totalEntries = (pagination["totalEntries"] as? Number)?.toInt() ?: 0
                            currentPage = (pagination["currentPage"] as? Number)?.toInt() ?: 1
                        }
                        
                        // Get current timer entry ID to exclude it
                        val activeTimerEntryId = TimerManager.getInstance().timerState.value.entryId
                        
                        trackingData = entriesData.mapNotNull { entryMap ->
                            if (entryMap is Map<*, *>) {
                                try {
                                    val entryId = entryMap["id"] as? String ?: ""
                                    
                                    // Exclude current timer entry (duration = 0)
                                    if (activeTimerEntryId.isNotEmpty() && entryId == activeTimerEntryId) {
                                        return@mapNotNull null
                                    }
                                    
                                    val timestamp = (entryMap["timestamp"] as? Number)?.toLong()
                                    LogManager.tracking("Entry ${entryMap["id"]}: timestamp=$timestamp (${DateUtils.formatFullDateTime(timestamp ?: 0)})")
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
                                    LogManager.tracking("Failed to map entry", "ERROR", e)
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
        LogManager.tracking("=== UpdateEntry start ===")
        LogManager.tracking("updateEntry called: entryId=$entryId, name=$name, dataJson=$dataJson, newTimestamp=$newTimestamp")
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
                
                LogManager.tracking("Final update params: $params")
                
                val result = coordinator.processUserAction("tool_data.update", params)
                
                LogManager.tracking("=== Update result ===")
                LogManager.tracking("Result status: ${result.status}")
                LogManager.tracking("Result error: ${result.error}")
                LogManager.tracking("Result data: ${result.data}")
                
                when {
                    result.isSuccess -> {
                        UI.Toast(context, s.tool("usage_entry_updated"), Duration.SHORT)
                        loadData() // Reload data to show changes
                    }
                    else -> {
                        UI.Toast(context, result.error ?: s.tool("error_entry_update"), Duration.LONG)
                    }
                }
            } catch (e: Exception) {
                UI.Toast(context, s.shared("message_error").format(e.message ?: ""), Duration.LONG)
            }
        }
    }
    
    // Delete entry
    val deleteEntry = { entryId: String ->
        scope.launch {
            try {
                val result = coordinator.processUserAction(
                    "tool_data.delete",
                    mapOf(
                        "tool_type" to "tracking",
                        "operation" to "delete",
                        "id" to entryId
                    )
                )
                
                when {
                    result.isSuccess -> {
                        UI.Toast(context, s.tool("usage_entry_deleted"), Duration.SHORT)
                        trackingData = trackingData.filter { it.id != entryId }
                    }
                    else -> {
                        UI.Toast(context, result.error ?: s.tool("error_entry_deletion"), Duration.LONG)
                    }
                }
            } catch (e: Exception) {
                UI.Toast(context, s.shared("message_error").format(e.message ?: ""), Duration.LONG)
            }
        }
    }
    
    // Load app config on composition
    LaunchedEffect(Unit) {
        try {
            val configResult = coordinator.processUserAction("app_config.get", mapOf("category" to "format"))
            if (configResult.isSuccess) {
                val config = configResult.data?.get("settings") as? Map<String, Any>
                dayStartHour = (config?.get("day_start_hour") as? Number)?.toInt()
                weekStartDay = config?.get("week_start_day") as? String
            }
        } catch (e: Exception) {
            // Config loading failed
        } finally {
            isConfigLoading = false
        }
    }
    
    // Initialize currentPeriod when config is loaded
    if (dayStartHour != null && weekStartDay != null && currentPeriod == null) {
        currentPeriod = Period.now(PeriodType.DAY, dayStartHour!!, weekStartDay!!)
    }
    
    // Show loading state while config is being loaded
    if (isConfigLoading || dayStartHour == null || weekStartDay == null || currentPeriod == null) {
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
                period = currentPeriod!!,
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
                
                // Name header (weight=3f)
                Box(
                    modifier = Modifier.weight(3f).padding(8.dp)
                ) {
                    UI.Text(s.shared("label_name"), TextType.CAPTION)
                }
                
                // Value header (weight=3f)
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
                        LogManager.tracking("Scale JSON data: $json")
                        mapOf(
                            "rating" to json.optInt("rating"),
                            "min_value" to json.optInt("min_value"),
                            "max_value" to json.optInt("max_value"),
                            "min_label" to json.optString("min_label"),
                            "max_label" to json.optString("max_label")
                        ).also { 
                            LogManager.tracking("InitialProperties created: $it")
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
                    LogManager.tracking("TrackingHistory - onConfirm called: name='$name', dataJson=$dataJson, trackingType=$trackingType")
                    
                    LogManager.tracking("TrackingHistory - calling updateEntry: id=${entry.id}, name='$name', dataJson=$dataJson, timestamp=$timestamp")
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
        
        // Name (weight=3f)
        Box(
            modifier = Modifier.weight(3f).padding(8.dp)
        ) {
            UI.Text(
                text = entry.name ?: "",
                type = TextType.BODY
            )
        }
        
        // Value (weight=3f)
        Box(
            modifier = Modifier.weight(3f).padding(8.dp)
        ) {
            UI.Text(
                text = formatTrackingValue(entry, trackingType),
                type = TextType.BODY
            )
        }
        
        // Update (weight=1f)
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
        
        // Delete (weight=1f)
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