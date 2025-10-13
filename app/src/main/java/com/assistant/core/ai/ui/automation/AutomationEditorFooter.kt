package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.data.Automation
import com.assistant.core.ai.data.MessageSegment
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.ui.components.RichComposer
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.ScheduleConfig

/**
 * Footer component for SEED automation editor
 *
 * Architecture:
 * - RichComposer (without send button) for message composition + enrichments
 * - Configuration buttons for schedule and triggers
 * - FormActions (Save/Cancel/Test) for global save
 *
 * Usage:
 * - In AIScreen SeedMode for editing automation template message
 * - Supports enrichments (periods are stored as relative for AUTOMATION)
 */
@Composable
fun AutomationEditorFooter(
    automation: Automation?,  // null if creating new automation
    segments: List<MessageSegment>,
    onSegmentsChange: (List<MessageSegment>) -> Unit,
    scheduleConfig: ScheduleConfig?,
    onConfigureSchedule: () -> Unit,
    triggersCount: Int,
    onConfigureTriggers: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onTest: (() -> Unit)? = null  // Non-null if editing existing automation
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Message composer with enrichments
        // sessionType = SEED because this is a SEED session template
        // But periods will be stored as relative (handled by RichComposer internally)
        UI.RichComposer(
            segments = segments,
            onSegmentsChange = onSegmentsChange,
            onSend = { /* Not used - showSendButton = false */ },
            placeholder = s.shared("automation_message_placeholder"),
            showEnrichmentButtons = true,
            showSendButton = false,  // Hide send button - save is in FormActions
            sessionType = SessionType.SEED  // SEED session for template
        )

        // Configuration buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Schedule configuration button
            Box(modifier = Modifier.weight(1f)) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = onConfigureSchedule
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UI.Text(
                            text = "⏰", // Clock icon
                            type = TextType.BODY
                        )
                        UI.Text(
                            text = scheduleConfig?.let {
                                generateScheduleLabel(context, it)
                            } ?: s.shared("automation_schedule_not_configured"),
                            type = TextType.BODY
                        )
                    }
                }
            }

            // Triggers configuration button
            Box(modifier = Modifier.weight(1f)) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = onConfigureTriggers
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UI.Text(
                            text = "⚡", // Lightning icon for triggers
                            type = TextType.BODY
                        )
                        UI.Text(
                            text = if (triggersCount > 0) {
                                s.shared("automation_triggers_count").format(triggersCount)
                            } else {
                                s.shared("automation_triggers_none")
                            },
                            type = TextType.BODY
                        )
                    }
                }
            }
        }

        // Form actions with conditional Test button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Test button (only when editing existing automation)
            onTest?.let { testCallback ->
                UI.ActionButton(
                    action = ButtonAction.REFRESH,
                    display = ButtonDisplay.LABEL,
                    onClick = testCallback
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cancel button
            UI.ActionButton(
                action = ButtonAction.CANCEL,
                display = ButtonDisplay.LABEL,
                onClick = onCancel
            )

            // Save button
            UI.ActionButton(
                action = ButtonAction.SAVE,
                display = ButtonDisplay.LABEL,
                onClick = onSave
            )
        }
    }
}

/**
 * Generate human-readable schedule label for button display
 *
 * Examples:
 * - "Quotidien 9h, 14h, 18h"
 * - "Lun/Mer/Ven 9h"
 * - "15 de Jan/Mar/Juin 10h"
 * - "Manuel" (if no schedule)
 */
private fun generateScheduleLabel(
    context: android.content.Context,
    scheduleConfig: ScheduleConfig
): String {
    val s = Strings.`for`(context = context)

    return when (val pattern = scheduleConfig.pattern) {
        is com.assistant.core.utils.SchedulePattern.DailyMultiple -> {
            val times = pattern.times.joinToString(", ")
            s.shared("automation_schedule_daily").format(times)
        }
        is com.assistant.core.utils.SchedulePattern.WeeklySimple -> {
            val days = pattern.daysOfWeek.joinToString("/") { day ->
                s.shared("day_of_week_short_$day")
            }
            s.shared("automation_schedule_weekly").format(days, pattern.time)
        }
        is com.assistant.core.utils.SchedulePattern.MonthlyRecurrent -> {
            val months = pattern.months.joinToString("/") { month ->
                s.shared("month_short_$month")
            }
            s.shared("automation_schedule_monthly").format(pattern.dayOfMonth, months, pattern.time)
        }
        is com.assistant.core.utils.SchedulePattern.WeeklyCustom -> {
            val count = pattern.moments.size
            s.shared("automation_schedule_weekly_custom").format(count)
        }
        is com.assistant.core.utils.SchedulePattern.YearlyRecurrent -> {
            val count = pattern.dates.size
            s.shared("automation_schedule_yearly").format(count)
        }
        is com.assistant.core.utils.SchedulePattern.SpecificDates -> {
            val count = pattern.timestamps.size
            s.shared("automation_schedule_specific").format(count)
        }
    }
}
