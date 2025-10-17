package com.assistant.core.ai.scheduling

import com.assistant.core.ai.data.ExecutionTrigger
import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.database.AIDao
import com.assistant.core.ai.domain.AIState
import com.assistant.core.ai.domain.Phase
import com.assistant.core.utils.LogManager

/**
 * Scheduler for AI session activation and interruption logic.
 *
 * Manages:
 * - Session priority (CHAT > MANUAL > SCHEDULED)
 * - Slot availability and eviction logic
 * - Inactivity calculation and timeout detection
 * - Next session selection (queue + scheduled automations)
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Pure decision logic (no state mutation)
 * - Returns activation results and decisions
 * - Event processor handles actual activation
 */
class AISessionScheduler(
    private val aiDao: AIDao,
    private val automationScheduler: AutomationScheduler
) {

    companion object {
        /** Chat session inactivity timeout (5 minutes) */
        const val CHAT_INACTIVITY_TIMEOUT = 300_000L

        /** Automation session inactivity timeout (2 minutes) */
        const val AUTO_INACTIVITY_TIMEOUT = 120_000L
    }

    /**
     * Request activation for a session.
     *
     * Decides if activation is immediate (slot free), requires eviction,
     * or should be enqueued based on priority and inactivity.
     *
     * @param sessionId Session ID to activate
     * @param sessionType Session type (CHAT or AUTOMATION)
     * @param trigger Execution trigger (for automations only)
     * @return Activation result with decision
     */
    suspend fun requestSession(
        sessionId: String,
        sessionType: SessionType,
        trigger: ExecutionTrigger,
        currentState: AIState
    ): ActivationResult {
        // Check if slot is available
        if (currentState.isSlotAvailable()) {
            return ActivationResult.ActivateImmediate(sessionId)
        }

        // Slot occupied - check interruption logic
        val activeSessionType = currentState.sessionType
        val inactivity = currentState.calculateInactivity(System.currentTimeMillis())

        LogManager.aiSession(
            "Request session: $sessionId ($sessionType/$trigger), active: ${currentState.sessionId} ($activeSessionType), " +
                "phase: ${currentState.phase}, inactivity: ${inactivity}ms",
            "INFO"
        )

        return when {
            // CHAT requests
            sessionType == SessionType.CHAT -> {
                when (activeSessionType) {
                    SessionType.CHAT -> {
                        // CHAT replaces CHAT immediately
                        ActivationResult.EvictAndActivate(
                            sessionToEvict = currentState.sessionId!!,
                            sessionToActivate = sessionId,
                            evictionReason = SessionEndReason.CANCELLED
                        )
                    }
                    SessionType.AUTOMATION -> {
                        // Check AUTOMATION inactivity
                        if (inactivity > CHAT_INACTIVITY_TIMEOUT) {
                            // Evict inactive automation
                            ActivationResult.EvictAndActivate(
                                sessionToEvict = currentState.sessionId!!,
                                sessionToActivate = sessionId,
                                evictionReason = SessionEndReason.CANCELLED
                            )
                        } else {
                            // Automation active - enqueue CHAT
                            ActivationResult.Enqueue(sessionId, priority = 1)
                        }
                    }
                    else -> ActivationResult.ActivateImmediate(sessionId)
                }
            }

            // AUTOMATION MANUAL requests
            sessionType == SessionType.AUTOMATION && trigger == ExecutionTrigger.MANUAL -> {
                when (activeSessionType) {
                    SessionType.CHAT -> {
                        // Check CHAT inactivity
                        if (inactivity > AUTO_INACTIVITY_TIMEOUT) {
                            // Evict inactive chat
                            ActivationResult.EvictAndActivate(
                                sessionToEvict = currentState.sessionId!!,
                                sessionToActivate = sessionId,
                                evictionReason = SessionEndReason.CANCELLED
                            )
                        } else {
                            // CHAT active - enqueue MANUAL (priority 2, after CHAT)
                            ActivationResult.Enqueue(sessionId, priority = 2)
                        }
                    }
                    SessionType.AUTOMATION -> {
                        // Check AUTOMATION inactivity
                        if (inactivity > AUTO_INACTIVITY_TIMEOUT) {
                            // Evict inactive automation
                            ActivationResult.EvictAndActivate(
                                sessionToEvict = currentState.sessionId!!,
                                sessionToActivate = sessionId,
                                evictionReason = SessionEndReason.CANCELLED
                            )
                        } else {
                            // Automation active - enqueue MANUAL
                            ActivationResult.Enqueue(sessionId, priority = 2)
                        }
                    }
                    else -> ActivationResult.ActivateImmediate(sessionId)
                }
            }

            // AUTOMATION SCHEDULED requests
            sessionType == SessionType.AUTOMATION && trigger == ExecutionTrigger.SCHEDULED -> {
                // SCHEDULED automations never interrupt or enqueue
                // They're created only when slot is free
                ActivationResult.Skip("Slot occupied")
            }

            else -> ActivationResult.Skip("Unknown session type/trigger combination")
        }
    }

    /**
     * Get next session to activate when slot becomes free.
     *
     * Priority: CHAT (from queue) > MANUAL (from queue) > SCHEDULED (from automations)
     *
     * @param queue Current session queue
     * @return Session to activate or null
     */
    suspend fun getNextSession(queue: List<QueuedSession>): SessionToActivate? {
        // Priority 1: CHAT from queue
        val chatSession = queue.find { it.sessionType == SessionType.CHAT }
        if (chatSession != null) {
            return SessionToActivate(
                sessionId = chatSession.sessionId,
                automationId = null,
                scheduledFor = null,
                sessionType = chatSession.sessionType,
                trigger = chatSession.trigger,
                removeFromQueue = true
            )
        }

        // Priority 2: MANUAL automation from queue
        val manualSession = queue.find {
            it.sessionType == SessionType.AUTOMATION && it.trigger == ExecutionTrigger.MANUAL
        }
        if (manualSession != null) {
            return SessionToActivate(
                sessionId = manualSession.sessionId,
                automationId = null,
                scheduledFor = null,
                sessionType = manualSession.sessionType,
                trigger = manualSession.trigger,
                removeFromQueue = true
            )
        }

        // Priority 3: SCHEDULED automation (not from queue - calculated dynamically)
        val nextSession = automationScheduler.getNextSession()
        return when (nextSession) {
            is NextSession.Resume -> {
                // Resume existing incomplete session
                SessionToActivate(
                    sessionId = nextSession.sessionId,
                    automationId = null,
                    scheduledFor = null,
                    sessionType = SessionType.AUTOMATION,
                    trigger = ExecutionTrigger.SCHEDULED, // Was scheduled, resume it
                    removeFromQueue = false // Not in queue
                )
            }
            is NextSession.Create -> {
                // Create new scheduled session from automation
                SessionToActivate(
                    sessionId = null,
                    automationId = nextSession.automationId,
                    scheduledFor = nextSession.scheduledFor,
                    sessionType = SessionType.AUTOMATION,
                    trigger = ExecutionTrigger.SCHEDULED,
                    removeFromQueue = false // Not in queue
                )
            }
            NextSession.None -> null
        }
    }

    /**
     * Check if active session should timeout due to inactivity.
     *
     * @param currentState Current AI state
     * @return true if session should timeout
     */
    fun shouldTimeout(currentState: AIState): Boolean {
        if (currentState.sessionType == null) {
            return false // No active session
        }

        val inactivity = currentState.calculateInactivity(System.currentTimeMillis())

        val timeout = when (currentState.sessionType) {
            SessionType.CHAT -> CHAT_INACTIVITY_TIMEOUT
            SessionType.AUTOMATION -> AUTO_INACTIVITY_TIMEOUT
            else -> return false
        }

        val shouldTimeout = inactivity > timeout

        if (shouldTimeout) {
            LogManager.aiSession(
                "Session timeout: ${currentState.sessionId}, type=${currentState.sessionType}, " +
                    "inactivity=${inactivity}ms > ${timeout}ms",
                "INFO"
            )
        }

        return shouldTimeout
    }
}

