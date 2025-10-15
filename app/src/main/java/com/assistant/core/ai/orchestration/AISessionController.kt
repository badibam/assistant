package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.ExecutionTrigger
import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.scheduling.AutomationScheduler
import com.assistant.core.ai.scheduling.NextSession
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI Session Controller - manages session activation and queue
 *
 * Responsibilities:
 * - Manage active session state (one session active at a time)
 * - Manage session queue with priority rules (CHAT vs AUTOMATION)
 * - Handle session activation and deactivation
 * - Coordinate with other components via callbacks
 *
 * Session Rules:
 * - One active session at a time (CHAT or AUTOMATION)
 * - CHAT: Only one CHAT in queue, immediate switch if another CHAT active
 * - AUTOMATION: Can evict inactive CHAT, otherwise queued FIFO
 */
class AISessionController(
    private val context: Context,
    private val coordinator: Coordinator,
    private val scope: CoroutineScope
) {

    // Round executor reference (set after construction for circular dependency)
    private var roundExecutor: AIRoundExecutor? = null

    /**
     * Set round executor (called from AIOrchestrator after all components are initialized)
     */
    fun setRoundExecutor(executor: AIRoundExecutor) {
        roundExecutor = executor
    }

    // Active session state (one session active at a time)
    private var _activeSessionId: MutableStateFlow<String?> = MutableStateFlow(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private var activeSessionType: SessionType? = null
    private var activeAutomationId: String? = null
    private var activeScheduledExecutionTime: Long? = null
    private var lastActivityTimestamp: Long = 0

    // Session queue (FIFO with priority rules)
    private val sessionQueue = mutableListOf<QueuedSession>()

    // Queued sessions flow (observable by UI)
    private val _queuedSessions = MutableStateFlow<List<QueuedSessionInfo>>(emptyList())
    val queuedSessions: StateFlow<List<QueuedSessionInfo>> = _queuedSessions.asStateFlow()

    // Callbacks for coordination with other components
    private var onSessionActivated: ((String) -> Unit)? = null
    private var onSessionClosed: (() -> Unit)? = null

    /**
     * Set callback for session activation
     * Called when a new session becomes active
     */
    fun setOnSessionActivatedCallback(callback: (String) -> Unit) {
        onSessionActivated = callback
    }

    /**
     * Set callback for session closure
     * Called when active session is closed
     */
    fun setOnSessionClosedCallback(callback: () -> Unit) {
        onSessionClosed = callback
    }

    /**
     * Get active session ID (current value)
     */
    fun getActiveSessionId(): String? = _activeSessionId.value

    /**
     * Get active session type
     */
    fun getActiveSessionType(): SessionType? = activeSessionType

    /**
     * Update activity timestamp (called when user interacts with session)
     */
    fun updateActivityTimestamp() {
        lastActivityTimestamp = System.currentTimeMillis()
    }

    /**
     * Update queued sessions flow for UI observation
     * Called after any modification to sessionQueue
     */
    private fun updateQueueFlow() {
        scope.launch {
            try {
                val queuedInfoList = sessionQueue.mapIndexed { index, queued ->
                    // Load session name from DB
                    val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf(
                        "sessionId" to queued.sessionId
                    ))

                    val name = if (sessionResult.isSuccess) {
                        val sessionData = sessionResult.data?.get("session") as? Map<*, *>
                        sessionData?.get("name") as? String ?: "Session"
                    } else {
                        "Session"
                    }

                    QueuedSessionInfo(
                        sessionId = queued.sessionId,
                        type = queued.type,
                        name = name,
                        automationId = queued.automationId,
                        position = index + 1  // 1-indexed for UI display
                    )
                }

                _queuedSessions.value = queuedInfoList
                LogManager.aiSession("Updated queued sessions flow: ${queuedInfoList.size} sessions", "DEBUG")
            } catch (e: Exception) {
                LogManager.aiSession("Error updating queued sessions flow: ${e.message}", "ERROR", e)
            }
        }
    }

    // ========================================================================================
    // Session Control API
    // ========================================================================================

    /**
     * Result of session control request
     */
    sealed class SessionControlResult {
        object ACTIVATED : SessionControlResult()
        object ALREADY_ACTIVE : SessionControlResult()
        data class QUEUED(val position: Int) : SessionControlResult()
    }

    /**
     * Request control of a session
     * Returns ACTIVATED if session activated immediately, ALREADY_ACTIVE if already active, or QUEUED with position
     *
     * @param trigger null for CHAT, MANUAL/SCHEDULED/EVENT for AUTOMATION
     */
    @Synchronized
    fun requestSessionControl(
        sessionId: String,
        type: SessionType,
        trigger: ExecutionTrigger? = null,
        automationId: String? = null,
        scheduledExecutionTime: Long? = null
    ): SessionControlResult {
        LogManager.aiSession("Requesting session control: sessionId=$sessionId, type=$type", "DEBUG")

        // Already active?
        if (_activeSessionId.value == sessionId) {
            LogManager.aiSession("Session already active: $sessionId", "DEBUG")
            return SessionControlResult.ALREADY_ACTIVE
        }

        // No active session → enqueue and process immediately
        if (_activeSessionId.value == null) {
            enqueueSession(sessionId, type, trigger, automationId, scheduledExecutionTime)
            processNextInQueue() // Will activate and execute (including AI round for AUTOMATION)
            LogManager.aiSession("Session activated immediately via queue: $sessionId", "INFO")
            return SessionControlResult.ACTIVATED
        }

        // CHAT logic: one CHAT at a time
        if (type == SessionType.CHAT) {
            // Remove any other CHAT from queue
            sessionQueue.removeAll { it.type == SessionType.CHAT }
            updateQueueFlow()

            // If other CHAT is active, enqueue new one and close active (queue will process it)
            if (activeSessionType == SessionType.CHAT) {
                LogManager.aiSession("Closing active CHAT and switching to new CHAT: $sessionId", "INFO")
                enqueueSession(sessionId, type, trigger, automationId, scheduledExecutionTime)
                closeActiveSession() // Calls processNextInQueue internally, will activate enqueued session
                return SessionControlResult.ACTIVATED
            }

            // AUTOMATION active → check if can evict
            if (activeSessionType == SessionType.AUTOMATION) {
                val limits = AppConfigManager.getAILimits()
                val now = System.currentTimeMillis()
                val inactivityDuration = now - lastActivityTimestamp

                if (inactivityDuration > limits.chatMaxInactivityBeforeAutomationEviction) {
                    LogManager.aiSession(
                        "Active AUTOMATION inactive for ${inactivityDuration / 1000}s (limit: ${limits.chatMaxInactivityBeforeAutomationEviction / 1000}s) - evicting for CHAT: $sessionId",
                        "INFO"
                    )
                    // Re-queue automation with its original scheduledExecutionTime
                    val evictedSessionId = _activeSessionId.value!!
                    val evictedAutomationId = activeAutomationId
                    val evictedScheduledTime = activeScheduledExecutionTime

                    // Re-queue evicted automation (will be picked first due to older scheduledExecutionTime)
                    if (evictedAutomationId != null) {
                        enqueueSession(evictedSessionId, SessionType.AUTOMATION, ExecutionTrigger.SCHEDULED, evictedAutomationId, evictedScheduledTime)
                        LogManager.aiSession("Re-queued evicted AUTOMATION: $evictedSessionId", "INFO")
                    }

                    // Enqueue new CHAT and close active (queue will process CHAT with priority)
                    enqueueSession(sessionId, type, trigger, automationId, scheduledExecutionTime)
                    closeActiveSession() // Calls processNextInQueue internally, will activate CHAT (priority)
                    return SessionControlResult.ACTIVATED
                } else {
                    LogManager.aiSession(
                        "Active AUTOMATION still active (inactive for ${inactivityDuration / 1000}s < ${limits.chatMaxInactivityBeforeAutomationEviction / 1000}s) - queuing CHAT: $sessionId",
                        "INFO"
                    )
                }
            }

            // Queue CHAT (will have priority in processNextInQueue)
            enqueueSession(sessionId, type, trigger, automationId, scheduledExecutionTime)
            LogManager.aiSession("CHAT queued: $sessionId", "INFO")
            return SessionControlResult.QUEUED(1)
        }

        // AUTOMATION logic: Check if active CHAT can be evicted
        if (type == SessionType.AUTOMATION && activeSessionType == SessionType.CHAT) {
            val limits = AppConfigManager.getAILimits()
            val now = System.currentTimeMillis()
            val inactivityDuration = now - lastActivityTimestamp

            if (inactivityDuration > limits.chatMaxInactivityBeforeAutomationEviction) {
                LogManager.aiSession(
                    "Active CHAT inactive for ${inactivityDuration / 1000}s (limit: ${limits.chatMaxInactivityBeforeAutomationEviction / 1000}s) - evicting for AUTOMATION: $sessionId",
                    "INFO"
                )
                // Enqueue AUTOMATION and close CHAT (queue will process it)
                enqueueSession(sessionId, type, trigger, automationId, scheduledExecutionTime)
                closeActiveSession() // Calls processNextInQueue internally, will activate AUTOMATION and execute round
                return SessionControlResult.ACTIVATED
            } else {
                LogManager.aiSession(
                    "Active CHAT still active (inactive for ${inactivityDuration / 1000}s < ${limits.chatMaxInactivityBeforeAutomationEviction / 1000}s) - queuing AUTOMATION: $sessionId",
                    "INFO"
                )
            }
        }

        // AUTOMATION queued (sorted by scheduledExecutionTime in processNextInQueue)
        enqueueSession(sessionId, type, trigger, automationId, scheduledExecutionTime)
        val position = sessionQueue.size
        LogManager.aiSession("AUTOMATION queued at position $position: $sessionId", "INFO")
        return SessionControlResult.QUEUED(position)
    }

    /**
     * Cancel a queued session
     * Removes session from queue and deletes from DB
     * Does NOT affect active session
     *
     * @param sessionId Session ID to cancel
     */
    @Synchronized
    fun cancelQueuedSession(sessionId: String) {
        LogManager.aiSession("Cancelling queued session: $sessionId", "INFO")

        // Remove from queue
        val removed = sessionQueue.removeAll { it.sessionId == sessionId }

        if (removed) {
            // Update flow
            updateQueueFlow()

            // Delete session from DB (async)
            scope.launch {
                try {
                    val result = coordinator.processUserAction("ai_sessions.delete", mapOf(
                        "sessionId" to sessionId
                    ))
                    if (result.isSuccess) {
                        LogManager.aiSession("Deleted queued session from DB: $sessionId", "DEBUG")
                    } else {
                        LogManager.aiSession("Failed to delete queued session from DB: ${result.error}", "WARN")
                    }
                } catch (e: Exception) {
                    LogManager.aiSession("Exception deleting queued session from DB: ${e.message}", "ERROR", e)
                }
            }
        } else {
            LogManager.aiSession("Session not found in queue: $sessionId", "WARN")
        }
    }

    /**
     * Close active session manually
     * Updates both memory state (immediate) and DB state (async)
     * Calls onSessionClosed callback for coordination with other components
     * Triggers tick() to process queue or fetch scheduled automations
     */
    @Synchronized
    fun closeActiveSession() {
        if (_activeSessionId.value == null) {
            LogManager.aiSession("No active session to close", "DEBUG")
            return
        }

        val sessionToClose = _activeSessionId.value
        LogManager.aiSession("Closing active session: $sessionToClose", "INFO")

        // 1. Call callback for coordination (interruption, clear messages, resume interactions)
        onSessionClosed?.invoke()

        // 2. Update memory state (immediate)
        _activeSessionId.value = null
        activeSessionType = null
        activeAutomationId = null
        activeScheduledExecutionTime = null
        lastActivityTimestamp = 0

        // 3. Sync DB state (async, non-blocking)
        scope.launch {
            try {
                val result = coordinator.processUserAction("ai_sessions.stop_active_session", emptyMap())
                if (result.isSuccess) {
                    LogManager.aiSession("Session deactivated in DB: $sessionToClose", "DEBUG")
                } else {
                    LogManager.aiSession("Failed to deactivate session in DB: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                LogManager.aiSession("Exception deactivating session in DB: ${e.message}", "ERROR", e)
            }
        }

        // 4. Trigger tick to process queue or fetch scheduled automations
        scope.launch {
            tick()
        }
    }

    /**
     * Restore active session from DB at app startup
     * Called from AIOrchestrator.initialize()
     */
    fun restoreActiveSession(sessionId: String, type: SessionType) {
        _activeSessionId.value = sessionId
        activeSessionType = type
        lastActivityTimestamp = System.currentTimeMillis()
        LogManager.aiSession("Restored active session: $sessionId (type=$type)", "INFO")

        // Trigger callback to initialize messages flow
        onSessionActivated?.invoke(sessionId)
    }

    // ========================================================================================
    // Tick System - Point d'entrée unique pour scheduling
    // ========================================================================================

    /**
     * Tick - Point d'entrée unique pour le système de scheduling
     *
     * Responsabilités:
     * 1. Vérifier si session active est zombie/inactive (watchdog)
     * 2. Traiter la queue mémoire (CHAT + MANUAL) si non vide
     * 3. Demander au scheduler s'il y a des automations scheduled à lancer
     *
     * Appelé par:
     * - WorkManager (toutes les 5 min, automatique)
     * - closeActiveSession() (réactivité immédiate)
     * - AutomationService CRUD (create/update/enable/disable)
     */
    suspend fun tick() {
        LogManager.aiSession("Tick: Starting scheduler cycle", "DEBUG")

        // 1. Si slot occupé, vérifier si session zombie/inactive (watchdog unique)
        if (_activeSessionId.value != null) {
            if (shouldStopInactiveSession()) {
                LogManager.aiSession("Tick: Stopping inactive/zombie session: ${_activeSessionId.value}", "WARN")
                closeActiveSession()  // Libère le slot et rappellera tick()
                return  // closeActiveSession() appelera tick() récursivement
            } else {
                LogManager.aiSession("Tick: Slot occupied by legitimate session, early return", "DEBUG")
                return  // Session légitime en cours, rien à faire
            }
        }

        // 2. Queue mémoire non vide → la vider d'abord (priorités CHAT > MANUAL)
        if (sessionQueue.isNotEmpty()) {
            LogManager.aiSession("Tick: Processing queue (${sessionQueue.size} sessions)", "DEBUG")
            processNextInQueue()
            return
        }

        // 3. Queue vide → demander nouvelles automations scheduled au scheduler
        LogManager.aiSession("Tick: Queue empty, requesting scheduled automations from scheduler", "DEBUG")
        val next = AutomationScheduler(context).getNextSession()

        when (next) {
            is NextSession.Resume -> {
                LogManager.aiSession("Tick: Scheduler returned RESUME for session ${next.sessionId}", "INFO")
                resumeSession(next.sessionId)
            }
            is NextSession.Create -> {
                LogManager.aiSession("Tick: Scheduler returned CREATE for automation ${next.automationId} (scheduled=${next.scheduledFor})", "INFO")
                createScheduledSession(next.automationId, next.scheduledFor)
            }
            is NextSession.None -> {
                LogManager.aiSession("Tick: No scheduled automations to execute", "DEBUG")
            }
        }
    }

    /**
     * Check if active session should be stopped (zombie/inactive detection)
     * UNIQUE watchdog - no watchdog in AIRoundExecutor
     *
     * Returns true if session should be stopped
     */
    private suspend fun shouldStopInactiveSession(): Boolean {
        val sessionId = _activeSessionId.value ?: return false
        val now = System.currentTimeMillis()
        val sessionAge = now - lastActivityTimestamp
        val limits = AppConfigManager.getAILimits()

        return when (activeSessionType) {
            SessionType.AUTOMATION -> {
                if (sessionAge > limits.automationMaxSessionDuration) {
                    LogManager.aiSession(
                        "Watchdog: AUTOMATION inactive for ${sessionAge / 1000}s (limit: ${limits.automationMaxSessionDuration / 1000}s)",
                        "WARN"
                    )

                    // Déterminer endReason selon flag réseau
                    val database = AppDatabase.getDatabase(context)
                    val session = database.aiDao().getSession(sessionId)
                    val endReason = if (session?.isWaitingForNetwork == true) {
                        SessionEndReason.NETWORK_ERROR  // Reprendre
                    } else {
                        SessionEndReason.TIMEOUT  // Abandonner
                    }

                    // Set endReason sur session
                    coordinator.processUserAction("ai_sessions.set_end_reason", mapOf(
                        "sessionId" to sessionId,
                        "endReason" to endReason.name
                    ))

                    LogManager.aiSession("Watchdog: Set endReason=$endReason for session $sessionId", "INFO")
                    true  // Stop session
                } else {
                    false
                }
            }
            SessionType.CHAT -> {
                // Pas de timeout automatique pour CHAT via tick
                // (seulement éviction si AUTOMATION demande, ou user clique stop)
                false
            }
            else -> false
        }
    }

    /**
     * Resume incomplete session (crash/network/suspended)
     * NO system message added - transparent resume
     */
    private suspend fun resumeSession(sessionId: String) {
        try {
            // Load session to get metadata
            val database = AppDatabase.getDatabase(context)
            val session = database.aiDao().getSession(sessionId)

            if (session == null) {
                LogManager.aiSession("Resume: Session not found: $sessionId", "ERROR")
                return
            }

            // Determine RoundReason based on endReason
            val reason = when (session.endReason) {
                null -> RoundReason.AUTOMATION_RESUME_CRASH
                "NETWORK_ERROR" -> RoundReason.AUTOMATION_RESUME_NETWORK
                "SUSPENDED" -> RoundReason.AUTOMATION_RESUME_SUSPENDED
                else -> {
                    LogManager.aiSession("Resume: Invalid endReason=${session.endReason} for session $sessionId", "WARN")
                    RoundReason.AUTOMATION_START
                }
            }

            LogManager.aiSession("Resume: Resuming session $sessionId with reason=$reason", "INFO")

            // Request session control
            val result = requestSessionControl(
                sessionId = sessionId,
                type = SessionType.AUTOMATION,
                trigger = ExecutionTrigger.SCHEDULED,
                automationId = session.automationId,
                scheduledExecutionTime = session.scheduledExecutionTime
            )

            when (result) {
                SessionControlResult.ACTIVATED -> {
                    // Session activated, trigger AI round
                    roundExecutor?.executeAIRound(reason)
                        ?: LogManager.aiSession("Resume: RoundExecutor not set, cannot execute round", "ERROR")
                }
                is SessionControlResult.QUEUED -> {
                    // Rare: slot pris par CHAT entre temps
                    LogManager.aiSession("Resume: Session queued at position ${result.position}", "INFO")
                }
                SessionControlResult.ALREADY_ACTIVE -> {
                    LogManager.aiSession("Resume: Session already active", "DEBUG")
                }
            }
        } catch (e: Exception) {
            LogManager.aiSession("Resume: Error resuming session $sessionId: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Create new scheduled session for automation
     */
    private suspend fun createScheduledSession(automationId: String, scheduledFor: Long) {
        try {
            LogManager.aiSession("CreateScheduled: Creating session for automation $automationId (scheduled=$scheduledFor)", "INFO")

            // Create new session via AIOrchestrator
            val createResult = coordinator.processUserAction("automations.execute", mapOf(
                "automation_id" to automationId,
                "trigger" to "SCHEDULED",
                "scheduled_for" to scheduledFor
            ))

            if (!createResult.isSuccess) {
                LogManager.aiSession("CreateScheduled: Failed to create session: ${createResult.error}", "ERROR")
            }
        } catch (e: Exception) {
            LogManager.aiSession("CreateScheduled: Error creating session: ${e.message}", "ERROR", e)
        }
    }

    // ========================================================================================
    // Private Helpers
    // ========================================================================================

    /**
     * Activate a session
     * Updates both memory state (immediate) and DB state (async)
     * Also resets session state and endReason for fresh execution
     */
    private fun activateSession(
        sessionId: String,
        type: SessionType,
        automationId: String?,
        scheduledExecutionTime: Long?
    ) {
        // 1. Update memory state (immediate)
        _activeSessionId.value = sessionId
        activeSessionType = type
        activeAutomationId = automationId
        activeScheduledExecutionTime = scheduledExecutionTime
        lastActivityTimestamp = System.currentTimeMillis()
        LogManager.aiSession("Session activated in memory: $sessionId (type=$type, automationId=$automationId)", "DEBUG")

        // 2. Call callback for messages flow initialization
        onSessionActivated?.invoke(sessionId)

        // 3. Sync DB state (async, non-blocking)
        scope.launch {
            try {
                // Reset session state and endReason for fresh execution
                coordinator.processUserAction("ai_sessions.reset_session_state", mapOf(
                    "sessionId" to sessionId
                ))

                // Set session as active
                val result = coordinator.processUserAction("ai_sessions.set_active_session", mapOf(
                    "sessionId" to sessionId
                ))
                if (result.isSuccess) {
                    LogManager.aiSession("Session activated in DB: $sessionId", "DEBUG")
                } else {
                    LogManager.aiSession("Failed to activate session in DB: ${result.error}", "WARN")
                }
            } catch (e: Exception) {
                LogManager.aiSession("Exception activating session in DB: ${e.message}", "ERROR", e)
            }
        }
    }

    /**
     * Enqueue a session
     * Sorting is handled in processNextInQueue() (CHAT priority, then by scheduledExecutionTime)
     */
    private fun enqueueSession(
        sessionId: String,
        type: SessionType,
        trigger: ExecutionTrigger? = null,
        automationId: String?,
        scheduledExecutionTime: Long?
    ) {
        val queued = QueuedSession(
            sessionId = sessionId,
            type = type,
            trigger = trigger,
            automationId = automationId,
            scheduledExecutionTime = scheduledExecutionTime,
            enqueuedAt = System.currentTimeMillis()
        )

        // CHAT: remove any other CHAT (already done in requestSessionControl, but defensive)
        if (type == SessionType.CHAT) {
            sessionQueue.removeAll { it.type == SessionType.CHAT }
        }

        sessionQueue.add(queued)
        updateQueueFlow()

        LogManager.aiSession("Session enqueued: $sessionId (queue size=${sessionQueue.size})", "DEBUG")
    }

    /**
     * Process next session in queue
     * Selection logic:
     * - CHAT has absolute priority (anywhere in queue)
     * - MANUAL automations: by enqueuedAt (FIFO)
     *
     * Note: SCHEDULED automations are NEVER in queue (created on demand by tick())
     * Queue contains ONLY: CHAT + MANUAL
     *
     * Also handles dismiss logic for automations
     */
    private fun processNextInQueue() {
        if (sessionQueue.isEmpty()) {
            LogManager.aiSession("Queue empty, no session to process", "DEBUG")
            return
        }

        // Priority selection: CHAT > MANUAL (by enqueuedat)
        val chatIndex = sessionQueue.indexOfFirst { it.type == SessionType.CHAT }
        val nextIndex = if (chatIndex >= 0) {
            chatIndex // CHAT found → priority
        } else {
            // Only MANUAL automations → select earliest enqueuedAt (FIFO)
            sessionQueue
                .withIndex()
                .minByOrNull { it.value.enqueuedAt }
                ?.index ?: 0
        }

        val next = sessionQueue[nextIndex]

        // Check dismiss logic for automations (async check, then activate/execute)
        if (next.type == SessionType.AUTOMATION && next.automationId != null) {
            scope.launch {
                try {
                    // Load automation to check dismissOlderInstances flag
                    val automationResult = coordinator.processUserAction("automations.get", mapOf(
                        "automation_id" to next.automationId!!
                    ))

                    if (automationResult.isSuccess) {
                        val automationData = automationResult.data?.get("automation") as? Map<*, *>
                        val dismissOlderInstances = automationData?.get("dismiss_older_instances") as? Boolean ?: false

                        if (dismissOlderInstances) {
                            // Check if newer instance of same automation exists in queue
                            val hasNewerInstance = sessionQueue
                                .drop(1) // Skip current (first)
                                .any { it.automationId == next.automationId }

                            if (hasNewerInstance) {
                                LogManager.aiSession("Skipping older automation instance ${next.sessionId} (newer instance in queue)", "INFO")
                                sessionQueue.removeAt(nextIndex)
                                updateQueueFlow()

                                // Store DISMISSED system message
                                // TODO: Create and store SystemMessage with type DISMISSED

                                // Process next recursively
                                processNextInQueue()
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogManager.aiSession("Error checking dismiss logic: ${e.message}", "ERROR", e)
                }

                // No dismiss or no newer instance → activate session and execute AI round
                sessionQueue.removeAt(nextIndex)
                updateQueueFlow()
                activateSession(next.sessionId, next.type, next.automationId, next.scheduledExecutionTime)
                LogManager.aiSession("Processing next in queue: ${next.sessionId} (type=${next.type})", "INFO")

                // Trigger AI round for AUTOMATION
                roundExecutor?.let { executor ->
                    try {
                        LogManager.aiSession("Triggering AUTOMATION execution for session ${next.sessionId}", "INFO")
                        executor.executeAIRound(RoundReason.AUTOMATION_START)
                    } catch (e: Exception) {
                        LogManager.aiSession("Error executing automation round: ${e.message}", "ERROR", e)
                    }
                } ?: run {
                    LogManager.aiSession("RoundExecutor not set, cannot trigger automation", "ERROR")
                }
            }
        } else {
            // CHAT or session without automationId → activate only (UI handles CHAT execution)
            sessionQueue.removeAt(nextIndex)
            updateQueueFlow()
            activateSession(next.sessionId, next.type, next.automationId, next.scheduledExecutionTime)
            LogManager.aiSession("Processing next in queue: ${next.sessionId} (type=${next.type})", "INFO")
        }
    }
}

/**
 * Queued session data (internal)
 * trigger is null for CHAT sessions
 */
data class QueuedSession(
    val sessionId: String,
    val type: SessionType,
    val trigger: ExecutionTrigger?,      // null for CHAT, MANUAL/SCHEDULED/EVENT for AUTOMATION
    val automationId: String?,
    val scheduledExecutionTime: Long?,
    val enqueuedAt: Long
)

/**
 * Queued session info for UI observation
 * Exposes minimal information about queued sessions
 */
data class QueuedSessionInfo(
    val sessionId: String,
    val type: SessionType,
    val name: String,
    val automationId: String?,
    val position: Int
)
