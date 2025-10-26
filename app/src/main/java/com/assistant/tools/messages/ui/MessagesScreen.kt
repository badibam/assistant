package com.assistant.tools.messages.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.ScheduleConfig
import com.assistant.tools.messages.ui.components.EditMessageDialog
import com.assistant.tools.messages.ui.components.MessageDialogData
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class for execution entries (messages received)
 */
data class ExecutionEntry(
    val messageId: String,
    val messageTitle: String,
    val executionIndex: Int,
    val scheduledTime: Long,
    val sentAt: Long?,
    val status: String,
    val titleSnapshot: String,
    val contentSnapshot: String?,
    val read: Boolean,
    val archived: Boolean
)

/**
 * Data class for message templates (messages management)
 */
data class MessageTemplate(
    val id: String,
    val title: String,
    val content: String?,
    val schedule: ScheduleConfig?,
    val priority: String,
    val executionCount: Int
)

/**
 * Main screen for Messages tool instance
 *
 * Displays 2 tabs:
 * - Tab 1 "Messages reçus": Execution history with filters (unread/read/archived)
 * - Tab 2 "Gestion messages": Message templates CRUD with schedule configuration
 *
 * Pattern reference: NotesScreen (structure), first tab pattern in project
 */
@Composable
fun MessagesScreen(
    toolInstanceId: String,
    zoneName: String,
    onNavigateBack: () -> Unit,
    onConfigureClick: () -> Unit = {}
) {
    LogManager.ui("MessagesScreen called with toolInstanceId: $toolInstanceId")

    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // State
    var toolInstance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Tab state (survives rotation)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Load tool instance data
    LaunchedEffect(toolInstanceId) {
        coordinator.executeWithLoading(
            operation = "tools.get",
            params = mapOf("tool_instance_id" to toolInstanceId),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            toolInstance = result.mapSingleData("tool_instance") { map -> map }
        }
    }

    // Observe data changes and refresh
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> {
                    if (event.toolInstanceId == toolInstanceId) {
                        refreshTrigger++
                    }
                }
                else -> {} // Ignore other events
            }
        }
    }

    // Parse configuration
    val config = remember(toolInstance) {
        val configJson = toolInstance?.get("config_json") as? String ?: "{}"
        try {
            JSONObject(configJson)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    val defaultPriority = remember(config) {
        config.optString("default_priority", "default")
    }

    // Error message display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    // Early return for loading state
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(s.shared("tools_loading"), TextType.BODY)
        }
        return
    }

    // Main screen layout
    Column(modifier = Modifier.fillMaxSize()) {
        // Tool header (fixed at top)
        val toolName = config.optString("name", s.tool("display_name"))
        val toolDescription = config.optString("description", "")

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            UI.PageHeader(
                title = toolName,
                subtitle = toolDescription.takeIf { it.isNotBlank() },
                icon = config.optString("icon_name", "notification"),
                leftButton = ButtonAction.BACK,
                rightButton = ButtonAction.CONFIGURE,
                onLeftClick = onNavigateBack,
                onRightClick = onConfigureClick
            )
        }

        // Tab navigation
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { UI.Text(s.tool("tab_received_messages"), TextType.BODY) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { UI.Text(s.tool("tab_manage_messages"), TextType.BODY) }
            )
        }

        // Tab content
        when (selectedTab) {
            0 -> ReceivedMessagesTab(
                toolInstanceId = toolInstanceId,
                coordinator = coordinator,
                refreshTrigger = refreshTrigger,
                onError = { error -> errorMessage = error }
            )
            1 -> ManageMessagesTab(
                toolInstanceId = toolInstanceId,
                coordinator = coordinator,
                defaultPriority = defaultPriority,
                refreshTrigger = refreshTrigger,
                onRefresh = { refreshTrigger++ },
                onError = { error -> errorMessage = error }
            )
        }
    }
}

/**
 * Tab 1: Messages reçus (Execution history)
 *
 * Displays execution history with filters (checkboxes OR logic):
 * - Non lus (default checked)
 * - Lus
 * - Archivés
 *
 * List sorted by sent_at DESC
 * Actions: toggle read, archive
 */