/**
 * Result of session activation request.
 */
sealed class ActivationResult {
    /** Activate session immediately (slot free) */
    data class ActivateImmediate(val sessionId: String) : ActivationResult()

    /** Evict current session and activate new one */
    data class EvictAndActivate(
        val sessionToEvict: String,
        val sessionToActivate: String,
        val evictionReason: SessionEndReason
    ) : ActivationResult()

    /** Add session to queue */
    data class Enqueue(val sessionId: String, val priority: Int) : ActivationResult()

    /** Skip activation (scheduled automation when slot occupied) */
    data class Skip(val reason: String) : ActivationResult()
}

/**
 * Session in queue waiting for activation.
 *
 * Minimal structure - additional info (like automationId) can be loaded from DB via sessionId.
 */
data class QueuedSession(
    val sessionId: String,
    val sessionType: SessionType,
    val trigger: ExecutionTrigger,
    val queuedAt: Long
)

/**
 * Session to activate (result of getNextSession).
 *
 * Two modes:
 * 1. Resume: sessionId is set (existing session to activate)
 * 2. Create: automationId is set (create new session from automation)
 */
data class SessionToActivate(
    val sessionId: String?,              // Existing session to activate (Resume)
    val automationId: String?,           // Automation to create session from (Create)
    val scheduledFor: Long?,             // For Create only - timestamp for scheduledExecutionTime
    val sessionType: SessionType,
    val trigger: ExecutionTrigger,
    val removeFromQueue: Boolean
) {
    init {
        // Exactly one of sessionId or automationId must be set
        require((sessionId != null) xor (automationId != null)) {
            "SessionToActivate must have either sessionId or automationId set, not both or neither"
        }
    }

    /**
     * Check if this is a resume (existing session)
     */
    fun isResume(): Boolean = sessionId != null

    /**
     * Check if this is a create (new automation session)
     */
    fun isCreate(): Boolean = automationId != null
}
