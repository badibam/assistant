package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType

/**
 * Complete state of the AI execution system.
 *
 * This is the single source of truth during execution (in-memory).
 * DB is synchronized transactionally after each state transition.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Memory = source of truth during execution
 * - DB = backup for recovery and audit
 * - Transitions are atomic (memory + DB)
 */
data class AIState(
    /** Current active session ID, null if no session active (IDLE) */
    val sessionId: String?,

    /** Current execution phase */
    val phase: Phase,

    /** Type of current session (CHAT, AUTOMATION, SEED), null if IDLE */
    val sessionType: SessionType?,

    /**
     * End reason when phase == COMPLETED.
     * Used to persist reason in DB before clearing state.
     * Null for all other phases.
     */
    val endReason: SessionEndReason? = null,

    // ==================== Retry Counters ====================

    /**
     * Consecutive format/parse errors count.
     * Reset when format parsing succeeds or different counter type increments.
     */
    val consecutiveFormatErrors: Int = 0,

    /**
     * Consecutive action failure count.
     * Reset when all actions succeed or different counter type increments.
     */
    val consecutiveActionFailures: Int = 0,

    /**
     * Consecutive data query count.
     * Reset when actions execute or different counter type increments.
     */
    val consecutiveDataQueries: Int = 0,

    /**
     * Total autonomous roundtrips count (never reset during session).
     * Incremented on each AI call (regardless of reason).
     */
    val totalRoundtrips: Int = 0,

    // ==================== Timestamps ====================

    /**
     * Timestamp of last event processed (any event).
     * Used for inactivity timeout calculation when not in active processing.
     */
    val lastEventTime: Long = 0L,

    /**
     * Timestamp of last user interaction (message, validation, response).
     * Used for inactivity timeout when waiting for user.
     */
    val lastUserInteractionTime: Long = 0L,

    // ==================== Waiting Context ====================

    /**
     * Context data when waiting for user interaction (validation, communication).
     * Null when not waiting.
     */
    val waitingContext: WaitingContext? = null,

    // ==================== Pause State ====================

    /**
     * Phase before manual pause (stored to resume from correct phase).
     * Null when not paused.
     * Set when SessionPaused event occurs, used when SessionResumed event occurs.
     */
    val phaseBeforePause: Phase? = null
) {
    /**
     * Calculate current inactivity duration in milliseconds.
     *
     * Returns 0 for active processing phases (no timeout).
     * Returns time since last user interaction for waiting phases.
     * Returns time since last event for other phases.
     */
    fun calculateInactivity(currentTime: Long): Long {
        return when {
            // Active processing = no inactivity
            phase.isActiveProcessing() -> 0L

            // Network retry = no inactivity (actively waiting)
            phase.isNetworkRetry() -> 0L

            // Waiting for user = inactivity since last user interaction
            phase.isWaitingForUser() -> currentTime - lastUserInteractionTime

            // Other phases = inactivity since last event
            else -> currentTime - lastEventTime
        }
    }

    /**
     * Check if slot is available (IDLE phase)
     */
    fun isSlotAvailable(): Boolean = phase == Phase.IDLE

    companion object {
        /**
         * Initial state when no session is active
         */
        fun idle() = AIState(
            sessionId = null,
            phase = Phase.IDLE,
            sessionType = null
        )
    }
}
