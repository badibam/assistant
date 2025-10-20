package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.ScheduleConfig
import com.assistant.core.utils.SchedulePattern
import com.assistant.core.utils.WeekMoment
import com.assistant.core.utils.YearlyDate
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reusable dialog for configuring schedule patterns
 *
 * Supports 6 pattern types:
 * - DailyMultiple: Multiple times per day
 * - WeeklySimple: Specific days with same time
 * - MonthlyRecurrent: Specific months and day
 * - WeeklyCustom: Different times for different days
 * - YearlyRecurrent: Recurring yearly dates
 * - SpecificDates: One-shot specific dates
 *
 * Usage:
 * - Automation schedules (primary use case)
 * - Alerts, reminders, any recurring tasks
 */
@Composable
fun ScheduleConfigEditor(
    existingConfig: ScheduleConfig?,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleConfig?) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scrollState = rememberScrollState()

    // Pattern selection (with "None" option)
    val patternTypes = listOf(
        "None",
        "DailyMultiple",
        "WeeklySimple",
        "MonthlyRecurrent",
        "WeeklyCustom",
        "YearlyRecurrent",
        "SpecificDates"
    )

    var selectedPatternType by remember {
        mutableStateOf(
            existingConfig?.pattern?.let { getPatternTypeName(it) } ?: "None"
        )
    }

    // Pattern-specific states
    // DailyMultiple
    var dailyTimes by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.DailyMultiple)?.times ?: listOf("09:00")
        )
    }

    // WeeklySimple
    var weeklyDays by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.WeeklySimple)?.daysOfWeek ?: listOf(1)
        )
    }
    var weeklyTime by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.WeeklySimple)?.time ?: "09:00"
        )
    }

    // MonthlyRecurrent
    var monthlyMonths by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.MonthlyRecurrent)?.months ?: listOf(1)
        )
    }
    var monthlyDay by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.MonthlyRecurrent)?.dayOfMonth ?: 1
        )
    }
    var monthlyTime by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.MonthlyRecurrent)?.time ?: "09:00"
        )
    }

    // WeeklyCustom
    var weeklyCustomMoments by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.WeeklyCustom)?.moments
                ?: listOf(WeekMoment(1, "09:00"))
        )
    }

    // YearlyRecurrent
    var yearlyDates by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.YearlyRecurrent)?.dates
                ?: listOf(YearlyDate(1, 1, "09:00"))
        )
    }

    // SpecificDates
    var specificTimestamps by remember {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.SpecificDates)?.timestamps
                ?: listOf(System.currentTimeMillis())
        )
    }

    // Common config fields
    var timezone by remember { mutableStateOf(existingConfig?.timezone ?: "Europe/Paris") }

    UI.Dialog(
        type = DialogType.CONFIGURE,
        onConfirm = {
            val pattern = if (selectedPatternType == "None") {
                null
            } else {
                buildSchedulePattern(
                patternType = selectedPatternType,
                dailyTimes = dailyTimes,
                weeklyDays = weeklyDays,
                weeklyTime = weeklyTime,
                monthlyMonths = monthlyMonths,
                monthlyDay = monthlyDay,
                monthlyTime = monthlyTime,
                    weeklyCustomMoments = weeklyCustomMoments,
                    yearlyDates = yearlyDates,
                    specificTimestamps = specificTimestamps
                )
            }

            val scheduleConfig = if (pattern == null) {
                null
            } else {
                ScheduleConfig(
                    pattern = pattern,
                    timezone = timezone,
                    enabled = true, // Always enabled if schedule is configured
                    startDate = existingConfig?.startDate ?: System.currentTimeMillis(), // Initialize to now on creation, preserve on update
                    endDate = null,
                    nextExecutionTime = null // Will be calculated by ScheduleCalculator
                )
            }

            onConfirm(scheduleConfig)
        },
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            UI.Text(
                text = s.shared("schedule_config_title"),
                type = TextType.TITLE
            )

            // Pattern selector
            UI.FormSelection(
                label = s.shared("schedule_pattern_label"),
                options = patternTypes.map { s.shared("schedule_pattern_$it") },
                selected = s.shared("schedule_pattern_$selectedPatternType"),
                onSelect = { selectedLabel ->
                    selectedPatternType = patternTypes[patternTypes.map { s.shared("schedule_pattern_$it") }.indexOf(selectedLabel)]
                }
            )

            // Pattern-specific UI
            when (selectedPatternType) {
                "None" -> {
                    UI.Card(type = CardType.DEFAULT) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            UI.Text(
                                text = s.shared("schedule_no_schedule_message"),
                                type = TextType.BODY
                            )
                        }
                    }
                }
                "DailyMultiple" -> DailyMultipleEditor(
                    times = dailyTimes,
                    onTimesChange = { dailyTimes = it }
                )
                "WeeklySimple" -> WeeklySimpleEditor(
                    days = weeklyDays,
                    time = weeklyTime,
                    onDaysChange = { weeklyDays = it },
                    onTimeChange = { weeklyTime = it }
                )
                "MonthlyRecurrent" -> MonthlyRecurrentEditor(
                    months = monthlyMonths,
                    dayOfMonth = monthlyDay,
                    time = monthlyTime,
                    onMonthsChange = { monthlyMonths = it },
                    onDayChange = { monthlyDay = it },
                    onTimeChange = { monthlyTime = it }
                )
                "WeeklyCustom" -> WeeklyCustomEditor(
                    moments = weeklyCustomMoments,
                    onMomentsChange = { weeklyCustomMoments = it }
                )
                "YearlyRecurrent" -> YearlyRecurrentEditor(
                    dates = yearlyDates,
                    onDatesChange = { yearlyDates = it }
                )
                "SpecificDates" -> SpecificDatesEditor(
                    timestamps = specificTimestamps,
                    onTimestampsChange = { specificTimestamps = it }
                )
            }

            // Preview label (only if not "None")
            if (selectedPatternType != "None") {
                val previewLabel = generatePreviewLabel(
                    context = context,
                    patternType = selectedPatternType,
                    dailyTimes = dailyTimes,
                    weeklyDays = weeklyDays,
                    weeklyTime = weeklyTime,
                    monthlyMonths = monthlyMonths,
                    monthlyDay = monthlyDay,
                    monthlyTime = monthlyTime,
                    weeklyCustomMoments = weeklyCustomMoments,
                    yearlyDates = yearlyDates,
                    specificTimestamps = specificTimestamps
                )

                UI.Card(type = CardType.DEFAULT) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        UI.Text(
                            text = s.shared("schedule_preview_label"),
                            type = TextType.LABEL
                        )
                        UI.Text(
                            text = previewLabel,
                            type = TextType.BODY
                        )
                    }
                }
            }
        }
    }
}

