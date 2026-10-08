package app.treelune.core.ai.domain

import app.treelune.core.ai.data.SessionEndReason
import app.treelune.core.ai.data.SessionType

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

    /** Automation ID (only for AUTOMATION sessions), null otherwise */
    val automationId: String? = null,

    /**
     * End reason when phase == COMPLETED.
     * Used to persist reason in DB before clearing state.
     * Null for all other phases.
     */
    val endReason: SessionEndReason? = null,

    // ==================== Loop Counters ====================

    /**
     * AI calls made since the user last acted -- sent a message, answered a question or a
     * validation, cancelled, interrupted. Each of those starts the count over, so the limit
     * bounds what the AI does on its own, never the length of a conversation. An AUTOMATION
     * has nobody acting on it, so there it counts the whole session.
     * Incremented on each AI call.
     */
    val totalRoundtrips: Int = 0,

    // ==================== Timestamps ====================

    /**
     * Timestamp when session was created.
     * Used for global timeout calculation (AUTOMATION only).
     */
    val sessionCreatedAt: Long = 0L,

    /**
     * When the outage the session is currently sitting out began, or 0 when the network is fine.
     * Set on entering WAITING_NETWORK_RETRY, cleared on leaving it.
     */
    val networkLostAt: Long = 0L,

    /**
     * How long this session has spent waiting for the network, added up across outages.
     * Subtracted from the global timeout so an automation gets its ten minutes of working time
     * rather than ten minutes of wall clock.
     */
    val networkDownTime: Long = 0L,

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

    // ==================== Continuation Context ====================

    /**
     * Reason for entering PREPARING_CONTINUATION phase (AUTOMATION only).
     * Determines which guidance message to send to AI before continuing.
     * Null when not in PREPARING_CONTINUATION phase.
     */
    val continuationReason: ContinuationReason? = null,

    /**
     * Flag indicating AI sent completed=true once (AUTOMATION only).
     * Used for double confirmation: first completed=true sets this flag,
     * second completed=true actually completes the session.
     * Reset to false if AI sends anything other than completed=true.
     */
    val awaitingCompletionConfirmation: Boolean = false
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
     * Check if slot is available (IDLE phase AND no active session)
     */
    fun isSlotAvailable(): Boolean = phase == Phase.IDLE && sessionId == null

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

/**
 * How long the outage currently under way has lasted, or zero when the network is fine.
 *
 * Kept beside the accumulator so the two transitions that end a wait bank the same thing, and so
 * the timeout can count an outage that has not ended yet.
 */
fun AIState.currentOutage(currentTime: Long): Long =
    if (networkLostAt == 0L) 0L else currentTime - networkLostAt
