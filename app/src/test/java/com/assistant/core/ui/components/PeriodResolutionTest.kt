package com.assistant.core.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Covers turning "yesterday" or "last week" into the two timestamps a query runs between.
 *
 * This is what a command means by a period, and the recent work on automations turns on it:
 * a run catching up on a past day has to resolve against the moment it was scheduled for,
 * not against the clock, or every late run reads today. TODO.md still sends that to the
 * device; these cases take the arithmetic half of it.
 *
 * The day's start hour and the week's first day are parameters here, so a case states the
 * settings it assumes instead of depending on how the app happens to be configured.
 */
class PeriodResolutionTest {

    /**
     * These functions work in Calendar.getInstance(), which is the machine's timezone and
     * not the app's. Fixing it for the duration keeps the cases meaningful wherever they
     * run; the last test is about that being so.
     */
    private lateinit var originalZone: TimeZone

    @Before
    fun fixTheMachineTimezone() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
    }

    @After
    fun restoreTheMachineTimezone() {
        TimeZone.setDefault(originalZone)
    }

    /** A moment, written as the machine's local time. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun normalize(timestamp: Long, type: PeriodType, dayStart: Int = 0, weekStart: String = "monday") =
        normalizeTimestampWithConfig(timestamp, type, dayStart, weekStart)

    private fun resolve(offset: Int, type: PeriodType, reference: Long, dayStart: Int = 0, weekStart: String = "monday") =
        resolveRelativePeriod(RelativePeriod(offset, type), reference, dayStart, weekStart)

    // ==================== Where a period starts ====================

    /** An hour starts on the hour. */
    @Test
    fun anHourStartsOnTheHour() {
        assertEquals(at(2025, 3, 15, 14), normalize(at(2025, 3, 15, 14, 37), PeriodType.HOUR))
    }

    /** A day starts at midnight when that is how the app is set. */
    @Test
    fun aDayStartsAtMidnightByDefault() {
        assertEquals(at(2025, 3, 15, 0), normalize(at(2025, 3, 15, 14, 37), PeriodType.DAY))
    }

    /**
     * With a later start hour, a moment before it still belongs to the day before. This is
     * the setting for someone whose day ends after midnight: 2am on Saturday is Friday.
     */
    @Test
    fun aDayCanStartLaterAndSwallowTheSmallHours() {
        val twoInTheMorning = at(2025, 3, 15, 2)

        assertEquals(at(2025, 3, 14, 4), normalize(twoInTheMorning, PeriodType.DAY, dayStart = 4))
        // Past the start hour, it is that day.
        assertEquals(at(2025, 3, 15, 4), normalize(at(2025, 3, 15, 9), PeriodType.DAY, dayStart = 4))
    }

    /** A week starts on the configured day. 2025-03-15 is a Saturday. */
    @Test
    fun aWeekStartsOnTheConfiguredDay() {
        val saturday = at(2025, 3, 15, 12)

        assertEquals(at(2025, 3, 10, 0), normalize(saturday, PeriodType.WEEK, weekStart = "monday"))
        assertEquals(at(2025, 3, 9, 0), normalize(saturday, PeriodType.WEEK, weekStart = "sunday"))
    }

    /** Months and years start on their first day. */
    @Test
    fun monthsAndYearsStartOnTheirFirstDay() {
        val midMarch = at(2025, 3, 15, 12)

        assertEquals(at(2025, 3, 1, 0), normalize(midMarch, PeriodType.MONTH))
        assertEquals(at(2025, 1, 1, 0), normalize(midMarch, PeriodType.YEAR))
    }

    /** Normalising twice changes nothing the second time. */
    @Test
    fun normalisingIsSettledInOneStep() {
        for (type in PeriodType.values()) {
            val once = normalize(at(2025, 3, 15, 14, 37), type)
            assertEquals("for $type", once, normalize(once, type))
        }
    }

    // ==================== Counting backwards and forwards ====================

    /** No offset is the period the reference falls in. */
    @Test
    fun noOffsetIsThePeriodTheReferenceIsIn() {
        val resolved = resolve(0, PeriodType.DAY, at(2025, 3, 15, 14))

        assertEquals(at(2025, 3, 15, 0), resolved.timestamp)
        assertEquals(PeriodType.DAY, resolved.type)
    }

    /** Yesterday, last week, last month, last year. */
    @Test
    fun aNegativeOffsetStepsBack() {
        val reference = at(2025, 3, 15, 14)

        assertEquals(at(2025, 3, 14, 0), resolve(-1, PeriodType.DAY, reference).timestamp)
        assertEquals(at(2025, 3, 3, 0), resolve(-1, PeriodType.WEEK, reference).timestamp)
        assertEquals(at(2025, 2, 1, 0), resolve(-1, PeriodType.MONTH, reference).timestamp)
        assertEquals(at(2024, 1, 1, 0), resolve(-1, PeriodType.YEAR, reference).timestamp)
    }

    /** And forwards, for a period still to come. */
    @Test
    fun aPositiveOffsetStepsForward() {
        val reference = at(2025, 3, 15, 14)

        assertEquals(at(2025, 3, 16, 0), resolve(1, PeriodType.DAY, reference).timestamp)
        assertEquals(at(2025, 4, 1, 0), resolve(1, PeriodType.MONTH, reference).timestamp)
    }

    /** Stepping back across the turn of a month, and of a year. */
    @Test
    fun steppingBackCrossesTheTurnOfAMonthAndOfAYear() {
        assertEquals(at(2025, 2, 28, 0), resolve(-1, PeriodType.DAY, at(2025, 3, 1, 10)).timestamp)
        assertEquals(at(2024, 12, 31, 0), resolve(-1, PeriodType.DAY, at(2025, 1, 1, 10)).timestamp)
        assertEquals(at(2024, 12, 1, 0), resolve(-1, PeriodType.MONTH, at(2025, 1, 15, 10)).timestamp)
    }

    /** A large offset walks the whole way rather than jumping. */
    @Test
    fun aLargeOffsetWalksTheWholeWay() {
        assertEquals(at(2025, 1, 1, 0), resolve(-31, PeriodType.DAY, at(2025, 2, 1, 10)).timestamp)
    }

    /**
     * The reference is what "yesterday" is counted from, which is the point of it being
     * a parameter: an automation catching up on a day long past reads that day.
     */
    @Test
    fun theReferenceDecidesWhichDayYesterdayIs() {
        val today = resolve(-1, PeriodType.DAY, at(2025, 3, 15, 10)).timestamp
        val weeksAgo = resolve(-1, PeriodType.DAY, at(2025, 2, 10, 10)).timestamp

        assertEquals(at(2025, 3, 14, 0), today)
        assertEquals(at(2025, 2, 9, 0), weeksAgo)
        assertNotEquals(today, weeksAgo)
    }

    // ==================== Where a period ends ====================

    /** A period ends one millisecond before the next begins, leaving no gap and no overlap. */
    @Test
    fun aPeriodEndsJustBeforeTheNextBegins() {
        for (type in PeriodType.values()) {
            val period = resolve(0, type, at(2025, 3, 15, 14))
            val next = resolve(1, type, at(2025, 3, 15, 14))

            assertEquals("for $type", next.timestamp - 1, getPeriodEndTimestamp(period, 0, "monday"))
        }
    }

    /** A day with a later start hour ends just before the next day's start hour. */
    @Test
    fun aShiftedDayEndsAtTheNextDaysStartHour() {
        val period = resolve(0, PeriodType.DAY, at(2025, 3, 15, 9), dayStart = 4)

        assertEquals(at(2025, 3, 15, 4), period.timestamp)
        assertEquals(at(2025, 3, 16, 4) - 1, getPeriodEndTimestamp(period, 4, "monday"))
    }

    // ==================== Which timezone this all happens in ====================

    /**
     * Periods are worked out in the machine's timezone, not the one configured in the app.
     *
     * This states what the code does today. Calendar.getInstance() takes the device's
     * default, while DateUtils, DateTimeConverter and ScheduleCalculator all work in the
     * configured one. So a user who sets a timezone override gets days that begin at a
     * different moment from the dates shown beside them, and an automation resolving
     * "yesterday" answers to the phone's setting rather than the app's.
     */
    @Test
    fun periodsFollowTheMachinesTimezoneRatherThanTheApps() {
        val instant = at(2025, 3, 15, 23, 30) // late evening in Paris

        val startInParis = normalize(instant, PeriodType.DAY)

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val startInTokyo = normalize(instant, PeriodType.DAY)

        // Eight hours further east that instant is already the next day, so the day it
        // belongs to moves -- on the same instant, with the same app settings.
        assertNotEquals(startInParis, startInTokyo)
    }
}
