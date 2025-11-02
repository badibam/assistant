package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.utils.DateUtils
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import kotlinx.coroutines.launch
import java.util.*

/**
 * Reusable period filter types
 */
enum class PeriodFilterType {
    ALL,    // All data
    HOUR,   // Filter by hour
    DAY,    // Filter by day  
    WEEK,   // Filter by week
    MONTH,  // Filter by month
    YEAR    // Filter by year
}

/**
 * Enum for supported period types
 */
enum class PeriodType {
    HOUR, DAY, WEEK, MONTH, YEAR
}

/**
 * Data class representing an absolute period with a specific timestamp
 */
data class Period(
    val timestamp: Long,  // Absolute timestamp marking the start of the period
    val type: PeriodType
) {
    companion object {
        fun now(type: PeriodType): Period = Period(
            normalizeTimestampWithConfig(System.currentTimeMillis(), type),
            type
        )
    }
}

/**
 * Data class representing a relative period (for automation contexts)
 * Offset is relative to "now": -1 = previous, 0 = current, 1 = next
 */
data class RelativePeriod(
    val offset: Int,      // Relative offset from current period
    val type: PeriodType  // Type of period (DAY, WEEK, MONTH, YEAR)
) {
    companion object {
        fun now(type: PeriodType): RelativePeriod = RelativePeriod(0, type)
    }
}

/**
 * Calculate offset between a timestamp and "now" for a given period type
 * Returns number of periods difference (negative = past, positive = future, 0 = current)
 */
fun calculatePeriodOffset(
    timestamp: Long,
    type: PeriodType
): Int {
    val now = System.currentTimeMillis()
    val normalizedNow = normalizeTimestampWithConfig(now, type)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, type)

    return when (type) {
        PeriodType.HOUR -> {
            -((normalizedNow - normalizedTimestamp) / (60 * 60 * 1000)).toInt()
        }
        PeriodType.DAY -> {
            -((normalizedNow - normalizedTimestamp) / (24 * 60 * 60 * 1000)).toInt()
        }
        PeriodType.WEEK -> {
            -((normalizedNow - normalizedTimestamp) / (7 * 24 * 60 * 60 * 1000)).toInt()
        }
        PeriodType.MONTH -> {
            val nowCal = Calendar.getInstance().apply { timeInMillis = normalizedNow }
            val tsCal = Calendar.getInstance().apply { timeInMillis = normalizedTimestamp }
            -((nowCal.get(Calendar.YEAR) - tsCal.get(Calendar.YEAR)) * 12 +
                (nowCal.get(Calendar.MONTH) - tsCal.get(Calendar.MONTH)))
        }
        PeriodType.YEAR -> {
            val nowYear = Calendar.getInstance().apply { timeInMillis = normalizedNow }.get(Calendar.YEAR)
            val tsYear = Calendar.getInstance().apply { timeInMillis = normalizedTimestamp }.get(Calendar.YEAR)
            -(nowYear - tsYear)
        }
    }
}

/**
 * Resolve relative period to absolute Period
 * Applies offset from current period
 */
fun resolveRelativePeriod(relativePeriod: RelativePeriod): Period {
    val dayStartHour = AppConfigManager.getDayStartHour()
    val weekStartDay = AppConfigManager.getWeekStartDay()

    val now = System.currentTimeMillis()
    val currentNormalized = normalizeTimestampWithConfig(now, relativePeriod.type)
    var targetPeriod = Period(currentNormalized, relativePeriod.type)

    // Apply offset by navigating periods
    repeat(kotlin.math.abs(relativePeriod.offset)) {
        targetPeriod = if (relativePeriod.offset < 0) {
            getPreviousPeriod(targetPeriod)
        } else {
            getNextPeriod(targetPeriod)
        }
    }

    return targetPeriod
}

/**
 * Get the end timestamp of a period (last millisecond of the period)
 */
fun getPeriodEndTimestamp(period: Period): Long {
    // Get the start of the next period
    val nextPeriodStart = getNextPeriod(period).timestamp
    // End of current period is 1ms before start of next period
    return nextPeriodStart - 1
}

/**
 * Extension function to get the end timestamp of a period
 * Simplifies usage: period.getEndTimestamp()
 */
fun Period.getEndTimestamp(): Long {
    return getPeriodEndTimestamp(this)
}

/**
 * Normalizes a timestamp according to period type with configuration parameters
 */
