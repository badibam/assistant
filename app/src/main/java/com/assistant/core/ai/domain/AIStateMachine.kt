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
                // Transition from IDLE to EXECUTING_ENRICHMENTS
                if (state.phase != Phase.IDLE) {
                    // Slot not available - event processor should handle scheduling
                    state
                } else {
                    state.copy(
                        sessionId = event.sessionId,
                        phase = Phase.EXECUTING_ENRICHMENTS,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.SessionCompleted -> {
                // Transition to COMPLETED from any phase
                AIState.idle().copy(
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

            // ==================== User Interactions ====================

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
                    // User rejected - transition to COMPLETED
                    state.copy(
                        phase = Phase.COMPLETED,
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

            // ==================== Command Execution ====================

            is AIEvent.DataQueriesExecuted -> {
                // Check consecutive data query limit
                val newConsecutiveQueries = state.consecutiveDataQueries + 1

                if (newConsecutiveQueries >= limits.maxDataQueryIterations) {
                    // Limit reached - transition to COMPLETED
                    state.copy(
                        phase = Phase.COMPLETED,
                        consecutiveDataQueries = newConsecutiveQueries,
                        totalRoundtrips = state.totalRoundtrips + 1,
                        lastEventTime = currentTime
                    )
                } else {
                    // Continue - transition to CALLING_AI with query results
                    state.copy(
                        phase = Phase.CALLING_AI,
                        consecutiveDataQueries = newConsecutiveQueries,
                        consecutiveActionFailures = 0, // Reset other counters
                        totalRoundtrips = state.totalRoundtrips + 1,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.ActionsExecuted -> {
                handleActionsExecuted(state, event, limits, currentTime)
            }

            // ==================== Completion ====================

            is AIEvent.CompletionConfirmed -> {
                // Transition to COMPLETED
                state.copy(
                    phase = Phase.COMPLETED,
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

            is AIEvent.NetworkErrorOccurred -> {
                // CHAT: immediate failure (handled by event processor)
                // AUTOMATION: transition to WAITING_NETWORK_RETRY
                if (state.sessionType == SessionType.AUTOMATION) {
                    state.copy(
                        phase = Phase.WAITING_NETWORK_RETRY,
                        lastEventTime = currentTime
                    )
                } else {
                    // CHAT - complete session with network error
                    state.copy(
                        phase = Phase.COMPLETED,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.ParseErrorOccurred -> {
                // Check consecutive format error limit
                val newFormatErrors = state.consecutiveFormatErrors + 1

                if (newFormatErrors >= limits.maxFormatErrorRetries) {
                    // Limit reached - transition to COMPLETED
                    state.copy(
                        phase = Phase.COMPLETED,
                        consecutiveFormatErrors = newFormatErrors,
                        totalRoundtrips = state.totalRoundtrips + 1,
                        lastEventTime = currentTime
                    )
                } else {
                    // Retry - transition to RETRYING_AFTER_FORMAT_ERROR
                    state.copy(
                        phase = Phase.RETRYING_AFTER_FORMAT_ERROR,
                        consecutiveFormatErrors = newFormatErrors,
                        totalRoundtrips = state.totalRoundtrips + 1,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.ActionFailureOccurred -> {
                // Check consecutive action failure limit
                val newActionFailures = state.consecutiveActionFailures + 1

                if (newActionFailures >= limits.maxActionRetries) {
                    // Limit reached - transition to COMPLETED
                    state.copy(
                        phase = Phase.COMPLETED,
                        consecutiveActionFailures = newActionFailures,
                        consecutiveDataQueries = 0, // Reset other counters
                        totalRoundtrips = state.totalRoundtrips + 1,
                        lastEventTime = currentTime
                    )
                } else {
                    // Retry - transition to RETRYING_AFTER_ACTION_FAILURE
                    state.copy(
                        phase = Phase.RETRYING_AFTER_ACTION_FAILURE,
                        consecutiveActionFailures = newActionFailures,
                        consecutiveDataQueries = 0, // Reset other counters
                        totalRoundtrips = state.totalRoundtrips + 1,
                        lastEventTime = currentTime
                    )
                }
            }

            is AIEvent.NetworkRetryScheduled,
            is AIEvent.NetworkAvailable -> {
                // Transition from WAITING_NETWORK_RETRY to CALLING_AI
                state.copy(
                    phase = Phase.CALLING_AI,
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
                // Transition to COMPLETED on system error
                state.copy(
                    phase = Phase.COMPLETED,
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

        // Priority 0: COMPLETED flag (AUTOMATION only)
        if (message.completed == true && state.sessionType == SessionType.AUTOMATION) {
            return state.copy(
                phase = Phase.WAITING_COMPLETION_CONFIRMATION,
                waitingContext = WaitingContext.CompletionConfirmation(
                    aiMessageId = "", // Will be set by event processor
                    scheduledConfirmationTime = currentTime + 1000L // 1 second delay
                ),
                lastEventTime = currentTime
            )
        }

        // Priority 1: Validation request
        if (message.validationRequest == true) {
            return state.copy(
                phase = Phase.WAITING_VALIDATION,
                // waitingContext will be set by event processor after ValidationResolver
                lastEventTime = currentTime
            )
        }

        // Priority 2: Communication module (CHAT only)
        if (message.communicationModule != null && state.sessionType == SessionType.CHAT) {
            return state.copy(
                phase = Phase.WAITING_COMMUNICATION_RESPONSE,
                // waitingContext will be set by event processor
                lastEventTime = currentTime
            )
        }

        // Priority 3: Data commands (queries)
        if (!message.dataCommands.isNullOrEmpty()) {
            return state.copy(
                phase = Phase.EXECUTING_DATA_QUERIES,
                lastEventTime = currentTime
            )
        }

        // Priority 4: Action commands
        if (!message.actionCommands.isNullOrEmpty()) {
            return state.copy(
                phase = Phase.EXECUTING_ACTIONS,
                lastEventTime = currentTime
            )
        }

        // No commands - session completed
        return state.copy(
            phase = Phase.COMPLETED,
            lastEventTime = currentTime
        )
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
            return state.copy(
                phase = Phase.COMPLETED,
                totalRoundtrips = newTotalRoundtrips,
                lastEventTime = currentTime
            )
        }

        if (event.allSuccess) {
            // All actions succeeded
            val shouldContinue = event.keepControl == true || state.sessionType == SessionType.AUTOMATION

            return if (shouldContinue) {
                // Continue autonomous execution
                state.copy(
                    phase = Phase.CALLING_AI,
                    consecutiveActionFailures = 0,
                    consecutiveDataQueries = 0,
                    consecutiveFormatErrors = 0,
                    totalRoundtrips = newTotalRoundtrips,
                    lastEventTime = currentTime
                )
            } else {
                // keepControl=false or null for CHAT - stop here
                state.copy(
                    phase = Phase.COMPLETED,
                    totalRoundtrips = newTotalRoundtrips,
                    lastEventTime = currentTime
                )
            }
        } else {
            // Some actions failed - retry logic handled by ActionFailureOccurred event
            // This path should not be reached as event processor emits ActionFailureOccurred
            state
        }
    }
}
