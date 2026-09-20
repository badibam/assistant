package com.assistant.core.ai.domain

import com.assistant.core.ai.data.CommandStatus
import com.assistant.core.ai.data.SessionEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures where the autonomous roundtrip limit actually stops an AUTOMATION session.
 *
 * The limit exists to stop an AUTOMATION that loops. AIState.totalRoundtrips is incremented
 * by several transitions, but limits.maxAutonomousRoundtrips is read by only one of them.
 * These tests pin which loops the limit closes and which ones it lets run, so that a change
 * either way shows up as a failing test rather than as unexplained API spend.
 */
class AIStateMachineRoundtripLimitTest {

    // ==================== The path that enforces the limit ====================

    /**
     * ActionsExecuted is the transition that reads the limit. Reaching it closes the
     * session with LIMIT_REACHED, which is the behaviour the limit was written for.
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

    // ==================== The loops that do not consult the limit ====================

    /**
     * A malformed AI response loops CALLING_AI -> PARSING_AI_RESPONSE -> ParseErrorOccurred
     * -> RETRYING_AFTER_FORMAT_ERROR -> RetryScheduled -> CALLING_AI. The cycle never passes
     * through ActionsExecuted, so it never reaches the one transition that reads the limit.
     *
     * This test states what the machine does today, not what it should do: the counter runs
     * far past maxAutonomousRoundtrips and the session stays open. Outside the state machine
     * the watchdog still closes it on AUTOMATION_GLOBAL_TIMEOUT (AISessionScheduler), so the
     * backstop here is wall-clock time, not the roundtrip count.
     */
    @Test
    fun parseErrorLoop_runsPastTheLimitWithoutClosingTheSession() {
        var state = automationAt(Phase.CALLING_AI)

        repeat(CYCLES_WELL_PAST_THE_LIMIT) {
            state = AIStateMachine.transition(
                state = state.copy(phase = Phase.PARSING_AI_RESPONSE),
                event = AIEvent.ParseErrorOccurred("unparseable response"),
                limits = testLimits,
                currentTime = T0
            )
            assertEquals(Phase.RETRYING_AFTER_FORMAT_ERROR, state.phase)

            state = AIStateMachine.transition(
                state = state,
                event = AIEvent.RetryScheduled,
                limits = testLimits,
                currentTime = T0
            )
            assertEquals(Phase.CALLING_AI, state.phase)
        }

        assertEquals(CYCLES_WELL_PAST_THE_LIMIT, state.totalRoundtrips)
        assertTrue(
            "the counter runs past the limit it is measured against",
            state.totalRoundtrips > testLimits.maxAutonomousRoundtrips
        )
        assertNotEquals(Phase.CLOSED, state.phase)
        assertNotEquals(Phase.AWAITING_SESSION_CLOSURE, state.phase)
        assertEquals(null, state.endReason)
    }

    /**
     * Same shape for a failing action: ActionFailureOccurred goes to
     * RETRYING_AFTER_ACTION_FAILURE, then RetryScheduled returns to CALLING_AI. The successful
     * path (ActionsExecuted) is the one that checks the limit; the failing path bypasses it.
     */
    @Test
    fun actionFailureLoop_runsPastTheLimitWithoutClosingTheSession() {
        var state = automationAt(Phase.EXECUTING_ACTIONS)

        repeat(CYCLES_WELL_PAST_THE_LIMIT) {
            state = AIStateMachine.transition(
                state = state.copy(phase = Phase.EXECUTING_ACTIONS),
                event = AIEvent.ActionFailureOccurred(errors = listOf(commandResult(CommandStatus.FAILED))),
                limits = testLimits,
                currentTime = T0
            )
            assertEquals(Phase.RETRYING_AFTER_ACTION_FAILURE, state.phase)

            state = AIStateMachine.transition(
                state = state,
                event = AIEvent.RetryScheduled,
                limits = testLimits,
                currentTime = T0
            )
            assertEquals(Phase.CALLING_AI, state.phase)
        }

        assertEquals(CYCLES_WELL_PAST_THE_LIMIT, state.totalRoundtrips)
        assertNotEquals(Phase.CLOSED, state.phase)
        assertEquals(null, state.endReason)
    }

    /**
     * An AUTOMATION that answers without any command is sent to PREPARING_CONTINUATION to be
     * guided, and ContinuationReady brings it back to CALLING_AI. That transition increments
     * the counter too, and also without reading the limit.
     */
    @Test
    fun continuationLoop_runsPastTheLimitWithoutClosingTheSession() {
        var state = automationAt(Phase.PREPARING_CONTINUATION)

        repeat(CYCLES_WELL_PAST_THE_LIMIT) {
            state = AIStateMachine.transition(
                state = state.copy(phase = Phase.PREPARING_CONTINUATION),
                event = AIEvent.ContinuationReady,
                limits = testLimits,
                currentTime = T0
            )
            assertEquals(Phase.CALLING_AI, state.phase)
        }

        assertEquals(CYCLES_WELL_PAST_THE_LIMIT, state.totalRoundtrips)
        assertNotEquals(Phase.CLOSED, state.phase)
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
