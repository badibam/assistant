package app.treelune.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers the next-execution calculation for all six schedule patterns.
 *
 * Every case fixes both the clock and the timezone. That is the whole point of testing this
 * here: TODO.md still carries "check a real catch-up on the device, automation scheduled,
 * app closed for several days", and a scheduling question that costs days to observe by
 * hand costs milliseconds with fromTimestamp set by hand.
 *
 * Europe/Paris throughout, because the interesting cases are the ones a timezone with
 * daylight saving produces, and because it is the zone this app is actually used in.
 */
class ScheduleCalculatorTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    /** A wall-clock moment in Paris, as the epoch milliseconds the calculator deals in. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), paris)
            .toInstant().toEpochMilli()

    private fun next(
        pattern: SchedulePattern,
        from: Long
    ): Long? = ScheduleCalculator.calculateNextExecution(pattern, from, paris)

    // ==================== Type 1: several times a day ====================

    /** The next time today, when there is one still to come. */
    @Test
    fun daily_takesTheNextTimeStillToComeToday() {
        val pattern = SchedulePattern.DailyMultiple(listOf("09:00", "14:00", "18:00"))

        assertEquals(at(2025, 1, 15, 14, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2025, 1, 15, 18, 0), next(pattern, from = at(2025, 1, 15, 14, 30)))
    }

    /** Once the day's times have all gone by, the first one tomorrow. */
    @Test
    fun daily_rollsOverToTomorrow() {
        val pattern = SchedulePattern.DailyMultiple(listOf("09:00", "14:00", "18:00"))

        assertEquals(at(2025, 1, 16, 9, 0), next(pattern, from = at(2025, 1, 15, 20, 0)))
    }

    /**
     * Tomorrow's first time is the earliest hour, however the hour is written. An hour without
     * its leading zero sorts after every hour that has one, so reading the list as text used to
     * land the rollover on the afternoon. Nothing in the app writes unpadded times -- the editor
     * produces HH:mm -- but a schedule from the model or from a restored backup can.
     */
    @Test
    fun daily_picksTomorrowsEarliestTimeHoweverItIsWritten() {
        val padded = SchedulePattern.DailyMultiple(listOf("09:00", "14:00"))
        val unpadded = SchedulePattern.DailyMultiple(listOf("9:00", "14:00"))
        val afterBothHaveGone = at(2025, 1, 15, 20, 0)

        assertEquals(at(2025, 1, 16, 9, 0), next(padded, from = afterBothHaveGone))
        assertEquals(at(2025, 1, 16, 9, 0), next(unpadded, from = afterBothHaveGone))
    }

    /** No times, nothing to schedule. */
    @Test
    fun daily_withNoTimes_hasNoNextExecution() {
        assertNull(next(SchedulePattern.DailyMultiple(emptyList()), from = at(2025, 1, 15, 10, 0)))
    }

    /** A time that is not a time schedules nothing, rather than being rounded into one. */
    @Test
    fun daily_withAnImpossibleTime_hasNoNextExecution() {
        assertNull(next(SchedulePattern.DailyMultiple(listOf("25:00")), from = at(2025, 1, 15, 10, 0)))
    }

    // ==================== Type 2: certain days, one time ====================

    /** 2025-01-15 is a Wednesday, so day 3 of the week. */
    private val mondayWednesdayFriday = SchedulePattern.WeeklySimple(listOf(1, 3, 5), "09:00")

    /** Today counts when its time has not gone by. */
    @Test
    fun weekly_takesTodayWhenTheTimeIsStillAhead() {
        assertEquals(
            at(2025, 1, 15, 9, 0),
            next(mondayWednesdayFriday, from = at(2025, 1, 15, 8, 0))
        )
    }

    /** Once it has gone by, the next day in the list. */
    @Test
    fun weekly_movesToTheNextDayInTheList() {
        assertEquals(
            at(2025, 1, 17, 9, 0),
            next(mondayWednesdayFriday, from = at(2025, 1, 15, 10, 0))
        )
    }

    /** With no day left this week, it wraps to the first one of the next. */
    @Test
    fun weekly_wrapsToNextWeek() {
        val mondaysOnly = SchedulePattern.WeeklySimple(listOf(1), "09:00")

        assertEquals(at(2025, 1, 20, 9, 0), next(mondaysOnly, from = at(2025, 1, 15, 10, 0)))
    }

    // ==================== Type 3: certain months, a fixed day ====================

    /** The current month counts when the day and time are still ahead. */
    @Test
    fun monthly_takesTheCurrentMonthWhenItIsStillAhead() {
        val pattern = SchedulePattern.MonthlyRecurrent(listOf(1, 3, 6), dayOfMonth = 15, time = "10:00")

        assertEquals(at(2025, 1, 15, 10, 0), next(pattern, from = at(2025, 1, 15, 9, 0)))
        assertEquals(at(2025, 3, 15, 10, 0), next(pattern, from = at(2025, 1, 15, 11, 0)))
    }

    /** A month too short for the day is passed over, not clamped to its last day. */
    @Test
    fun monthly_skipsAMonthTooShortForTheDay() {
        val pattern = SchedulePattern.MonthlyRecurrent(listOf(2, 3), dayOfMonth = 31, time = "10:00")

        assertEquals(at(2025, 3, 31, 10, 0), next(pattern, from = at(2025, 1, 1, 0, 0)))
    }

    /** A day no listed month ever has schedules nothing at all. */
    @Test
    fun monthly_withADayNoListedMonthHas_hasNoNextExecution() {
        val pattern = SchedulePattern.MonthlyRecurrent(listOf(2), dayOfMonth = 31, time = "10:00")

        assertNull(next(pattern, from = at(2025, 1, 1, 0, 0)))
    }

    // ==================== Type 4: a time per day ====================

    /** Several moments on the same day are taken in order. */
    @Test
    fun weeklyCustom_takesTheDaysMomentsInOrder() {
        val pattern = SchedulePattern.WeeklyCustom(
            listOf(WeekMoment(3, "09:00"), WeekMoment(3, "14:00"), WeekMoment(5, "17:00"))
        )

        assertEquals(at(2025, 1, 15, 14, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2025, 1, 17, 17, 0), next(pattern, from = at(2025, 1, 15, 15, 0)))
    }

    /** And wrap to the first moment of the following week. */
    @Test
    fun weeklyCustom_wrapsToNextWeek() {
        val pattern = SchedulePattern.WeeklyCustom(listOf(WeekMoment(1, "09:00"), WeekMoment(3, "09:00")))

        assertEquals(at(2025, 1, 20, 9, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
    }

    // ==================== Type 5: the same dates every year ====================

    /** This year when the date is still ahead, next year once it has gone. */
    @Test
    fun yearly_takesThisYearThenTheNext() {
        val pattern = SchedulePattern.YearlyRecurrent(listOf(YearlyDate(12, 25, "08:00")))

        assertEquals(at(2025, 12, 25, 8, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2026, 12, 25, 8, 0), next(pattern, from = at(2025, 12, 26, 10, 0)))
    }

    /** A 29 February schedule waits for the next leap year rather than stopping. */
    @Test
    fun yearly_waitsForTheNextLeapYear() {
        val pattern = SchedulePattern.YearlyRecurrent(listOf(YearlyDate(2, 29, "08:00")))

        assertEquals(at(2024, 2, 29, 8, 0), next(pattern, from = at(2024, 1, 15, 10, 0)))
        assertEquals(at(2028, 2, 29, 8, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2028, 2, 29, 8, 0), next(pattern, from = at(2024, 3, 1, 10, 0)))
    }

    /**
     * A year that skips a leap day is still found: 2100 is not a leap year, so the eight
     * year gap from 2096 to 2104 is the longest a date can go without coming round, and the
     * search has to outlast it.
     */
    @Test
    fun yearly_crossesACenturyThatIsNotALeapYear() {
        val pattern = SchedulePattern.YearlyRecurrent(listOf(YearlyDate(2, 29, "08:00")))

        assertEquals(at(2104, 2, 29, 8, 0), next(pattern, from = at(2096, 3, 1, 10, 0)))
    }

    /**
     * With several dates, one of them being impossible this year does not stop the others.
     * The old fallback only ever looked at the first date of the list.
     */
    @Test
    fun yearly_doesNotLetOneImpossibleDateHideTheRest() {
        val pattern = SchedulePattern.YearlyRecurrent(
            listOf(YearlyDate(2, 29, "08:00"), YearlyDate(12, 25, "08:00"))
        )

        // Past Christmas in a non-leap year: the next one is Christmas again, not nothing.
        assertEquals(at(2026, 12, 25, 8, 0), next(pattern, from = at(2025, 12, 26, 10, 0)))
    }

    // ==================== Type 6: fixed dates, once each ====================

    /** The next one still ahead, then nothing. */
    @Test
    fun specificDates_runOnceEachAndThenStop() {
        val first = at(2025, 3, 15, 14, 30)
        val second = at(2025, 4, 20, 10, 0)
        val pattern = SchedulePattern.SpecificDates(listOf(second, first))

        assertEquals(first, next(pattern, from = at(2025, 1, 1, 0, 0)))
        assertEquals(second, next(pattern, from = first))
        assertNull(next(pattern, from = second))
    }

    // ==================== Clocks that move ====================

    /**
     * On the night the clocks go forward, 02:30 does not exist in Paris. The schedule is
     * not skipped: it lands at 03:30, the same instant the missing half hour would have
     * been. Java's own rule for a gap, pinned here because a schedule silently moving by an
     * hour once a year is the sort of thing nobody notices.
     */
    @Test
    fun springForward_movesATimeThatDoesNotExistToTheEndOfTheGap() {
        val pattern = SchedulePattern.DailyMultiple(listOf("02:30"))

        assertEquals(
            at(2024, 3, 31, 3, 30),
            next(pattern, from = at(2024, 3, 30, 12, 0))
        )
    }

    /**
     * On the night they go back, 02:30 happens twice. The earlier one is taken, so the
     * schedule runs once rather than twice.
     */
    @Test
    fun fallBack_takesTheFirstOfTwoIdenticalTimes() {
        val pattern = SchedulePattern.DailyMultiple(listOf("02:30"))
        val beforeTheChange = ZonedDateTime.of(
            LocalDate.of(2024, 10, 27), LocalTime.of(1, 0), paris
        ).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()

        val result = next(pattern, from = beforeTheChange)!!

        // The earlier 02:30 is still on summer time, an hour ahead of the later one.
        val earlier = ZonedDateTime.of(LocalDate.of(2024, 10, 27), LocalTime.of(2, 30), paris)
            .withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        assertEquals(earlier, result)
    }

    /** The zone is what the wall-clock time is read in, so it changes the instant produced. */
    @Test
    fun theZone_decidesWhichInstantAWallClockTimeMeans() {
        val pattern = SchedulePattern.DailyMultiple(listOf("09:00"))
        val from = at(2025, 1, 15, 10, 0)

        val inParis = ScheduleCalculator.calculateNextExecution(pattern, from, paris)
        val inUtc = ScheduleCalculator.calculateNextExecution(pattern, from, ZoneId.of("UTC"))

        // Paris is an hour ahead of UTC in January, so its 09:00 comes an hour earlier.
        assertEquals(3_600_000L, inUtc!! - inParis!!)
    }
}
