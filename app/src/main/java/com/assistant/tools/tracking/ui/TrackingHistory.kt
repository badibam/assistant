package com.assistant.tools.tracking.ui

import com.assistant.core.ui.NullablePeriodSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.assistant.tools.tracking.ui.components.TrackingEntryDraft
import com.assistant.tools.tracking.TrackingConfig
import com.assistant.tools.tracking.TrackingToolType
import com.assistant.core.fields.FieldContainer
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldValue
import com.assistant.core.fields.RunningDurations
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DateUtils
import com.assistant.core.ui.components.PeriodFilterType
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.SinglePeriodSelector
import com.assistant.core.ui.components.normalizeTimestampWithConfig
import com.assistant.core.ui.components.getPeriodEndTimestamp
import kotlinx.coroutines.launch
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject
import java.util.*

/**
 * Creates current period with normalization according to configuration
 */
private fun createCurrentPeriod(type: PeriodType): Period {
    val now = System.currentTimeMillis()
    val normalizedTimestamp = normalizeTimestampWithConfig(now, type)
    return Period(normalizedTimestamp, type)
}

/**
 * Responsive table display for tracking data history with CRUD operations
 * Shows chronological list of entries with edit/delete functionality
 */
@Composable
fun TrackingHistory(
    toolInstanceId: String,
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
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    // The entry being edited is kept by id and resolved from the loaded data, so the edit
    // dialog survives a rotation
    var editingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingEntry = editingEntryId?.let { id -> trackingData.find { it.id == id } }
    
    // New filter system state
    var periodFilter by rememberSaveable { mutableStateOf(PeriodFilterType.DAY) }
    var currentPeriod by rememberSaveable(stateSaver = NullablePeriodSaver) { mutableStateOf<Period?>(null) }
    var entriesLimit by rememberSaveable { mutableStateOf(100) }
    
    // Pagination state
    var currentPage by rememberSaveable { mutableStateOf(1) }
    var totalEntries by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    
    // Tool instance config: the value's field and the user's fields are read from it
    var toolConfig by remember { mutableStateOf<JSONObject?>(null) }
    val valueField = remember(toolConfig) {
        toolConfig?.let { config -> TrackingToolType.getEntryFields(config, context).data.firstOrNull { it.definition.name == "value" }?.definition }
    }

    // Load tool instance config once
    LaunchedEffect(toolInstanceId) {
        val configResult = coordinator.processUserAction("tools.get", mapOf(
            "tool_instance_id" to toolInstanceId
        ))

        if (configResult.isSuccess) {
            val toolData = configResult.data?.get("tool_instance") as? Map<*, *>
            toolData?.let { data ->
                val configJson = JsonUtils.toJSONObject(data["config"] as? Map<String, Any?> ?: emptyMap())
                try {
                    toolConfig = configJson
                    LogManager.tracking("TrackingHistory - Loaded tool config")
                } catch (e: Exception) {
                    LogManager.tracking("Error parsing tool config: ${e.message}", "ERROR")
                }
            }
        }
    }

    // Load data. Run only by the LaunchedEffect below: a change of filter, page or refresh
    // cancels the load in flight, so two loads never race and flash the list in turn
    val loadData: suspend () -> Unit = {
        run {
            isLoading = true
            errorMessage = null
            
            try {
                // Prepare parameters according to period filter
                val params = mutableMapOf<String, Any>(
                    "operation" to "get_entries",
                    "tool_instance_id" to toolInstanceId,
                    "limit" to entriesLimit,
                    "page" to currentPage
                )
                
                // The period as a filter on timestamp, from its first millisecond to its last:
                // worked out in the app's timezone by the period functions, so a day lasts 23
                // or 25 hours when the clocks change instead of a fixed 24.
                if (periodFilter != PeriodFilterType.ALL) {
                    val period = currentPeriod!!
                    params["filters"] = listOf(
                        mapOf("field" to "timestamp", "op" to ">=", "value" to period.timestamp),
                        mapOf("field" to "timestamp", "op" to "<=", "value" to getPeriodEndTimestamp(period))
                    )
                }
                
                val result = coordinator.processUserAction("tool_data.get", params)
                
                when {
                    result.isSuccess -> {
                        val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
                        val paginationData = result.data?.get("pagination") as? Map<*, *>
                        
                        // Update pagination data
                        paginationData?.let { pagination ->
                            totalPages = (pagination["total_pages"] as? Number)?.toInt() ?: 1
                            totalEntries = (pagination["total_entries"] as? Number)?.toInt() ?: 0
                            currentPage = (pagination["current_page"] as? Number)?.toInt() ?: 1
                        }
                        
                        trackingData = entriesData.mapNotNull { entryMap ->
                            if (entryMap is Map<*, *>) {
                                try {
                                    val entryId = entryMap["id"] as? String ?: ""
                                    
                                    // Milliseconds from the service, as stored. An entry
                                    // without one is skipped rather than shown at the present
                                    // moment, which would read as an entry recorded just now.
                                    val timestamp = (entryMap["timestamp"] as? Number)?.toLong()
                                        ?: return@mapNotNull null
                                    LogManager.tracking("Entry ${entryMap["id"]}: timestamp=$timestamp (${com.assistant.core.utils.DateTimeFormatter.formatForDisplay(timestamp, context)})")
                                    ToolDataEntity(
                                        id = entryId,
                                        toolInstanceId = entryMap["tool_instance_id"] as? String ?: "",
                                        tooltype = entryMap["tooltype"] as? String ?: "tracking",
                                        timestamp = timestamp,
                                        name = entryMap["name"] as? String,
                                        data = (entryMap["data"] as? Map<*, *>)?.let {
                                            JsonUtils.toJSONObject(it.entries.associate { (k, v) -> k.toString() to v }).toString()
                                        } ?: "{}",
                                        createdAt = (entryMap["created_at"] as? Number)?.toLong() ?: 0L,
                                        updatedAt = (entryMap["updated_at"] as? Number)?.toLong() ?: 0L,
                                        extra = (entryMap["extra"] as? Map<*, *>)?.let {
                                            JsonUtils.toJSONObject(it.entries.associate { (k, v) -> k.toString() to v }).toString()
                                        },
                                        state = (entryMap["state"] as? Map<*, *>)?.let {
                                            JsonUtils.toJSONObject(it.entries.associate { (k, v) -> k.toString() to v }).toString()
                                        }
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // A newer load took over: not an error
            } catch (e: Exception) {
                errorMessage = s.shared("message_error").format(e.message ?: "")
            } finally {
                isLoading = false
            }
        }
    }
    
    // Update entry: its name, moment, value and unit, and the user's fields
    val updateEntry = { entryId: String, draft: TrackingEntryDraft ->
        scope.launch {
            try {
                val params = mutableMapOf<String, Any>(
                    "id" to entryId,
                    "name" to draft.name,
                    "timestamp" to draft.timestamp,
                    "data" to TrackingConfig.entryData(draft.value, draft.unit),
                    "extra" to JSONObject(draft.extra)
                )
                val result = coordinator.processUserAction("tool_data.update", params)

                when {
                    result.isSuccess -> {
                        UI.Toast(context, s.tool("usage_entry_updated"), Duration.SHORT)
                        // The data change notification reloads the list
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
                        "tooltype" to "tracking",
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
    
    // Initialize currentPeriod when first loaded
    if (currentPeriod == null) {
        currentPeriod = Period.now(PeriodType.DAY)
    }
    
    // Back to the first page when a filter changes
    OnChangedEffect("$periodFilter|$currentPeriod|$entriesLimit") { currentPage = 1 }
    
    // Load data on composition and when filters or pagination change
    var reloadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(toolInstanceId, periodFilter, currentPeriod, entriesLimit, currentPage, refreshTrigger, reloadCount) {
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
                            PeriodFilterType.ALL -> createCurrentPeriod(PeriodType.DAY) // Default for ALL
                            PeriodFilterType.HOUR -> createCurrentPeriod(PeriodType.HOUR)
                            PeriodFilterType.DAY -> createCurrentPeriod(PeriodType.DAY)
                            PeriodFilterType.WEEK -> createCurrentPeriod(PeriodType.WEEK)
                            PeriodFilterType.MONTH -> createCurrentPeriod(PeriodType.MONTH)
                            PeriodFilterType.YEAR -> createCurrentPeriod(PeriodType.YEAR)
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
                // Stays in place while loading, only disabled: swapping it for a marker made
                // the row jump twice on every load
                UI.ActionButton(
                    action = ButtonAction.REFRESH,
                    display = ButtonDisplay.ICON,
                    enabled = !isLoading,
                    onClick = { reloadCount++ }
                )
            }
        }
        
        // Level 2: Period selector (hidden for ALL filter)
        if (periodFilter != PeriodFilterType.ALL) {
            SinglePeriodSelector(
                period = currentPeriod!!,
                onPeriodChange = { newPeriod ->
                    currentPeriod = newPeriod
                }
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
                    valueField = valueField,
                    onEdit = {
                        editingEntryId = entry.id
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
        if (showEditDialog && editingEntry != null && toolConfig != null) {
            val entry = editingEntry!!
            val data = JSONObject(entry.data)
            TrackingEntryDialog(
                config = toolConfig!!,
                title = s.tool("usage_dialog_edit_entry"),
                dialogType = DialogType.EDIT,
                initial = TrackingEntryDraft(
                    name = entry.name ?: "",
                    timestamp = entry.timestamp ?: System.currentTimeMillis(),
                    value = data.opt("value")?.let { if (it is org.json.JSONArray) JsonUtils.toList(it) else it },
                    unit = data.optString("unit").takeIf { it.isNotEmpty() },
                    extra = entry.extra?.let { JsonUtils.toMap(JSONObject(it)) } ?: emptyMap()
                ),
                nameEditable = true,
                offerShortcut = false,
                onConfirm = { draft ->
                    updateEntry(entry.id, draft)
                    showEditDialog = false
                    editingEntryId = null
                },
                onCancel = {
                    showEditDialog = false
                    editingEntryId = null
                }
            )
        }

    }
}

/**
 * Individual table row for tracking data: its moment, its name, and its value drawn by the
 * value's field type, with its unit. A running stopwatch shows the time so far.
 */
@Composable
private fun TrackingHistoryRow(
    entry: ToolDataEntity,
    valueField: FieldDefinition?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val data = remember(entry.data) { JSONObject(entry.data) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(3f).padding(8.dp)) {
            UI.Text(
                text = com.assistant.core.utils.DateTimeFormatter.formatForDisplay(entry.timestamp ?: entry.createdAt, context),
                type = TextType.BODY
            )
        }

        Box(modifier = Modifier.weight(3f).padding(8.dp)) {
            UI.Text(text = entry.name ?: "", type = TextType.BODY)
        }

        Column(modifier = Modifier.weight(3f).padding(8.dp)) {
            if (valueField != null) {
                val state = entry.state?.let { JSONObject(it) }
                val stored = data.opt("value")?.let { if (it is org.json.JSONArray) JsonUtils.toList(it) else it }
                val isRunning = RunningDurations.startedAt(state, FieldContainer.DATA, "value") != null
                val shown = if (isRunning) {
                    RunningDurations.currentValue((stored as? Number)?.toLong(), state, FieldContainer.DATA, "value", System.currentTimeMillis())
                } else stored
                FieldValue(valueField, shown, context)
                data.optString("unit").takeIf { it.isNotEmpty() }?.let { UI.Text(it, TextType.CAPTION) }
                if (isRunning) UI.Text(Strings.`for`(tool = "tracking", context = context).tool("usage_timer_running"), TextType.CAPTION)
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            UI.ActionButton(action = ButtonAction.EDIT, display = ButtonDisplay.ICON, size = Size.S, onClick = onEdit)
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
