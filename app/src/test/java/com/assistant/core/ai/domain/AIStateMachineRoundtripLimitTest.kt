package com.assistant.core.ai.domain

import com.assistant.core.ai.data.CommandStatus
import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers where the autonomous roundtrip limit stops an AUTOMATION session.
 *
 * Seven transitions increment AIState.totalRoundtrips, and the limit is read once, on the
 * way out of transition(), at the moment a call would go out. Each loop that can reach the
 * provider is checked here, since the point of the guard being in one place is that no path
 * escapes it -- and the four that did escape are the reason it moved there.
 */
class AIStateMachineRoundtripLimitTest {

    // ==================== The productive loop ====================

    /**
     * Actions that succeed send the automation back to the provider, and that is the loop
     * the limit was written for: reaching it closes the session with LIMIT_REACHED.
     */
    @Test
    fun actionsExecuted_closesTheSessionOnceTheLimitIsReached() {
        // One below the limit: the session keeps going.
        val stillRunning = AIStateMachine.transition(
            state = automationAt(Phase.EXECUTING_ACTIONS, roundtrips = 1),
            event = AIEvent.ActionsExecuted(results = emptyList(), allSuccess = true, keepControl = null),
            limits = testLimits,
            currentTime = T0
        )
        assertEquals(Phase.CALLING_AI, stillRunning.phase)
        assertEquals(2, stillRunning.totalRoundtrips)

        // Reaching the limit: the session closes and says why.
        val stopped = AIStateMachine.transition(
            state = automationAt(Phase.EXECUTING_ACTIONS, roundtrips = 2),
            event = AIEvent.ActionsExecuted(results = emptyList(), allSuccess = true, keepControl = null),
            limits = testLimits,
            currentTime = T0
        )
        assertEquals(3, stopped.totalRoundtrips)
        assertEquals(SessionEndReason.LIMIT_REACHED, stopped.endReason)
        assertEquals(Phase.AWAITING_SESSION_CLOSURE, stopped.phase)
    }

