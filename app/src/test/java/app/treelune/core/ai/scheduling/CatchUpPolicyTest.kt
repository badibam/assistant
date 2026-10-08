package app.treelune.core.ai.scheduling

import app.treelune.core.utils.ScheduleConfig
import app.treelune.core.utils.SchedulePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers what an automation catches up on after the phone was off or the app closed.
 *
 * This is the logic the device checks have been waiting on: it decides whether a run that should
 * have happened yesterday still happens, and which one when several were missed. Every value it
 * works from is an argument, so none of it needs a device, a database or a clock.
 */
class CatchUpPolicyTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    private fun dailyAt9() = ScheduleConfig(pattern = SchedulePattern.DailyMultiple(listOf("09:00")))

    private val minute = 60_000L

    // ==================== Where the search starts ====================

    /** With a completed run behind it, the search resumes from that run. */
    @Test
    fun theSearchResumesFromTheLastCompletedRun() {
        val lastRun = at(2025, 3, 10, 9, 0)

        val start = CatchUpPolicy.searchStart(
            lastExecutionTime = lastRun,
            automationUpdatedAt = at(2025, 3, 1, 12, 0),
            catchUpWindow = null,
            now = at(2025, 3, 15, 10, 0)
        )

        assertEquals(lastRun, start)
    }

    /**
     * An automation edited after its last run resumes from the edit, not from the run: a schedule
     * changed on Thursday must not be walked back through Monday's occurrences.
     */
    @Test
    fun anEditAfterTheLastRunMovesTheSearchForward() {
        val edited = at(2025, 3, 13, 18, 0)

        val start = CatchUpPolicy.searchStart(
            lastExecutionTime = at(2025, 3, 10, 9, 0),
            automationUpdatedAt = edited,
            catchUpWindow = null,
            now = at(2025, 3, 15, 10, 0)
        )

        assertEquals(edited, start)
    }

    /** With nothing at all to go on, there is nothing to catch up: the search starts now. */
    @Test
    fun withNothingToGoOnTheSearchStartsNow() {
        val now = at(2025, 3, 15, 10, 0)

        val start = CatchUpPolicy.searchStart(
            lastExecutionTime = 0L,
            automationUpdatedAt = 0L,
            catchUpWindow = null,
            now = now
        )

        assertEquals(now, start)
    }

    // ==================== The catch-up window ====================

    /**
     * A window pulls the search forward, so occurrences older than it are never visited. This is
     * what keeps 47 days of missed runs from becoming 47 sessions.
     */
    @Test
    fun aWindowPullsTheSearchForward() {
        val now = at(2025, 3, 15, 10, 0)
        val longAgo = at(2025, 1, 1, 9, 0)

        val start = CatchUpPolicy.searchStart(
            lastExecutionTime = longAgo,
            automationUpdatedAt = 0L,
            catchUpWindow = 60 * minute,
            now = now
        )

        assertEquals(now - 60 * minute, start)
        assertTrue(
            CatchUpPolicy.windowSkippedOccurrences(longAgo, 0L, 60 * minute, now)
        )
    }

    /** A window wider than the gap changes nothing, and skips nothing. */
    @Test
    fun aWindowWiderThanTheGapChangesNothing() {
        val now = at(2025, 3, 15, 10, 0)
        val lastRun = now - 30 * minute

        val start = CatchUpPolicy.searchStart(
            lastExecutionTime = lastRun,
            automationUpdatedAt = 0L,
            catchUpWindow = 120 * minute,
            now = now
        )

        assertEquals(lastRun, start)
        assertFalse(
            CatchUpPolicy.windowSkippedOccurrences(lastRun, 0L, 120 * minute, now)
        )
    }

    /** No window means no limit on how late an occurrence may still run. */
    @Test
    fun noWindowMeansNoLimit() {
        val now = at(2025, 3, 15, 10, 0)
        val longAgo = at(2024, 1, 1, 9, 0)

        assertEquals(
            longAgo,
            CatchUpPolicy.searchStart(longAgo, 0L, null, now)
        )
        assertFalse(
            CatchUpPolicy.windowSkippedOccurrences(longAgo, 0L, null, now)
        )
    }

    // ==================== Which missed occurrence runs ====================

    /**
     * Four days off, a daily schedule, and an automation set to run only the latest missed
     * occurrence: it runs the most recent one, not the oldest.
     */
    @Test
    fun theLatestMissedOccurrenceIsTheMostRecentOne() {
        val fromTimestamp = at(2025, 3, 10, 12, 0)
        val now = at(2025, 3, 15, 10, 0)
        val firstDue = at(2025, 3, 11, 9, 0)

        val last = CatchUpPolicy.lastDueOccurrence(dailyAt9(), fromTimestamp, now, firstDue, paris)

        assertEquals(at(2025, 3, 15, 9, 0), last)
    }

    /** With a single occurrence missed, the bisection lands back on the one already found. */
    @Test
    fun aSingleMissedOccurrenceIsReturnedAsItIs() {
        val fromTimestamp = at(2025, 3, 14, 12, 0)
        val now = at(2025, 3, 15, 10, 0)
        val firstDue = at(2025, 3, 15, 9, 0)

        val last = CatchUpPolicy.lastDueOccurrence(dailyAt9(), fromTimestamp, now, firstDue, paris)

        assertEquals(firstDue, last)
    }

    /**
     * A month away from a schedule that fires several times a day is answered by bisection, not
     * by walking every occurrence in between -- here more than a hundred of them.
     */
    @Test
    fun aMonthOfMissedOccurrencesIsAnsweredWithoutWalkingThem() {
        val fourTimesADay = ScheduleConfig(
            pattern = SchedulePattern.DailyMultiple(listOf("06:00", "12:00", "18:00", "22:00"))
        )
        val fromTimestamp = at(2025, 2, 15, 9, 0)
        val now = at(2025, 3, 15, 13, 0)

        val last = CatchUpPolicy.lastDueOccurrence(fourTimesADay, fromTimestamp, now, at(2025, 2, 15, 12, 0), paris)

        assertEquals(at(2025, 3, 15, 12, 0), last)
    }
}