@Composable
private fun ReceivedMessagesTab(
    toolInstanceId: String,
    coordinator: Coordinator,
    refreshTrigger: Int,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Filter states (survive rotation)
    var filterUnread by rememberSaveable { mutableStateOf(true) }  // Default checked
    var filterRead by rememberSaveable { mutableStateOf(false) }
    var filterArchived by rememberSaveable { mutableStateOf(false) }

    // Execution data
    var executions by remember { mutableStateOf<List<ExecutionEntry>>(emptyList()) }
    var isLoadingExecutions by remember { mutableStateOf(true) }

    // Load executions with filters
    LaunchedEffect(toolInstanceId, refreshTrigger, filterUnread, filterRead, filterArchived) {
        isLoadingExecutions = true

        // Build filters according to specs (Option A - Inclusion stricte)
        val filters = mutableMapOf<String, Any>()

        // Determine which executions to include based on checkbox states
        val includeUnread = filterUnread
        val includeRead = filterRead
        val includeArchived = filterArchived

        // If no checkbox is checked, show empty list
        if (!includeUnread && !includeRead && !includeArchived) {
            executions = emptyList()
            isLoadingExecutions = false
            return@LaunchedEffect
        }

        // Apply filters according to logic in specs (section 13.2)
        // We need to call get_history multiple times for different filter combinations
        val allExecutions = mutableListOf<ExecutionEntry>()

        // Unread checked → read=false AND archived=false
        if (includeUnread) {
            val params = mapOf(
                "toolInstanceId" to toolInstanceId,
                "filters" to JSONObject().apply {
                    put("read", false)
                    put("archived", false)
                }
            )
            val result = coordinator.processUserAction("messages.get_history", params)
            if (result?.isSuccess == true) {
                val entries = parseExecutionEntries(result.data?.get("executions") as? List<*>)
                allExecutions.addAll(entries)
            }
        }

        // Read checked → read=true AND archived=false
        if (includeRead) {
            val params = mapOf(
                "toolInstanceId" to toolInstanceId,
                "filters" to JSONObject().apply {
                    put("read", true)
                    put("archived", false)
                }
            )
            val result = coordinator.processUserAction("messages.get_history", params)
            if (result?.isSuccess == true) {
                val entries = parseExecutionEntries(result.data?.get("executions") as? List<*>)
                allExecutions.addAll(entries)
            }
        }

        // Archived checked → archived=true (regardless of read status)
        if (includeArchived) {
            val params = mapOf(
                "toolInstanceId" to toolInstanceId,
                "filters" to JSONObject().apply {
                    put("archived", true)
                }
            )
            val result = coordinator.processUserAction("messages.get_history", params)
            if (result?.isSuccess == true) {
                val entries = parseExecutionEntries(result.data?.get("executions") as? List<*>)
                allExecutions.addAll(entries)
            }
        }

        // Remove duplicates (can happen if unread+archived both checked)
        executions = allExecutions.distinctBy { "${it.messageId}_${it.executionIndex}" }

        isLoadingExecutions = false
        LogManager.ui("Loaded ${executions.size} executions with filters: unread=$filterUnread, read=$filterRead, archived=$filterArchived")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filters section
        UI.Card(type = CardType.DEFAULT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Unread checkbox
                UI.FormField(
                    label = s.tool("filter_unread"),
                    value = if (filterUnread) "☑" else "☐",
                    onChange = {},
                    fieldType = FieldType.TEXT,
                    readonly = true,
                    onClick = { filterUnread = !filterUnread }
                )

                // Read checkbox
                UI.FormField(
                    label = s.tool("filter_read"),
                    value = if (filterRead) "☑" else "☐",
                    onChange = {},
                    fieldType = FieldType.TEXT,
                    readonly = true,
                    onClick = { filterRead = !filterRead }
                )

                // Archived checkbox
                UI.FormField(
                    label = s.tool("filter_archived"),
                    value = if (filterArchived) "☑" else "☐",
                    onChange = {},
                    fieldType = FieldType.TEXT,
                    readonly = true,
                    onClick = { filterArchived = !filterArchived }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Executions list
        if (isLoadingExecutions) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                UI.Text(s.shared("tools_loading"), TextType.BODY)
            }
        } else if (executions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                UI.Text(s.tool("empty_received_messages"), TextType.BODY)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(executions) { execution ->
                    ExecutionCard(
                        execution = execution,
                        onToggleRead = {
                            coroutineScope.launch {
                                toggleExecutionRead(coordinator, execution, onError)
                            }
                        },
                        onToggleArchived = {
                            coroutineScope.launch {
                                toggleExecutionArchived(coordinator, execution, onError)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Card displaying a single execution entry
 */
@Composable
private fun ExecutionCard(
    execution: ExecutionEntry,
    onToggleRead: () -> Unit,
    onToggleArchived: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "messages", context = context) }

    // Format date/time
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
    val sentAtFormatted = remember(execution.sentAt) {
        execution.sentAt?.let { dateFormat.format(Date(it)) } ?: s.tool("status_pending")
    }

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Date/time + status badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UI.Text(sentAtFormatted, TextType.CAPTION)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Status badge (if not sent)
                    if (execution.status != "sent") {
                        val statusText = when (execution.status) {
                            "pending" -> s.tool("status_pending")
                            "failed" -> s.tool("status_failed")
                            else -> execution.status
                        }
                        UI.Text(statusText, TextType.CAPTION)
                    }

                    // Unread badge (if sent and not read)
                    if (execution.status == "sent" && !execution.read) {
                        UI.Text("🔴 ${s.tool("badge_unread")}", TextType.CAPTION)
                    }
                }
            }

            // Message title (snapshot)
            UI.Text(execution.titleSnapshot, TextType.SUBTITLE)

            // Message content (snapshot, if exists)
            execution.contentSnapshot?.let { content ->
                UI.Text(content, TextType.BODY)
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Toggle read/unread
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.S,
                    onClick = onToggleRead
                ) {
                    UI.Text(if (execution.read) s.tool("action_mark_unread") else s.tool("action_mark_read"), TextType.LABEL)
                }

                // Toggle archive/unarchive
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.S,
                    onClick = onToggleArchived
                ) {
                    UI.Text(if (execution.archived) s.tool("action_unarchive") else s.tool("action_archive"), TextType.LABEL)
                }
            }
        }
    }
}

