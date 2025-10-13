package com.assistant.core.utils

import kotlinx.serialization.Serializable

/**
 * Schedule configuration for automated executions
 * Supports 6 types of scheduling patterns
 */
@Serializable
data class ScheduleConfig(
    val pattern: SchedulePattern,
    val timezone: String = "Europe/Paris",
    val enabled: Boolean = true,
    val startDate: Long? = null,        // Start executing from this date (null = now)
    val endDate: Long? = null,          // Stop executing after this date (null = indefinite)
    val nextExecutionTime: Long? = null // Calculated by system (ScheduleCalculator)
)

/**
 * Sealed class hierarchy for 6 different schedule patterns
 *
 * Note: Regular intervals (every X minutes/hours) are NOT included
 * as no real use case has been identified for them
 */
@Serializable
sealed class SchedulePattern {

    /**
     * Type 1: Daily - Multiple times per day
     * Example: "Every day at 9am, 2pm, and 6pm"
     *
     * @param times List of times in HH:mm format (24h)
     */
    @Serializable
    data class DailyMultiple(
        val times: List<String>  // ["09:00", "14:00", "18:00"]
    ) : SchedulePattern()

    /**
     * Type 2: Weekly Simple - Specific days with single common time
     * Example: "Monday, Wednesday, Friday at 9am"
     *
     * @param daysOfWeek List of days (1=Monday, 7=Sunday)
     * @param time Single time in HH:mm format (24h)
     */
    @Serializable
    data class WeeklySimple(
        val daysOfWeek: List<Int>,  // 1-7
        val time: String             // "09:00"
    ) : SchedulePattern()

    /**
     * Type 3: Monthly Recurrent - Specific months with fixed day
     * Example: "On the 15th of January, March, and June at 10am"
     *
     * Note: For days > 28, if month doesn't have that day (e.g., Feb 31),
     * the execution is skipped for that month
     *
     * @param months List of months (1-12)
     * @param dayOfMonth Day of month (1-31)
     * @param time Time in HH:mm format (24h)
     */
    @Serializable
    data class MonthlyRecurrent(
        val months: List<Int>,       // 1-12
        val dayOfMonth: Int,         // 1-31
        val time: String
    ) : SchedulePattern()

    /**
     * Type 4: Weekly Custom - Different times for different days
     * Example: "Monday 9am, Wednesday 2pm, Friday 5pm"
     *
     * @param moments List of day+time combinations
     */
    @Serializable
    data class WeeklyCustom(
        val moments: List<WeekMoment>
    ) : SchedulePattern()

    /**
     * Type 5: Yearly Recurrent - Same dates every year
     * Example: "Every January 1st and December 25th at 8am"
     *
     * @param dates List of month+day+time combinations
     */
    @Serializable
    data class YearlyRecurrent(
        val dates: List<YearlyDate>
    ) : SchedulePattern()

    /**
     * Type 6: Specific Dates - One-shot executions (no repetition)
     * Example: "March 15, 2025 at 2:30pm and April 20, 2025 at 10am"
     *
     * @param timestamps List of exact timestamps (UTC milliseconds)
     */
    @Serializable
    data class SpecificDates(
        val timestamps: List<Long>
    ) : SchedulePattern()
}

/**
 * Day of week + time combination for WeeklyCustom pattern
 */
@Serializable
data class WeekMoment(
    val dayOfWeek: Int,  // 1=Monday, 7=Sunday
    val time: String     // HH:mm format
)

/**
 * Month + day + time combination for YearlyRecurrent pattern
 */
@Serializable
data class YearlyDate(
    val month: Int,      // 1-12
    val day: Int,        // 1-31
    val time: String     // HH:mm format
)
