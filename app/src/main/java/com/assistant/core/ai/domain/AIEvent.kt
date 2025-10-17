package com.assistant.core.ai.domain

import com.assistant.core.ai.data.AIMessage
import com.assistant.core.ai.data.CommandResult
import com.assistant.core.ai.data.SessionEndReason

/**
 * Events that drive the AI state machine.
 *
 * Each event represents something that happened and triggers a state transition.
 * Events are immutable data describing what occurred.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Events are emitted by external actors (UI, network, timers, etc.)
 * - AIEventProcessor processes events and triggers side effects
 * - AIStateMachine handles pure state transitions
 * - AIStateRepository ensures atomic memory + DB updates
 */
sealed class AIEvent {

    // ==================== Session Lifecycle ====================

    /**
     * Request to activate a session (make it the active session).
     * Can be CHAT or AUTOMATION session.
     *
     * Scheduler decides if activation is immediate (slot free)
     * or requires eviction/queueing (slot occupied).
     *
     * @param sessionId ID of session to activate
     * @param sessionType Type of session (CHAT, AUTOMATION, SEED)
     */
    data class SessionActivationRequested(
        val sessionId: String,
        val sessionType: com.assistant.core.ai.data.SessionType
    ) : AIEvent()

    /**
     * Session completed and should be closed.
     *
     * @param reason Why the session ended (COMPLETED, CANCELLED, TIMEOUT, ERROR, etc.)
     */
    data class SessionCompleted(val reason: SessionEndReason) : AIEvent()

    // ==================== User Message ====================

    /**
     * User message enrichments executed successfully.
     *
     * Enrichments are blocks added by user (POINTER, USE, CREATE, MODIFY_CONFIG)
     * that generate data commands executed before calling AI.
     *
     * @param results Results of enrichment command executions
     */
    data class EnrichmentsExecuted(val results: List<CommandResult>) : AIEvent()

    // ==================== AI Interaction ====================

    /**
     * AI provider returned a response (raw content string).
     *
     * Next phase will parse this content into AIMessage structure.
     *
     * @param content Raw AI response content (JSON string expected)
     */
    data class AIResponseReceived(val content: String) : AIEvent()

    /**
     * AI response successfully parsed into AIMessage.
     *
     * Decision logic based on AIMessage fields determines next transition:
     * - completed=true → WAITING_COMPLETION_CONFIRMATION (AUTOMATION only)
     * - validationRequest=true → WAITING_VALIDATION
     * - communicationModule → WAITING_COMMUNICATION_RESPONSE (CHAT only)
     * - dataCommands → EXECUTING_DATA_QUERIES
     * - actionCommands → EXECUTING_ACTIONS
     * - else → COMPLETED
     *
     * @param message Parsed AI message structure
     */
    data class AIResponseParsed(val message: AIMessage) : AIEvent()

    // ==================== Continuation ====================

    /**
     * Continuation guidance message prepared and ready to continue.
     *
     * Emitted after PREPARING_CONTINUATION phase completes.
     * Transition back to CALLING_AI to send guidance message to AI.
     */
    object ContinuationReady : AIEvent()

    // ==================== User Interactions ====================

    /**
     * User validated or rejected actions (CHAT only).
     *
     * @param approved true if user approved, false if rejected
     */
    data class ValidationReceived(val approved: Boolean) : AIEvent()

    /**
     * User responded to communication module (CHAT only).
     *
     * @param response User's text response
     */
    data class CommunicationResponseReceived(val response: String) : AIEvent()

    /**
     * User manually paused active session.
     *
     * Session keeps the slot but stops sending new requests to AI.
     * If waiting for AI response, will process it when received then pause.
     * Phase transitions to PAUSED, requires manual resume.
     */
    object SessionPaused : AIEvent()

    /**
     * User manually resumed paused session.
     *
     * Session continues from PAUSED phase and resumes AI interaction flow.
     */
    object SessionResumed : AIEvent()

