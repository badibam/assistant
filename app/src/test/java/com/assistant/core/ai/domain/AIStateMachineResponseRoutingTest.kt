package com.assistant.core.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers AIResponseParsed, which is where the machine decides what the AI's answer means,
 * and DataQueriesExecuted, which brings a query round back to the provider.
 *
 * The routing is a priority list: the completed flag first (AUTOMATION only), then a
 * question for the user (CHAT only), then queries, then actions, then an answer that is
 * only text. Each test here pins one branch and, where the branch differs by session type,
 * both sides of it.
 */
class AIStateMachineResponseRoutingTest {

    private fun parsed(state: AIState, message: com.assistant.core.ai.data.AIMessage) =
        AIStateMachine.transition(
            state = state,
            event = AIEvent.AIResponseParsed(message),
            limits = testLimits,
            currentTime = T1
        )

    // ==================== Priority 0: the completed flag ====================

    /**
     * completed=true is claimed once and confirmed once. The first claim does not end the
     * session: it sets the flag and sends the AI a request to confirm.
     */
    @Test
    fun automationClaimingCompletion_asksForConfirmationFirst() {
        val state = parsed(automationAt(Phase.PARSING_AI_RESPONSE), aiMessage(completed = true))

        assertEquals(Phase.PREPARING_CONTINUATION, state.phase)
        assertEquals(ContinuationReason.COMPLETION_CONFIRMATION_REQUIRED, state.continuationReason)
        assertTrue(state.awaitingCompletionConfirmation)
    }

    /** The second claim, with the flag already set, is the one that ends the session. */
    @Test
    fun automationClaimingCompletionTwice_windsTheSessionDown() {
        val state = parsed(
            automationAt(Phase.PARSING_AI_RESPONSE, awaitingCompletionConfirmation = true),
            aiMessage(completed = true)
        )

        assertEquals(Phase.AWAITING_SESSION_CLOSURE, state.phase)
        assertEquals(false, state.awaitingCompletionConfirmation)
    }

    /**
     * Claiming completion while also asking for actions runs the actions first. The flag is
     * carried so that the confirmation happens once they have succeeded.
     */
    @Test
    fun automationClaimingCompletionWithActions_runsThemFirst() {
        val state = parsed(
            automationAt(Phase.PARSING_AI_RESPONSE),
            aiMessage(completed = true, actionCommands = listOf(command("tool_data.create")))
        )

        assertEquals(Phase.EXECUTING_ACTIONS, state.phase)
        assertTrue(state.awaitingCompletionConfirmation)
    }

    /**
     * Claiming completion while also asking questions of the data is contradictory. The
     * queries are dropped and the confirmation is asked for anyway.
     */
    @Test
    fun automationClaimingCompletionWithQueries_dropsTheQueries() {
        val state = parsed(
            automationAt(Phase.PARSING_AI_RESPONSE),
            aiMessage(completed = true, dataCommands = listOf(command()))
        )

        assertEquals(Phase.PREPARING_CONTINUATION, state.phase)
        assertEquals(ContinuationReason.COMPLETION_CONFIRMATION_REQUIRED, state.continuationReason)
    }

    /**
     * The flag is an AUTOMATION affair. A CHAT that sets it is routed on what else the
     * answer holds -- here nothing, so back to waiting for the user.
     */
    @Test
    fun chatClaimingCompletion_isRoutedLikeAnyOtherAnswer() {
        val state = parsed(chatAt(Phase.PARSING_AI_RESPONSE), aiMessage(completed = true))

        assertEquals(Phase.IDLE, state.phase)
        assertEquals(false, state.awaitingCompletionConfirmation)
    }

    // ==================== Priority 1: a question for the user ====================

    /** A CHAT can be asked something, and stops until it is answered. */
    @Test
    fun chatAskedAQuestion_waitsForTheAnswer() {
        val state = parsed(chatAt(Phase.PARSING_AI_RESPONSE), aiMessage(communicationModule = question()))

        assertEquals(Phase.WAITING_COMMUNICATION_RESPONSE, state.phase)
    }

    /**
     * An AUTOMATION has nobody to ask. The question is ignored and the answer is routed on
     * what else it holds -- here nothing, so the AI is guided to produce commands.
     */
    @Test
    fun automationAskingAQuestion_isIgnoredAndGuided() {
        val state = parsed(
            automationAt(Phase.PARSING_AI_RESPONSE),
            aiMessage(communicationModule = question())
        )

        assertEquals(Phase.PREPARING_CONTINUATION, state.phase)
        assertEquals(ContinuationReason.AUTOMATION_NO_COMMANDS, state.continuationReason)
    }

