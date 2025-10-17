package com.assistant.core.ai.domain

/**
 * Reason for entering PREPARING_CONTINUATION phase.
 *
 * This phase is used to prepare guidance messages for the AI before continuing
 * autonomous execution. Different reasons trigger different guidance strategies.
 *
 * Architecture: Event-Driven State Machine (V2)
 * - Used by AIState.continuationReason field
 * - Determines which guidance message to send to AI
 * - Specific to AUTOMATION sessions
 */
enum class ContinuationReason {
    /**
     * AUTOMATION responded without any commands (no data queries, no actions).
     * Need to guide AI to either provide commands or mark as completed.
     */
    AUTOMATION_NO_COMMANDS,

    /**
     * AI sent completed=true flag for the first time.
     * Need to ask AI to confirm this means ALL work is done (double confirmation).
     */
    COMPLETION_CONFIRMATION_REQUIRED
}
