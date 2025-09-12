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
 * Data class representing a period
 */
data class Period(
    val timestamp: Long,
    val type: PeriodType
) {
    companion object {
        fun now(type: PeriodType): Period = Period(normalizeTimestamp(System.currentTimeMillis(), type), type)
        
        /**
         * Normalizes a timestamp according to period type (basic version without config)
         */
        fun normalizeTimestamp(timestamp: Long, type: PeriodType): Long {
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
                    // Start of day (00:00:00.000)
                    cal.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                PeriodType.WEEK -> {
                    // Start of week (Monday 00:00:00.000)
                    // TODO: Use AppConfigService.getWeekStartDay()
                    cal.apply {
                        firstDayOfWeek = Calendar.MONDAY
                        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                PeriodType.MONTH -> {
                    // First day of month (01/XX/XXXX 00:00:00.000)
                    cal.apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                PeriodType.YEAR -> {
                    // First day of year (01/01/XXXX 00:00:00.000)
                    cal.apply {
                        set(Calendar.MONTH, Calendar.JANUARY)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
            }
        }
    }
}

/**
 * Normalizes a timestamp according to period type with configuration parameters
 */
fun normalizeTimestampWithConfig(timestamp: Long, type: PeriodType, dayStartHour: Int, weekStartDay: String): Long {
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
                set(Calendar.HOUR_OF_DAY, dayStartHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}

/**
 * Generic period selection component with smart navigation
 * Displays relative labels (Today, This week, etc.) with navigation arrows
 * and ability to click to open date selector
 */
@Composable
fun PeriodSelector(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
    showDatePicker: Boolean = true,
    dayStartHour: Int = 0,
    weekStartDay: String = "monday"
) {
    
    // State for date selector
    var showPicker by remember { mutableStateOf(false) }
    
    
    // Smart label generation
    val label = remember(period, dayStartHour, weekStartDay) {
        generatePeriodLabel(period, dayStartHour, weekStartDay)
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
                val previousPeriod = getPreviousPeriod(period, dayStartHour, weekStartDay)
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
                val nextPeriod = getNextPeriod(period, dayStartHour, weekStartDay)
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
                        val normalizedTimestamp = normalizeTimestampWithConfig(combinedTimestamp, period.type, dayStartHour, weekStartDay)
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
                        val newTimestamp = normalizeTimestampWithConfig(DateUtils.parseDateForFilter(selectedDate), period.type, dayStartHour, weekStartDay)
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
 */
private fun generatePeriodLabel(period: Period, dayStartHour: Int, weekStartDay: String): String {
    val now = System.currentTimeMillis()
    
    return when (period.type) {
        PeriodType.HOUR -> generateHourLabel(period.timestamp, now)
        PeriodType.DAY -> generateDayLabel(period.timestamp, now, dayStartHour)
        PeriodType.WEEK -> generateWeekLabel(period.timestamp, now, weekStartDay)
        PeriodType.MONTH -> generateMonthLabel(period.timestamp, now)
        PeriodType.YEAR -> generateYearLabel(period.timestamp, now)
    }
}

/**
 * Labels for hours
 */
private fun generateHourLabel(timestamp: Long, now: Long): String {
    // Normalize both timestamps to compare hours correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.HOUR, 0, "monday")
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.HOUR, 0, "monday")
    
    val diffHours = ((normalizedNow - normalizedTimestamp) / (60 * 60 * 1000)).toInt()
    
    return when {
        diffHours == 0 -> "This hour"
        diffHours > 0 && diffHours <= 12 -> "$diffHours hour${if (diffHours > 1) "s" else ""} ago"
        diffHours < 0 && diffHours >= -12 -> "In ${-diffHours} hour${if (-diffHours > 1) "s" else ""}"
        else -> {
            val date = DateUtils.formatDateForDisplay(timestamp)
            val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
            "$date ${hour}h"
        }
    }
}

/**
 * Labels for days
 */
private fun generateDayLabel(timestamp: Long, now: Long, dayStartHour: Int): String {
    // Normalize timestamps to compare them according to dayStartHour
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.DAY, dayStartHour, "monday")
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.DAY, dayStartHour, "monday")
    
    val diffDays = ((normalizedNow - normalizedTimestamp) / (24 * 60 * 60 * 1000)).toInt()
    
    return when {
        diffDays == 0 -> "Today"
        diffDays == 1 -> "Yesterday"
        diffDays == -1 -> "Tomorrow"
        diffDays > 0 && diffDays <= 7 -> "$diffDays day${if (diffDays > 1) "s" else ""} ago"
        diffDays < 0 && diffDays >= -7 -> "In ${-diffDays} day${if (-diffDays > 1) "s" else ""}"
        else -> DateUtils.formatDateForDisplay(timestamp)
    }
}

/**
 * Labels for weeks
 */
private fun generateWeekLabel(timestamp: Long, now: Long, weekStartDay: String): String {
    // Normalize timestamps to compare weeks correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.WEEK, 4, weekStartDay)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.WEEK, 4, weekStartDay)
    
    val diffWeeks = ((normalizedNow - normalizedTimestamp) / (7 * 24 * 60 * 60 * 1000)).toInt()
    
    return when {
        diffWeeks == 0 -> "This week"
        diffWeeks == 1 -> "Last week"
        diffWeeks == -1 -> "Next week"
        diffWeeks > 0 && diffWeeks <= 4 -> "$diffWeeks week${if (diffWeeks > 1) "s" else ""} ago"
        diffWeeks < 0 && diffWeeks >= -4 -> "In ${-diffWeeks} week${if (-diffWeeks > 1) "s" else ""}"
        else -> {
            val weekStart = getWeekStart(timestamp, weekStartDay)
            "Week of ${DateUtils.formatDateForDisplay(weekStart)}"
        }
    }
}

/**
 * Labels for months
 */
private fun generateMonthLabel(timestamp: Long, now: Long): String {
    // Normalize timestamps to compare months correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.MONTH, 4, "monday")
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.MONTH, 4, "monday")
    
    val nowCal = Calendar.getInstance().apply { timeInMillis = normalizedNow }
    val tsCal = Calendar.getInstance().apply { timeInMillis = normalizedTimestamp }
    
    val diffMonths = (nowCal.get(Calendar.YEAR) - tsCal.get(Calendar.YEAR)) * 12 + 
                    (nowCal.get(Calendar.MONTH) - tsCal.get(Calendar.MONTH))
    
    return when {
        diffMonths == 0 -> "This month"
        diffMonths == 1 -> "Last month"
        diffMonths == -1 -> "Next month"
        diffMonths > 0 && diffMonths <= 6 -> "$diffMonths months ago"
        diffMonths < 0 && diffMonths >= -6 -> "In ${-diffMonths} months"
        else -> {
            val monthNames = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val month = monthNames[tsCal.get(Calendar.MONTH)]
            val year = tsCal.get(Calendar.YEAR)
            "$month $year"
        }
    }
}

/**
 * Labels for years
 */
private fun generateYearLabel(timestamp: Long, now: Long): String {
    // Normalize timestamps to compare years correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.YEAR, 4, "monday")
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.YEAR, 4, "monday")
    
    val nowYear = Calendar.getInstance().apply { timeInMillis = normalizedNow }.get(Calendar.YEAR)
    val tsYear = Calendar.getInstance().apply { timeInMillis = normalizedTimestamp }.get(Calendar.YEAR)
    val diffYears = nowYear - tsYear
    
    return when {
        diffYears == 0 -> "This year"
        diffYears == 1 -> "Last year"
        diffYears == -1 -> "Next year"
        diffYears > 0 && diffYears <= 3 -> "$diffYears year${if (diffYears > 1) "s" else ""} ago"
        diffYears < 0 && diffYears >= -3 -> "In ${-diffYears} year${if (-diffYears > 1) "s" else ""}"
        else -> tsYear.toString()
    }
}

/**
 * Calculates previous period with normalization
 */
private fun getPreviousPeriod(period: Period, dayStartHour: Int, weekStartDay: String): Period {
    val cal = Calendar.getInstance().apply { timeInMillis = period.timestamp }
    
    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, -1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, -1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, -1)
    }
    
    // Normalize timestamp after calculation with configuration parameters
    return Period(normalizeTimestampWithConfig(cal.timeInMillis, period.type, dayStartHour, weekStartDay), period.type)
}

/**
 * Calculates next period with normalization
 */
private fun getNextPeriod(period: Period, dayStartHour: Int, weekStartDay: String): Period {
    val cal = Calendar.getInstance().apply { timeInMillis = period.timestamp }
    
    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, 1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, 1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, 1)
    }
    
    // Normalize timestamp after calculation with configuration parameters
    return Period(normalizeTimestampWithConfig(cal.timeInMillis, period.type, dayStartHour, weekStartDay), period.type)
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