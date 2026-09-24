package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the events that open, advance and close a session:
 * SessionActivationRequested, UserMessageSent, EnrichmentsExecuted, AIResponseReceived,
 * SessionCompleted, SystemErrorOccurred, SchedulerHeartbeat.
 */
class AIStateMachineLifecycleTest {

    // ==================== SessionActivationRequested ====================

    /**
     * A CHAT session is activated but not started: it holds at IDLE until the user says
     * something. The slot is taken all the same, which is what sessionId records.
     */
    @Test
    fun activatingChat_takesTheSlotButStaysIdle() {
        val state = AIStateMachine.transition(
            state = AIState.idle(),
            event = AIEvent.SessionActivationRequested("new-chat", SessionType.CHAT),
            limits = testLimits,
            currentTime = T0
        )

        assertEquals(Phase.IDLE, state.phase)
        assertEquals("new-chat", state.sessionId)
        assertEquals(SessionType.CHAT, state.sessionType)
        assertEquals(T0, state.sessionCreatedAt)
        // IDLE with a session id is a taken slot; only IDLE with none is free.
        assertEquals(false, state.isSlotAvailable())
    }

    /** An AUTOMATION has nobody to wait for, so activation starts the work at once. */
    @Test
    fun activatingAutomation_startsExecutingImmediately() {
        val state = AIStateMachine.transition(
            state = AIState.idle(),
            event = AIEvent.SessionActivationRequested("new-automation", SessionType.AUTOMATION),
            limits = testLimits,
            currentTime = T0
        )

        assertEquals(Phase.EXECUTING_ENRICHMENTS, state.phase)
        assertEquals(SessionType.AUTOMATION, state.sessionType)
    }

    /**
     * Only one session runs at a time. Asking to activate another while one is busy leaves
     * the state untouched -- the machine does not queue or evict, the processor does.
     */
    @Test
    fun activatingWhileBusy_leavesTheStateUntouched() {
        val busy = automationAt(Phase.CALLING_AI)

        val state = AIStateMachine.transition(
            state = busy,
            event = AIEvent.SessionActivationRequested("intruder", SessionType.CHAT),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(busy, state)
    }

    /**
     * A CHAT that has been activated but not yet spoken to sits at IDLE with its session
     * id. That is a taken slot, not a free one: another activation leaves it in place.
     */
    @Test
    fun activatingOverAnIdleChat_leavesTheSessionInPlace() {
        val waitingChat = chatAt(Phase.IDLE)

        val state = AIStateMachine.transition(
            state = waitingChat,
            event = AIEvent.SessionActivationRequested("second-chat", SessionType.CHAT),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(waitingChat, state)
    }

    // ==================== The ordinary way forward ====================

    /** The user speaks: enrichments run before anything is sent to the provider. */
    @Test
    fun userMessageSent_movesToEnrichments() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.IDLE),
            event = AIEvent.UserMessageSent,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.EXECUTING_ENRICHMENTS, state.phase)
        // A user message is a user interaction: it postpones the inactivity timeout.
        assertEquals(T1, state.lastUserInteractionTime)
    }

    /** Enrichments done, the prompt goes out. */
    @Test
    fun enrichmentsExecuted_movesToCallingAI() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.EXECUTING_ENRICHMENTS),
            event = AIEvent.EnrichmentsExecuted(results = emptyList()),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.CALLING_AI, state.phase)
    }

    /** An answer came back; it still has to be parsed before anything is decided. */
    @Test
    fun aiResponseReceived_movesToParsing() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.CALLING_AI),
            event = AIEvent.AIResponseReceived("{...}"),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.PARSING_AI_RESPONSE, state.phase)
        // Waiting on the provider is not the user's doing: their clock does not move.
        assertEquals(T0, state.lastUserInteractionTime)
    }

    // ==================== Closing ====================

    /** Closing is unconditional: it applies from any phase, and records why. */
    @Test
    fun sessionCompleted_closesFromAnyPhase() {
        val phases = listOf(
            Phase.CALLING_AI,
            Phase.WAITING_VALIDATION,
            Phase.EXECUTING_ACTIONS,
            Phase.RETRYING_AFTER_FORMAT_ERROR,
            Phase.WAITING_NETWORK_RETRY
        )

        for (phase in phases) {
            val state = AIStateMachine.transition(
                state = automationAt(phase),
                event = AIEvent.SessionCompleted(SessionEndReason.CANCELLED),
                limits = testLimits,
                currentTime = T1
            )

            assertEquals("closing from $phase", Phase.CLOSED, state.phase)
            assertEquals("closing from $phase", SessionEndReason.CANCELLED, state.endReason)
        }
    }

    /** An unexpected exception ends the session, and says it was an error. */
    @Test
    fun systemError_closesWithErrorReason() {
        val state = AIStateMachine.transition(
            state = automationAt(Phase.EXECUTING_ACTIONS),
            event = AIEvent.SystemErrorOccurred("unexpected"),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.CLOSED, state.phase)
        assertEquals(SessionEndReason.ERROR, state.endReason)
    }

    // ==================== Heartbeat ====================

    /**
     * The heartbeat decides nothing here. It only marks that the machine was looked at,
     * which matters because lastEventTime is what the inactivity timeout measures from.
     */
    @Test
    fun heartbeat_onlyMovesTheClock() {
        val before = chatAt(Phase.WAITING_VALIDATION, waitingContext = someWaitingContext())

        val after = AIStateMachine.transition(
            state = before,
            event = AIEvent.SchedulerHeartbeat,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(before.copy(lastEventTime = T1), after)
    }

    /** A fresh state is a free slot, and carries no session and no end reason. */
    @Test
    fun idleState_isAFreeSlot() {
        val idle = AIState.idle()

        assertEquals(true, idle.isSlotAvailable())
        assertNull(idle.sessionId)
        assertNull(idle.sessionType)
        assertNull(idle.endReason)
        assertEquals(0, idle.totalRoundtrips)
    }
}
