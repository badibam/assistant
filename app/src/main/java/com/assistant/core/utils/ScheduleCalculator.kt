package com.assistant.core.utils

import java.time.*
import java.time.temporal.TemporalAdjusters

/**
 * Calculator for next execution time based on schedule patterns
 * Handles all 6 schedule pattern types with timezone support
 */
object ScheduleCalculator {

    /**
     * Calculate next execution time for a schedule
     *
     * @param pattern The schedule pattern to evaluate
     * @param timezone Timezone string (e.g., "Europe/Paris")
     * @param startDate Earliest allowed execution time (null = now)
     * @param endDate Latest allowed execution time (null = no limit)
     * @param fromTimestamp Calculate from this timestamp (default = now)
     * @return Next execution timestamp in UTC milliseconds, or null if no more executions
     */
    fun calculateNextExecution(
        pattern: SchedulePattern,
        timezone: String,
        startDate: Long?,
        endDate: Long?,
        fromTimestamp: Long = System.currentTimeMillis()
    ): Long? {
        val zoneId = ZoneId.of(timezone)
        val fromInstant = Instant.ofEpochMilli(fromTimestamp)
        val fromZoned = ZonedDateTime.ofInstant(fromInstant, zoneId)

        // Calculate next execution based on pattern type
        val nextZoned = when (pattern) {
            is SchedulePattern.DailyMultiple -> calculateDailyMultiple(fromZoned, pattern, zoneId)
            is SchedulePattern.WeeklySimple -> calculateWeeklySimple(fromZoned, pattern, zoneId)
            is SchedulePattern.MonthlyRecurrent -> calculateMonthlyRecurrent(fromZoned, pattern, zoneId)
            is SchedulePattern.WeeklyCustom -> calculateWeeklyCustom(fromZoned, pattern, zoneId)
            is SchedulePattern.YearlyRecurrent -> calculateYearlyRecurrent(fromZoned, pattern, zoneId)
            is SchedulePattern.SpecificDates -> calculateSpecificDates(fromTimestamp, pattern)
        } ?: return null

        val nextTimestamp = nextZoned.toInstant().toEpochMilli()

        // Apply start/end date constraints
        if (startDate != null && nextTimestamp < startDate) {
            // Recursively calculate from startDate
            return calculateNextExecution(pattern, timezone, startDate, endDate, startDate)
        }

        if (endDate != null && nextTimestamp > endDate) {
            return null // Past end date
        }

        return nextTimestamp
    }

    /**
     * Type 1: Daily - Multiple times per day
     * Example: ["09:00", "14:00", "18:00"] → next occurrence of any of these times
     */
    private fun calculateDailyMultiple(
        from: ZonedDateTime,
        pattern: SchedulePattern.DailyMultiple,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.times.isEmpty()) return null

        // Try all times today
        val today = from.toLocalDate()
        val candidatesToday = pattern.times.mapNotNull { timeStr ->
            parseTime(timeStr)?.let { (hour, minute) ->
                ZonedDateTime.of(today, LocalTime.of(hour, minute), zoneId)
            }
        }.filter { it.isAfter(from) }

        if (candidatesToday.isNotEmpty()) {
            return candidatesToday.minOrNull()
        }