/**
 * Tab 2: Gestion messages (Message templates management)
 *
 * Displays list of message templates (sorted by title alphabetically)
 * Actions: Edit, Delete
 * FAB: Add new message
 */
@Composable
private fun ManageMessagesTab(
    toolInstanceId: String,
    coordinator: Coordinator,
    defaultPriority: String,
    refreshTrigger: Int,
    onRefresh: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    val coroutineScope = rememberCoroutineScope()

    // Message templates data
    var templates by remember { mutableStateOf<List<MessageTemplate>>(emptyList()) }
    var isLoadingTemplates by remember { mutableStateOf(true) }

    // Dialog states (survive rotation for navigation safety)
    var showMessageDialog by rememberSaveable { mutableStateOf(false) }
    var editingMessageId by rememberSaveable { mutableStateOf<String?>(null) }

    // Load message templates
    LaunchedEffect(toolInstanceId, refreshTrigger) {
        isLoadingTemplates = true

        val params = mapOf(
            "toolInstanceId" to toolInstanceId,
            "limit" to 100
        )

        LogManager.ui("Loading message templates for tool $toolInstanceId (refreshTrigger=$refreshTrigger)", "DEBUG")

        val result = coordinator.processUserAction("tool_data.get", params)
        if (result?.isSuccess == true) {
            val entriesData = result.data?.get("entries") as? List<*> ?: emptyList<Any>()
            LogManager.ui("Received ${entriesData.size} raw entries from tool_data.get", "DEBUG")

            templates = entriesData.mapNotNull { entry ->
                try {
                    val parsed = parseMessageTemplate(entry as? Map<*, *>)
                    if (parsed == null) {
                        LogManager.ui("Failed to parse entry: ${entry}", "WARN")
                    }
                    parsed
                } catch (e: Exception) {
                    LogManager.ui("Error parsing message template: ${e.message}", "ERROR", e)
                    null
                }
            }.sortedBy { it.title }

            LogManager.ui("Loaded ${templates.size} message templates", "INFO")
        } else {
            LogManager.ui("Failed to load templates: ${result?.error}", "ERROR")
            templates = emptyList()
            onError(s.tool("error_load_messages"))
        }

        isLoadingTemplates = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Templates list
            if (isLoadingTemplates) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    UI.Text(s.shared("tools_loading"), TextType.BODY)
                }
            } else if (templates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    UI.Text(s.tool("empty_templates"), TextType.BODY)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(templates) { template ->
                        TemplateCard(
                            template = template,
                            defaultPriority = defaultPriority,
                            onEdit = {
                                editingMessageId = template.id
                                showMessageDialog = true
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    deleteTemplate(coordinator, template.id, onRefresh, onError)
                                }
                            }
                        )
                    }
                }
            }
        }

        // FAB: Add new message
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            UI.Button(
                type = ButtonType.PRIMARY,
                size = Size.M,
                onClick = {
                    editingMessageId = null
                    showMessageDialog = true
                }
            ) {
                UI.Text(s.tool("action_add_message"), TextType.LABEL)
            }
        }

        // Edit/Create Message Dialog
        if (showMessageDialog) {
            // Find message data if editing
            val editingTemplate = templates.find { it.id == editingMessageId }
            val initialMessage = editingTemplate?.let {
                MessageDialogData(
                    id = it.id,
                    title = it.title,
                    content = it.content,
                    schedule = it.schedule,
                    priority = it.priority
                )
            }

            EditMessageDialog(
                isVisible = showMessageDialog,
                toolInstanceId = toolInstanceId,
                defaultPriority = defaultPriority,
                initialMessage = initialMessage,
                onConfirm = { title, content, schedule, priority ->
                    if (editingMessageId == null) {
                        // Create new message
                        createMessage(
                            coordinator,
                            toolInstanceId,
                            title,
                            content,
                            schedule,
                            priority,
                            onSuccess = {
                                showMessageDialog = false
                                onRefresh()
                            },
                            onError
                        )
                    } else {
                        // Update existing message
                        updateMessage(
                            coordinator,
                            editingMessageId!!,
                            title,
                            content,
                            schedule,
                            priority,
                            onSuccess = {
                                showMessageDialog = false
                                onRefresh()
                            },
                            onError
                        )
                    }
                    true // Always return success (errors handled in callbacks)
                },
                onCancel = {
                    showMessageDialog = false
                    editingMessageId = null
                }
            )
        }
    }
}

