package com.assistant.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers the formatting and parsing every screen goes through to show or read a date.
 *
 * Two things are pinned here. The first is the ordinary behaviour: what a timestamp looks
 * like once displayed, what a displayed date means once read back, and where a day starts
 * and ends -- all of which depend on the timezone, which is why it is a parameter.
 *
 * The second is what happens when the parsing fails, and that part is not reassuring: seven
 * functions answer an unreadable date with the current time. Those cases are written as
 * measurements. docs/reference.md says a failure is explicit or it is not, and these are
 * not; the tests state what is there so that removing it is a visible change.
 */
class DateUtilsTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = paris): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), zone)
            .toInstant().toEpochMilli()

    /** True when the value is the current time, to within a generous margin. */
    private fun isRoughlyNow(timestamp: Long): Boolean =
        Math.abs(System.currentTimeMillis() - timestamp) < 10_000L

    // ==================== Showing a timestamp ====================

    /** The three display formats, and the fact that all of them read the timezone. */
    @Test
    fun aTimestampIsShownInTheGivenTimezone() {
        val instant = at(2025, 3, 15, 14, 30)

        assertEquals("15/03/2025", DateUtils.formatDateForDisplay(instant, paris))
        assertEquals("14:30", DateUtils.formatTimeForDisplay(instant, paris))
        assertEquals("15/03/25 14:30", DateUtils.formatFullDateTime(instant, paris))

        // The same instant, eight hours further east, is already the small hours of the 15th.
        assertEquals("15/03/2025", DateUtils.formatDateForDisplay(instant, tokyo))
        assertEquals("22:30", DateUtils.formatTimeForDisplay(instant, tokyo))
    }

    /** Far enough east, the same instant is a different day. */
    @Test
    fun theTimezoneCanChangeWhichDayItIs() {
        val lateEvening = at(2025, 3, 15, 23, 30)

        assertEquals("15/03/2025", DateUtils.formatDateForDisplay(lateEvening, paris))
        assertEquals("16/03/2025", DateUtils.formatDateForDisplay(lateEvening, tokyo))
    }

    // ==================== Reading a displayed date ====================

    /** A date typed as dd/MM/yyyy means the start of that day in the given timezone. */
    @Test
    fun aDisplayedDateIsReadAsTheStartOfThatDay() {
        assertEquals(at(2025, 3, 15, 0, 0), DateUtils.parseDateForFilter("15/03/2025", paris))
        assertEquals(at(2025, 3, 15, 0, 0, tokyo), DateUtils.parseDateForFilter("15/03/2025", tokyo))
    }

    /** Out and back returns the day it started on. */
    @Test
    fun showingThenReadingADateKeepsTheDay() {
        val instant = at(2025, 3, 15, 14, 30)
        val readBack = DateUtils.parseDateForFilter(DateUtils.formatDateForDisplay(instant, paris), paris)

        assertTrue(DateUtils.isOnSameDay(instant, readBack, paris))
    }

    // ==================== Where a day begins and ends ====================

    /** Midnight to the last millisecond before the next, in the given timezone. */
    @Test
    fun aDayRunsFromMidnightToTheLastMillisecond() {
        val middayOn15th = at(2025, 3, 15, 12, 0)

        assertEquals(at(2025, 3, 15, 0, 0), DateUtils.getStartOfDay(middayOn15th, paris))
        assertEquals(
            at(2025, 3, 16, 0, 0) - 1L,
            DateUtils.getEndOfDay(middayOn15th, paris)
        )
    }

    /**
     * On the night the clocks go forward there is no midnight missing, but the day is an
     * hour shorter. The bounds follow the calendar day rather than a fixed 24 hours.
     */
    @Test
    fun aDayThatLosesAnHourIsStillOneDay() {
        val duringTheShortDay = at(2024, 3, 31, 12, 0)

        val start = DateUtils.getStartOfDay(duringTheShortDay, paris)
        val end = DateUtils.getEndOfDay(duringTheShortDay, paris)

        assertEquals(23 * 3_600_000L - 1L, end - start)
    }

    /** Two moments are on the same day or not according to the timezone they are read in. */
    @Test
    fun beingOnTheSameDayDependsOnTheTimezone() {
        val lateOn15th = at(2025, 3, 15, 23, 0)
        val earlyOn16th = at(2025, 3, 16, 1, 0)

        assertFalse(DateUtils.isOnSameDay(lateOn15th, earlyOn16th, paris))
        assertTrue(DateUtils.isOnSameDay(lateOn15th, earlyOn16th, tokyo))
    }

    // ==================== The ISO helpers for custom fields ====================

    /** A date field's value out and back in the same timezone. */
    @Test
    fun anIsoDateSurvivesTheRoundTrip() {
        val timestamp = DateUtils.parseIso8601Date("2025-03-15", paris)

        assertEquals(at(2025, 3, 15, 0, 0), timestamp)
        assertEquals("2025-03-15", DateUtils.timestampToIso8601Date(timestamp, paris))
    }

    /** A datetime field's value, likewise. */
    @Test
    fun anIsoDateTimeSurvivesTheRoundTrip() {
        val timestamp = DateUtils.parseIso8601DateTime("2025-03-15T14:30:00", paris)

        assertEquals(at(2025, 3, 15, 14, 30), timestamp)
        assertEquals("2025-03-15T14:30:00", DateUtils.timestampToIso8601DateTime(timestamp, paris))
    }

    /** A time field has no date of its own: it is read as that time today. */
    @Test
    fun anIsoTimeIsReadAsThatTimeToday() {
        val timestamp = DateUtils.parseIso8601Time("14:30", paris)

        assertEquals("14:30", DateUtils.timestampToIso8601Time(timestamp, paris))
        assertTrue(DateUtils.isOnSameDay(timestamp, System.currentTimeMillis(), paris))
    }

    /** A time in hours and minutes is read as written. */
    @Test
    fun aTimeIsSplitIntoHoursAndMinutes() {
        assertEquals(Pair(9, 30), DateUtils.parseTime("09:30", paris))
        assertEquals(Pair(0, 0), DateUtils.parseTime("00:00", paris))
        assertEquals(Pair(23, 59), DateUtils.parseTime("23:59", paris))
    }

    /** A date and a time given separately combine into the moment they name. */
    @Test
    fun aDateAndATimeCombineIntoTheMomentTheyName() {
        assertEquals(
            at(2025, 3, 15, 14, 30),
            DateUtils.combineDateTime("15/03/2025", "14:30", paris)
        )
    }

    // ==================== What happens when it cannot be read ====================

    /**
     * Every parser answers an unreadable value with the current time.
     *
     * This states what the code does today. docs/reference.md says a failure is explicit or
     * it is not, and none of these is: nothing distinguishes "the string was nonsense" from
     * "the date really is today", at the call site or afterwards in the data.
     *
     * combineDateTime is the one that bites. It is what sets the timestamp of an entry in
     * JournalEntryScreen and in TrackingEntryDialog, so a date it cannot read files the
     * entry under the present moment instead of the day chosen, and says nothing.
     */
    @Test
    fun anUnreadableValue_becomesTheCurrentTime() {
        val unreadable = listOf("", "not a date", "2025-03-15", "32/13/2025")

        for (bad in unreadable) {
            assertTrue("parseDateForFilter(\"$bad\")", isRoughlyNow(DateUtils.parseDateForFilter(bad, paris)))
            assertTrue("combineDateTime(\"$bad\")", isRoughlyNow(DateUtils.combineDateTime(bad, "14:30", paris)))
        }

        assertTrue(isRoughlyNow(DateUtils.parseIso8601Date("15/03/2025", paris)))
        assertTrue(isRoughlyNow(DateUtils.parseIso8601Time("not a time", paris)))
        assertTrue(isRoughlyNow(DateUtils.parseIso8601DateTime("2025-03-15", paris)))
    }

    /**
     * An unreadable time answers with the current hour and minute, which for a good part of
     * any day is indistinguishable from a time somebody meant to enter.
     */
    @Test
    fun anUnreadableTime_becomesTheCurrentHourAndMinute() {
        val now = ZonedDateTime.now(paris)
        val expected = Pair(now.hour, now.minute)

        assertEquals(expected, DateUtils.parseTime("", paris))
        assertEquals(expected, DateUtils.parseTime("half past two", paris))
        assertEquals(expected, DateUtils.parseTime("14:30:00", paris))
    }

    /**
     * A time out of range is not refused either, and does not fall back: it is returned as
     * written, for whatever comes next to deal with.
     *
     * combineDateTime is what comes next, and it passes the pair to LocalDate.atTime, which
     * throws -- caught by its own handler, so an hour of 99 also ends up as the current
     * time, by a different route than the one above.
     */
    @Test
    fun anOutOfRangeTime_isReturnedAsWrittenAndThenSwallowed() {
        assertEquals(Pair(99, 99), DateUtils.parseTime("99:99", paris))
        assertTrue(isRoughlyNow(DateUtils.combineDateTime("15/03/2025", "99:99", paris)))
    }
}
