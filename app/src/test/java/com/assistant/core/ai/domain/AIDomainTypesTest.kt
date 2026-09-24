package com.assistant.core.ai.domain

import com.assistant.core.ai.data.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONException
import org.json.JSONObject
import org.junit.Test

/**
 * Covers the two small pieces the state machine leans on and that its own cases only
 * touched in passing: how a phase classifies itself, and which limits a session type gets.
 *
 * Both are tables rather than logic, and a table is exactly what gets extended without
 * anyone revisiting the places that read it. A phase added tomorrow falls into "neither
 * working nor waiting" by default, which is the answer that makes the watchdog close a
 * session; these cases make that choice visible instead of silent.
 */
class AIDomainTypesTest {

    // ==================== What a phase says about itself ====================

    /** Every phase, and what each of the three questions answers for it. */
    @Test
    fun everyPhaseAnswersTheThreeQuestions() {
        val working = setOf(
            Phase.CALLING_AI,
            Phase.EXECUTING_ENRICHMENTS,
            Phase.EXECUTING_DATA_QUERIES,
            Phase.EXECUTING_ACTIONS,
            Phase.PARSING_AI_RESPONSE,
            Phase.PREPARING_CONTINUATION
        )
        val waitingOnTheUser = setOf(Phase.WAITING_VALIDATION, Phase.WAITING_COMMUNICATION_RESPONSE)
        val waitingOnTheNetwork = setOf(Phase.WAITING_NETWORK_RETRY)

        for (phase in Phase.values()) {
            assertEquals("isActiveProcessing at $phase", phase in working, phase.isActiveProcessing())
            assertEquals("isWaitingForUser at $phase", phase in waitingOnTheUser, phase.isWaitingForUser())
            assertEquals("isNetworkRetry at $phase", phase in waitingOnTheNetwork, phase.isNetworkRetry())
        }
    }

    /** No phase is two of those at once, so the inactivity rules cannot disagree. */
    @Test
    fun noPhaseIsTwoThingsAtOnce() {
        for (phase in Phase.values()) {
            val answers = listOf(
                phase.isActiveProcessing(),
                phase.isWaitingForUser(),
                phase.isNetworkRetry()
            ).count { it }

            assertTrue("$phase answers yes $answers times", answers <= 1)
        }
    }

    /**
     * The phases that are none of the three are the ones whose inactivity counts from the
     * last event, and so the ones the watchdog can close. Listed here because that is a
     * consequence of saying nothing, and a phase added without a thought lands in it.
     */
    @Test
    fun thePhasesThatCanTimeOutAreTheOnesLeftOver() {
        val leftOver = Phase.values().filterNot {
            it.isActiveProcessing() || it.isWaitingForUser() || it.isNetworkRetry()
        }

        assertEquals(
            listOf(
                Phase.IDLE,
                Phase.RETRYING_AFTER_FORMAT_ERROR,
                Phase.RETRYING_AFTER_ACTION_FAILURE,
                Phase.INTERRUPTED,
                Phase.AWAITING_SESSION_CLOSURE,
                Phase.CLOSED
            ),
            leftOver
        )
    }

    /**
     * A retry phase is not counted as working, so an automation looping on a malformed
     * reply is ticking towards the inactivity timeout between attempts rather than being
     * held open. That is the one thing stopping the loops the roundtrip counter misses.
     */
    @Test
    fun aRetryPhaseIsNotCountedAsWorking() {
        assertFalse(Phase.RETRYING_AFTER_FORMAT_ERROR.isActiveProcessing())
        assertFalse(Phase.RETRYING_AFTER_ACTION_FAILURE.isActiveProcessing())
    }

    // ==================== Which limits a session type gets ====================

    /**
     * A CHAT is limited too: ten calls the AI makes on its own since the user last stepped in.
     * Each call is paid for, and nothing else stops an AI that keeps calling itself.
     */
    @Test
    fun aChatIsLimitedToTenCallsInARow() {
        val limits = AILimitsConfig.default().getLimitsForSessionType(SessionType.CHAT)

        assertEquals(10, limits.maxAutonomousRoundtrips)
    }

    /** An automation is, since nobody is watching it. */
    @Test
    fun anAutomationIsLimited() {
        val limits = AILimitsConfig.default().getLimitsForSessionType(SessionType.AUTOMATION)

        assertEquals(20, limits.maxAutonomousRoundtrips)
    }

    /**
     * A SEED is a template that never runs, and it is given the automation's limit rather
     * than none: if one were ever executed, it would be stopped like the thing it is a
     * template for.
     */
    @Test
    fun aSeedFallsBackToTheAutomationLimit() {
        val config = AILimitsConfig(chatMaxAutonomousRoundtrips = 5, automationMaxAutonomousRoundtrips = 7)

        assertEquals(7, config.getLimitsForSessionType(SessionType.SEED).maxAutonomousRoundtrips)
    }

    /** The configured values are what comes back, not the defaults. */
    @Test
    fun theConfiguredValuesAreTheOnesUsed() {
        val config = AILimitsConfig(chatMaxAutonomousRoundtrips = 5, automationMaxAutonomousRoundtrips = 7)

        assertEquals(5, config.getLimitsForSessionType(SessionType.CHAT).maxAutonomousRoundtrips)
        assertEquals(7, config.getLimitsForSessionType(SessionType.AUTOMATION).maxAutonomousRoundtrips)
    }

    // ==================== How the limits are stored ====================

    /** What a fresh install writes is what the app reads back. */
    @Test
    fun theDefaultsReadBackAsWritten() {
        val stored = JSONObject(AILimitsConfig.default().toSettingsJson())

        assertEquals(AILimitsConfig.default(), AILimitsConfig.fromSettingsJson(stored))
    }

    /** A missing limit is an error, not "no limit": it used to be read as unlimited. */
    @Test(expected = JSONException::class)
    fun aMissingLimitIsRefused() {
        AILimitsConfig.fromSettingsJson(JSONObject().put(AILimitsConfig.KEY_AUTOMATION, 20))
    }
}
