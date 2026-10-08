package app.treelune.core.fields.settings

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType

/**
 * A schedule, declared with the fields: a recurrence pattern and a variant on its type. The form ScheduleConfig reads and writes; a Messages tool holds one,
 * and so does an automation.
 */
object ScheduleSettings {

    /** The name a schedule is stored under in the object holding it, and by which SettingsForm knows it. */
    const val NAME = "schedule"

    /** A schedule labelled [label], as a config or an automation declares it. */
    fun group(label: String, text: (String) -> String) = SettingNode.Group(NAME, label, nodes(text))

    /** The pattern types, as ScheduleConfig's SchedulePattern names them. */
    private val PATTERNS = listOf("DailyMultiple", "WeeklySimple", "MonthlyRecurrent", "WeeklyCustom", "YearlyRecurrent", "SpecificDates")

    /** The settings of a schedule, as the object stored under a config's "schedule". */
    fun nodes(text: (String) -> String): List<SettingNode> = listOf(
        SettingNode.Group(
            name = "pattern",
            label = text("schedule_pattern_label"),
            required = true,
            nodes = listOf(SettingNode.Variant(
                selector = field("type", text("schedule_pattern_label"), FieldType.CHOICE, required = true,
                    config = mapOf("options" to ChoiceSettings.storedOptions(PATTERNS, PATTERNS.associateWith { text("schedule_pattern_$it") }))),
                cases = mapOf(
                    "DailyMultiple" to listOf(
                        values("times", text("schedule_daily_times_label"), time(text))
                    ),
                    "WeeklySimple" to listOf(
                        values("days_of_week", text("schedule_weekly_days_label"), dayOfWeek(text)),
                        timeField(text)
                    ),
                    "MonthlyRecurrent" to listOf(
                        values("months", text("schedule_month_label"), month(text)),
                        field("day_of_month", text("schedule_day_of_month_label"), FieldType.NUMERIC, required = true, config = whole(1, 31)),
                        timeField(text)
                    ),
                    "WeeklyCustom" to listOf(
                        SettingNode.ListOf("moments", text("schedule_weekly_custom_label"), minItems = 1, required = true,
                            summary = listOf("day_of_week", "time"),
                            item = SettingNode.Item.Of(listOf(
                                SettingNode.Field(dayOfWeek(text), required = true),
                                timeField(text)
                            )))
                    ),
                    "YearlyRecurrent" to listOf(
                        SettingNode.ListOf("dates", text("schedule_yearly_recurrent_label"), minItems = 1, required = true,
                            summary = listOf("day", "month", "time"),
                            item = SettingNode.Item.Of(listOf(
                                SettingNode.Field(month(text), required = true),
                                field("day", text("schedule_day_label"), FieldType.NUMERIC, required = true, config = whole(1, 31)),
                                timeField(text)
                            )))
                    ),
                    "SpecificDates" to listOf(
                        values("timestamps", text("schedule_specific_dates_label"),
                            FieldDefinition("timestamp", text("schedule_specific_dates_label"), null, FieldType.DATETIME, false, null))
                    )
                )
            ))
        )
    )

    private fun field(name: String, label: String, type: FieldType, required: Boolean = false, config: Map<String, Any>? = null) =
        SettingNode.Field(FieldDefinition(name, label, null, type, false, config), required = required)

    /** A required list of at least one value. */
    private fun values(name: String, label: String, value: FieldDefinition) =
        SettingNode.ListOf(name, label, SettingNode.Item.Value(value), required = true, minItems = 1)

    private fun whole(min: Int, max: Int): Map<String, Any> = mapOf("min" to min, "max" to max, "decimals" to 0)

    private fun time(text: (String) -> String) = FieldDefinition("time", text("schedule_time_label_single"), null, FieldType.TIME, false, null)
    private fun timeField(text: (String) -> String) = SettingNode.Field(time(text), required = true)
    private fun dayOfWeek(text: (String) -> String) = FieldDefinition("day_of_week", text("schedule_day_label"), null, FieldType.NUMERIC, false, whole(1, 7))
    private fun month(text: (String) -> String) = FieldDefinition("month", text("schedule_month_label"), null, FieldType.NUMERIC, false, whole(1, 12))
}