/**
 * DailyMultiple pattern editor
 * Multiple times per day (e.g., "09:00", "14:00", "18:00")
 */
@Composable
private fun DailyMultipleEditor(
    times: List<String>,
    onTimesChange: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("schedule_daily_times_label"),
            type = TextType.SUBTITLE
        )

        times.forEachIndexed { index, time ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TimePickerField(
                        label = s.shared("schedule_time_label").format(index + 1),
                        value = time,
                        onChange = { newTime ->
                            onTimesChange(times.toMutableList().apply { set(index, newTime) })
                        }
                    )
                }

                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = {
                        if (times.size > 1) {
                            onTimesChange(times.filterIndexed { i, _ -> i != index })
                        }
                    }
                )
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onTimesChange(times + "09:00")
            }
        )
    }
}

/**
 * WeeklySimple pattern editor
 * Specific days with same time (e.g., Monday, Wednesday, Friday at 9:00)
 */
@Composable
private fun WeeklySimpleEditor(
    days: List<Int>,
    time: String,
    onDaysChange: (List<Int>) -> Unit,
    onTimeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("schedule_weekly_days_label"),
            type = TextType.SUBTITLE
        )

        // Day checkboxes (1=Monday to 7=Sunday)
        val dayNames = (1..7).map { s.shared("day_of_week_$it") }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            dayNames.forEachIndexed { index, dayName ->
                val dayNumber = index + 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.ToggleField(
                        label = dayName,
                        checked = days.contains(dayNumber),
                        onCheckedChange = { checked ->
                            onDaysChange(
                                if (checked) {
                                    (days + dayNumber).sorted()
                                } else {
                                    days.filter { it != dayNumber }
                                }
                            )
                        }
                    )
                }
            }
        }

        // Time selector
        TimePickerField(
            label = s.shared("schedule_time_label_single"),
            value = time,
            onChange = onTimeChange
        )
    }
}

