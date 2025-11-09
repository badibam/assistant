package com.assistant.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * HistoryScreen - Display CHAT session history
 *
 * Shows:
 * - Search bar (text search in session names and message content)
 * - Period filters (ALL, DAY, WEEK, MONTH, YEAR)
 * - List of SessionCard with pagination
 * - Actions: Resume, Rename, Delete
 *
 * Navigation:
 * - BACK button returns to MainScreen
 * - RESUME button opens AIFloatingChat with selected session
 *
 * Usage: Accessed from MainScreen menu
 */
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onResumeSession: (sessionId: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()

    // States
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Session list
    var sessions by remember { mutableStateOf<List<SessionSummary>>(emptyList()) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var totalEntries by remember { mutableStateOf(0) }

    // Filters
    var searchQuery by remember { mutableStateOf("") }
    var periodFilter by remember { mutableStateOf(PeriodFilterType.ALL) }
    var currentPeriod by remember { mutableStateOf<Period?>(null) }
    var entriesLimit by remember { mutableStateOf(20) }

    // Search debounce
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var selectedSessionName by remember { mutableStateOf("") }

    // Helper to create Period for current time
    fun createCurrentPeriod(periodType: PeriodType): Period {
        return Period(
            timestamp = normalizeTimestampWithConfig(
                timestamp = System.currentTimeMillis(),
                type = periodType
            ),
            type = periodType
        )
    }

    // Initialize currentPeriod with DAY by default
    LaunchedEffect(Unit) {
        currentPeriod = createCurrentPeriod(PeriodType.DAY)
    }

    // Load sessions
    suspend fun loadSessions() {
        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            // Calculate period boundaries if not ALL
            val startTime: Long? = if (periodFilter != PeriodFilterType.ALL && currentPeriod != null) {
                currentPeriod!!.timestamp
            } else null

            val endTime: Long? = if (periodFilter != PeriodFilterType.ALL && currentPeriod != null) {
                getPeriodEndTimestamp(currentPeriod!!)
            } else null

            // Build params
            val params = mutableMapOf<String, Any>(
                "limit" to entriesLimit,
                "page" to currentPage
            )
            if (searchQuery.isNotEmpty()) params["search"] = searchQuery
            if (startTime != null) params["startTime"] = startTime
            if (endTime != null) params["endTime"] = endTime

            // Call service
            val result = coordinator.processUserAction("ai_sessions.list", params)

            if (result.status == CommandStatus.SUCCESS) {
                val data = result.data
                if (data != null) {
                    // Parse sessions
                    @Suppress("UNCHECKED_CAST")
                    val sessionsList = data["sessions"] as? List<Map<String, Any>> ?: emptyList()

                    sessions = sessionsList.map { sessionMap ->
                        SessionSummary(
                            id = sessionMap["id"] as String,
                            name = sessionMap["name"] as String,
                            createdAt = sessionMap["createdAt"] as? Long ?: 0L,
                            lastActivity = sessionMap["lastActivity"] as? Long ?: 0L,
                            messageCount = sessionMap["messageCount"] as? Int ?: 0,
                            firstUserMessage = sessionMap["firstUserMessage"] as? String ?: ""
                        )
                    }

                    // Parse pagination
                    @Suppress("UNCHECKED_CAST")
                    val pagination = data["pagination"] as? Map<String, Any>
                    if (pagination != null) {
                        currentPage = pagination["currentPage"] as? Int ?: 1
                        totalPages = pagination["totalPages"] as? Int ?: 1
                        totalEntries = pagination["totalEntries"] as? Int ?: 0
                    }
                }
            } else {
                errorMessage = result.error ?: s.shared("error_load_failed")
                sessions = emptyList()
            }
        }

        isLoading = false

        // After loading, check if current page is now invalid (happens after deletion)
        // If we're on a page that no longer exists, go back to the last valid page
        if (totalPages > 0 && currentPage > totalPages) {
            currentPage = totalPages
            // Reload will trigger automatically via LaunchedEffect on currentPage change
        }
    }

    // Reset page when filters change
    LaunchedEffect(periodFilter, currentPeriod, entriesLimit, searchQuery) {
        currentPage = 1
    }

    // Load sessions when filters/pagination change
    LaunchedEffect(periodFilter, currentPeriod, entriesLimit, currentPage) {
        if (currentPeriod != null || periodFilter == PeriodFilterType.ALL) {
            loadSessions()
        }
    }

    // Search debounce - reload sessions after 300ms of no typing
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(300)
            if (currentPeriod != null || periodFilter == PeriodFilterType.ALL) {
                loadSessions()
            }
        }
    }

    // Show error toast
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            UI.Toast(context, errorMessage!!)
            errorMessage = null
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("history_title"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onNavigateBack
        )

        // Search bar
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            UI.FormField(
                label = s.shared("history_search_placeholder"),
                value = searchQuery,
                onChange = { searchQuery = it },
                fieldType = FieldType.SEARCH
            )
        }

        // Filters row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            // Period filter dropdown
            Box(modifier = Modifier.weight(1f)) {
                UI.FormSelection(
                    label = "",
                    options = listOf(
                        s.shared("period_all"),
                        s.shared("period_day"),
                        s.shared("period_week"),
                        s.shared("period_month"),
                        s.shared("period_year")
                    ),
                    selected = when (periodFilter) {
                        PeriodFilterType.ALL -> s.shared("period_all")
                        PeriodFilterType.DAY -> s.shared("period_day")
                        PeriodFilterType.WEEK -> s.shared("period_week")
                        PeriodFilterType.MONTH -> s.shared("period_month")
                        PeriodFilterType.YEAR -> s.shared("period_year")
                        else -> s.shared("period_all")
                    },
                    onSelect = { selection ->
                        periodFilter = when (selection) {
                            s.shared("period_all") -> PeriodFilterType.ALL
                            s.shared("period_day") -> PeriodFilterType.DAY
                            s.shared("period_week") -> PeriodFilterType.WEEK
                            s.shared("period_month") -> PeriodFilterType.MONTH
                            s.shared("period_year") -> PeriodFilterType.YEAR
                            else -> PeriodFilterType.DAY
                        }
                        // Update current period when filter changes
                        if (periodFilter != PeriodFilterType.ALL) {
                            currentPeriod = when (periodFilter) {
                                PeriodFilterType.DAY -> createCurrentPeriod(PeriodType.DAY)
                                PeriodFilterType.WEEK -> createCurrentPeriod(PeriodType.WEEK)
                                PeriodFilterType.MONTH -> createCurrentPeriod(PeriodType.MONTH)
                                PeriodFilterType.YEAR -> createCurrentPeriod(PeriodType.YEAR)
                                else -> createCurrentPeriod(PeriodType.DAY)
                            }
                        }
                        // Force reload when period type changes
                        scope.launch { loadSessions() }
                    }
                )
            }

            // Entries limit dropdown
            Box(modifier = Modifier.weight(1f)) {
                UI.FormSelection(
                    label = "",
                    options = listOf("10", "20", "50", "100"),
                    selected = entriesLimit.toString(),
                    onSelect = { selection ->
                        entriesLimit = selection.toInt()
                        // Force reload when limit changes
                        scope.launch { loadSessions() }
                    }
                )
            }

            // Refresh button
            Box(contentAlignment = Alignment.Center) {
                if (!isLoading) {
                    UI.ActionButton(
                        action = ButtonAction.REFRESH,
                        display = ButtonDisplay.ICON,
                        onClick = { scope.launch { loadSessions() } }
                    )
                } else {
                    UI.CenteredText("...", TextType.BODY)
                }
            }
        }

        // Period selector (hidden for ALL filter)
        if (periodFilter != PeriodFilterType.ALL && currentPeriod != null) {
            SinglePeriodSelector(
                period = currentPeriod!!,
                onPeriodChange = { newPeriod ->
                    currentPeriod = newPeriod
                    // Force reload even if period value is the same
                    scope.launch { loadSessions() }
                }
            )
        }

        // Loading state
        if (isLoading && sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                UI.CenteredText(s.shared("tools_loading"), TextType.BODY)
            }
        }

        // Empty state
        if (!isLoading && sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                UI.CenteredText(s.shared("history_empty"), TextType.BODY)
            }
        }

        // Session list
        if (sessions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sessions.forEach { session ->
                    SessionCard(
                        sessionId = session.id,
                        name = session.name,
                        createdAt = session.createdAt,
                        messageCount = session.messageCount,
                        firstUserMessage = session.firstUserMessage,
                        onResumeClick = {
                            scope.launch {
                                // Resume session via AIOrchestrator
                                try {
                                    withContext(Dispatchers.IO) {
                                        AIOrchestrator.resumeChatSession(session.id)
                                    }
                                    // Navigate to chat
                                    onResumeSession(session.id)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: s.shared("error_resume_session")
                                }
                            }
                        },
                        onRenameClick = {
                            selectedSessionId = session.id
                            selectedSessionName = session.name
                            showRenameDialog = true
                        },
                        onDeleteClick = {
                            selectedSessionId = session.id
                            selectedSessionName = session.name
                            showDeleteDialog = true
                        }
                    )
                }

                // Pagination
                if (totalPages > 1) {
                    UI.Pagination(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageChange = { newPage -> currentPage = newPage }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog && selectedSessionId != null) {
        var newName by remember { mutableStateOf(selectedSessionName) }
        var renameError by remember { mutableStateOf<String?>(null) }

        UI.Dialog(
            type = DialogType.INFO,
            onConfirm = { },
            onCancel = {
                showRenameDialog = false
                selectedSessionId = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                UI.Text(
                    text = s.shared("history_rename_title"),
                    type = TextType.SUBTITLE,
                    fillMaxWidth = true
                )

                // Name field
                UI.FormField(
                    label = s.shared("history_rename_label"),
                    value = newName,
                    onChange = { newName = it },
                    fieldType = FieldType.TEXT
                )

                // Error message
                if (renameError != null) {
                    UI.Text(
                        text = renameError!!,
                        type = TextType.ERROR,
                        fillMaxWidth = true
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.ActionButton(
                        action = ButtonAction.CANCEL,
                        display = ButtonDisplay.LABEL,
                        size = Size.M,
                        onClick = {
                            showRenameDialog = false
                            selectedSessionId = null
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    UI.ActionButton(
                        action = ButtonAction.SAVE,
                        display = ButtonDisplay.LABEL,
                        size = Size.M,
                        onClick = {
                            scope.launch {
                                if (newName.isEmpty()) {
                                    renameError = s.shared("error_validation_required")
                                    return@launch
                                }

                                val result = withContext(Dispatchers.IO) {
                                    coordinator.processUserAction("ai_sessions.rename", mapOf(
                                        "sessionId" to selectedSessionId!!,
                                        "name" to newName
                                    ))
                                }

                                if (result.status == CommandStatus.SUCCESS) {
                                    showRenameDialog = false
                                    selectedSessionId = null
                                    loadSessions() // Reload to show updated name
                                } else {
                                    renameError = result.error ?: s.shared("error_rename_failed")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Delete Dialog
    if (showDeleteDialog && selectedSessionId != null) {
        UI.ConfirmDialog(
            title = s.shared("action_delete"),
            message = s.shared("history_delete_confirm").format(selectedSessionName),
            confirmText = s.shared("action_delete"),
            cancelText = s.shared("action_cancel"),
            onConfirm = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        coordinator.processUserAction("ai_sessions.delete", mapOf(
                            "sessionId" to selectedSessionId!!
                        ))
                    }

                    if (result.status == CommandStatus.SUCCESS) {
                        showDeleteDialog = false
                        selectedSessionId = null
                        loadSessions() // Reload to update list
                    } else {
                        errorMessage = result.error ?: s.shared("error_delete_failed")
                    }
                }
            },
            onDismiss = {
                showDeleteDialog = false
                selectedSessionId = null
            }
        )
    }
}

/**
 * Data class for session summary display
 */
data class SessionSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastActivity: Long,
    val messageCount: Int,
    val firstUserMessage: String
)
