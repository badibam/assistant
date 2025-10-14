package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.Automation
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.SchedulePattern
import java.text.SimpleDateFormat
import java.util.*

/**
 * AutomationCard - Display automation in zone list
 *
 * Shows:
 * - Name + Enabled status
 * - Trigger type (manual/schedule/triggers/hybrid)
 * - Next execution time (if scheduled)
 * - Actions: Test + Edit + Toggle enabled
 *
 * Usage: In ZoneScreen automation section
 */
@Composable
fun AutomationCard(
    automation: Automation,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    UI.Card(
        type = CardType.DEFAULT
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: Name + Enabled toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(
                        text = automation.name,
                        type = TextType.SUBTITLE
                    )
                }

                // Enabled toggle
                UI.ToggleField(
                    label = "",
                    checked = automation.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            // Trigger type and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator text
                val statusText = when {
                    !automation.isEnabled -> s.shared("label_disabled")
                    automation.schedule == null && automation.triggerIds.isEmpty() -> s.shared("automation_status_manual")
                    automation.schedule != null && automation.triggerIds.isNotEmpty() -> {
                        // Hybrid: schedule + triggers
                        val triggerCount = automation.triggerIds.size
                        "${getScheduleLabel(context, automation.schedule!!.pattern)} + ${s.shared("automation_triggers_count").format(triggerCount)}"
                    }
                    automation.schedule != null -> getScheduleLabel(context, automation.schedule!!.pattern)
                    else -> s.shared("automation_triggers_count").format(automation.triggerIds.size)
                }

                UI.Text(
                    text = statusText,
                    type = TextType.CAPTION
                )
            }

            // Next execution time (if scheduled and enabled)
            automation.schedule?.nextExecutionTime?.let { nextExecution ->
                if (automation.isEnabled && nextExecution > System.currentTimeMillis()) {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val formattedDate = dateFormat.format(Date(nextExecution))

                    UI.Text(
                        text = s.shared("automation_next_execution").format(formattedDate),
                        type = TextType.CAPTION
                    )
                }
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Test button (manual execution)
                Box(modifier = Modifier.weight(1f)) {
                    UI.Button(
                        type = ButtonType.DEFAULT,
                        size = Size.S,
                        onClick = onTest
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UI.Text(text = "▶️", type = TextType.CAPTION)
                            UI.Text(text = s.shared("automation_execute_now"), type = TextType.CAPTION)
                        }
                    }
                }

                // Edit button
                Box(modifier = Modifier.weight(1f)) {
                    UI.ActionButton(
                        action = ButtonAction.EDIT,
                        display = ButtonDisplay.LABEL,
                        size = Size.S,
                        onClick = onEdit
                    )
                }
            }
        }
    }
}

/**
 * Generate human-readable schedule label for card display
 * Compact format for small space
 */
private fun getScheduleLabel(context: android.content.Context, pattern: SchedulePattern): String {
    val s = Strings.`for`(context = context)

    return when (pattern) {
        is SchedulePattern.DailyMultiple -> {
            if (pattern.times.size == 1) {
                s.shared("automation_schedule_daily").format(pattern.times[0])
            } else {
                s.shared("automation_schedule_daily").format("${pattern.times.size}x")
            }
        }
        is SchedulePattern.WeeklySimple -> {
            val days = pattern.daysOfWeek.joinToString("/") { day ->
                s.shared("day_of_week_short_$day")
            }
            s.shared("automation_schedule_weekly").format(days, pattern.time)
        }
        is SchedulePattern.MonthlyRecurrent -> {
            val months = pattern.months.joinToString("/") { month ->
                s.shared("month_short_$month")
            }
            s.shared("automation_schedule_monthly").format(pattern.dayOfMonth, months, pattern.time)
        }
        is SchedulePattern.WeeklyCustom -> {
            s.shared("automation_schedule_weekly_custom").format(pattern.moments.size)
        }
        is SchedulePattern.YearlyRecurrent -> {
            s.shared("automation_schedule_yearly").format(pattern.dates.size)
        }
        is SchedulePattern.SpecificDates -> {
            s.shared("automation_schedule_specific").format(pattern.timestamps.size)
        }
    }
}
