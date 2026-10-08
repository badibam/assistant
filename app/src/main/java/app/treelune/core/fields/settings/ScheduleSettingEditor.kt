package app.treelune.core.fields.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.ScheduleConfig
import app.treelune.core.utils.SchedulePattern
import app.treelune.core.utils.StoredSchedule
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * A schedule on a config screen (ScheduleSettings.group): its [label], a line saying what it is,
 * and ScheduleConfigEditor behind a button. SettingsForm draws every schedule group with it.
 *
 * A stored schedule that does not read is said so, and stays as it was until redefined: the
 * service then refuses it, rather than the screen saving it away as empty.
 */
class ScheduleSettingEditor(private val label: String, private val s: StringsContext) : SettingEditor {

    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        var editing by rememberSaveable { mutableStateOf(false) }
        val stored = StoredSchedule.of(JSONObject().apply { (value as? JSONObject)?.let { put(ScheduleSettings.NAME, it) } })

        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(label, TextType.LABEL)
            when (stored) {
                is StoredSchedule.Unreadable -> UI.Text(s.shared("schedule_unreadable").format(stored.cause), TextType.ERROR)
                is StoredSchedule.Readable -> UI.Text(scheduleSummary(stored.schedule, s), TextType.BODY)
                StoredSchedule.None -> UI.Text(s.shared("schedule_summary_none"), TextType.CAPTION)
            }
            UI.Button(type = ButtonType.DEFAULT, size = Size.M, onClick = { editing = true }) {
                UI.Text(s.shared("action_configure_schedule"), TextType.LABEL)
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
}

/** One line saying what [schedule] is, wherever a schedule is shown. */
fun scheduleSummary(schedule: ScheduleConfig, s: StringsContext): String = when (val pattern = schedule.pattern) {
    is SchedulePattern.DailyMultiple -> s.shared("schedule_summary_daily").format(pattern.times.joinToString(", "))
    is SchedulePattern.WeeklySimple -> s.shared("schedule_summary_weekly")
        .format(pattern.daysOfWeek.joinToString("/") { s.shared("day_of_week_short_$it") }, pattern.time)
    is SchedulePattern.MonthlyRecurrent -> s.shared("schedule_summary_monthly")
        .format(pattern.dayOfMonth, pattern.months.joinToString("/") { s.shared("month_short_$it") }, pattern.time)
    is SchedulePattern.WeeklyCustom -> s.shared("schedule_summary_weekly_custom").format(pattern.moments.size)
    is SchedulePattern.YearlyRecurrent -> s.shared("schedule_summary_yearly").format(pattern.dates.size)
    is SchedulePattern.SpecificDates -> s.shared("schedule_summary_specific").format(pattern.timestamps.size)
}
