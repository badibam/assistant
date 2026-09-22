package com.assistant.core.ai.scheduling

import com.assistant.core.ai.data.ExecutionTrigger
import com.assistant.core.ai.data.SessionEndReason
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.domain.AIState
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.domain.T0
import com.assistant.core.ai.domain.automationAt
import com.assistant.core.ai.domain.chatAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers who gets the one execution slot, and when its occupant has to give it up.
 *
 * Every case here fixes the clock rather than waiting on it: putting a session six minutes
 * into the past is what makes an eviction or a timeout observable at all, and it is the
 * reason these decisions were worth pulling out of AISessionScheduler, whose constructor
 * opens a database.
 */
class SessionSlotPolicyTest {

    /** A session that last did anything this long ago. */
    private fun chatIdleFor(millis: Long) = chatAt(Phase.IDLE).copy(lastEventTime = T0 - millis)

    private fun request(
        sessionType: SessionType,
        trigger: ExecutionTrigger,
        currentState: AIState,
        currentTime: Long = T0
    ) = SessionSlotPolicy.requestSession(
        sessionId = "asking-session",
        sessionType = sessionType,
        trigger = trigger,
        currentState = currentState,
        currentTime = currentTime
    )

    // ==================== A free slot ====================

    /** Nothing is running: whoever asks, starts. */
    @Test
    fun anyRequest_startsWhenTheSlotIsFree() {
        for (type in listOf(SessionType.CHAT, SessionType.AUTOMATION)) {
            val result = request(type, ExecutionTrigger.MANUAL, AIState.idle())

            assertEquals(
                "for $type",
                ActivationResult.ActivateImmediate("asking-session"),
                result
            )
        }
    }

    // ==================== A CHAT asks ====================

    /** The user opening a new conversation replaces the old one, whatever it was doing. */
    @Test
    fun chat_replacesAnotherChatAtOnce() {
        val result = request(SessionType.CHAT, ExecutionTrigger.MANUAL, chatAt(Phase.CALLING_AI))

        val evict = result as ActivationResult.EvictAndActivate
        assertEquals("chat-session", evict.sessionToEvict)
        assertEquals("asking-session", evict.sessionToActivate)
        assertEquals(SessionEndReason.CANCELLED, evict.evictionReason)
    }

    /**
     * A CHAT does not cut into a running automation. It queues, and the interface asks the
     * user whether to interrupt -- that question is not settled here.
     */
    @Test
    fun chat_queuesBehindARunningAutomation() {
        val result = request(SessionType.CHAT, ExecutionTrigger.MANUAL, automationAt(Phase.CALLING_AI))

        assertEquals(ActivationResult.Enqueue("asking-session", priority = 1), result)
    }

    // ==================== An automation asks, a CHAT holds the slot ====================