/**
 * Card displaying a single message template
 */
@Composable
private fun TemplateCard(
    template: MessageTemplate,
    defaultPriority: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "messages", context = context) }

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            UI.Text(template.title, TextType.SUBTITLE)

            // Schedule summary
            val scheduleSummary = remember(template.schedule) {
                getScheduleSummary(template.schedule, s.tool("schedule_summary_on_demand"))
            }
            UI.Text(scheduleSummary, TextType.CAPTION)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Priority badge (if different from default)
                    if (template.priority != defaultPriority) {
                        val priorityText = when (template.priority) {
                            "high" -> s.tool("priority_high")
                            "low" -> s.tool("priority_low")
                            else -> s.tool("priority_default")
                        }
                        UI.Text(priorityText, TextType.CAPTION)
                    }

                    // Execution count
                    UI.Text(
                        s.tool("schedule_executions_count").format(template.executionCount),
                        TextType.CAPTION
                    )
                }

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UI.ActionButton(
                        action = ButtonAction.EDIT,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = onEdit
                    )

                    UI.ActionButton(
                        action = ButtonAction.DELETE,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        requireConfirmation = true,
                        confirmMessage = s.tool("delete_message_confirm"),
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

// ============================================================================
// Helper functions
// ============================================================================

/**
 * Parse execution entries from get_history result
 */
private fun parseExecutionEntries(data: List<*>?): List<ExecutionEntry> {
    if (data == null) return emptyList()

    return data.mapNotNull { item ->
        try {
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val messageId = map["messageId"] as? String ?: return@mapNotNull null
            val messageTitle = map["messageTitle"] as? String ?: return@mapNotNull null
            val executionIndex = (map["executionIndex"] as? Number)?.toInt() ?: return@mapNotNull null
            val execution = map["execution"] as? Map<*, *> ?: return@mapNotNull null

            val scheduledTime = (execution["scheduled_time"] as? Number)?.toLong() ?: return@mapNotNull null
            val sentAt = (execution["sent_at"] as? Number)?.toLong()
            val status = execution["status"] as? String ?: "pending"
            val titleSnapshot = execution["title_snapshot"] as? String ?: ""
            val contentSnapshot = execution["content_snapshot"] as? String?
            val read = execution["read"] as? Boolean ?: false
            val archived = execution["archived"] as? Boolean ?: false

            ExecutionEntry(
                messageId, messageTitle, executionIndex,
                scheduledTime, sentAt, status,
                titleSnapshot, contentSnapshot,
                read, archived
            )
        } catch (e: Exception) {
            LogManager.ui("Error parsing execution entry: ${e.message}", "ERROR")
            null
        }
    }
}

/**
 * Parse message template from tool_data entry
 */
private fun parseMessageTemplate(map: Map<*, *>?): MessageTemplate? {
    if (map == null) return null

    try {
        val id = map["id"] as? String ?: return null

        // Get title from entity-level "name" field (not from data.title)
        val title = map["name"] as? String ?: return null

        // Parse data field (can be String or Map)
        val dataValue = map["data"]
        val parsedData = when (dataValue) {
            is Map<*, *> -> dataValue as Map<String, Any>
            is String -> {
                val dataJson = JSONObject(dataValue)
                mutableMapOf<String, Any>().apply {
                    dataJson.keys().forEach { key -> put(key, dataJson.get(key)) }
                }
            }
            else -> return null
        }
        val content = parsedData["content"] as? String?
        val priority = parsedData["priority"] as? String ?: "default"

        // Parse schedule (can be null, Map, or JSONObject)
        val schedule = parsedData["schedule"]?.let { scheduleValue ->
            try {
                // Convert to JSON string if needed
                val scheduleJsonStr = when (scheduleValue) {
                    is String -> scheduleValue
                    is Map<*, *> -> JSONObject(scheduleValue as Map<String, Any>).toString()
                    is JSONObject -> scheduleValue.toString()
                    else -> null
                }

                // Deserialize using kotlinx.serialization
                if (scheduleJsonStr != null && scheduleJsonStr != "null") {
                    kotlinx.serialization.json.Json.decodeFromString<ScheduleConfig>(scheduleJsonStr)
                } else {
                    null
                }
            } catch (e: Exception) {
                LogManager.ui("Error parsing schedule: ${e.message}", "WARN")
                null
            }
        }

        // Count executions
        val executionsData = parsedData["executions"]
        val executionCount = when (executionsData) {
            is List<*> -> executionsData.size
            is org.json.JSONArray -> executionsData.length()
            else -> 0
        }

        return MessageTemplate(id, title, content, schedule, priority, executionCount)
    } catch (e: Exception) {
        LogManager.ui("Error parsing message template: ${e.message}", "ERROR")
        return null
    }
}

/**
 * Get schedule summary text
 * Pass strings context from caller for translations
 */
private fun getScheduleSummary(schedule: ScheduleConfig?, onDemandText: String): String {
    if (schedule == null) return onDemandText
    // TODO: Use ScheduleFormatter when available
    return when (schedule.pattern) {
        is com.assistant.core.utils.SchedulePattern.DailyMultiple -> "Quotidien"
        is com.assistant.core.utils.SchedulePattern.WeeklySimple -> "Hebdomadaire"
        is com.assistant.core.utils.SchedulePattern.MonthlyRecurrent -> "Mensuel"
        is com.assistant.core.utils.SchedulePattern.WeeklyCustom -> "Hebdomadaire personnalisé"
        is com.assistant.core.utils.SchedulePattern.YearlyRecurrent -> "Annuel"
        is com.assistant.core.utils.SchedulePattern.SpecificDates -> "Dates spécifiques"
    }
}

/**
 * Toggle execution read status
 */
private suspend fun toggleExecutionRead(
    coordinator: Coordinator,
    execution: ExecutionEntry,
    onError: (String) -> Unit
) {
    val params = mapOf(
        "message_id" to execution.messageId,
        "execution_index" to execution.executionIndex,
        "read" to !execution.read
    )

    val result = coordinator.processUserAction("messages.mark_read", params)
    if (result?.isSuccess != true) {
        onError(result?.error ?: "Failed to update read status")
    }
}

/**
 * Toggle execution archived status
 */
private suspend fun toggleExecutionArchived(
    coordinator: Coordinator,
    execution: ExecutionEntry,
    onError: (String) -> Unit
) {
    val params = mapOf(
        "message_id" to execution.messageId,
        "execution_index" to execution.executionIndex,
        "archived" to !execution.archived
    )

    val result = coordinator.processUserAction("messages.mark_archived", params)
    if (result?.isSuccess != true) {
        onError(result?.error ?: "Failed to update archived status")
    }
}

/**
 * Create new message template
 */
private suspend fun createMessage(
    coordinator: Coordinator,
    toolInstanceId: String,
    title: String,
    content: String?,
    schedule: ScheduleConfig?,
    priority: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // Serialize ScheduleConfig to JSON if present
    val scheduleJson = schedule?.let {
        // Configure JSON to encode defaults (timezone, enabled, etc.)
        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }
        val jsonString = json.encodeToString(
            ScheduleConfig.serializer(),
            it
        )
        JSONObject(jsonString)
    }

    val dataJson = JSONObject().apply {
        put("schema_id", "messages_data")
        put("content", content)
        put("schedule", scheduleJson ?: JSONObject.NULL)
        put("priority", priority)
        put("triggers", JSONObject.NULL)
        // executions auto-initialized by service
        // Note: title is stored at entity level as "name", not in data
    }

    val params = mapOf(
        "toolInstanceId" to toolInstanceId,
        "tooltype" to "messages",
        "name" to title,  // Use title as name in tool_data
        "timestamp" to System.currentTimeMillis(),
        "data" to dataJson  // JSONObject, not .toString()
    )

    val result = coordinator.processUserAction("tool_data.create", params)
    if (result?.isSuccess == true) {
        onSuccess()
    } else {
        onError(result?.error ?: "Failed to create message")
    }
}