/**
 * MonthlyRecurrent pattern editor
 * Specific months and day (e.g., 15th of January, March, June at 10:00)
 */
@Composable
private fun MonthlyRecurrentEditor(
    months: List<Int>,
    dayOfMonth: Int,
    time: String,
    onMonthsChange: (List<Int>) -> Unit,
    onDayChange: (Int) -> Unit,
    onTimeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("schedule_monthly_config_label"),
            type = TextType.SUBTITLE
        )

        // Month checkboxes
        val monthNames = (1..12).map { s.shared("month_$it") }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            monthNames.forEachIndexed { index, monthName ->
                val monthNumber = index + 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.ToggleField(
                        label = monthName,
                        checked = months.contains(monthNumber),
                        onCheckedChange = { checked ->
                            onMonthsChange(
                                if (checked) {
                                    (months + monthNumber).sorted()
                                } else {
                                    months.filter { it != monthNumber }
                                }
                            )
                        }
                    )
                }
            }
        }

        // Day of month (1-31)
        UI.FormField(
            label = s.shared("schedule_day_of_month_label"),
            value = dayOfMonth.toString(),
            onChange = { newDay ->
                newDay.toIntOrNull()?.let { day ->
                    if (day in 1..31) onDayChange(day)
                }
            },
            fieldType = FieldType.NUMERIC
        )

        // Time
        TimePickerField(
            label = s.shared("schedule_time_label_single"),
            value = time,
            onChange = onTimeChange
        )
    }
}

/**
 * WeeklyCustom pattern editor
 * Different times for different days (e.g., Monday 9:00, Wednesday 14:00, Friday 17:00)
 */
@Composable
private fun WeeklyCustomEditor(
    moments: List<WeekMoment>,
    onMomentsChange: (List<WeekMoment>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("schedule_weekly_custom_label"),
            type = TextType.SUBTITLE
        )

        moments.forEachIndexed { index, moment ->
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Day selector
                    val dayNames = (1..7).map { s.shared("day_of_week_$it") }
                    UI.FormSelection(
                        label = s.shared("schedule_day_label"),
                        options = dayNames,
                        selected = dayNames[moment.dayOfWeek - 1],
                        onSelect = { selectedDay ->
                            val newDayOfWeek = dayNames.indexOf(selectedDay) + 1
                            onMomentsChange(
                                moments.toMutableList().apply {
                                    set(index, moment.copy(dayOfWeek = newDayOfWeek))
                                }
                            )
                        }
                    )

                    // Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TimePickerField(
                                label = s.shared("schedule_time_label_single"),
                                value = moment.time,
                                onChange = { newTime ->
                                    onMomentsChange(
                                        moments.toMutableList().apply {
                                            set(index, moment.copy(time = newTime))
                                        }
                                    )
                                }
                            )
                        }

                        UI.ActionButton(
                            action = ButtonAction.DELETE,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                if (moments.size > 1) {
                                    onMomentsChange(moments.filterIndexed { i, _ -> i != index })
                                }
                            }
                        )
                    }
                }
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onMomentsChange(moments + WeekMoment(1, "09:00"))
            }
        )
    }
}

/**
 * YearlyRecurrent pattern editor
 * Recurring yearly dates (e.g., January 1st and December 25th at 8:00)
 */