    // ==================== Priority 2: queries ====================

    /** Queries run the same way for both session types: nothing is written, so nothing to approve. */
    @Test
    fun queries_runWithoutApprovalForBothSessionTypes() {
        for (state in listOf(chatAt(Phase.PARSING_AI_RESPONSE), automationAt(Phase.PARSING_AI_RESPONSE))) {
            val after = parsed(state, aiMessage(dataCommands = listOf(command())))

            assertEquals("for ${state.sessionType}", Phase.EXECUTING_DATA_QUERIES, after.phase)
        }
    }

    /** Asking a question of the data is not finishing: a pending completion claim is dropped. */
    @Test
    fun queriesAfterAClaimedCompletion_withdrawTheClaim() {
        val state = parsed(
            automationAt(Phase.PARSING_AI_RESPONSE, awaitingCompletionConfirmation = true),
            aiMessage(dataCommands = listOf(command()))
        )

        assertEquals(Phase.EXECUTING_DATA_QUERIES, state.phase)
        assertEquals(false, state.awaitingCompletionConfirmation)
    }

    // ==================== Priority 3: actions ====================

    /**
     * Actions write. In a CHAT they go past the user first -- WAITING_VALIDATION is where
     * ValidationResolver then decides whether approval is actually required.
     */
    @Test
    fun chatActions_goThroughValidation() {
        val state = parsed(
            chatAt(Phase.PARSING_AI_RESPONSE),
            aiMessage(actionCommands = listOf(command("tool_data.create")))
        )

        assertEquals(Phase.WAITING_VALIDATION, state.phase)
    }

    /** An AUTOMATION has nobody to ask, so its actions run straight away. */
    @Test
    fun automationActions_runWithoutValidation() {
        val state = parsed(
            automationAt(Phase.PARSING_AI_RESPONSE),
            aiMessage(actionCommands = listOf(command("tool_data.create")))
        )

        assertEquals(Phase.EXECUTING_ACTIONS, state.phase)
    }

    // ==================== Nothing but text ====================

    /** A CHAT answered in words has said its piece: control returns to the user. */
    @Test
    fun chatAnsweringWithTextOnly_returnsControl() {
        val state = parsed(chatAt(Phase.PARSING_AI_RESPONSE), aiMessage())

        assertEquals(Phase.IDLE, state.phase)
    }

    /**
     * An AUTOMATION answering in words has done nothing. It is sent back with guidance
     * saying so, rather than being left to stall.
     */
    @Test
    fun automationAnsweringWithTextOnly_isGuidedBack() {
        val state = parsed(automationAt(Phase.PARSING_AI_RESPONSE), aiMessage())

        assertEquals(Phase.PREPARING_CONTINUATION, state.phase)
        assertEquals(ContinuationReason.AUTOMATION_NO_COMMANDS, state.continuationReason)
    }

    /** Parsing an answer is not a roundtrip: the counter moves when a call goes out. */
    @Test
    fun routingAnAnswer_doesNotCountARoundtrip() {
        val state = parsed(automationAt(Phase.PARSING_AI_RESPONSE, roundtrips = 2), aiMessage())

        assertEquals(2, state.totalRoundtrips)
    }

    // ==================== DataQueriesExecuted ====================

    /** The answers to the queries go back to the provider, and that call is a roundtrip. */
    @Test
    fun dataQueriesExecuted_callsBackAndCountsARoundtrip() {
        val state = AIStateMachine.transition(
            state = automationAt(Phase.EXECUTING_DATA_QUERIES, roundtrips = 1),
            event = AIEvent.DataQueriesExecuted(results = listOf(commandResult())),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.CALLING_AI, state.phase)
        assertEquals(2, state.totalRoundtrips)
    }

    /**
     * That call answers to the limit like any other. A session that only ever queries used
     * to walk past it, this transition incrementing the counter without reading it.
     */
    @Test
    fun dataQueriesExecuted_isStoppedOnceTheLimitIsReached() {
        val state = AIStateMachine.transition(
            state = automationAt(Phase.EXECUTING_DATA_QUERIES, roundtrips = testLimits.maxAutonomousRoundtrips),
            event = AIEvent.DataQueriesExecuted(results = emptyList()),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(com.assistant.core.ai.data.SessionEndReason.LIMIT_REACHED, state.endReason)
        assertEquals(Phase.AWAITING_SESSION_CLOSURE, state.phase)
    }
}