/**
 * Update existing message template
 */
private suspend fun updateMessage(
    coordinator: Coordinator,
    messageId: String,
    title: String,
    content: String?,
    schedule: ScheduleConfig?,
    priority: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // Serialize ScheduleConfig to JSON if present
    val scheduleJson = schedule?.let {
        // Configure JSON to encode defaults (timezone, enabled, etc.)
        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }
        val jsonString = json.encodeToString(
            ScheduleConfig.serializer(),
            it
        )
        JSONObject(jsonString)
    }

    val dataJson = JSONObject().apply {
        put("schema_id", "messages_data")
        put("content", content)
        put("schedule", scheduleJson ?: JSONObject.NULL)
        put("priority", priority)
        put("triggers", JSONObject.NULL)
        // executions preserved by service
        // Note: title is stored at entity level as "name", not in data
    }

    val params = mapOf(
        "id" to messageId,
        "name" to title,  // Update name in tool_data as well
        "data" to dataJson  // JSONObject, not .toString()
    )

    val result = coordinator.processUserAction("tool_data.update", params)
    if (result?.isSuccess == true) {
        onSuccess()
    } else {
        onError(result?.error ?: "Failed to update message")
    }
}

/**
 * Delete message template
 */
private suspend fun deleteTemplate(
    coordinator: Coordinator,
    messageId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val params = mapOf("id" to messageId)
    val result = coordinator.processUserAction("tool_data.delete", params)
    if (result?.isSuccess == true) {
        onSuccess()
    } else {
        onError(result?.error ?: "Failed to delete message")
    }
}
