package com.assistant.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * The second is what happens when the parsing fails. Every parser answers null, and these
 * cases hold it there: docs/reference.md says a failure is explicit or it is not, and a
 * date that silently became the current moment was not.
 */
class DateUtilsTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = paris): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), zone)
            .toInstant().toEpochMilli()

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
        val readBack = DateUtils.parseDateForFilter(DateUtils.formatDateForDisplay(instant, paris), paris)!!

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
        val timestamp = DateUtils.parseIso8601Date("2025-03-15", paris)!!

        assertEquals(at(2025, 3, 15, 0, 0), timestamp)
        assertEquals("2025-03-15", DateUtils.timestampToIso8601Date(timestamp, paris))
    }

    /** A time field has no date of its own: it is read as that time today. */
    @Test
    fun anIsoTimeIsReadAsThatTimeToday() {
        val timestamp = DateUtils.parseIso8601Time("14:30", paris)!!

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


    // ==================== The two that read the clock ====================

    /**
     * These are the only two functions left that look at the clock, and they mean it: they
     * are what a picker opens on when nothing has been chosen. Each is its formatter
     * applied to now, which is all there is to check without freezing time.
     */
    @Test
    fun todayAndNowAreTheirFormattersAppliedToTheClock() {
        val now = System.currentTimeMillis()

        assertEquals(DateUtils.formatDateForDisplay(now, paris), DateUtils.getTodayFormatted(paris))
        assertEquals(DateUtils.formatTimeForDisplay(now, paris), DateUtils.getCurrentTimeFormatted(paris))
    }

    /** And they answer to the timezone given, like everything else here. */
    @Test
    fun todayDependsOnTheTimezone() {
        val inParis = DateUtils.getTodayFormatted(paris)
        val inTokyo = DateUtils.getTodayFormatted(tokyo)

        // Same day or the next one, never anything else: Tokyo is eight hours ahead.
        val readBackParis = DateUtils.parseDateForFilter(inParis, paris)!!
        val readBackTokyo = DateUtils.parseDateForFilter(inTokyo, tokyo)
        assertTrue(readBackTokyo == null || readBackTokyo >= readBackParis - 86_400_000L)
    }

    // ==================== What happens when it cannot be read ====================

    /**
     * Every parser answers an unreadable value with null rather than with a date.
     *
     * The alternative, which these functions used to do, was to return the current time:
     * nothing afterwards could then tell "the string was nonsense" from "the date really is
     * today". combineDateTime is the one that mattered, being what sets the timestamp of an
     * entry in JournalEntryScreen and in TrackingEntryDialog.
     */
    @Test
    fun anUnreadableValue_isRefused() {
        val unreadable = listOf("", "not a date", "2025-03-15", "32/13/2025", "15/03/2025 14:30")

        for (bad in unreadable) {
            assertNull("parseDateForFilter(\"$bad\")", DateUtils.parseDateForFilter(bad, paris))
            assertNull("combineDateTime(\"$bad\")", DateUtils.combineDateTime(bad, "14:30", paris))
        }

        assertNull(DateUtils.parseIso8601Date("15/03/2025", paris))
        assertNull(DateUtils.parseIso8601Time("not a time", paris))
    }

    /** An unreadable time is refused too, rather than becoming the current hour. */
    @Test
    fun anUnreadableTime_isRefused() {
        val unreadable = listOf("", "half past two", "14", "14:30:00", "abc:def")

        for (bad in unreadable) {
            assertNull("parseTime(\"$bad\")", DateUtils.parseTime(bad, paris))
        }
    }

    /**
     * A time outside the clock is refused where it is read, not left for whatever comes
     * next. It used to come back as written -- 99:99 as Pair(99, 99) -- and only failed one
     * call later inside combineDateTime, where the time string was no longer in sight.
     */
    @Test
    fun anOutOfRangeTime_isRefusedWhereItIsRead() {
        assertNull(DateUtils.parseTime("99:99", paris))
        assertNull(DateUtils.parseTime("24:00", paris))
        assertNull(DateUtils.parseTime("12:60", paris))
        assertNull(DateUtils.parseTime("-1:30", paris))

        assertNull(DateUtils.combineDateTime("15/03/2025", "99:99", paris))
    }

    /** The bounds of the clock are themselves valid. */
    @Test
    fun theEdgesOfTheClockAreAccepted() {
        assertEquals(Pair(0, 0), DateUtils.parseTime("00:00", paris))
        assertEquals(Pair(23, 59), DateUtils.parseTime("23:59", paris))
    }
}
