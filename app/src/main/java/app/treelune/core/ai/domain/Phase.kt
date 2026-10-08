package app.treelune.core.ai.domain

/**
 * Represents the current execution phase of an AI session.
 *
 * Phase transitions are managed by AIStateMachine in response to AIEvents.
 * Each phase represents a distinct state in the AI execution lifecycle.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - IDLE: No active session
 * - EXECUTING_ENRICHMENTS: Processing user message enrichments
 * - CALLING_AI: Sending prompt to AI provider
 * - PARSING_AI_RESPONSE: Parsing AI response JSON
 * - PREPARING_CONTINUATION: Preparing guidance message before continuing (AUTOMATION only)
 * - WAITING_VALIDATION: Waiting for user validation (CHAT only)
 * - WAITING_COMMUNICATION_RESPONSE: Waiting for user response to communication module (CHAT only)
 * - EXECUTING_DATA_QUERIES: Executing data query commands
 * - EXECUTING_ACTIONS: Executing action commands
 * - WAITING_NETWORK_RETRY: Waiting before network retry (infinite for AUTOMATION)
 * - RETRYING_AFTER_FORMAT_ERROR: Retrying after AI format error
 * - RETRYING_AFTER_ACTION_FAILURE: Retrying after action failure
 * - AWAITING_SESSION_CLOSURE: Waiting 5s before closing session (AUTOMATION only)
 * - CLOSED: Session finished
 */
enum class Phase {
    /** No active session - slot available */
    IDLE,

    /** Processing user message enrichments (blocks, commands) */
    EXECUTING_ENRICHMENTS,

    /** Calling AI provider with prompt */
    CALLING_AI,

    /** Parsing AI response JSON into AIMessage */
    PARSING_AI_RESPONSE,

    /** Preparing guidance message before continuing autonomous execution (AUTOMATION only) */
    PREPARING_CONTINUATION,

    /** Waiting for user validation of actions (CHAT only) */
    WAITING_VALIDATION,

    /** Waiting for user response to communication module (CHAT only) */
    WAITING_COMMUNICATION_RESPONSE,

    /** Waiting for the user to send or refuse data above the size threshold (CHAT only) */
    WAITING_DATA_CONFIRMATION,

    /** Executing data query commands (tool_data.get, etc.) */
    EXECUTING_DATA_QUERIES,

    /** Executing action commands (tool_data.create, zones.update, etc.) */
    EXECUTING_ACTIONS,

    /** Waiting for system auto-confirmation of completion (AUTOMATION only) */

    /** Waiting before network retry (30s delay, infinite for AUTOMATION) */
    WAITING_NETWORK_RETRY,

    /** Retrying after AI format/parse error */
    RETRYING_AFTER_FORMAT_ERROR,

    /** Retrying after action execution failure */
    RETRYING_AFTER_ACTION_FAILURE,

    /**
     * AI round interrupted by user (CHAT only).
     * Brief: the call in flight is already cancelled, the interruption is recorded, then IDLE.
     */
    INTERRUPTED,

    /** Waiting 5s before closing completed AUTOMATION session*/
    AWAITING_SESSION_CLOSURE,

    /** Session closed - ready for cleanup */
    CLOSED;

    /**
     * Check if this phase represents an inactive state where user interaction is expected.
     * Used for inactivity timeout calculation.
     */
    fun isWaitingForUser(): Boolean = when (this) {
        WAITING_VALIDATION, WAITING_COMMUNICATION_RESPONSE, WAITING_DATA_CONFIRMATION -> true
        else -> false
    }

    /**
     * Check if this phase represents active processing.
     * Used for inactivity timeout calculation (active phases don't timeout).
     */
    fun isActiveProcessing(): Boolean = when (this) {
        CALLING_AI, EXECUTING_ENRICHMENTS, EXECUTING_DATA_QUERIES,
        EXECUTING_ACTIONS, PARSING_AI_RESPONSE, PREPARING_CONTINUATION -> true
        else -> false
    }

    /**
     * Check if this phase represents a network retry wait state.
     * Network retry phases should not timeout (they're actively waiting).
     */
    fun isNetworkRetry(): Boolean = this == WAITING_NETWORK_RETRY
}
