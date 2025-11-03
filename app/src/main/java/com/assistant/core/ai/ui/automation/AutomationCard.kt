package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.Automation
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.SchedulePattern
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * AutomationCard - Display automation in zone list
 *
 * Shows:
 * - Name + Enabled status
 * - Trigger type (manual/schedule/triggers/hybrid)
 * - Next execution time (if scheduled)
 * - Queued status badge (if in queue)
 * - Actions: Test + Edit + Toggle enabled + Cancel (if queued)
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

    // Observe queued sessions to detect if this automation is queued
    val queuedSessions by AIOrchestrator.queuedSessions.collectAsState()

    // Need to load session from DB to check automationId (QueuedSession only has sessionId)
    var queuedAutomationSession by remember { mutableStateOf<com.assistant.core.ai.scheduling.QueuedSession?>(null) }

    LaunchedEffect(queuedSessions, automation.id) {
        // Find queued AUTOMATION sessions and check if one belongs to this automation
        val coordinator = com.assistant.core.coordinator.Coordinator(context)

        for (queued in queuedSessions) {
            if (queued.sessionType == SessionType.AUTOMATION) {
                // Load session from DB to get automationId
                val result = coordinator.processUserAction("ai_sessions.get", mapOf("id" to queued.sessionId))
                if (result.status == com.assistant.core.commands.CommandStatus.SUCCESS) {
                    val sessionAutomationId = result.data?.get("automationId") as? String
                    if (sessionAutomationId == automation.id) {
                        queuedAutomationSession = queued
                        break
                    }
                }
            }
        }

        // Reset if not found
        if (queuedSessions.none { it.sessionId == queuedAutomationSession?.sessionId }) {
            queuedAutomationSession = null
        }
    }

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

            // Queued badge (if automation is in queue)
            queuedAutomationSession?.let { queued ->
                // Calculate position in queue (1-indexed for display)
                val position = queuedSessions.indexOfFirst { it.sessionId == queued.sessionId } + 1
                UI.Text(
                    text = s.shared("ai_automation_queued_badge").format(position),
                    type = TextType.CAPTION
                )
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel execution button (if queued)
                if (queuedAutomationSession != null) {
                    val scope = rememberCoroutineScope()
                    UI.Button(
                        type = ButtonType.DANGER,
                        size = Size.S,
                        onClick = {
                            scope.launch {
                                AIOrchestrator.cancelQueuedSession(queuedAutomationSession!!.sessionId)
                            }
                        }
                    ) {
                        UI.Text(
                            text = s.shared("ai_automation_cancel_execution"),
                            type = TextType.BODY
                        )
                    }
                } else {
                    // Test button (manual execution) - only shown if not queued
                    UI.ActionButton(
                        action = ButtonAction.START,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = onTest
                    )
                }

                // Edit button
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onEdit
                )
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
