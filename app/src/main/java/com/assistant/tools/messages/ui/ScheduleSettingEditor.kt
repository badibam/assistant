package com.assistant.tools.messages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.ui.automation.ScheduleConfigEditor
import com.assistant.core.fields.settings.SettingEditor
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.ScheduleConfig
import com.assistant.core.utils.SchedulePattern
import com.assistant.core.utils.StoredSchedule
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * The recurrence of a Messages tool, on its config screen: a line saying what it is, and the
 * schedule editor behind a button. Absent, nothing fires on its own.
 *
 * A stored recurrence that does not read is said so, and stays as it was until redefined: the
 * service then refuses it, rather than the screen saving it away as empty.
 */
class ScheduleSettingEditor(private val s: StringsContext) : SettingEditor {

    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        var editing by rememberSaveable { mutableStateOf(false) }
        val stored = StoredSchedule.of(JSONObject().apply { (value as? JSONObject)?.let { put("schedule", it) } })

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(s.tool("label_schedule"), TextType.LABEL)
            when (stored) {
                is StoredSchedule.Unreadable -> UI.Text(s.tool("schedule_unreadable_config").format(stored.cause), TextType.ERROR)
                is StoredSchedule.Readable -> UI.Text(summary(stored.schedule), TextType.BODY)
                StoredSchedule.None -> UI.Text(s.tool("schedule_summary_not_configured"), TextType.CAPTION)
            }
            UI.Button(type = ButtonType.DEFAULT, size = Size.M, onClick = { editing = true }) {
                UI.Text(s.tool("action_configure_schedule"), TextType.LABEL)
            }
        }

        if (editing) {
            ScheduleConfigEditor(
                existingConfig = (stored as? StoredSchedule.Readable)?.schedule,
                onConfirm = { schedule ->
                    editing = false
                    onChange(schedule?.let { JSONObject(Json.encodeToString(ScheduleConfig.serializer(), it)) })
                },
                onDismiss = { editing = false }
            )
        }
    }

    /** One line describing the recurrence. */
    private fun summary(schedule: ScheduleConfig): String = when (schedule.pattern) {
        is SchedulePattern.DailyMultiple -> s.tool("schedule_summary_daily").format((schedule.pattern as SchedulePattern.DailyMultiple).times.size)
        is SchedulePattern.WeeklySimple -> s.tool("schedule_summary_weekly")
        is SchedulePattern.WeeklyCustom -> s.tool("schedule_summary_weekly_custom")
        is SchedulePattern.MonthlyRecurrent -> s.tool("schedule_summary_monthly")
        is SchedulePattern.YearlyRecurrent -> s.tool("schedule_summary_yearly")
        is SchedulePattern.SpecificDates -> s.tool("schedule_summary_specific_dates")
    }
}