    /** A conversation nobody has touched for over five minutes gives way, and is suspended. */
    @Test
    fun automation_evictsALongIdleChat() {
        for (trigger in listOf(ExecutionTrigger.MANUAL, ExecutionTrigger.SCHEDULED)) {
            val result = request(
                SessionType.AUTOMATION,
                trigger,
                chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT + 1_000L)
            )

            val evict = result as ActivationResult.EvictAndActivate
            assertEquals("for $trigger", SessionEndReason.SUSPENDED, evict.evictionReason)
        }
    }

    /**
     * A conversation still in use does not. What happens then depends on who asked: someone
     * pressing the button waits in the queue, where a scheduled run simply gives up and
     * comes back at the next tick.
     */
    @Test
    fun automation_waitsOrGivesUpOnAChatStillInUse() {
        val busyChat = chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT - 1_000L)

        assertEquals(
            ActivationResult.Enqueue("asking-session", priority = 2),
            request(SessionType.AUTOMATION, ExecutionTrigger.MANUAL, busyChat)
        )
        assertTrue(
            request(SessionType.AUTOMATION, ExecutionTrigger.SCHEDULED, busyChat)
                is ActivationResult.Skip
        )
    }

    /**
     * A CHAT that is mid-call reports no inactivity at all, however long the call has been
     * out, so it cannot be evicted. Waiting on the provider is not the user being away.
     */
    @Test
    fun automation_cannotEvictAChatThatIsMidCall() {
        val callingForAges = chatAt(Phase.CALLING_AI)
            .copy(lastEventTime = T0 - 10 * SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT)

        val result = request(SessionType.AUTOMATION, ExecutionTrigger.MANUAL, callingForAges)

        assertEquals(ActivationResult.Enqueue("asking-session", priority = 2), result)
    }

    // ==================== An automation asks, an automation holds the slot ====================

    /**
     * One automation never evicts another, however long it has been running: the watchdog
     * is what ends it. The asker queues or gives up depending on where it came from.
     */
    @Test
    fun automation_neverEvictsAnotherAutomation() {
        val running = automationAt(Phase.EXECUTING_ACTIONS)

        assertEquals(
            ActivationResult.Enqueue("asking-session", priority = 2),
            request(SessionType.AUTOMATION, ExecutionTrigger.MANUAL, running)
        )
        assertTrue(
            request(SessionType.AUTOMATION, ExecutionTrigger.SCHEDULED, running)
                is ActivationResult.Skip
        )
    }

    // ==================== When the occupant's time is up ====================

    /** Nothing is running, so nothing can run out of time. */
    @Test
    fun noSession_neverTimesOut() {
        assertFalse(SessionSlotPolicy.shouldTimeout(AIState.idle(), hasWaitingAutomations = true, currentTime = T0))
    }

    /**
     * A conversation left open costs nothing while nothing else wants the slot, so it is
     * only timed out when an automation is actually waiting for it.
     */
    @Test
    fun chat_onlyTimesOutWhenSomethingIsWaiting() {
        val longIdle = chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT + 1_000L)

        assertFalse(SessionSlotPolicy.shouldTimeout(longIdle, hasWaitingAutomations = false, currentTime = T0))
        assertTrue(SessionSlotPolicy.shouldTimeout(longIdle, hasWaitingAutomations = true, currentTime = T0))
    }

    /** Under five minutes, a waiting automation is not reason enough. */
    @Test
    fun chat_survivesAShortSilence() {
        val shortIdle = chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT - 1_000L)

        assertFalse(SessionSlotPolicy.shouldTimeout(shortIdle, hasWaitingAutomations = true, currentTime = T0))
    }

    /** An automation that has been going for over ten minutes is stopped. */
    @Test
    fun automation_timesOutOnTheGlobalBudget() {
        val started = automationAt(Phase.CALLING_AI)
        val tenMinutesLater = started.sessionCreatedAt + SessionSlotPolicy.AUTOMATION_GLOBAL_TIMEOUT + 1_000L

        assertTrue(
            SessionSlotPolicy.shouldTimeout(started, hasWaitingAutomations = false, currentTime = tenMinutesLater)
        )
    }

    /** An automation that has stopped doing anything for two minutes is stopped too. */
    @Test
    fun automation_timesOutOnGoingQuiet() {
        // PREPARING_CONTINUATION would report no inactivity; INTERRUPTED measures from the last event.
        val quiet = automationAt(Phase.INTERRUPTED)
        val twoMinutesLater = quiet.lastEventTime + SessionSlotPolicy.AUTO_INACTIVITY_TIMEOUT + 1_000L

        assertTrue(
            SessionSlotPolicy.shouldTimeout(quiet, hasWaitingAutomations = false, currentTime = twoMinutesLater)
        )
    }

    /** An automation getting on with the work is left alone until the global budget runs out. */
    @Test
    fun automation_isLeftAloneWhileWorking() {
        val working = automationAt(Phase.EXECUTING_ACTIONS)
        val fiveMinutesLater = working.sessionCreatedAt + 5 * 60_000L

        assertFalse(
            SessionSlotPolicy.shouldTimeout(working, hasWaitingAutomations = false, currentTime = fiveMinutesLater)
        )
    }

    /** An automation waiting out a network outage is never timed out, whatever the clock says. */
    @Test
    fun automation_isNeverTimedOutWhileWaitingOnTheNetwork() {
        val offline = automationAt(Phase.WAITING_NETWORK_RETRY)
        val hoursLater = offline.sessionCreatedAt + 100 * SessionSlotPolicy.AUTOMATION_GLOBAL_TIMEOUT

        assertFalse(
            SessionSlotPolicy.shouldTimeout(offline, hasWaitingAutomations = false, currentTime = hoursLater)
        )
    }

    /**
     * An outage that is over is not charged to the automation.
     *
     * The ten-minute budget is working time, not wall clock: an automation that spent eight of
     * its first nine minutes offline comes back with its budget nearly intact. Reading the
     * downtime off the current phase used to make the subtraction unreachable, since the only
     * phase carrying one is the phase shouldTimeout returns on.
     */
    @Test
    fun automation_isNotChargedForAnOutageItHasComeBackFrom() {
        val start = T0
        val outage = 8 * 60_000L
        val justPastTheWallClockBudget = start + SessionSlotPolicy.AUTOMATION_GLOBAL_TIMEOUT + 1_000L

        val resumed = automationAt(Phase.CALLING_AI).copy(
            sessionCreatedAt = start,
            networkDownTime = outage,
            lastEventTime = justPastTheWallClockBudget
        )

        assertFalse(
            SessionSlotPolicy.shouldTimeout(resumed, hasWaitingAutomations = false, currentTime = justPastTheWallClockBudget)
        )
    }

    /** Once it has actually worked for ten minutes, outages aside, it is stopped. */
    @Test
    fun automation_isStoppedAfterTenMinutesOfWorkingTime() {
        val start = T0
        val outage = 8 * 60_000L
        val past = start + outage + SessionSlotPolicy.AUTOMATION_GLOBAL_TIMEOUT + 1_000L

        val resumed = automationAt(Phase.CALLING_AI).copy(
            sessionCreatedAt = start,
            networkDownTime = outage,
            lastEventTime = past
        )

        assertTrue(
            SessionSlotPolicy.shouldTimeout(resumed, hasWaitingAutomations = false, currentTime = past)
        )
    }

    /** Several outages add up, rather than only the last one counting. */
    @Test
    fun automation_addsUpTheOutagesItHasSatOut() {
        val start = T0
        val outages = 3 * 60_000L + 4 * 60_000L
        val currentTime = start + outages + 9 * 60_000L

        val resumed = automationAt(Phase.CALLING_AI).copy(
            sessionCreatedAt = start,
            networkDownTime = outages,
            lastEventTime = currentTime
        )

        assertFalse(
            SessionSlotPolicy.shouldTimeout(resumed, hasWaitingAutomations = false, currentTime = currentTime)
        )
    }
}