    /**
     * A CHAT reaching the same transition hands control back instead of closing, because a
     * CHAT never closes itself. Its limit is Int.MAX_VALUE, so in practice only an explicit
     * keepControl=false brings it here.
     */
    @Test
    fun actionsExecuted_returnsControlInAChatRatherThanClosing() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.EXECUTING_ACTIONS, roundtrips = 2),
            event = AIEvent.ActionsExecuted(results = emptyList(), allSuccess = true, keepControl = false),
            limits = testLimits,
            currentTime = T0
        )

        assertEquals(Phase.IDLE, state.phase)
        assertEquals(null, state.endReason)
    }

    /** keepControl=true keeps a CHAT working without handing control back between rounds. */
    @Test
    fun actionsExecuted_keepsGoingInAChatWhenAskedTo() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.EXECUTING_ACTIONS),
            event = AIEvent.ActionsExecuted(results = emptyList(), allSuccess = true, keepControl = true),
            limits = testLimits,
            currentTime = T0
        )

        assertEquals(Phase.CALLING_AI, state.phase)
    }

    /**
     * Actions that succeeded while a completion claim was pending settle the claim: the
     * confirmation is asked for now that the work is actually done.
     */
    @Test
    fun actionsExecuted_asksForConfirmationWhenCompletionWasClaimed() {
        val state = AIStateMachine.transition(
            state = automationAt(Phase.EXECUTING_ACTIONS, awaitingCompletionConfirmation = true),
            event = AIEvent.ActionsExecuted(results = emptyList(), allSuccess = true, keepControl = null),
            limits = testLimits,
            currentTime = T0
        )

        assertEquals(Phase.PREPARING_CONTINUATION, state.phase)
        assertEquals(ContinuationReason.COMPLETION_CONFIRMATION_REQUIRED, state.continuationReason)
    }

    // ==================== The loops that used to walk past it ====================

    /**
     * A malformed reply loops CALLING_AI -> PARSING_AI_RESPONSE -> ParseErrorOccurred ->
     * RETRYING_AFTER_FORMAT_ERROR -> RetryScheduled -> CALLING_AI. It never passes through
     * ActionsExecuted, so for a long time it never met the limit at all and ran until the
     * watchdog's ten minutes of wall clock. It is now stopped on the count, like any other.
     */
    @Test
    fun parseErrorLoop_isStoppedOnTheCount() {
        var state = automationAt(Phase.CALLING_AI)

        repeat(CYCLES_WELL_PAST_THE_LIMIT) {
            if (state.phase != Phase.CALLING_AI) return@repeat

            state = AIStateMachine.transition(
                state = state.copy(phase = Phase.PARSING_AI_RESPONSE),
                event = AIEvent.ParseErrorOccurred("unparseable response"),
                limits = testLimits,
                currentTime = T0
            )
            state = AIStateMachine.transition(state, AIEvent.RetryScheduled, testLimits, T0)
        }

        assertEquals(testLimits.maxAutonomousRoundtrips, state.totalRoundtrips)
        assertEquals(SessionEndReason.LIMIT_REACHED, state.endReason)
        assertEquals(Phase.AWAITING_SESSION_CLOSURE, state.phase)
    }

    /** Same for an action that keeps failing. */
    @Test
    fun actionFailureLoop_isStoppedOnTheCount() {
        var state = automationAt(Phase.EXECUTING_ACTIONS)

        repeat(CYCLES_WELL_PAST_THE_LIMIT) {
            if (state.phase != Phase.CALLING_AI && state.totalRoundtrips > 0) return@repeat

            state = AIStateMachine.transition(
                state = state.copy(phase = Phase.EXECUTING_ACTIONS),
                event = AIEvent.ActionFailureOccurred(errors = listOf(commandResult(CommandStatus.FAILED))),
                limits = testLimits,
                currentTime = T0
            )
            state = AIStateMachine.transition(state, AIEvent.RetryScheduled, testLimits, T0)
        }

        assertEquals(testLimits.maxAutonomousRoundtrips, state.totalRoundtrips)
        assertEquals(SessionEndReason.LIMIT_REACHED, state.endReason)
    }

    /** And for an automation that keeps answering without any command. */
    @Test
    fun continuationLoop_isStoppedOnTheCount() {
        var state = automationAt(Phase.PREPARING_CONTINUATION)

        repeat(CYCLES_WELL_PAST_THE_LIMIT) {
            if (state.endReason != null) return@repeat

            state = AIStateMachine.transition(
                state = state.copy(phase = Phase.PREPARING_CONTINUATION),
                event = AIEvent.ContinuationReady,
                limits = testLimits,
                currentTime = T0
            )
        }

        assertEquals(testLimits.maxAutonomousRoundtrips, state.totalRoundtrips)
        assertEquals(SessionEndReason.LIMIT_REACHED, state.endReason)
    }

    /**
     * The guard catches the moment a call would go out, so a phase that only leads there is
     * let through and caught one transition later -- after the guidance is prepared, before
     * anything is sent.
     */
    @Test
    fun aPhaseLeadingToACallIsCaughtOnItsWayThrough() {
        val atTheLimit = automationAt(
            Phase.EXECUTING_ACTIONS,
            roundtrips = testLimits.maxAutonomousRoundtrips - 1,
            awaitingCompletionConfirmation = true
        )

        // Reaching the limit while going to prepare a continuation: not stopped yet.
        val preparing = AIStateMachine.transition(
            state = atTheLimit,
            event = AIEvent.ActionsExecuted(results = emptyList(), allSuccess = true, keepControl = null),
            limits = testLimits,
            currentTime = T0
        )
        assertEquals(Phase.PREPARING_CONTINUATION, preparing.phase)
        assertEquals(null, preparing.endReason)

        // The step that would place the call is.
        val stopped = AIStateMachine.transition(preparing, AIEvent.ContinuationReady, testLimits, T0)
        assertEquals(SessionEndReason.LIMIT_REACHED, stopped.endReason)
    }

    /** A CHAT is not limited, so the guard never fires on one however long it runs. */
    @Test
    fun aChatIsNeverStoppedOnTheCount() {
        val chatLimits = AILimitsConfig.default().getLimitsForSessionType(SessionType.CHAT)
        var state = chatAt(Phase.PREPARING_CONTINUATION, roundtrips = 1_000)

        state = AIStateMachine.transition(state, AIEvent.ContinuationReady, chatLimits, T0)

        assertEquals(Phase.CALLING_AI, state.phase)
        assertEquals(null, state.endReason)
    }

    /** Entering the guidance phase clears the reason that sent it there. */
    @Test
    fun continuationReady_clearsTheReasonItWasGuidedFor() {
        val guided = automationAt(Phase.PREPARING_CONTINUATION)
            .copy(continuationReason = ContinuationReason.AUTOMATION_NO_COMMANDS)

        val state = AIStateMachine.transition(
            state = guided,
            event = AIEvent.ContinuationReady,
            limits = testLimits,
            currentTime = T0
        )

        assertEquals(null, state.continuationReason)
    }

    companion object {
        /** Enough cycles that a limit of 3 could not be missed by chance. */
        private const val CYCLES_WELL_PAST_THE_LIMIT = 10
    }
}
