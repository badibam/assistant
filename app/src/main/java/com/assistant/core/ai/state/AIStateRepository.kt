package com.assistant.core.ai.state

import android.content.Context
import com.assistant.core.ai.database.AIDao
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.domain.AIEvent
import com.assistant.core.ai.domain.AILimitsConfig
import com.assistant.core.ai.domain.AIState
import com.assistant.core.ai.domain.AIStateMachine
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.domain.WaitingContext
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for AI state management with atomic memory + DB synchronization.
 *
 * Single source of truth pattern:
 * - Memory (_state) is the source of truth during execution
 * - DB is backup for recovery and audit
 * - All transitions are atomic (memory + DB updated together)
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Receives events via emit()
 * - Delegates transition logic to AIStateMachine
 * - Synchronizes state to DB transactionally
 * - Emits state changes via StateFlow
 */
class AIStateRepository(
    private val context: Context,
    private val aiDao: AIDao
) {
    /**
     * In-memory state (source of truth during execution)
     */
    private val _state = MutableStateFlow(AIState.idle())

    /**
     * Observable state flow for UI and components
     */
    val state: StateFlow<AIState> = _state.asStateFlow()

    /**
     * Current state value (convenience accessor)
     */
    val currentState: AIState
        get() = _state.value

    /**
     * Process an event and transition to new state.
     *
     * This operation is atomic:
     * 1. Calculate new state via AIStateMachine
     * 2. Update memory state
     * 3. Sync to DB
     * 4. Emit state change
     *
     * @param event Event to process
     * @return New state after transition
     */
    suspend fun emit(event: AIEvent): AIState {
        val oldState = currentState
        val currentTime = System.currentTimeMillis()

        // Get limits configuration for current session type
        val limits = if (oldState.sessionType != null) {
            AppConfigManager.getAILimits().getLimitsForSessionType(oldState.sessionType)
        } else {
            // No active session - use default CHAT limits
            AppConfigManager.getAILimits().getLimitsForSessionType(SessionType.CHAT)
        }

        // Calculate new state via pure state machine
        val newState = AIStateMachine.transition(oldState, event, limits, currentTime)

        // Update memory state
        _state.value = newState

        // Sync to DB if there's an active session
        if (newState.sessionId != null) {
            syncStateToDb(newState)
        }

        // Log transition for debugging
        LogManager.aiSession(
            "State transition: ${oldState.phase} -> ${newState.phase} (event: ${event::class.simpleName})",
            "INFO"
        )

        return newState
    }

    /**
     * Initialize state from DB on app startup.
     *
     * Loads active session from DB and reconstructs state.
     * If no active session, starts with idle state.
     */
    suspend fun initializeFromDb() {
        val activeSession = aiDao.getActiveSession()

        if (activeSession != null) {
            val restoredState = entityToState(activeSession)
            _state.value = restoredState

            LogManager.aiSession(
                "State restored from DB: session=${activeSession.id}, phase=${activeSession.phase}",
                "INFO"
            )
        } else {
            _state.value = AIState.idle()

            LogManager.aiSession("No active session in DB - starting idle", "INFO")
        }
    }

    /**
     * Sync current state to DB.
     *
     * Updates the active session entity with current state values.
     * This is called automatically after each state transition.
     *
     * Important: When a session becomes active (phase != IDLE and != CLOSED),
     * all other sessions are deactivated to ensure only one active session at a time.
     */
    private suspend fun syncStateToDb(state: AIState) {
        try {
            val sessionId = state.sessionId ?: return

            // Load existing session entity to preserve fields not in AIState
            val existingSession = aiDao.getSession(sessionId) ?: run {
                LogManager.aiSession(
                    "Cannot sync state to DB: session $sessionId not found",
                    "ERROR"
                )
                return
            }

            // Determine if this session should be active
            val shouldBeActive = state.phase != Phase.CLOSED

            // If this session is becoming active, deactivate all other sessions first
            // This ensures only one session has isActive=1 at any time
            if (shouldBeActive && !existingSession.isActive) {
                aiDao.deactivateAllSessions()
                LogManager.aiSession(
                    "Deactivated all sessions before activating session: $sessionId",
                    "DEBUG"
                )
            }

            // Update entity with new state values
            val updatedEntity = existingSession.copy(
                phase = state.phase.name,
                waitingContextJson = state.waitingContext?.toJson(),
                consecutiveFormatErrors = state.consecutiveFormatErrors,
                consecutiveActionFailures = state.consecutiveActionFailures,
                consecutiveDataQueries = state.consecutiveDataQueries,
                totalRoundtrips = state.totalRoundtrips,
                lastEventTime = state.lastEventTime,
                lastUserInteractionTime = state.lastUserInteractionTime,
                lastActivity = System.currentTimeMillis(),
                isActive = shouldBeActive,
                endReason = if (state.phase == Phase.CLOSED) state.endReason?.name else existingSession.endReason
            )

            aiDao.updateSession(updatedEntity)

        } catch (e: Exception) {
            LogManager.aiSession(
                "Failed to sync state to DB: ${e.message}",
                "ERROR",
                e
            )
        }
    }

    /**
     * Convert AISessionEntity to AIState.
     *
     * Used for state restoration from DB.
     */
    private fun entityToState(entity: AISessionEntity): AIState {
        return AIState(
            sessionId = entity.id,
            phase = Phase.valueOf(entity.phase),
            sessionType = entity.type,
            consecutiveFormatErrors = entity.consecutiveFormatErrors,
            consecutiveActionFailures = entity.consecutiveActionFailures,
            consecutiveDataQueries = entity.consecutiveDataQueries,
            totalRoundtrips = entity.totalRoundtrips,
            lastEventTime = entity.lastEventTime,
            lastUserInteractionTime = entity.lastUserInteractionTime,
            waitingContext = entity.waitingContextJson?.let {
                WaitingContext.fromJson(it)
            }
        )
    }

    /**
     * Force state to idle (used for cleanup after session completion).
     */
    suspend fun forceIdle() {
        _state.value = AIState.idle()
        LogManager.aiSession("State forced to IDLE", "INFO")
    }

    /**
     * Update waiting context without full state transition.
     *
     * Used by event processor to set waiting context after ValidationResolver
     * or communication module processing.
     */
    suspend fun updateWaitingContext(waitingContext: WaitingContext?) {
        val updatedState = currentState.copy(waitingContext = waitingContext)
        _state.value = updatedState

        // Sync to DB
        if (updatedState.sessionId != null) {
            syncStateToDb(updatedState)
        }

        LogManager.aiSession(
            "Waiting context updated: ${waitingContext?.javaClass?.simpleName ?: "null"}",
            "INFO"
        )
    }
}
