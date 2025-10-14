package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.SessionType
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
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
     */
    @Synchronized
    fun requestSessionControl(
        sessionId: String,
        type: SessionType,
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
            enqueueSession(sessionId, type, automationId, scheduledExecutionTime)
            processNextInQueue() // Will activate and execute (including AI round for AUTOMATION)
            LogManager.aiSession("Session activated immediately via queue: $sessionId", "INFO")
            return SessionControlResult.ACTIVATED
        }

        // CHAT logic: one CHAT at a time
        if (type == SessionType.CHAT) {
            // Remove any other CHAT from queue
            sessionQueue.removeAll { it.type == SessionType.CHAT }

            // If other CHAT is active, enqueue new one and close active (queue will process it)
            if (activeSessionType == SessionType.CHAT) {
                LogManager.aiSession("Closing active CHAT and switching to new CHAT: $sessionId", "INFO")
                enqueueSession(sessionId, type, automationId, scheduledExecutionTime)
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
                        enqueueSession(evictedSessionId, SessionType.AUTOMATION, evictedAutomationId, evictedScheduledTime)
                        LogManager.aiSession("Re-queued evicted AUTOMATION: $evictedSessionId", "INFO")
                    }

                    // Enqueue new CHAT and close active (queue will process CHAT with priority)
                    enqueueSession(sessionId, type, automationId, scheduledExecutionTime)
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
            enqueueSession(sessionId, type, automationId, scheduledExecutionTime)
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
                enqueueSession(sessionId, type, automationId, scheduledExecutionTime)
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
        enqueueSession(sessionId, type, automationId, scheduledExecutionTime)
        val position = sessionQueue.size
        LogManager.aiSession("AUTOMATION queued at position $position: $sessionId", "INFO")
        return SessionControlResult.QUEUED(position)
    }

    /**
     * Close active session manually
     * Updates both memory state (immediate) and DB state (async)
     * Calls onSessionClosed callback for coordination with other components
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

        // 4. Process queue
        processNextInQueue()
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
        automationId: String?,
        scheduledExecutionTime: Long?
    ) {
        val queued = QueuedSession(
            sessionId = sessionId,
            type = type,
            automationId = automationId,
            scheduledExecutionTime = scheduledExecutionTime,
            enqueuedAt = System.currentTimeMillis()
        )

        // CHAT: remove any other CHAT (already done in requestSessionControl, but defensive)
        if (type == SessionType.CHAT) {
            sessionQueue.removeAll { it.type == SessionType.CHAT }
        }

        sessionQueue.add(queued)

        LogManager.aiSession("Session enqueued: $sessionId (queue size=${sessionQueue.size})", "DEBUG")
    }

    /**
     * Process next session in queue
     * Selection logic:
     * - CHAT has absolute priority (anywhere in queue)
     * - AUTOMATION: earliest scheduledExecutionTime
     *
     * Also handles dismiss logic for automations
     */
    private fun processNextInQueue() {
        if (sessionQueue.isEmpty()) {
            LogManager.aiSession("Queue empty, no session to process", "DEBUG")
            return
        }

        // Smart selection: CHAT priority, else earliest scheduledExecutionTime
        val chatIndex = sessionQueue.indexOfFirst { it.type == SessionType.CHAT }
        val nextIndex = if (chatIndex >= 0) {
            chatIndex // CHAT found → priority
        } else {
            // Only AUTOMATION → select earliest scheduledExecutionTime
            sessionQueue
                .withIndex()
                .minByOrNull { it.value.scheduledExecutionTime ?: Long.MAX_VALUE }
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
            activateSession(next.sessionId, next.type, next.automationId, next.scheduledExecutionTime)
            LogManager.aiSession("Processing next in queue: ${next.sessionId} (type=${next.type})", "INFO")
        }
    }
}

/**
 * Queued session data
 */
data class QueuedSession(
    val sessionId: String,
    val type: SessionType,
    val automationId: String?,
    val scheduledExecutionTime: Long?,
    val enqueuedAt: Long
)
