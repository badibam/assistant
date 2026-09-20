package com.assistant.core.ai.domain

import com.assistant.core.ai.data.CommandResult
import com.assistant.core.ai.data.CommandStatus
import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures where the autonomous roundtrip limit actually stops an AUTOMATION session.
 *
 * AIStateMachine is a pure function of (state, event, limits, currentTime), so every case
 * here is built by hand and needs no mock, no database and no Android context.
 *
 * The limit exists to stop an AUTOMATION that loops. AIState.totalRoundtrips is incremented
 * by several transitions, but limits.maxAutonomousRoundtrips is read by only one of them.
 * These tests pin which loops the limit closes and which ones it lets run, so that a change
 * either way shows up as a failing test rather than as unexplained API spend.
 */
class AIStateMachineRoundtripLimitTest {

    private val limits = SessionLimits(maxAutonomousRoundtrips = 3)

    /** A running AUTOMATION session, at the phase a given loop cycles through. */
    private fun automationAt(phase: Phase, roundtrips: Int = 0) = AIState(
        sessionId = "session-under-test",
        phase = phase,
        sessionType = SessionType.AUTOMATION,
        automationId = "automation-under-test",
        totalRoundtrips = roundtrips,
        sessionCreatedAt = T0,
        lastNetworkAvailableTime = T0,
        lastEventTime = T0
    )

    private fun failedAction() = CommandResult(
        command = "tool_data.create",
        status = CommandStatus.FAILED,
        details = null,
        data = null,
        error = "rejected by the service",
        isActionCommand = true
    )

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
            limits = limits,
            currentTime = T0
        )
        assertEquals(Phase.CALLING_AI, stillRunning.phase)
        assertEquals(2, stillRunning.totalRoundtrips)

        // Reaching the limit: the session closes and says why.
        val stopped = AIStateMachine.transition(
            state = automationAt(Phase.EXECUTING_ACTIONS, roundtrips = 2),
            event = AIEvent.ActionsExecuted(results = emptyList(), allSuccess = true, keepControl = null),
            limits = limits,
            currentTime = T0
        )
        assertEquals(3, stopped.totalRoundtrips)
        assertEquals(SessionEndReason.LIMIT_REACHED, stopped.endReason)
        assertEquals(Phase.AWAITING_SESSION_CLOSURE, stopped.phase)
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
                limits = limits,
                currentTime = T0
            )
            assertEquals(Phase.RETRYING_AFTER_FORMAT_ERROR, state.phase)

            state = AIStateMachine.transition(
                state = state,
                event = AIEvent.RetryScheduled,
                limits = limits,
                currentTime = T0
            )
            assertEquals(Phase.CALLING_AI, state.phase)
        }

        assertEquals(CYCLES_WELL_PAST_THE_LIMIT, state.totalRoundtrips)
        assertTrue(
            "the counter runs past the limit it is measured against",
            state.totalRoundtrips > limits.maxAutonomousRoundtrips
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
                event = AIEvent.ActionFailureOccurred(errors = listOf(failedAction())),
                limits = limits,
                currentTime = T0
            )
            assertEquals(Phase.RETRYING_AFTER_ACTION_FAILURE, state.phase)

            state = AIStateMachine.transition(
                state = state,
                event = AIEvent.RetryScheduled,
                limits = limits,
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
                limits = limits,
                currentTime = T0
            )
            assertEquals(Phase.CALLING_AI, state.phase)
        }

        assertEquals(CYCLES_WELL_PAST_THE_LIMIT, state.totalRoundtrips)
        assertNotEquals(Phase.CLOSED, state.phase)
        assertEquals(null, state.endReason)
    }

    companion object {
        /** Any fixed instant: none of these transitions branch on the clock. */
        private const val T0 = 1_700_000_000_000L

        /** Enough cycles that a limit of 3 could not be missed by chance. */
        private const val CYCLES_WELL_PAST_THE_LIMIT = 10
    }
}
