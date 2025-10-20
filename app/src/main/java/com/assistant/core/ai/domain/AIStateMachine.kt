package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType

/**
 * Pure state machine for AI execution.
 *
 * Handles state transitions in response to events.
 * Contains ZERO side effects - only pure state transformations.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Input: current state + event
 * - Output: new state
 * - Testable without mocks
 * - Side effects handled by AIEventProcessor
 */
object AIStateMachine {

    /**
     * Process an event and return the new state.
     *
     * This is a pure function with no side effects.
     *
     * @param state Current state
     * @param event Event that occurred
     * @param limits AI limits configuration for current session type
     * @param currentTime Current timestamp (for inactivity calculation)
     * @return New state after transition
     */
    fun transition(
        state: AIState,
        event: AIEvent,
        limits: SessionLimits,
        currentTime: Long
    ): AIState {
        return when (event) {
            // ==================== Session Lifecycle ====================

            is AIEvent.SessionActivationRequested -> {
                // Activate session and set sessionId + sessionType
                // CHAT sessions stay IDLE waiting for first user message
                // AUTOMATION sessions transition to EXECUTING_ENRICHMENTS immediately
                if (state.phase != Phase.IDLE) {
                    // Slot not available - event processor should handle scheduling
                    state
                } else {
                    val nextPhase = if (event.sessionType == SessionType.CHAT) {
                        Phase.IDLE // CHAT waits for user message
                    } else {
                        Phase.EXECUTING_ENRICHMENTS // AUTOMATION starts immediately
                    }

                    state.copy(
                        sessionId = event.sessionId,
                        sessionType = event.sessionType,
                        phase = nextPhase,
                        sessionCreatedAt = currentTime,
                        lastNetworkAvailableTime = currentTime,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.SessionCompleted -> {
                // Transition to CLOSED from any phase
                // Store endReason temporarily for handleSessionCompletion to persist in DB
                state.copy(
                    phase = Phase.CLOSED,
                    endReason = event.reason,
                    lastEventTime = currentTime
                )
            }

            // ==================== User Message ====================

            is AIEvent.EnrichmentsExecuted -> {
                // Transition from EXECUTING_ENRICHMENTS to CALLING_AI
                state.copy(
                    phase = Phase.CALLING_AI,
                    lastEventTime = currentTime
                )
            }

            // ==================== AI Interaction ====================

            is AIEvent.AIResponseReceived -> {
                // Transition from CALLING_AI to PARSING_AI_RESPONSE
                state.copy(
                    phase = Phase.PARSING_AI_RESPONSE,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.AIResponseParsed -> {
                // Complex decision tree based on AIMessage content
                handleAIResponseParsed(state, event, currentTime)
            }

            // ==================== Continuation ====================

            is AIEvent.ContinuationReady -> {
                // Guidance message prepared - transition to CALLING_AI
                state.copy(
                    phase = Phase.CALLING_AI,
                    continuationReason = null, // Clear continuation reason
                    totalRoundtrips = state.totalRoundtrips + 1,
                    lastEventTime = currentTime
                )
            }

            // ==================== User Interactions ====================

            is AIEvent.ValidationNotRequired -> {
                // Validation check completed - no validation needed, proceed to execution
                state.copy(
                    phase = Phase.EXECUTING_ACTIONS,
                    waitingContext = null,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.ValidationReceived -> {
                if (event.approved) {
                    // User approved - transition to EXECUTING_ACTIONS
                    state.copy(
                        phase = Phase.EXECUTING_ACTIONS,
                        waitingContext = null,
                        lastEventTime = currentTime,
                        lastUserInteractionTime = currentTime
                    )
                } else {
                    // User rejected - CHAT returns to IDLE (session stays active)
                    // AUTOMATION should not reach this path (no validation for AUTOMATION)
                    state.copy(
                        phase = Phase.IDLE,
                        waitingContext = null,
                        lastEventTime = currentTime,
                        lastUserInteractionTime = currentTime
                    )
                }
            }

            is AIEvent.CommunicationResponseReceived -> {
                // User responded - transition to CALLING_AI (send response to AI)
                state.copy(
                    phase = Phase.CALLING_AI,
                    waitingContext = null,
                    totalRoundtrips = state.totalRoundtrips + 1,
                    lastEventTime = currentTime,
                    lastUserInteractionTime = currentTime
                )
            }

            is AIEvent.CommunicationCancelled -> {
                // User cancelled communication - transition to IDLE (CHAT session stays active)
                state.copy(
                    phase = Phase.IDLE,
                    waitingContext = null,
                    lastEventTime = currentTime,
                    lastUserInteractionTime = currentTime
                )
            }

            is AIEvent.SessionPaused -> {
                // User paused session - store current phase and transition to PAUSED
                // NOTE: If currently in CALLING_AI, will pause after response is processed
                state.copy(
                    phase = Phase.PAUSED,
                    phaseBeforePause = state.phase,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.SessionResumed -> {
                // User resumed session - restore phase from before pause
                val resumePhase = state.phaseBeforePause ?: Phase.IDLE
                state.copy(
                    phase = resumePhase,
                    phaseBeforePause = null,
                    lastEventTime = currentTime,
                    lastUserInteractionTime = currentTime
                )
            }

            is AIEvent.AIRoundInterrupted -> {
                // User interrupted current AI round (CHAT only)
                // Stay in INTERRUPTED phase to ignore AI response when it arrives
                // Will transition to IDLE after response is ignored
                state.copy(
                    phase = Phase.INTERRUPTED,
                    waitingContext = null, // Clear any waiting context
                    lastEventTime = currentTime,
                    lastUserInteractionTime = currentTime
                )
            }

            is AIEvent.AIResponseIgnored -> {
                // AI response was ignored after interruption
                // Return to IDLE, ready for next user message
                state.copy(
                    phase = Phase.IDLE,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.UserMessageSent -> {
                // User sent message - transition to EXECUTING_ENRICHMENTS
                // Only valid from IDLE phase
                state.copy(
                    phase = Phase.EXECUTING_ENRICHMENTS,
                    lastEventTime = currentTime,
                    lastUserInteractionTime = currentTime
                )
            }

            // ==================== Command Execution ====================

            is AIEvent.DataQueriesExecuted -> {
                // Data queries executed successfully - continue to CALLING_AI
                state.copy(
                    phase = Phase.CALLING_AI,
                    totalRoundtrips = state.totalRoundtrips + 1,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.ActionsExecuted -> {
                handleActionsExecuted(state, event, limits, currentTime)
            }

            // ==================== Completion ====================

            is AIEvent.CompletionConfirmed -> {
                // Transition to CLOSED with COMPLETED reason
                state.copy(
                    phase = Phase.CLOSED,
                    endReason = SessionEndReason.COMPLETED,
                    waitingContext = null,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.CompletionRejected -> {
                // User rejected completion - continue with AI
                state.copy(
                    phase = Phase.CALLING_AI,
                    waitingContext = null,
                    totalRoundtrips = state.totalRoundtrips + 1,
                    lastEventTime = currentTime
                )
            }

            // ==================== Errors & Retry ====================

            is AIEvent.ProviderErrorOccurred -> {
                // Provider error - permanent failure (not configured, invalid config, etc.)
                // CHAT: return to IDLE (session stays active, user can configure provider)
                // AUTOMATION: close session with ERROR reason
                if (state.sessionType == SessionType.AUTOMATION) {
                    // AUTOMATION - close session with ERROR
                    state.copy(
                        phase = Phase.CLOSED,
                        endReason = SessionEndReason.ERROR,
                        lastEventTime = currentTime
                    )
                } else {
                    // CHAT - return to IDLE (session stays active, event processor shows toast)
                    state.copy(
                        phase = Phase.IDLE,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.NetworkErrorOccurred -> {
                // CHAT: return to IDLE (session stays active, user must retry manually)
                // AUTOMATION: transition to WAITING_NETWORK_RETRY (automatic retry)
                if (state.sessionType == SessionType.AUTOMATION) {
                    state.copy(
                        phase = Phase.WAITING_NETWORK_RETRY,
                        lastEventTime = currentTime
                    )
                } else {
                    // CHAT - return to IDLE (session stays active per AI.md line 461)
                    state.copy(
                        phase = Phase.IDLE,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.ParseErrorOccurred -> {
                // Parse error - retry immediately (AI should self-correct)
                // No artificial limit, maxRoundtrips will stop if AI loops
                state.copy(
                    phase = Phase.RETRYING_AFTER_FORMAT_ERROR,
                    totalRoundtrips = state.totalRoundtrips + 1,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.ActionFailureOccurred -> {
                // Action failed - retry (no limit, maxRoundtrips will stop if AI loops)
                state.copy(
                    phase = Phase.RETRYING_AFTER_ACTION_FAILURE,
                    totalRoundtrips = state.totalRoundtrips + 1,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.NetworkRetryScheduled -> {
                // Transition from WAITING_NETWORK_RETRY to CALLING_AI
                // (network retry scheduled, will check again after delay)
                state.copy(
                    phase = Phase.CALLING_AI,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.NetworkAvailable -> {
                // Transition from WAITING_NETWORK_RETRY to CALLING_AI
                // Update lastNetworkAvailableTime to exclude downtime from global timeout
                state.copy(
                    phase = Phase.CALLING_AI,
                    lastNetworkAvailableTime = currentTime,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.RetryScheduled -> {
                // Transition from retry phases to CALLING_AI
                state.copy(
                    phase = Phase.CALLING_AI,
                    lastEventTime = currentTime
                )
            }

            is AIEvent.SystemErrorOccurred -> {
                // Transition to CLOSED on system error
                state.copy(
                    phase = Phase.CLOSED,
                    endReason = SessionEndReason.ERROR,
                    lastEventTime = currentTime
                )
            }

            // ==================== System ====================

            is AIEvent.SchedulerHeartbeat -> {
                // Heartbeat doesn't change state directly
                // Event processor handles watchdog and scheduling logic
                state.copy(
                    lastEventTime = currentTime
                )
            }
        }
    }

    /**
     * Handle AIResponseParsed event with complex decision logic.
     */
    private fun handleAIResponseParsed(
        state: AIState,
        event: AIEvent.AIResponseParsed,
        currentTime: Long
    ): AIState {
        val message = event.message
        val hasActions = !message.actionCommands.isNullOrEmpty()
        val hasDataCommands = !message.dataCommands.isNullOrEmpty()

        // Priority 0: COMPLETED flag (AUTOMATION only) - with special handling
        if (message.completed == true && state.sessionType == SessionType.AUTOMATION) {
            // If actions present: execute first, completed will be handled after
            if (hasActions) {
                return state.copy(
                    phase = Phase.EXECUTING_ACTIONS,
                    awaitingCompletionConfirmation = true, // Store flag to handle after actions
                    lastEventTime = currentTime
                )
            }

            // If dataCommands present: illogical, ignore queries and go to confirmation
            if (hasDataCommands) {
                com.assistant.core.utils.LogManager.aiSession(
                    "completed=true with dataCommands ignored, going to confirmation",
                    "WARN"
                )
                return state.copy(
                    phase = Phase.PREPARING_CONTINUATION,
                    continuationReason = ContinuationReason.COMPLETION_CONFIRMATION_REQUIRED,
                    awaitingCompletionConfirmation = true,
                    lastEventTime = currentTime
                )
            }

            // Neither actions nor queries: direct confirmation logic
            return if (!state.awaitingCompletionConfirmation) {
                // First completed=true - ask for confirmation
                state.copy(
                    phase = Phase.PREPARING_CONTINUATION,
                    continuationReason = ContinuationReason.COMPLETION_CONFIRMATION_REQUIRED,
                    awaitingCompletionConfirmation = true,
                    lastEventTime = currentTime
                )
            } else {
                // Second completed=true - actually complete the session
                state.copy(
                    phase = Phase.AWAITING_SESSION_CLOSURE,
                    awaitingCompletionConfirmation = false, // Reset flag
                    lastEventTime = currentTime
                )
            }
        }

        // Priority 1: Communication module (CHAT only - ignored for AUTOMATION)
        if (message.communicationModule != null && state.sessionType == SessionType.CHAT) {
            return state.copy(
                phase = Phase.WAITING_COMMUNICATION_RESPONSE,
                // waitingContext will be set by event processor
                lastEventTime = currentTime
            )
        }

        // Priority 2: Data commands (queries)
        // Reset awaitingCompletionConfirmation since AI is continuing work
        if (!message.dataCommands.isNullOrEmpty()) {
            return state.copy(
                phase = Phase.EXECUTING_DATA_QUERIES,
                awaitingCompletionConfirmation = false, // Reset since AI is continuing work
                lastEventTime = currentTime
            )
        }

        // Priority 3: Action commands
        // CHAT: Go through WAITING_VALIDATION first - ValidationResolver decides if validation needed
        // AUTOMATION: Execute directly without validation (no user interaction)
        // Reset awaitingCompletionConfirmation since AI is continuing work
        if (!message.actionCommands.isNullOrEmpty()) {
            val nextPhase = if (state.sessionType == SessionType.CHAT) {
                Phase.WAITING_VALIDATION
            } else {
                Phase.EXECUTING_ACTIONS
            }

            return state.copy(
                phase = nextPhase,
                awaitingCompletionConfirmation = false, // Reset since AI is continuing work
                lastEventTime = currentTime
            )
        }

        // No commands - behavior depends on session type
        // CHAT: preText only → return to IDLE (ready for next user message)
        // AUTOMATION: no commands → guidance needed via PREPARING_CONTINUATION phase
        // Also reset awaitingCompletionConfirmation since AI is continuing work
        return if (state.sessionType == SessionType.CHAT) {
            state.copy(
                phase = Phase.IDLE,
                awaitingCompletionConfirmation = false, // Reset if was set
                lastEventTime = currentTime
            )
        } else {
            // AUTOMATION without commands - need to guide AI to provide commands or mark as completed
            state.copy(
                phase = Phase.PREPARING_CONTINUATION,
                continuationReason = ContinuationReason.AUTOMATION_NO_COMMANDS,
                awaitingCompletionConfirmation = false, // Reset since AI is continuing work
                lastEventTime = currentTime
            )
        }
    }

    /**
     * Transition to session completion based on session type.
     *
     * - CHAT: Returns to IDLE (session remains active, waiting for next user message)
     * - AUTOMATION: Transitions to AWAITING_SESSION_CLOSURE (5s countdown before CLOSED)
     */
    private fun transitionToCompletion(
        state: AIState,
        currentTime: Long,
        endReason: SessionEndReason? = null
    ): AIState {
        return when (state.sessionType) {
            SessionType.CHAT -> {
                // CHAT never closes automatically - return to IDLE
                state.copy(
                    phase = Phase.IDLE,
                    lastEventTime = currentTime
                )
            }
            SessionType.AUTOMATION -> {
                // AUTOMATION: 5s countdown before closure
                state.copy(
                    phase = Phase.AWAITING_SESSION_CLOSURE,
                    endReason = endReason,
                    lastEventTime = currentTime
                )
            }
            else -> {
                // Fallback: IDLE for unknown session types
                state.copy(
                    phase = Phase.IDLE,
                    lastEventTime = currentTime
                )
            }
        }
    }

    /**
     * Handle ActionsExecuted event with retry and continuation logic.
     */
    private fun handleActionsExecuted(
        state: AIState,
        event: AIEvent.ActionsExecuted,
        limits: SessionLimits,
        currentTime: Long
    ): AIState {
        // Check total roundtrips limit first
        val newTotalRoundtrips = state.totalRoundtrips + 1
        if (newTotalRoundtrips >= limits.maxAutonomousRoundtrips) {
            return transitionToCompletion(
                state = state.copy(
                    totalRoundtrips = newTotalRoundtrips
                ),
                currentTime = currentTime,
                endReason = SessionEndReason.LIMIT_REACHED
            )
        }

        if (event.allSuccess) {
            // All actions succeeded

            // Check if awaitingCompletionConfirmation is true (completed flag was set with actions)
            if (state.awaitingCompletionConfirmation) {
                // Actions succeeded AND completed flag was set → go to confirmation
                return state.copy(
                    phase = Phase.PREPARING_CONTINUATION,
                    continuationReason = ContinuationReason.COMPLETION_CONFIRMATION_REQUIRED,
                    totalRoundtrips = newTotalRoundtrips,
                    lastEventTime = currentTime
                )
            }

            // Normal flow: no completed flag
            val shouldContinue = event.keepControl == true || state.sessionType == SessionType.AUTOMATION

            return if (shouldContinue) {
                // Continue autonomous execution
                state.copy(
                    phase = Phase.CALLING_AI,
                    totalRoundtrips = newTotalRoundtrips,
                    lastEventTime = currentTime
                )
            } else {
                // keepControl=false or null for CHAT - return to IDLE (await next user message)
                state.copy(
                    phase = Phase.IDLE,
                    totalRoundtrips = newTotalRoundtrips,
                    lastEventTime = currentTime
                )
            }
        } else {
            // Some actions failed - reset awaitingCompletionConfirmation (work not completed)
            // Retry logic handled by ActionFailureOccurred event
            // This path should not be reached as event processor emits ActionFailureOccurred
            return state.copy(
                awaitingCompletionConfirmation = false // Reset since actions failed
            )
        }
    }
}