    /**
     * User interrupted current AI round (CHAT only).
     *
     * Cancels the current AI call/processing but keeps session active.
     * If AI response arrives, it will be ignored.
     * Session remains active and waits for next user message.
     *
     * Different from SessionPaused: does not require manual resume,
     * session continues automatically when user sends next message.
     */
    object AIRoundInterrupted : AIEvent()

    /**
     * User message sent (triggers enrichment execution).
     *
     * First step of user message processing flow.
     */
    object UserMessageSent : AIEvent()

    // ==================== Command Execution ====================

    /**
     * Data query commands executed successfully.
     *
     * Results are formatted and will be sent back to AI in next round.
     *
     * @param results Command execution results
     */
    data class DataQueriesExecuted(val results: List<CommandResult>) : AIEvent()

    /**
     * Action commands executed (successfully or with failures).
     *
     * If all successful and keepControl=true (or AUTOMATION), AI continues.
     * If failures, retry logic applies.
     *
     * @param results Command execution results
     * @param allSuccess true if all actions succeeded
     * @param keepControl keepControl flag from AIMessage (null if not specified)
     */
    data class ActionsExecuted(
        val results: List<CommandResult>,
        val allSuccess: Boolean,
        val keepControl: Boolean?
    ) : AIEvent()

    // ==================== Completion (AUTOMATION only) ====================

    /**
     * System auto-confirmed completion or user clicked "Confirmer".
     *
     * Triggers session completion with COMPLETED reason.
     */
    object CompletionConfirmed : AIEvent()

    /**
     * User clicked "Rejeter" during completion confirmation window.
     *
     * AI continues working instead of completing.
     */
    object CompletionRejected : AIEvent()

    // ==================== Errors & Retry ====================

    /**
     * Provider error occurred (provider not configured, invalid config, etc.).
     *
     * This is a permanent error that should not retry.
     * Session ends with ERROR reason and user is notified via toast.
     *
     * @param message Error description for user
     */
    data class ProviderErrorOccurred(val message: String) : AIEvent()

    /**
     * Network error occurred while calling AI.
     *
     * CHAT: Stop immediately, no retry.
     * AUTOMATION: Infinite retry with 30s delay.
     *
     * @param attempt Retry attempt number (0 = first failure)
     */
    data class NetworkErrorOccurred(val attempt: Int) : AIEvent()

    /**
     * AI response parsing failed (format error).
     *
     * Store error system message and retry with error details.
     * Retry count checked against consecutiveFormatErrors limit.
     *
     * @param error Parse error description
     */
    data class ParseErrorOccurred(val error: String) : AIEvent()

    /**
     * Action execution had failures.
     *
     * Store results system message and retry with failure details.
     * Retry count checked against consecutiveActionFailures limit.
     *
     * @param errors Failed command results
     */
    data class ActionFailureOccurred(val errors: List<CommandResult>) : AIEvent()

    /**
     * Network retry scheduled (after 30s delay).
     *
     * Transition back to CALLING_AI phase to retry.
     */
    object NetworkRetryScheduled : AIEvent()

    /**
     * Retry scheduled after format error or action failure.
     *
     * Transition back to CALLING_AI phase to retry with error context.
     */
    object RetryScheduled : AIEvent()

    /**
     * Network became available after being offline.
     *
     * Used to trigger immediate retry instead of waiting full 30s delay.
     */
    object NetworkAvailable : AIEvent()

    /**
     * System error occurred (unexpected exception).
     *
     * Triggers session completion with ERROR reason.
     *
     * @param message Error description
     */
    data class SystemErrorOccurred(val message: String) : AIEvent()

    // ==================== System ====================

    /**
     * Scheduler heartbeat (every 5 minutes).
     *
     * Triggers:
     * - Watchdog check (timeout detection)
     * - Queue processing (if slot free)
     * - Scheduled automation check (if slot free + queue empty)
     */
    object SchedulerHeartbeat : AIEvent()
}