@Composable
private fun YearlyRecurrentEditor(
    dates: List<YearlyDate>,
    onDatesChange: (List<YearlyDate>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("schedule_yearly_recurrent_label"),
            type = TextType.SUBTITLE
        )

        dates.forEachIndexed { index, date ->
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Month selector
                    val monthNames = (1..12).map { s.shared("month_$it") }
                    UI.FormSelection(
                        label = s.shared("schedule_month_label"),
                        options = monthNames,
                        selected = monthNames[date.month - 1],
                        onSelect = { selectedMonth ->
                            val newMonth = monthNames.indexOf(selectedMonth) + 1
                            onDatesChange(
                                dates.toMutableList().apply {
                                    set(index, date.copy(month = newMonth))
                                }
                            )
                        }
                    )

                    // Day
                    UI.FormField(
                        label = s.shared("schedule_day_label"),
                        value = date.day.toString(),
                        onChange = { newDay ->
                            newDay.toIntOrNull()?.let { day ->
                                if (day in 1..31) {
                                    onDatesChange(
                                        dates.toMutableList().apply {
                                            set(index, date.copy(day = day))
                                        }
                                    )
                                }
                            }
                        },
                        fieldType = FieldType.NUMERIC
                    )

                    // Time + Delete button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TimePickerField(
                                label = s.shared("schedule_time_label_single"),
                                value = date.time,
                                onChange = { newTime ->
                                    onDatesChange(
                                        dates.toMutableList().apply {
                                            set(index, date.copy(time = newTime))
                                        }
                                    )
                                }
                            )
                        }

                        UI.ActionButton(
                            action = ButtonAction.DELETE,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                if (dates.size > 1) {
                                    onDatesChange(dates.filterIndexed { i, _ -> i != index })
                                }
                            }
                        )
                    }
                }
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onDatesChange(dates + YearlyDate(1, 1, "09:00"))
            }
        )
    }
}

/**
 * SpecificDates pattern editor
 * One-shot specific dates (e.g., March 15 2025 at 14:30, April 20 2025 at 10:00)
 */
@Composable
private fun SpecificDatesEditor(
    timestamps: List<Long>,
    onTimestampsChange: (List<Long>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text(
            text = s.shared("schedule_specific_dates_label"),
            type = TextType.SUBTITLE
        )

        timestamps.forEachIndexed { index, timestamp ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DateTimePickerField(
                        label = s.shared("schedule_date_label").format(index + 1),
                        timestamp = timestamp,
                        onChange = { newTimestamp ->
                            onTimestampsChange(timestamps.toMutableList().apply { set(index, newTimestamp) })
                        }
                    )
                }

                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = {
                        if (timestamps.size > 1) {
                            onTimestampsChange(timestamps.filterIndexed { i, _ -> i != index })
                        }
                    }
                )
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onTimestampsChange(timestamps + System.currentTimeMillis())
            }
        )
    }
}

/**
 * Time picker field - clickable field that opens a time picker dialog
 */
