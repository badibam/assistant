package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.data.SessionTokens
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.ai.scheduling.AutomationScheduler
import com.assistant.core.ai.scheduling.NextExecution
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.ui.components.*
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AutomationScreen - Display automation execution history
 *
 * Shows:
 * - Automation name in header
 * - Next execution card (if scheduled and enabled)
 * - Filters: period type + limit + refresh
 * - Period selector (if not ALL)
 * - List of ExecutionCard with pagination
 * - Real-time updates via DataChangeNotifier and AIOrchestrator.currentState
 *
 * Navigation:
 * - BACK button returns to previous screen
 * - VIEW button on ExecutionCard navigates to detail screen
 *
 * Usage: Accessed from AutomationCard VIEW button in ZoneScreen
 */
@Composable
fun AutomationScreen(
    automationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToExecution: (sessionId: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()

    // States
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Automation metadata (simple data, not full Automation object)
    var automationName by remember { mutableStateOf<String?>(null) }
    var isEnabled by remember { mutableStateOf(false) }
    var nextExecution by remember { mutableStateOf<NextExecution?>(null) }

    // Execution list
    var sessions by remember { mutableStateOf<List<ExecutionSummary>>(emptyList()) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var totalEntries by remember { mutableStateOf(0) }

    // Filters
    var periodFilter by remember { mutableStateOf(PeriodFilterType.ALL) }
    var currentPeriod by remember { mutableStateOf<Period?>(null) }
    var entriesLimit by remember { mutableStateOf(25) }
    var refreshTrigger by remember { mutableStateOf(0) }

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

    // Load automation metadata and next execution
    suspend fun loadAutomationMetadata() {
        // Load automation
        val result = withContext(Dispatchers.IO) {
            coordinator.processUserAction("automations.get", mapOf("automation_id" to automationId))
        }

        // Update states (automatically on Main thread from LaunchedEffect)
        if (result.status == CommandStatus.SUCCESS) {
            val data = result.data
            if (data != null) {
                // Service returns data under "automation" key
                @Suppress("UNCHECKED_CAST")
                val automation = data["automation"] as? Map<String, Any>
                if (automation != null) {
                    automationName = automation["name"] as? String
                    isEnabled = automation["is_enabled"] as? Boolean ?: false

                    // Load next execution if enabled
                    if (isEnabled) {
                        nextExecution = withContext(Dispatchers.IO) {
                            val scheduler = AutomationScheduler(context)
                            scheduler.getNextExecutionForAutomation(automationId)
                        }
                    } else {
                        nextExecution = null
                    }
                } else {
                    errorMessage = s.shared("error_automation_load_failed")
                }
            } else {
                errorMessage = s.shared("error_automation_load_failed")
            }
        } else {
            errorMessage = result.error ?: s.shared("error_automation_load_failed")
        }
    }

    // Load execution sessions
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
                "automationId" to automationId,
                "limit" to entriesLimit,
                "page" to currentPage
            )
            if (startTime != null) params["startTime"] = startTime
            if (endTime != null) params["endTime"] = endTime

            // Call service
            val result = coordinator.processUserAction("ai_sessions.list_sessions_for_automation", params)

            if (result.status == CommandStatus.SUCCESS) {
                val data = result.data
                if (data != null) {
                    // Parse sessions
                    @Suppress("UNCHECKED_CAST")
                    val sessionsList = data["sessions"] as? List<Map<String, Any>> ?: emptyList()

                    sessions = sessionsList.map { sessionMap ->
                        // Parse tokens JSON
                        val tokensJson = sessionMap["tokensJson"] as? String
                        val tokens = if (tokensJson != null) {
                            try {
                                val json = JSONObject(tokensJson)
                                SessionTokens(
                                    totalUncachedInputTokens = json.optInt("totalUncachedInputTokens", 0),
                                    totalCacheWriteTokens = json.optInt("totalCacheWriteTokens", 0),
                                    totalCacheReadTokens = json.optInt("totalCacheReadTokens", 0),
                                    totalOutputTokens = json.optInt("totalOutputTokens", 0)
                                )
                            } catch (e: Exception) {
                                SessionTokens(0, 0, 0, 0)
                            }
                        } else {
                            SessionTokens(0, 0, 0, 0)
                        }

                        // Parse cost JSON
                        val costJson = sessionMap["costJson"] as? String
                        val cost = if (costJson != null) {
                            try {
                                val json = JSONObject(costJson)
                                json.optDouble("totalCost", 0.0)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }

                        // Parse phase
                        val phaseStr = sessionMap["phase"] as? String ?: "IDLE"
                        val phase = try {
                            Phase.valueOf(phaseStr)
                        } catch (e: Exception) {
                            Phase.IDLE
                        }

                        // Calculate duration
                        val createdAtValue = sessionMap["createdAt"] as? Long ?: 0L
                        val lastActivity = sessionMap["lastActivity"] as? Long ?: createdAtValue
                        val duration = lastActivity - createdAtValue

                        // Calculate total tokens
                        val totalTokens = tokens.totalUncachedInputTokens +
                                        tokens.totalCacheWriteTokens +
                                        tokens.totalCacheReadTokens +
                                        tokens.totalOutputTokens

                        ExecutionSummary(
                            sessionId = sessionMap["id"] as String,
                            scheduledExecutionTime = sessionMap["scheduledExecutionTime"] as? Long,
                            createdAt = createdAtValue,
                            phase = phase,
                            endReason = sessionMap["endReason"] as? String,
                            duration = duration,
                            totalRoundtrips = sessionMap["totalRoundtrips"] as? Int ?: 0,
                            totalTokens = totalTokens,
                            cost = cost
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
    }

    // Reset page when filters change
    LaunchedEffect(periodFilter, currentPeriod, entriesLimit) {
        currentPage = 1
    }

    // Load automation metadata on first load
    LaunchedEffect(automationId) {
        loadAutomationMetadata()
    }

    // Load sessions when filters/pagination change
    LaunchedEffect(automationId, periodFilter, currentPeriod, entriesLimit, currentPage, refreshTrigger) {
        if (currentPeriod != null || periodFilter == PeriodFilterType.ALL) {
            loadSessions()
        }
    }

    // Real-time updates - Listen to DataChangeNotifier for session changes
    LaunchedEffect(automationId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.AISessionsChanged -> {
                    if (event.automationId == null || event.automationId == automationId) {
                        refreshTrigger++
                    }
                }
                else -> {} // Ignore other events
            }
        }
    }

    // Real-time updates - Observe current AI state for active session highlighting
    val aiState by AIOrchestrator.currentState.collectAsState()

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
            title = automationName ?: s.shared("tools_loading"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onNavigateBack
        )

        // Next execution card
        if (nextExecution != null) {
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UI.Text(
                        text = s.shared("automation_next_execution_title"),
                        type = TextType.SUBTITLE
                    )
                    UI.Text(
                        text = nextExecution!!.message,
                        type = TextType.BODY
                    )
                }
            }
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
                        s.shared("period_hour"),
                        s.shared("period_day"),
                        s.shared("period_week"),
                        s.shared("period_month"),
                        s.shared("period_year")
                    ),
                    selected = when (periodFilter) {
                        PeriodFilterType.ALL -> s.shared("period_all")
                        PeriodFilterType.HOUR -> s.shared("period_hour")
                        PeriodFilterType.DAY -> s.shared("period_day")
                        PeriodFilterType.WEEK -> s.shared("period_week")
                        PeriodFilterType.MONTH -> s.shared("period_month")
                        PeriodFilterType.YEAR -> s.shared("period_year")
                    },
                    onSelect = { selection ->
                        periodFilter = when (selection) {
                            s.shared("period_all") -> PeriodFilterType.ALL
                            s.shared("period_hour") -> PeriodFilterType.HOUR
                            s.shared("period_day") -> PeriodFilterType.DAY
                            s.shared("period_week") -> PeriodFilterType.WEEK
                            s.shared("period_month") -> PeriodFilterType.MONTH
                            s.shared("period_year") -> PeriodFilterType.YEAR
                            else -> PeriodFilterType.DAY
                        }
                        // Update current period when filter changes
                        if (periodFilter != PeriodFilterType.ALL) {
                            currentPeriod = when (periodFilter) {
                                PeriodFilterType.HOUR -> createCurrentPeriod(PeriodType.HOUR)
                                PeriodFilterType.DAY -> createCurrentPeriod(PeriodType.DAY)
                                PeriodFilterType.WEEK -> createCurrentPeriod(PeriodType.WEEK)
                                PeriodFilterType.MONTH -> createCurrentPeriod(PeriodType.MONTH)
                                PeriodFilterType.YEAR -> createCurrentPeriod(PeriodType.YEAR)
                                PeriodFilterType.ALL -> createCurrentPeriod(PeriodType.DAY) // Fallback
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
                    options = listOf("10", "25", "100", "250", "1000"),
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
                    // Force reload even if period value is the same (user navigated away and back)
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
                UI.CenteredText(s.shared("automation_no_executions"), TextType.BODY)
            }
        }

        // Execution list
        if (sessions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sessions.forEach { session ->
                    // Check if this session is currently active
                    val isActive = aiState.sessionType == SessionType.AUTOMATION &&
                                   aiState.sessionId == session.sessionId

                    // If active, use live phase from aiState for real-time updates
                    val livePhase = if (isActive) aiState.phase else null

                    ExecutionCard(
                        sessionId = session.sessionId,
                        scheduledExecutionTime = session.scheduledExecutionTime,
                        createdAt = session.createdAt,
                        phase = session.phase,
                        endReason = session.endReason,
                        duration = session.duration,
                        totalRoundtrips = session.totalRoundtrips,
                        totalTokens = session.totalTokens,
                        cost = session.cost,
                        livePhase = livePhase,
                        onViewClick = { onNavigateToExecution(session.sessionId) }
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
}

/**
 * Data class for execution summary display
 */
data class ExecutionSummary(
    val sessionId: String,
    val scheduledExecutionTime: Long?,
    val createdAt: Long,
    val phase: Phase,
    val endReason: String?,
    val duration: Long,
    val totalRoundtrips: Int,
    val totalTokens: Int,
    val cost: Double?
)