        // No match today, return first time tomorrow
        val tomorrow = today.plusDays(1)
        val firstTime = pattern.times.minOrNull() ?: return null
        val (hour, minute) = parseTime(firstTime) ?: return null
        return ZonedDateTime.of(tomorrow, LocalTime.of(hour, minute), zoneId)
    }

    /**
     * Type 2: Weekly Simple - Specific days with single time
     * Example: daysOfWeek=[1,3,5], time="09:00" → Monday/Wednesday/Friday at 9am
     */
    private fun calculateWeeklySimple(
        from: ZonedDateTime,
        pattern: SchedulePattern.WeeklySimple,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.daysOfWeek.isEmpty()) return null
        val (hour, minute) = parseTime(pattern.time) ?: return null

        // Try today if it matches
        val todayDayOfWeek = from.dayOfWeek.value // 1=Monday, 7=Sunday
        if (todayDayOfWeek in pattern.daysOfWeek) {
            val todayCandidate = ZonedDateTime.of(from.toLocalDate(), LocalTime.of(hour, minute), zoneId)
            if (todayCandidate.isAfter(from)) {
                return todayCandidate
            }
        }

        // Find next matching day in current week or next week
        val sortedDays = pattern.daysOfWeek.sorted()
        val nextDay = sortedDays.firstOrNull { it > todayDayOfWeek }
            ?: (sortedDays.first() + 7) // Wrap to next week

        val daysToAdd = if (nextDay > todayDayOfWeek) {
            nextDay - todayDayOfWeek
        } else {
            7 - todayDayOfWeek + nextDay
        }

        val targetDate = from.toLocalDate().plusDays(daysToAdd.toLong())
        return ZonedDateTime.of(targetDate, LocalTime.of(hour, minute), zoneId)
    }

    /**
     * Type 3: Monthly Recurrent - Specific months with fixed day
     * Example: months=[1,3,6], dayOfMonth=15, time="10:00" → 15th of Jan/Mar/Jun at 10am
     *
     * Note: If day doesn't exist in month (e.g., Feb 31), skip that month
     */
    private fun calculateMonthlyRecurrent(
        from: ZonedDateTime,
        pattern: SchedulePattern.MonthlyRecurrent,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.months.isEmpty()) return null
        val (hour, minute) = parseTime(pattern.time) ?: return null

        // Try current month if it matches
        val currentMonth = from.monthValue
        val currentYear = from.year
        if (currentMonth in pattern.months) {
            val daysInMonth = from.toLocalDate().lengthOfMonth()
            if (pattern.dayOfMonth <= daysInMonth) {
                val candidate = ZonedDateTime.of(
                    LocalDate.of(currentYear, currentMonth, pattern.dayOfMonth),
                    LocalTime.of(hour, minute),
                    zoneId
                )
                if (candidate.isAfter(from)) {
                    return candidate
                }
            }
        }

        // Find next valid month/year combination
        val sortedMonths = pattern.months.sorted()
        var testYear = currentYear
        var testMonth = sortedMonths.firstOrNull { it > currentMonth } ?: run {
            testYear = currentYear + 1
            sortedMonths.first()
        }

        // Search up to 2 years ahead
        for (attempt in 0..24) {
            val testDate = LocalDate.of(testYear, testMonth, 1)
            val daysInMonth = testDate.lengthOfMonth()

            if (pattern.dayOfMonth <= daysInMonth) {
                return ZonedDateTime.of(
                    LocalDate.of(testYear, testMonth, pattern.dayOfMonth),
                    LocalTime.of(hour, minute),
                    zoneId
                )
            }

            // Move to next matching month
            val nextMonthIdx = sortedMonths.indexOf(testMonth) + 1
            if (nextMonthIdx >= sortedMonths.size) {
                testYear++
                testMonth = sortedMonths.first()
            } else {
                testMonth = sortedMonths[nextMonthIdx]
            }
        }

        return null // Could not find valid date in 2 years
    }

    /**
     * Type 4: Weekly Custom - Different times for different days
     * Example: [(1,"09:00"), (3,"14:00"), (5,"17:00")] → Mon 9am, Wed 2pm, Fri 5pm
     */
    private fun calculateWeeklyCustom(
        from: ZonedDateTime,
        pattern: SchedulePattern.WeeklyCustom,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.moments.isEmpty()) return null

        // Try today if it has a matching moment
        val todayDayOfWeek = from.dayOfWeek.value
        val todayMoments = pattern.moments.filter { it.dayOfWeek == todayDayOfWeek }
        for (moment in todayMoments) {
            val (hour, minute) = parseTime(moment.time) ?: continue
            val candidate = ZonedDateTime.of(from.toLocalDate(), LocalTime.of(hour, minute), zoneId)
            if (candidate.isAfter(from)) {
                return candidate
            }
        }

        // Find next moment in current or next week
        val sortedMoments = pattern.moments.sortedWith(compareBy({ it.dayOfWeek }, { it.time }))
        val nextMoment = sortedMoments.firstOrNull { it.dayOfWeek > todayDayOfWeek }
            ?: sortedMoments.first() // Wrap to next week

        val daysToAdd = if (nextMoment.dayOfWeek > todayDayOfWeek) {
            nextMoment.dayOfWeek - todayDayOfWeek
        } else {
            7 - todayDayOfWeek + nextMoment.dayOfWeek
        }

        val (hour, minute) = parseTime(nextMoment.time) ?: return null
        val targetDate = from.toLocalDate().plusDays(daysToAdd.toLong())
        return ZonedDateTime.of(targetDate, LocalTime.of(hour, minute), zoneId)
    }

    /**
     * Type 5: Yearly Recurrent - Same dates every year
     * Example: [(1,1,"08:00"), (12,25,"08:00")] → Jan 1 and Dec 25 at 8am every year
     */
    private fun calculateYearlyRecurrent(
        from: ZonedDateTime,
        pattern: SchedulePattern.YearlyRecurrent,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.dates.isEmpty()) return null

        val currentYear = from.year
        val sortedDates = pattern.dates.sortedWith(compareBy({ it.month }, { it.day }, { it.time }))

        // Try current year
        for (date in sortedDates) {
            val (hour, minute) = parseTime(date.time) ?: continue
            // Check if day exists in month (e.g., Feb 30 is invalid)
            val daysInMonth = YearMonth.of(currentYear, date.month).lengthOfMonth()
            if (date.day > daysInMonth) continue

            val candidate = ZonedDateTime.of(
                LocalDate.of(currentYear, date.month, date.day),
                LocalTime.of(hour, minute),
                zoneId
            )
            if (candidate.isAfter(from)) {
                return candidate
            }
        }

        // No match this year, return first date next year
        val firstDate = sortedDates.first()
        val nextYear = currentYear + 1
        val daysInMonth = YearMonth.of(nextYear, firstDate.month).lengthOfMonth()
        if (firstDate.day > daysInMonth) return null // Invalid date

        val (hour, minute) = parseTime(firstDate.time) ?: return null
        return ZonedDateTime.of(
            LocalDate.of(nextYear, firstDate.month, firstDate.day),
            LocalTime.of(hour, minute),
            zoneId
        )
    }

    /**
     * Type 6: Specific Dates - One-shot executions
     * Example: [1735030800000, 1738051200000] → Specific timestamps, no repetition
     */
    private fun calculateSpecificDates(
        fromTimestamp: Long,
        pattern: SchedulePattern.SpecificDates
    ): ZonedDateTime? {
        if (pattern.timestamps.isEmpty()) return null

        // Find next timestamp after fromTimestamp
        val nextTimestamp = pattern.timestamps.sorted().firstOrNull { it > fromTimestamp }
            ?: return null

        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextTimestamp), ZoneId.systemDefault())
    }

    /**
     * Parse time string "HH:mm" to (hour, minute) pair
     */
    private fun parseTime(timeStr: String): Pair<Int, Int>? {
        return try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            if (hour !in 0..23 || minute !in 0..59) return null
            Pair(hour, minute)
        } catch (e: Exception) {
            null
        }
    }
}
