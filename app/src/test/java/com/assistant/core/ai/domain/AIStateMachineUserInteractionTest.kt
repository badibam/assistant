package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the events the user raises: ValidationNotRequired, ValidationReceived,
 * CommunicationResponseReceived, CommunicationCancelled, AIRoundInterrupted,
 * AIResponseIgnored.
 *
 * Two things recur and are checked throughout. A waiting phase leaves a waitingContext on
 * the state, and whatever ends the wait has to clear it, or the interface keeps showing a
 * question nobody is being asked any more. And anything the user does is a user
 * interaction, which postpones the inactivity timeout -- lastUserInteractionTime is what
 * that timeout measures from.
 */
class AIStateMachineUserInteractionTest {

    // ==================== Validation ====================

    /** No approval needed: the actions run, and the wait that was set up is dropped. */
    @Test
    fun validationNotRequired_runsTheActions() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.WAITING_VALIDATION, waitingContext = someWaitingContext()),
            event = AIEvent.ValidationNotRequired,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.EXECUTING_ACTIONS, state.phase)
        assertNull(state.waitingContext)
    }

    /** Approved: the actions run. */
    @Test
    fun validationApproved_runsTheActions() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.WAITING_VALIDATION, waitingContext = someWaitingContext()),
            event = AIEvent.ValidationReceived(approved = true),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.EXECUTING_ACTIONS, state.phase)
        assertNull(state.waitingContext)
        assertEquals(T1, state.lastUserInteractionTime)
    }

    /**
     * Refused: nothing runs and control returns to the user. The session stays open -- a
     * refusal is an answer, not an end.
     */
    @Test
    fun validationRefused_returnsControlWithoutClosing() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.WAITING_VALIDATION, waitingContext = someWaitingContext()),
            event = AIEvent.ValidationReceived(approved = false),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.IDLE, state.phase)
        assertNull(state.waitingContext)
        assertNull(state.endReason)
        assertEquals(T1, state.lastUserInteractionTime)
    }

    // ==================== A question put to the user ====================

    /** Answered: the answer goes back to the provider, and that call is a roundtrip. */
    @Test
    fun communicationAnswered_sendsTheAnswerBack() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.WAITING_COMMUNICATION_RESPONSE, roundtrips = 1, waitingContext = someWaitingContext()),
            event = AIEvent.CommunicationResponseReceived("option a"),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.CALLING_AI, state.phase)
        assertEquals(2, state.totalRoundtrips)
        assertNull(state.waitingContext)
        assertEquals(T1, state.lastUserInteractionTime)
    }

    /** Dismissed: the question is dropped and control returns, without calling anyone. */
    @Test
    fun communicationCancelled_returnsControlWithoutCalling() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.WAITING_COMMUNICATION_RESPONSE, roundtrips = 1, waitingContext = someWaitingContext()),
            event = AIEvent.CommunicationCancelled,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.IDLE, state.phase)
        assertEquals(1, state.totalRoundtrips)
        assertNull(state.waitingContext)
    }

    // ==================== Interruption ====================

    /**
     * Interrupting does not cancel the call that is already out. The session parks at
     * INTERRUPTED so that the answer, when it lands, is recognised as unwanted.
     */
    @Test
    fun interrupting_parksTheSessionToDiscardTheAnswer() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.CALLING_AI, waitingContext = someWaitingContext()),
            event = AIEvent.AIRoundInterrupted,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.INTERRUPTED, state.phase)
        assertNull(state.waitingContext)
        assertEquals(T1, state.lastUserInteractionTime)
    }

    /** Once the unwanted answer has been discarded, the session is ready again. */
    @Test
    fun discardingTheAnswer_returnsToIdle() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.INTERRUPTED),
            event = AIEvent.AIResponseIgnored,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.IDLE, state.phase)
        assertNull(state.endReason)
    }
}
