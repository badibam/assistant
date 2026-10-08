package app.treelune.core.ai.domain

import app.treelune.core.ai.data.SessionEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the events the user raises: ValidationNotRequired, ValidationReceived,
 * DataConfirmationRequested, DataConfirmationReceived, CommunicationResponseReceived,
 * CommunicationCancelled, AIRoundInterrupted, InterruptionRecorded.
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

    // ==================== Data above the size threshold ====================

    /** Data went over the CHAT threshold: the session waits for the user instead of calling the AI. */
    @Test
    fun dataConfirmationRequested_waitsForTheUser() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.EXECUTING_DATA_QUERIES, roundtrips = 2),
            event = AIEvent.DataConfirmationRequested,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.WAITING_DATA_CONFIRMATION, state.phase)
        assertEquals(2, state.totalRoundtrips)
    }

    /**
     * Sent or refused, the AI is called -- with the data or with the refusal -- and, the user
     * having acted, the count of calls starts over with this one. Even at the limit, the call
     * goes out: it is the user's answer that is being sent.
     */
    @Test
    fun dataConfirmationAnswered_callsTheAIEitherWay() {
        val data = WaitingContext.DataConfirmation(messageId = "m", dataChars = 5_000, maxDataChars = 1_000)
        for (approved in listOf(true, false)) {
            val state = AIStateMachine.transition(
                state = chatAt(Phase.WAITING_DATA_CONFIRMATION, roundtrips = testLimits.maxAutonomousRoundtrips, waitingContext = data),
                event = AIEvent.DataConfirmationReceived(approved),
                limits = testLimits,
                currentTime = T1
            )

            assertEquals(Phase.CALLING_AI, state.phase)
            assertNull(state.waitingContext)
            assertEquals(1, state.totalRoundtrips)
            assertEquals(T1, state.lastUserInteractionTime)
        }
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

    /**
     * Answered: the answer goes back to the provider. The user acted, so the count starts
     * over, and that call is its first roundtrip.
     */
    @Test
    fun communicationAnswered_sendsTheAnswerBack() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.WAITING_COMMUNICATION_RESPONSE, roundtrips = 1, waitingContext = someWaitingContext()),
            event = AIEvent.CommunicationResponseReceived("option a"),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.CALLING_AI, state.phase)
        assertEquals(1, state.totalRoundtrips)
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
        assertEquals(0, state.totalRoundtrips)
        assertNull(state.waitingContext)
    }

    /** Answered by a message instead: the message goes as any other, and the question is dropped. */
    @Test
    fun messageSentInsteadOfAnswer_dropsTheQuestion() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.WAITING_COMMUNICATION_RESPONSE, roundtrips = 1, waitingContext = someWaitingContext()),
            event = AIEvent.UserMessageSent,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.EXECUTING_ENRICHMENTS, state.phase)
        assertEquals(0, state.totalRoundtrips)
        assertNull(state.waitingContext)
        assertEquals(T1, state.lastUserInteractionTime)
    }

    // ==================== Interruption ====================

    /**
     * Interrupting passes through INTERRUPTED, where the interruption is recorded. The call
     * in flight is cancelled by AIEventProcessor, not by the transition.
     */
    @Test
    fun interrupting_passesThroughInterrupted() {
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

    /** Once the interruption is recorded, the session is ready again. */
    @Test
    fun recordingTheInterruption_returnsToIdle() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.INTERRUPTED),
            event = AIEvent.InterruptionRecorded,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.IDLE, state.phase)
        assertNull(state.endReason)
    }
}