fun normalizeTimestampWithConfig(timestamp: Long, type: PeriodType): Long {
    val dayStartHour = AppConfigManager.getDayStartHour()
    val weekStartDay = AppConfigManager.getWeekStartDay()

    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    return when (type) {
        PeriodType.HOUR -> {
            // Start of hour (XX:00:00.000)
            cal.apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        PeriodType.DAY -> {
            // Start of day according to dayStartHour
            cal.apply {
                // If current hour is before dayStartHour, we need to go back to previous day
                val currentHour = get(Calendar.HOUR_OF_DAY)
                if (currentHour < dayStartHour) {
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                set(Calendar.HOUR_OF_DAY, dayStartHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        PeriodType.WEEK -> {
            // Start of week according to weekStartDay
            val calendarDay = when (weekStartDay) {
                "sunday" -> Calendar.SUNDAY
                "monday" -> Calendar.MONDAY
                "tuesday" -> Calendar.TUESDAY
                "wednesday" -> Calendar.WEDNESDAY
                "thursday" -> Calendar.THURSDAY
                "friday" -> Calendar.FRIDAY
                "saturday" -> Calendar.SATURDAY
                else -> Calendar.MONDAY
            }
            cal.apply {
                firstDayOfWeek = calendarDay
                set(Calendar.DAY_OF_WEEK, calendarDay)
                // If current hour is before dayStartHour, we need to go back to previous day
                val currentHour = get(Calendar.HOUR_OF_DAY)
                if (currentHour < dayStartHour) {
                    add(Calendar.DAY_OF_MONTH, -1)
                    // After going back a day, we might need to adjust the week
                    set(Calendar.DAY_OF_WEEK, calendarDay)
                }
                set(Calendar.HOUR_OF_DAY, dayStartHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        PeriodType.MONTH -> {
            // First day of month with dayStartHour
            cal.apply {
                set(Calendar.DAY_OF_MONTH, 1)
                // If current hour is before dayStartHour, we need to go back to previous day
                val currentHour = get(Calendar.HOUR_OF_DAY)
                if (currentHour < dayStartHour) {
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                set(Calendar.HOUR_OF_DAY, dayStartHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        PeriodType.YEAR -> {
            // First day of year with dayStartHour
            cal.apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                // If current hour is before dayStartHour, we need to go back to previous day
                val currentHour = get(Calendar.HOUR_OF_DAY)
                if (currentHour < dayStartHour) {
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                set(Calendar.HOUR_OF_DAY, dayStartHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}

/**
 * Single period selector with navigation arrows (◀▶).
 * Displays relative labels (Today, This week, etc.) with navigation arrows
 * and ability to click to open date selector. Loads its own temporal configuration.
 */
@Composable
fun SinglePeriodSelector(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
    showDatePicker: Boolean = true,
    useOnlyRelativeLabels: Boolean = false
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Load app configuration internally
    var dayStartHour by remember { mutableStateOf(0) }
    var weekStartDay by remember { mutableStateOf("monday") }
    var isConfigLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val coordinator = Coordinator(context)
            val configResult = coordinator.processUserAction("app_config.get", mapOf("category" to "format"))
            if (configResult.isSuccess) {
                val config = configResult.data?.get("settings") as? Map<String, Any>
                dayStartHour = (config?.get("day_start_hour") as? Number)?.toInt() ?: 0
                weekStartDay = config?.get("week_start_day") as? String ?: "monday"
            }
        } catch (e: Exception) {
            // Use defaults if config loading fails
            dayStartHour = 0
            weekStartDay = "monday"
        } finally {
            isConfigLoading = false
        }
    }

    // State for date selector
    var showPicker by remember { mutableStateOf(false) }

    // Smart label generation
    val label = remember(period, dayStartHour, weekStartDay, isConfigLoading, useOnlyRelativeLabels) {
        if (isConfigLoading) {
            s.shared("tools_loading")
        } else {
            generatePeriodLabel(period, dayStartHour, weekStartDay, s, useOnlyRelativeLabels)
        }
    }

    if (isConfigLoading) {
        // Show loading state
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(text = s.shared("tools_loading_config"), type = TextType.BODY)
        }
        return
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        UI.ActionButton(
            action = ButtonAction.LEFT,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                val previousPeriod = getPreviousPeriod(period)
                onPeriodChange(previousPeriod)
            }
        )
        
        // Clickable label in center
        Box(modifier = Modifier.weight(1f)) {
            if (showDatePicker) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showPicker = true }
                ) {
                    UI.Text(
                        text = label,
                        type = TextType.BODY,
                        fillMaxWidth = true,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                UI.Text(
                    text = label,
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Next button
        UI.ActionButton(
            action = ButtonAction.RIGHT,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                val nextPeriod = getNextPeriod(period)
                onPeriodChange(nextPeriod)
            }
        )
    }
    
    // Date selector if enabled
    if (showPicker && showDatePicker) {
        when (period.type) {
            PeriodType.HOUR -> {
                // For HOUR: DatePicker keeps existing hour
                UI.DatePicker(
                    selectedDate = DateUtils.formatDateForDisplay(period.timestamp),
                    onDateSelected = { selectedDate ->
                        // Combine new date + existing hour
                        val existingHour = Calendar.getInstance().apply { 
                            timeInMillis = period.timestamp 
                        }.get(Calendar.HOUR_OF_DAY)
                        val existingMinute = Calendar.getInstance().apply { 
                            timeInMillis = period.timestamp 
                        }.get(Calendar.MINUTE)
                        
                        val newDate = DateUtils.parseDateForFilter(selectedDate)
                        val combinedTimestamp = Calendar.getInstance().apply {
                            timeInMillis = newDate
                            set(Calendar.HOUR_OF_DAY, existingHour)
                            set(Calendar.MINUTE, existingMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        // Normalize for HOUR (start of hour)
                        val normalizedTimestamp = normalizeTimestampWithConfig(combinedTimestamp, period.type)
                        onPeriodChange(Period(normalizedTimestamp, period.type))
                    },
                    onDismiss = { showPicker = false }
                )
            }
            else -> {
                // For other types: classic DatePicker
                UI.DatePicker(
                    selectedDate = DateUtils.formatDateForDisplay(period.timestamp),
                    onDateSelected = { selectedDate ->
                        val newTimestamp = normalizeTimestampWithConfig(DateUtils.parseDateForFilter(selectedDate), period.type)
                        onPeriodChange(Period(newTimestamp, period.type))
                    },
                    onDismiss = { showPicker = false }
                )
            }
        }
    }
}

/**
 * Generates smart label for given period
 * @param useOnlyRelativeLabels if true, always use relative labels ("dans 145 ans"),
 *                             if false, fallback to absolute dates for distant periods
 */
private fun generatePeriodLabel(
    period: Period,
    dayStartHour: Int,
    weekStartDay: String,
    s: StringsContext,
    useOnlyRelativeLabels: Boolean = false
): String {
    val now = System.currentTimeMillis()

    return when (period.type) {
        PeriodType.HOUR -> generateHourLabel(period.timestamp, now, s, useOnlyRelativeLabels)
        PeriodType.DAY -> generateDayLabel(period.timestamp, now, dayStartHour, s, useOnlyRelativeLabels)
        PeriodType.WEEK -> generateWeekLabel(period.timestamp, now, weekStartDay, s, useOnlyRelativeLabels)
        PeriodType.MONTH -> generateMonthLabel(period.timestamp, now, s, useOnlyRelativeLabels)
        PeriodType.YEAR -> generateYearLabel(period.timestamp, now, s, useOnlyRelativeLabels)
    }
}

/**
 * Labels for hours
 */
private fun generateHourLabel(timestamp: Long, now: Long, s: StringsContext, useOnlyRelativeLabels: Boolean): String {
    // Normalize both timestamps to compare hours correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.HOUR)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.HOUR)

    val diffHours = ((normalizedNow - normalizedTimestamp) / (60 * 60 * 1000)).toInt()

    return when {
        diffHours == 0 -> s.shared("period_this_hour")
        diffHours > 0 -> {
            if (useOnlyRelativeLabels || diffHours <= 12) {
                if (diffHours == 1) {
                    s.shared("period_hours_ago_singular").format(diffHours)
                } else {
                    s.shared("period_hours_ago_plural").format(diffHours)
                }
            } else {
                val date = DateUtils.formatDateForDisplay(timestamp)
                val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
                "$date ${hour}h"
            }
        }
        diffHours < 0 -> {
            val absDiff = -diffHours
            if (useOnlyRelativeLabels || absDiff <= 12) {
                if (absDiff == 1) {
                    s.shared("period_hours_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_hours_from_now_plural").format(absDiff)
                }
            } else {
                val date = DateUtils.formatDateForDisplay(timestamp)
                val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
                "$date ${hour}h"
            }
        }
        else -> {
            // Fallback case should never happen
            val date = DateUtils.formatDateForDisplay(timestamp)
            val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
            "$date ${hour}h"
        }
    }
}

/**
 * Labels for days
 */
private fun generateDayLabel(timestamp: Long, now: Long, dayStartHour: Int, s: StringsContext, useOnlyRelativeLabels: Boolean): String {
    // Normalize timestamps to compare them according to dayStartHour
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.DAY)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.DAY)

    val diffDays = ((normalizedNow - normalizedTimestamp) / (24 * 60 * 60 * 1000)).toInt()

    return when {
        diffDays == 0 -> s.shared("period_today")
        diffDays == 1 -> s.shared("period_yesterday")
        diffDays == -1 -> s.shared("period_tomorrow")
        diffDays > 0 -> {
            if (useOnlyRelativeLabels || diffDays <= 7) {
                if (diffDays == 1) {
                    s.shared("period_days_ago_singular").format(diffDays)
                } else {
                    s.shared("period_days_ago_plural").format(diffDays)
                }
            } else {
                DateUtils.formatDateForDisplay(timestamp)
            }
        }
        diffDays < 0 -> {
            val absDiff = -diffDays
            if (useOnlyRelativeLabels || absDiff <= 7) {
                if (absDiff == 1) {
                    s.shared("period_days_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_days_from_now_plural").format(absDiff)
                }
            } else {
                DateUtils.formatDateForDisplay(timestamp)
            }
        }
        else -> DateUtils.formatDateForDisplay(timestamp)
    }
}

/**
 * Labels for weeks
 */
private fun generateWeekLabel(timestamp: Long, now: Long, weekStartDay: String, s: StringsContext, useOnlyRelativeLabels: Boolean = false): String {
    // Normalize timestamps to compare weeks correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.WEEK)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.WEEK)

    val diffWeeks = ((normalizedNow - normalizedTimestamp) / (7 * 24 * 60 * 60 * 1000)).toInt()

    return when {
        diffWeeks == 0 -> s.shared("period_this_week")
        diffWeeks == 1 -> s.shared("period_last_week")
        diffWeeks == -1 -> s.shared("period_next_week")
        diffWeeks > 0 -> {
            if (useOnlyRelativeLabels || diffWeeks <= 4) {
                if (diffWeeks == 1) {
                    s.shared("period_weeks_ago_singular").format(diffWeeks)
                } else {
                    s.shared("period_weeks_ago_plural").format(diffWeeks)
                }
            } else {
                val weekStart = getWeekStart(timestamp, weekStartDay)
                s.shared("period_week_of").format(DateUtils.formatDateForDisplay(weekStart))
            }
        }
        diffWeeks < 0 -> {
            val absDiff = -diffWeeks
            if (useOnlyRelativeLabels || absDiff <= 4) {
                if (absDiff == 1) {
                    s.shared("period_weeks_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_weeks_from_now_plural").format(absDiff)
                }
            } else {
                val weekStart = getWeekStart(timestamp, weekStartDay)
                s.shared("period_week_of").format(DateUtils.formatDateForDisplay(weekStart))
            }
        }
        else -> {
            // Fallback case should never happen
            val weekStart = getWeekStart(timestamp, weekStartDay)
            s.shared("period_week_of").format(DateUtils.formatDateForDisplay(weekStart))
        }
    }
}

/**
 * Labels for months
 */
private fun generateMonthLabel(timestamp: Long, now: Long, s: StringsContext, useOnlyRelativeLabels: Boolean = false): String {
    // Normalize timestamps to compare months correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.MONTH)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.MONTH)

    val nowCal = Calendar.getInstance().apply { timeInMillis = normalizedNow }
    val tsCal = Calendar.getInstance().apply { timeInMillis = normalizedTimestamp }

    val diffMonths = (nowCal.get(Calendar.YEAR) - tsCal.get(Calendar.YEAR)) * 12 +
                    (nowCal.get(Calendar.MONTH) - tsCal.get(Calendar.MONTH))

    return when {
        diffMonths == 0 -> s.shared("period_this_month")
        diffMonths == 1 -> s.shared("period_last_month")
        diffMonths == -1 -> s.shared("period_next_month")
        diffMonths > 0 -> {
            if (useOnlyRelativeLabels || diffMonths <= 6) {
                s.shared("period_months_ago").format(diffMonths)
            } else {
                val monthKeys = arrayOf(
                    "month_january", "month_february", "month_march", "month_april", "month_may", "month_june",
                    "month_july", "month_august", "month_september", "month_october", "month_november", "month_december"
                )
                val month = s.shared(monthKeys[tsCal.get(Calendar.MONTH)])
                val year = tsCal.get(Calendar.YEAR)
                "$month $year"
            }
        }
        diffMonths < 0 -> {
            val absDiff = -diffMonths
            if (useOnlyRelativeLabels || absDiff <= 6) {
                s.shared("period_months_from_now").format(absDiff)
            } else {
                val monthKeys = arrayOf(
                    "month_january", "month_february", "month_march", "month_april", "month_may", "month_june",
                    "month_july", "month_august", "month_september", "month_october", "month_november", "month_december"
                )
                val month = s.shared(monthKeys[tsCal.get(Calendar.MONTH)])
                val year = tsCal.get(Calendar.YEAR)
                "$month $year"
            }
        }
        else -> {
            // Fallback case should never happen
            val monthKeys = arrayOf(
                "month_january", "month_february", "month_march", "month_april", "month_may", "month_june",
                "month_july", "month_august", "month_september", "month_october", "month_november", "month_december"
            )
            val month = s.shared(monthKeys[tsCal.get(Calendar.MONTH)])
            val year = tsCal.get(Calendar.YEAR)
            "$month $year"
        }
    }
}

/**
 * Labels for years
 */
private fun generateYearLabel(timestamp: Long, now: Long, s: StringsContext, useOnlyRelativeLabels: Boolean = false): String {
    // Normalize timestamps to compare years correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.YEAR)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.YEAR)

    val nowYear = Calendar.getInstance().apply { timeInMillis = normalizedNow }.get(Calendar.YEAR)
    val tsYear = Calendar.getInstance().apply { timeInMillis = normalizedTimestamp }.get(Calendar.YEAR)
    val diffYears = nowYear - tsYear

    return when {
        diffYears == 0 -> s.shared("period_this_year")
        diffYears == 1 -> s.shared("period_last_year")
        diffYears == -1 -> s.shared("period_next_year")
        diffYears > 0 -> {
            if (useOnlyRelativeLabels || diffYears <= 3) {
                if (diffYears == 1) {
                    s.shared("period_years_ago_singular").format(diffYears)
                } else {
                    s.shared("period_years_ago_plural").format(diffYears)
                }
            } else {
                tsYear.toString()
            }
        }
        diffYears < 0 -> {
            val absDiff = -diffYears
            if (useOnlyRelativeLabels || absDiff <= 3) {
                if (absDiff == 1) {
                    s.shared("period_years_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_years_from_now_plural").format(absDiff)
                }
            } else {
                tsYear.toString()
            }
        }
        else -> {
            // Fallback case should never happen
            tsYear.toString()
        }
    }
}

/**
 * Calculates previous period with normalization
 */
private fun getPreviousPeriod(period: Period): Period {
    val cal = Calendar.getInstance().apply { timeInMillis = period.timestamp }

    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, -1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, -1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, -1)
    }

    // Normalize timestamp after calculation with configuration parameters
    return Period(normalizeTimestampWithConfig(cal.timeInMillis, period.type), period.type)
}

/**
 * Calculates next period with normalization
 */
private fun getNextPeriod(period: Period): Period {
    val cal = Calendar.getInstance().apply { timeInMillis = period.timestamp }

    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, 1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, 1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, 1)
    }

    // Normalize timestamp after calculation with configuration parameters
    return Period(normalizeTimestampWithConfig(cal.timeInMillis, period.type), period.type)
}

/**
 * Calculates week start according to configuration
 */
private fun getWeekStart(timestamp: Long, weekStartDay: String): Long {
    val calendarDay = when (weekStartDay) {
        "sunday" -> Calendar.SUNDAY
        "monday" -> Calendar.MONDAY
        "tuesday" -> Calendar.TUESDAY
        "wednesday" -> Calendar.WEDNESDAY
        "thursday" -> Calendar.THURSDAY
        "friday" -> Calendar.FRIDAY
        "saturday" -> Calendar.SATURDAY
        else -> Calendar.MONDAY
    }
    val cal = Calendar.getInstance().apply { 
        timeInMillis = timestamp
        firstDayOfWeek = calendarDay
        set(Calendar.DAY_OF_WEEK, calendarDay)
    }
    return cal.timeInMillis
}

/**
 * Single relative period selector with navigation arrows (◀▶)
 * Works directly with RelativePeriod for AUTOMATION contexts
 * Displays truly relative labels ("Le jour-même", "La veille", "Il y a 2 jours")
 *
 * @param relativePeriod Current relative period (offset + type)
 * @param onRelativePeriodChange Callback when period changes via navigation
 */
@Composable
fun SingleRelativePeriodSelector(
    relativePeriod: RelativePeriod,
    onRelativePeriodChange: (RelativePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Generate truly relative label
    val label = remember(relativePeriod) {
        generateRelativePeriodLabel(relativePeriod, s)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button (go further back in time)
        UI.ActionButton(
            action = ButtonAction.LEFT,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                // Decrease offset (go more negative = further in past)
                val newOffset = relativePeriod.offset - 1
                onRelativePeriodChange(RelativePeriod(newOffset, relativePeriod.type))
            }
        )

        // Label in center (non-clickable for now - could add date picker later)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(
                text = label,
                type = TextType.BODY
            )
        }

        // Next button (go closer to now)
        UI.ActionButton(
            action = ButtonAction.RIGHT,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                // Increase offset (go less negative = closer to now)
                val newOffset = relativePeriod.offset + 1
                onRelativePeriodChange(RelativePeriod(newOffset, relativePeriod.type))
            }
        )
    }
}

/**
 * Generate truly relative label for RelativePeriod
 * Examples: "Le jour-même", "La veille", "Le lendemain", "2 jours avant", "3 semaines après"
 */
private fun generateRelativePeriodLabel(relativePeriod: RelativePeriod, s: com.assistant.core.strings.StringsContext): String {
    val offset = relativePeriod.offset
    val type = relativePeriod.type

    return when {
        // Current period (offset = 0)
        offset == 0 -> when (type) {
            PeriodType.HOUR -> s.shared("period_relative_current_hour")    // "L'heure en cours"
            PeriodType.DAY -> s.shared("period_relative_current_day")       // "Le jour-même"
            PeriodType.WEEK -> s.shared("period_relative_current_week")     // "La semaine en cours"
            PeriodType.MONTH -> s.shared("period_relative_current_month")   // "Le mois en cours"
            PeriodType.YEAR -> s.shared("period_relative_current_year")     // "L'année en cours"
        }
        // Previous period (offset = -1)
        offset == -1 -> when (type) {
            PeriodType.HOUR -> s.shared("period_relative_one_hour_before")     // "1 heure avant"
            PeriodType.DAY -> s.shared("period_relative_one_day_before")        // "La veille"
            PeriodType.WEEK -> s.shared("period_relative_one_week_before")      // "La semaine précédente"
            PeriodType.MONTH -> s.shared("period_relative_one_month_before")    // "Le mois précédent"
            PeriodType.YEAR -> s.shared("period_relative_one_year_before")      // "L'année précédente"
        }
        // Next period (offset = 1)
        offset == 1 -> when (type) {
            PeriodType.HOUR -> s.shared("period_relative_one_hour_after")      // "1 heure après"
            PeriodType.DAY -> s.shared("period_relative_one_day_after")         // "Le lendemain"
            PeriodType.WEEK -> s.shared("period_relative_one_week_after")       // "La semaine suivante"
            PeriodType.MONTH -> s.shared("period_relative_one_month_after")     // "Le mois suivant"
            PeriodType.YEAR -> s.shared("period_relative_one_year_after")       // "L'année suivante"
        }
        // Past periods (offset < -1)
        offset < -1 -> {
            val absOffset = -offset
            when (type) {
                PeriodType.HOUR -> String.format(s.shared("period_relative_hours_before"), absOffset)    // "X heures avant"
                PeriodType.DAY -> String.format(s.shared("period_relative_days_before"), absOffset)       // "X jours avant"
                PeriodType.WEEK -> String.format(s.shared("period_relative_weeks_before"), absOffset)     // "X semaines avant"
                PeriodType.MONTH -> String.format(s.shared("period_relative_months_before"), absOffset)   // "X mois avant"
                PeriodType.YEAR -> String.format(s.shared("period_relative_years_before"), absOffset)     // "X années avant"
            }
        }
        // Future periods (offset > 1)
        else -> {
            when (type) {
                PeriodType.HOUR -> String.format(s.shared("period_relative_hours_after"), offset)     // "X heures après"
                PeriodType.DAY -> String.format(s.shared("period_relative_days_after"), offset)        // "X jours après"
                PeriodType.WEEK -> String.format(s.shared("period_relative_weeks_after"), offset)      // "X semaines après"
                PeriodType.MONTH -> String.format(s.shared("period_relative_months_after"), offset)    // "X mois après"
                PeriodType.YEAR -> String.format(s.shared("period_relative_years_after"), offset)      // "X années après"
            }
        }
    }
}

/**
 * Simple date range picker with two DatePickers (start/end)
 * No navigation arrows, just direct date selection
 */
@Composable
fun CustomDateRangePicker(
    startDate: Long?,
    endDate: Long?,
    onStartDateChange: (Long?) -> Unit,
    onEndDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Start date picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = s.shared("range_start_date") + ":",
                type = TextType.LABEL
            )

            Box(modifier = Modifier.weight(1f)) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showStartDatePicker = true }
                ) {
                    UI.Text(
                        text = startDate?.let { DateUtils.formatDateForDisplay(it) } ?: s.shared("select_date"),
                        type = TextType.BODY,
                        fillMaxWidth = true,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // End date picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = s.shared("range_end_date") + ":",
                type = TextType.LABEL
            )

            Box(modifier = Modifier.weight(1f)) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showEndDatePicker = true }
                ) {
                    UI.Text(
                        text = endDate?.let { DateUtils.formatDateForDisplay(it) } ?: s.shared("select_date"),
                        type = TextType.BODY,
                        fillMaxWidth = true,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // DatePicker dialogs
    if (showStartDatePicker) {
        UI.DatePicker(
            selectedDate = startDate?.let { DateUtils.formatDateForDisplay(it) } ?: "",
            onDateSelected = { dateString ->
                val timestamp = DateUtils.parseDateForFilter(dateString)
                onStartDateChange(timestamp)
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        UI.DatePicker(
            selectedDate = endDate?.let { DateUtils.formatDateForDisplay(it) } ?: "",
            onDateSelected = { dateString ->
                val timestamp = DateUtils.parseDateForFilter(dateString)
                onEndDateChange(timestamp)
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

/**
 * Relative period range selector for AUTOMATION contexts
 * Works directly with RelativePeriod (offset + type) for relative periods
 * Also supports absolute custom dates for fixed reference points
 *
 * @param startPeriodType Type of start period (HOUR, DAY, WEEK, MONTH, YEAR, or null for custom)
 * @param endPeriodType Type of end period
 * @param startRelativePeriod Current start relative period (when type is not null)
 * @param endRelativePeriod Current end relative period (when type is not null)
 * @param startCustomDate Custom absolute date for start (mutually exclusive with relativePeriod)
 * @param endCustomDate Custom absolute date for end (mutually exclusive with relativePeriod)
 * @param onStartTypeChange Callback when start type changes
 * @param onEndTypeChange Callback when end type changes
 * @param onStartRelativePeriodChange Callback when start relative period changes
 * @param onEndRelativePeriodChange Callback when end relative period changes
 * @param onStartCustomDateChange Callback when start custom date changes
 * @param onEndCustomDateChange Callback when end custom date changes
 */
@Composable
fun RelativePeriodRangeSelector(
    startPeriodType: PeriodType?,
    endPeriodType: PeriodType?,
    startRelativePeriod: RelativePeriod?,
    endRelativePeriod: RelativePeriod?,
    startCustomDate: Long?,
    endCustomDate: Long?,
    onStartTypeChange: (PeriodType?) -> Unit,
    onEndTypeChange: (PeriodType?) -> Unit,
    onStartRelativePeriodChange: (RelativePeriod?) -> Unit,
    onEndRelativePeriodChange: (RelativePeriod?) -> Unit,
    onStartCustomDateChange: (Long?) -> Unit,
    onEndCustomDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // State for date pickers
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Start section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(
                text = s.shared("range_start") + ":",
                type = TextType.SUBTITLE
            )

            // Start type dropdown (includes "Date personnalisée" option with null value)
            val startTypeOptions = mapOf(
                s.shared("period_type_custom") to null,  // "Date personnalisée"
                s.shared("period_type_hour") to PeriodType.HOUR,
                s.shared("period_type_day") to PeriodType.DAY,
                s.shared("period_type_week") to PeriodType.WEEK,
                s.shared("period_type_month") to PeriodType.MONTH,
                s.shared("period_type_year") to PeriodType.YEAR
            )
            val startSelectedLabel = startTypeOptions.entries.find { it.value == startPeriodType }?.key
                ?: s.shared("period_type_custom")

            UI.FormSelection(
                label = s.shared("label_period_type"),
                options = startTypeOptions.keys.toList(),
                selected = startSelectedLabel,
                onSelect = { selectedLabel ->
                    // Convert label back to type
                    val newType = startTypeOptions[selectedLabel]
                    onStartTypeChange(newType)
                }
            )

            // Start relative period selector OR custom date button
            if (startPeriodType != null && startRelativePeriod != null) {
                // Relative period mode
                SingleRelativePeriodSelector(
                    relativePeriod = startRelativePeriod,
                    onRelativePeriodChange = { newRelativePeriod ->
                        onStartRelativePeriodChange(newRelativePeriod)
                    }
                )
            } else if (startPeriodType == null) {
                // Custom date mode
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showStartDatePicker = true }
                ) {
                    UI.Text(
                        text = startCustomDate?.let {
                            DateUtils.formatDateForDisplay(it)
                        } ?: s.shared("action_select_date"),
                        type = TextType.BODY
                    )
                }
            }
        }

        // End section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(
                text = s.shared("range_end") + ":",
                type = TextType.SUBTITLE
            )

            // End type dropdown (includes "Date personnalisée" option with null value)
            val endTypeOptions = mapOf(
                s.shared("period_type_custom") to null,  // "Date personnalisée"
                s.shared("period_type_hour") to PeriodType.HOUR,
                s.shared("period_type_day") to PeriodType.DAY,
                s.shared("period_type_week") to PeriodType.WEEK,
                s.shared("period_type_month") to PeriodType.MONTH,
                s.shared("period_type_year") to PeriodType.YEAR
            )
            val endSelectedLabel = endTypeOptions.entries.find { it.value == endPeriodType }?.key
                ?: s.shared("period_type_custom")

            UI.FormSelection(
                label = s.shared("label_period_type"),
                options = endTypeOptions.keys.toList(),
                selected = endSelectedLabel,
                onSelect = { selectedLabel ->
                    // Convert label back to type
                    val newType = endTypeOptions[selectedLabel]
                    onEndTypeChange(newType)
                }
            )

            // End relative period selector OR custom date button
            if (endPeriodType != null && endRelativePeriod != null) {
                // Relative period mode
                SingleRelativePeriodSelector(
                    relativePeriod = endRelativePeriod,
                    onRelativePeriodChange = { newRelativePeriod ->
                        onEndRelativePeriodChange(newRelativePeriod)
                    }
                )
            } else if (endPeriodType == null) {
                // Custom date mode
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showEndDatePicker = true }
                ) {
                    UI.Text(
                        text = endCustomDate?.let {
                            DateUtils.formatDateForDisplay(it)
                        } ?: s.shared("action_select_date"),
                        type = TextType.BODY
                    )
                }
            }
        }
    }

    // Date pickers
    if (showStartDatePicker) {
        UI.DatePicker(
            selectedDate = startCustomDate?.let { DateUtils.formatDateForDisplay(it) } ?: "",
            onDateSelected = { dateString ->
                val timestamp = DateUtils.parseDateForFilter(dateString)
                onStartCustomDateChange(timestamp)
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showEndDatePicker) {
        UI.DatePicker(
            selectedDate = endCustomDate?.let { DateUtils.formatDateForDisplay(it) } ?: "",
            onDateSelected = { dateString ->
                val timestamp = DateUtils.parseDateForFilter(dateString)
                onEndCustomDateChange(timestamp)
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

/**
 * Period range selector - wrapper that combines dropdowns with period selectors
 * Supports both standard periods (with navigation) and custom date ranges
 */
@Composable
fun PeriodRangeSelector(
    startPeriodType: PeriodType?,
    startPeriod: Period?,
    startCustomDate: Long?,
    startIsNow: Boolean = false,
    endPeriodType: PeriodType?,
    endPeriod: Period?,
    endCustomDate: Long?,
    endIsNow: Boolean = false,
    onStartTypeChange: (PeriodType?) -> Unit,
    onStartPeriodChange: (Period?) -> Unit,
    onStartCustomDateChange: (Long?) -> Unit,
    onStartIsNowChange: (Boolean) -> Unit = {},
    onEndTypeChange: (PeriodType?) -> Unit,
    onEndPeriodChange: (Period?) -> Unit,
    onEndCustomDateChange: (Long?) -> Unit,
    onEndIsNowChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    useOnlyRelativeLabels: Boolean = false,
    returnRelative: Boolean = false,
    startRelativePeriod: RelativePeriod? = null,
    endRelativePeriod: RelativePeriod? = null,
    onStartRelativePeriodChange: ((RelativePeriod?) -> Unit)? = null,
    onEndRelativePeriodChange: ((RelativePeriod?) -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // App configuration state
    var dayStartHour by remember { mutableStateOf(0) }
    var weekStartDay by remember { mutableStateOf("monday") }
    var isConfigLoading by remember { mutableStateOf(true) }

    // Load app configuration
    LaunchedEffect(Unit) {
        try {
            val coordinator = Coordinator(context)
            val configResult = coordinator.processUserAction("app_config.get", mapOf("category" to "format"))
            if (configResult.isSuccess) {
                val config = configResult.data?.get("settings") as? Map<String, Any>
                dayStartHour = (config?.get("day_start_hour") as? Number)?.toInt() ?: 0
                weekStartDay = config?.get("week_start_day") as? String ?: "monday"
            }
        } catch (e: Exception) {
            // Use defaults if config loading fails
            dayStartHour = 0
            weekStartDay = "monday"
        } finally {
            isConfigLoading = false
        }
    }

    // Show loading state while config is loading
    if (isConfigLoading) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(text = s.shared("tools_loading_config"), type = TextType.BODY)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Start section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(
                text = s.shared("range_start") + ":",
                type = TextType.SUBTITLE
            )

            // Start period type dropdown
            UI.FormSelection(
                label = "",
                options = listOf(
                    s.shared("period_hour"),
                    s.shared("period_day"),
                    s.shared("period_week"),
                    s.shared("period_month"),
                    s.shared("period_year"),
                    s.shared("period_now"),
                    s.shared("period_custom")
                ),
                selected = when {
                    startIsNow -> s.shared("period_now")
                    startPeriodType == PeriodType.HOUR -> s.shared("period_hour")
                    startPeriodType == PeriodType.DAY -> s.shared("period_day")
                    startPeriodType == PeriodType.WEEK -> s.shared("period_week")
                    startPeriodType == PeriodType.MONTH -> s.shared("period_month")
                    startPeriodType == PeriodType.YEAR -> s.shared("period_year")
                    else -> s.shared("period_custom")
                },
                onSelect = { selection ->
                    when (selection) {
                        s.shared("period_now") -> {
                            // NOW selected: clear period type and set isNow flag
                            onStartTypeChange(null)
                            onStartPeriodChange(null)
                            onStartCustomDateChange(null)
                            onStartIsNowChange(true)
                            if (returnRelative) {
                                onStartRelativePeriodChange?.invoke(null)
                            }
                        }
                        s.shared("period_custom") -> {
                            // Custom selected: clear period type and isNow flag
                            onStartTypeChange(null)
                            onStartPeriodChange(null)
                            onStartIsNowChange(false)
                            if (returnRelative) {
                                onStartRelativePeriodChange?.invoke(null)
                            }
                        }
                        else -> {
                            // Period type selected
                            val newType = when(selection) {
                                s.shared("period_hour") -> PeriodType.HOUR
                                s.shared("period_day") -> PeriodType.DAY
                                s.shared("period_week") -> PeriodType.WEEK
                                s.shared("period_month") -> PeriodType.MONTH
                                s.shared("period_year") -> PeriodType.YEAR
                                else -> null
                            }

                            onStartTypeChange(newType)
                            onStartIsNowChange(false)

                            // Create default period when type changes
                            if (newType != null && !isConfigLoading) {
                                if (returnRelative) {
                                    val newRelativePeriod = RelativePeriod.now(newType)
                                    onStartRelativePeriodChange?.invoke(newRelativePeriod)
                                } else {
                                    val now = System.currentTimeMillis()
                                    val normalizedTimestamp = normalizeTimestampWithConfig(now, newType)
                                    val newPeriod = Period(normalizedTimestamp, newType)
                                    onStartPeriodChange(newPeriod)
                                }
                            } else {
                                if (returnRelative) {
                                    onStartRelativePeriodChange?.invoke(null)
                                } else {
                                    onStartPeriodChange(null)
                                }
                            }
                        }
                    }
                }
            )

            // Start period selector, NOW label, or custom date
            when {
                startIsNow -> {
                    // NOW: display static label
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        UI.Text(
                            text = s.shared("period_now_label"),
                            type = TextType.BODY
                        )
                    }
                }
                startPeriodType != null && startPeriod != null -> {
                    // Period type: display selector with navigation
                    SinglePeriodSelector(
                        period = startPeriod,
                        onPeriodChange = { period ->
                            if (returnRelative) {
                                val offset = calculatePeriodOffset(period.timestamp, period.type)
                                val relativePeriod = RelativePeriod(offset, period.type)
                                onStartRelativePeriodChange?.invoke(relativePeriod)
                            } else {
                                onStartPeriodChange(period)
                            }
                        },
                        showDatePicker = true,
                        useOnlyRelativeLabels = useOnlyRelativeLabels
                    )
                }
                else -> {
                    // Custom date picker
                    var showDatePicker by remember { mutableStateOf(false) }

                    UI.Button(
                        type = ButtonType.DEFAULT,
                        size = Size.M,
                        onClick = { showDatePicker = true }
                    ) {
                        UI.Text(
                            text = startCustomDate?.let { DateUtils.formatDateForDisplay(it) } ?: s.shared("select_date"),
                            type = TextType.BODY,
                            fillMaxWidth = true,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (showDatePicker) {
                        UI.DatePicker(
                            selectedDate = startCustomDate?.let { DateUtils.formatDateForDisplay(it) } ?: "",
                            onDateSelected = { dateString ->
                                val timestamp = DateUtils.parseDateForFilter(dateString)
                                onStartCustomDateChange(timestamp)
                            },
                            onDismiss = { showDatePicker = false }
                        )
                    }
                }
            }
        }

        // End section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(
                text = s.shared("range_end") + ":",
                type = TextType.SUBTITLE
            )

            // End period type dropdown
            UI.FormSelection(
                label = "",
                options = listOf(
                    s.shared("period_hour"),
                    s.shared("period_day"),
                    s.shared("period_week"),
                    s.shared("period_month"),
                    s.shared("period_year"),
                    s.shared("period_now"),
                    s.shared("period_custom")
                ),
                selected = when {
                    endIsNow -> s.shared("period_now")
                    endPeriodType == PeriodType.HOUR -> s.shared("period_hour")
                    endPeriodType == PeriodType.DAY -> s.shared("period_day")
                    endPeriodType == PeriodType.WEEK -> s.shared("period_week")
                    endPeriodType == PeriodType.MONTH -> s.shared("period_month")
                    endPeriodType == PeriodType.YEAR -> s.shared("period_year")
                    else -> s.shared("period_custom")
                },
                onSelect = { selection ->
                    when (selection) {
                        s.shared("period_now") -> {
                            // NOW selected: clear period type and set isNow flag
                            onEndTypeChange(null)
                            onEndPeriodChange(null)
                            onEndCustomDateChange(null)
                            onEndIsNowChange(true)
                            if (returnRelative) {
                                onEndRelativePeriodChange?.invoke(null)
                            }
                        }
                        s.shared("period_custom") -> {
                            // Custom selected: clear period type and isNow flag
                            onEndTypeChange(null)
                            onEndPeriodChange(null)
                            onEndIsNowChange(false)
                            if (returnRelative) {
                                onEndRelativePeriodChange?.invoke(null)
                            }
                        }
                        else -> {
                            // Period type selected
                            val newType = when(selection) {
                                s.shared("period_hour") -> PeriodType.HOUR
                                s.shared("period_day") -> PeriodType.DAY
                                s.shared("period_week") -> PeriodType.WEEK
                                s.shared("period_month") -> PeriodType.MONTH
                                s.shared("period_year") -> PeriodType.YEAR
                                else -> null
                            }

                            onEndTypeChange(newType)
                            onEndIsNowChange(false)

                            // Create default period when type changes
                            if (newType != null && !isConfigLoading) {
                                if (returnRelative) {
                                    val newRelativePeriod = RelativePeriod.now(newType)
                                    onEndRelativePeriodChange?.invoke(newRelativePeriod)
                                } else {
                                    val now = System.currentTimeMillis()
                                    val normalizedTimestamp = normalizeTimestampWithConfig(now, newType)
                                    val newPeriod = Period(normalizedTimestamp, newType)
                                    onEndPeriodChange(newPeriod)
                                }
                            } else {
                                if (returnRelative) {
                                    onEndRelativePeriodChange?.invoke(null)
                                } else {
                                    onEndPeriodChange(null)
                                }
                            }
                        }
                    }
                }
            )

            // End period selector, NOW label, or custom date
            when {
                endIsNow -> {
                    // NOW: display static label
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        UI.Text(
                            text = s.shared("period_now_label"),
                            type = TextType.BODY
                        )
                    }
                }
                endPeriodType != null && endPeriod != null -> {
                    // Period type: display selector with navigation
                    SinglePeriodSelector(
                        period = endPeriod,
                        onPeriodChange = { period ->
                            if (returnRelative) {
                                val offset = calculatePeriodOffset(period.timestamp, period.type)
                                val relativePeriod = RelativePeriod(offset, period.type)
                                onEndRelativePeriodChange?.invoke(relativePeriod)
                            } else {
                                onEndPeriodChange(period)
                            }
                        },
                        showDatePicker = true,
                        useOnlyRelativeLabels = useOnlyRelativeLabels
                    )
                }
                else -> {
                    // Custom date picker
                    var showDatePicker by remember { mutableStateOf(false) }

                    UI.Button(
                        type = ButtonType.DEFAULT,
                        size = Size.M,
                        onClick = { showDatePicker = true }
                    ) {
                        UI.Text(
                            text = endCustomDate?.let { DateUtils.formatDateForDisplay(it) } ?: s.shared("select_date"),
                            type = TextType.BODY,
                            fillMaxWidth = true,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (showDatePicker) {
                        UI.DatePicker(
                            selectedDate = endCustomDate?.let { DateUtils.formatDateForDisplay(it) } ?: "",
                            onDateSelected = { dateString ->
                                val timestamp = DateUtils.parseDateForFilter(dateString)
                                onEndCustomDateChange(timestamp)
                            },
                            onDismiss = { showDatePicker = false }
                        )
                    }
                }
            }
        }
    }
}