@Composable
private fun TimePickerField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    UI.FormField(
        label = label,
        value = value,
        onChange = { },
        fieldType = FieldType.TEXT,
        readonly = true,
        onClick = { showPicker = true }
    )

    if (showPicker) {
        UI.TimePicker(
            selectedTime = value,
            onTimeSelected = { newTime ->
                onChange(newTime)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Date+Time picker field - clickable field that opens date then time picker dialogs
 */
@Composable
private fun DateTimePickerField(
    label: String,
    timestamp: Long,
    onChange: (Long) -> Unit
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var tempDate by remember { mutableStateOf("") }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeOnlyFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val formattedValue = dateFormatter.format(Date(timestamp))

    UI.FormField(
        label = label,
        value = formattedValue,
        onChange = { },
        fieldType = FieldType.TEXT,
        readonly = true,
        onClick = { showDatePicker = true }
    )

    if (showDatePicker) {
        UI.DatePicker(
            selectedDate = dateOnlyFormatter.format(Date(timestamp)),
            onDateSelected = { dateStr ->
                tempDate = dateStr
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        UI.TimePicker(
            selectedTime = timeOnlyFormatter.format(Date(timestamp)),
            onTimeSelected = { timeStr ->
                try {
                    // Combine date and time
                    val dateTimeStr = "$tempDate $timeStr"
                    val dateTimeFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val newTimestamp = dateTimeFormatter.parse(dateTimeStr)?.time ?: timestamp
                    onChange(newTimestamp)
                } catch (e: Exception) {
                    // Keep original timestamp on parse error
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

/**
 * Get pattern type name from SchedulePattern instance
 */
private fun getPatternTypeName(pattern: SchedulePattern?): String {
    return when (pattern) {
        null -> "None"
        is SchedulePattern.DailyMultiple -> "DailyMultiple"
        is SchedulePattern.WeeklySimple -> "WeeklySimple"
        is SchedulePattern.MonthlyRecurrent -> "MonthlyRecurrent"
        is SchedulePattern.WeeklyCustom -> "WeeklyCustom"
        is SchedulePattern.YearlyRecurrent -> "YearlyRecurrent"
        is SchedulePattern.SpecificDates -> "SpecificDates"
    }
}

/**
 * Build SchedulePattern from editor state
 */
private fun buildSchedulePattern(
    patternType: String,
    dailyTimes: List<String>,
    weeklyDays: List<Int>,
    weeklyTime: String,
    monthlyMonths: List<Int>,
    monthlyDay: Int,
    monthlyTime: String,
    weeklyCustomMoments: List<WeekMoment>,
    yearlyDates: List<YearlyDate>,
    specificTimestamps: List<Long>
): SchedulePattern {
    return when (patternType) {
        "DailyMultiple" -> SchedulePattern.DailyMultiple(dailyTimes)
        "WeeklySimple" -> SchedulePattern.WeeklySimple(weeklyDays, weeklyTime)
        "MonthlyRecurrent" -> SchedulePattern.MonthlyRecurrent(monthlyMonths, monthlyDay, monthlyTime)
        "WeeklyCustom" -> SchedulePattern.WeeklyCustom(weeklyCustomMoments)
        "YearlyRecurrent" -> SchedulePattern.YearlyRecurrent(yearlyDates)
        "SpecificDates" -> SchedulePattern.SpecificDates(specificTimestamps)
        else -> SchedulePattern.DailyMultiple(listOf("09:00")) // Fallback
    }
}

/**
 * Generate human-readable preview label
 */
private fun generatePreviewLabel(
    context: android.content.Context,
    patternType: String,
    dailyTimes: List<String>,
    weeklyDays: List<Int>,
    weeklyTime: String,
    monthlyMonths: List<Int>,
    monthlyDay: Int,
    monthlyTime: String,
    weeklyCustomMoments: List<WeekMoment>,
    yearlyDates: List<YearlyDate>,
    specificTimestamps: List<Long>
): String {
    val s = Strings.`for`(context = context)

    return when (patternType) {
        "DailyMultiple" -> {
            s.shared("schedule_preview_daily").format(dailyTimes.joinToString(", "))
        }
        "WeeklySimple" -> {
            val dayNames = weeklyDays.map { s.shared("day_of_week_$it") }.joinToString(", ")
            s.shared("schedule_preview_weekly_simple").format(dayNames, weeklyTime)
        }
        "MonthlyRecurrent" -> {
            val monthNames = monthlyMonths.map { s.shared("month_$it") }.joinToString(", ")
            s.shared("schedule_preview_monthly").format(monthlyDay, monthNames, monthlyTime)
        }
        "WeeklyCustom" -> {
            val momentsText = weeklyCustomMoments.joinToString(", ") { moment ->
                "${s.shared("day_of_week_${moment.dayOfWeek}")} ${moment.time}"
            }
            s.shared("schedule_preview_weekly_custom").format(momentsText)
        }
        "YearlyRecurrent" -> {
            val datesText = yearlyDates.joinToString(", ") { date ->
                "${date.day} ${s.shared("month_${date.month}")}"
            }
            s.shared("schedule_preview_yearly").format(datesText)
        }
        "SpecificDates" -> {
            val count = specificTimestamps.size
            s.shared("schedule_preview_specific").format(count)
        }
        else -> s.shared("schedule_preview_unknown")
    }
}
