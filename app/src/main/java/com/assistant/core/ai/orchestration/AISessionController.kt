package com.assistant.core.ai.orchestration

import android.content.Context
import com.assistant.core.ai.data.SessionType
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    // Active session state (one session active at a time)
    private var activeSessionId: String? = null
    private var activeSessionType: SessionType? = null
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
     * Get active session ID
     */
    fun getActiveSessionId(): String? = activeSessionId

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
        if (activeSessionId == sessionId) {
            LogManager.aiSession("Session already active: $sessionId", "DEBUG")
            return SessionControlResult.ALREADY_ACTIVE
        }

        // No active session → activate immediately
        if (activeSessionId == null) {
            activateSession(sessionId, type, automationId, scheduledExecutionTime)
            LogManager.aiSession("Session activated immediately: $sessionId", "INFO")
            return SessionControlResult.ACTIVATED
        }

        // CHAT logic: one CHAT at a time
        if (type == SessionType.CHAT) {
            // Remove any other CHAT from queue
            sessionQueue.removeAll { it.type == SessionType.CHAT }

            // If other CHAT is active, close it and activate new one
            if (activeSessionType == SessionType.CHAT) {
                LogManager.aiSession("Closing active CHAT and switching to new CHAT: $sessionId", "INFO")
                closeActiveSession()
                activateSession(sessionId, type, automationId, scheduledExecutionTime)
                return SessionControlResult.ACTIVATED
            }

            // AUTOMATION active → queue CHAT with priority (position 1)
            enqueueSession(sessionId, type, automationId, scheduledExecutionTime, priority = true)
            LogManager.aiSession("CHAT queued with priority: $sessionId", "INFO")
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
                closeActiveSession()
                activateSession(sessionId, type, automationId, scheduledExecutionTime)
                return SessionControlResult.ACTIVATED
            } else {
                LogManager.aiSession(
                    "Active CHAT still active (inactive for ${inactivityDuration / 1000}s < ${limits.chatMaxInactivityBeforeAutomationEviction / 1000}s) - queuing AUTOMATION: $sessionId",
                    "INFO"
                )
            }
        }

        // AUTOMATION queued normally (FIFO)
        enqueueSession(sessionId, type, automationId, scheduledExecutionTime, priority = false)
        val position = sessionQueue.indexOfFirst { it.sessionId == sessionId } + 1
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
        if (activeSessionId == null) {
            LogManager.aiSession("No active session to close", "DEBUG")
            return
        }

        val sessionToClose = activeSessionId
        LogManager.aiSession("Closing active session: $sessionToClose", "INFO")

        // 1. Call callback for coordination (interruption, clear messages, resume interactions)
        onSessionClosed?.invoke()

        // 2. Update memory state (immediate)
        activeSessionId = null
        activeSessionType = null
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
        activeSessionId = sessionId
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
     */
    private fun activateSession(
        sessionId: String,
        type: SessionType,
        automationId: String?,
        scheduledExecutionTime: Long?
    ) {
        // 1. Update memory state (immediate)
        activeSessionId = sessionId
        activeSessionType = type
        lastActivityTimestamp = System.currentTimeMillis()
        LogManager.aiSession("Session activated in memory: $sessionId (type=$type, automationId=$automationId)", "DEBUG")

        // 2. Call callback for messages flow initialization
        onSessionActivated?.invoke(sessionId)

        // 3. Sync DB state (async, non-blocking)
        scope.launch {
            try {
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
     * Enqueue a session with optional priority
     */
    private fun enqueueSession(
        sessionId: String,
        type: SessionType,
        automationId: String?,
        scheduledExecutionTime: Long?,
        priority: Boolean
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

        if (priority) {
            sessionQueue.add(0, queued) // Position 1 (CHAT prioritaire)
        } else {
            sessionQueue.add(queued) // FIFO normal
        }

        LogManager.aiSession("Session enqueued: $sessionId at position ${sessionQueue.size} (priority=$priority)", "DEBUG")
    }

    /**
     * Process next session in queue
     */
    private fun processNextInQueue() {
        if (sessionQueue.isEmpty()) {
            LogManager.aiSession("Queue empty, no session to process", "DEBUG")
            return
        }

        val next = sessionQueue.removeAt(0)
        activateSession(next.sessionId, next.type, next.automationId, next.scheduledExecutionTime)
        LogManager.aiSession("Processing next in queue: ${next.sessionId} (type=${next.type})", "INFO")

        // TODO: Trigger session execution (for AUTOMATION, will be done when scheduler is implemented)
        // For CHAT, UI will detect active session change and open chat interface
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
