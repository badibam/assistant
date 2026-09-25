package com.assistant.core.config

import android.content.Context
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.SettingValues
import com.assistant.core.fields.settings.SettingsSchemaGenerator
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * The app's settings, declared with the fields by category (docs/design/config-fields.md): the
 * schema AppConfigService checks every write of a category against, the settings screen, and the
 * reading of a category all come from here.
 */
object AppSettings {

    /** The bounds of the relative label limits */
    val HOUR_LIMIT_RANGE = 1..24
    val DAY_LIMIT_RANGE = 1..30
    val WEEK_LIMIT_RANGE = 1..12
    val MONTH_LIMIT_RANGE = 1..24
    val YEAR_LIMIT_RANGE = 1..10

    /** The bounds of the AI limits: at least one call, and below a runaway, since each call is paid for */
    val AI_LIMITS_CHAT_RANGE = 1..50
    val AI_LIMITS_AUTOMATION_RANGE = 1..100

    /** The data size thresholds, in characters, and their step */
    val AI_DATA_CHAT_RANGE = IntProgression.fromClosedRange(5_000, 100_000, 5_000)
    val AI_DATA_AUTOMATION_RANGE = IntProgression.fromClosedRange(10_000, 500_000, 10_000)

    private val WEEK_DAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val TIMEZONES = listOf("Europe/Paris", "UTC", "America/New_York", "America/Los_Angeles", "Asia/Tokyo", "Asia/Shanghai", "Australia/Sydney")
    private val LOCALES = listOf("fr-FR", "en-US", "en-GB", "de-DE", "es-ES", "it-IT", "ja-JP", "zh-CN")
    private val DATE_PATTERNS = listOf("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd")

    /** The categories a settings screen edits, each with its declaration. */
    val CATEGORIES = listOf(
        AppSettingCategories.FORMAT,
        AppSettingCategories.AI_LIMITS,
        AppSettingCategories.VALIDATION_CONFIG,
        AppSettingCategories.MAIN_SCREEN
    )

    fun nodes(category: String, context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        val text = s::shared
        return when (category) {
            AppSettingCategories.FORMAT -> listOf(
                // Absent, the time zone and the locale are the phone's
                SettingNode.Section(text("settings_format_timezone"), listOf(
                    choice("timezone_override", text("app_config_format_timezone_override"), text("app_config_schema_format_timezone_override"), TIMEZONES)
                )),
                SettingNode.Section(text("settings_format_locale_section"), listOf(
                    choice("locale_override", text("app_config_format_locale_override"), text("app_config_schema_format_locale_override"), LOCALES)
                )),
                SettingNode.Section(text("settings_format_display_section"), listOf(
                    // Absent, the phone's habit
                    field("use_24_hour_format", text("settings_format_24h"), text("app_config_schema_format_use_24_hour_format"), FieldType.BOOLEAN),
                    choice("date_format_pattern", text("app_config_format_date_format_pattern"), text("app_config_schema_format_date_format_pattern"), DATE_PATTERNS),
                    choice("time_separator", text("app_config_format_time_separator"), text("app_config_schema_format_time_separator"), listOf(":", "h"), required = true)
                )),
                SettingNode.Section(text("settings_format_business_section"), listOf(
                    scale("day_start_hour", text("app_config_format_day_start_hour"), text("app_config_schema_format_day_start_hour"), 0..23),
                    choice("week_start_day", text("app_config_format_week_start_day"), text("app_config_schema_format_week_start_day"), WEEK_DAYS,
                        labels = WEEK_DAYS.mapIndexed { i, day -> day to text("day_of_week_${i + 1}") }.toMap(), required = true)
                )),
                SettingNode.Group("relative_label_limits", text("settings_format_relative_section"), required = true, nodes = listOf(
                    scale("hour_limit", text("app_config_format_hour_limit"), text("app_config_schema_format_hour_limit"), HOUR_LIMIT_RANGE),
                    scale("day_limit", text("app_config_format_day_limit"), text("app_config_schema_format_day_limit"), DAY_LIMIT_RANGE),
                    scale("week_limit", text("app_config_format_week_limit"), text("app_config_schema_format_week_limit"), WEEK_LIMIT_RANGE),
                    scale("month_limit", text("app_config_format_month_limit"), text("app_config_schema_format_month_limit"), MONTH_LIMIT_RANGE),
                    scale("year_limit", text("app_config_format_year_limit"), text("app_config_schema_format_year_limit"), YEAR_LIMIT_RANGE)
                ))
            )
            AppSettingCategories.AI_LIMITS -> listOf(
                scale("chat_max_autonomous_roundtrips", text("app_config_ai_limits_chat"), text("settings_ai_limits_chat_help"), AI_LIMITS_CHAT_RANGE),
                scale("automation_max_autonomous_roundtrips", text("app_config_ai_limits_automation"), text("settings_ai_limits_automation_help"), AI_LIMITS_AUTOMATION_RANGE),
                scale("chat_max_data_chars", text("app_config_ai_data_chat"), text("settings_ai_data_chat_help"), AI_DATA_CHAT_RANGE),
                scale("automation_max_data_chars", text("app_config_ai_data_automation"), text("settings_ai_data_automation_help"), AI_DATA_AUTOMATION_RANGE)
            )
            AppSettingCategories.VALIDATION_CONFIG -> listOf(
                ValidationConfig.KEY_APP_CONFIG, ValidationConfig.KEY_ZONE_CONFIG, ValidationConfig.KEY_TOOL_CONFIG, ValidationConfig.KEY_TOOL_DATA
            ).map { key -> field(key, text("app_config_$key"), null, FieldType.BOOLEAN, required = true) }
            AppSettingCategories.MAIN_SCREEN -> listOf(
                SettingNode.ListOf("zone_groups", text("label_zone_groups"),
                    SettingNode.Item.Value(FieldDefinition("zone_group", text("label_group"), text("message_zone_groups_description"),
                        FieldType.TEXT, false, mapOf("length" to TextLength.SHORT.name))),
                    required = true, distinct = true)
            )
            else -> throw IllegalArgumentException("No declaration for settings category '$category'")
        }
    }

    /** The schema a category's settings are held to. */
    fun schema(category: String, context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "app_config_$category",
            displayName = title(category, context),
            description = "",
            category = SchemaCategory.APP_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(category, context), s::shared).toString()
        )
    }

    /** The name a category's screen is shown under. */
    fun title(category: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (category) {
            AppSettingCategories.FORMAT -> s.shared("settings_format_subtitle")
            AppSettingCategories.AI_LIMITS -> s.shared("settings_ai_limits")
            AppSettingCategories.VALIDATION_CONFIG -> s.shared("settings_validation")
            AppSettingCategories.MAIN_SCREEN -> s.shared("label_main_screen_config")
            else -> throw IllegalArgumentException("No declaration for settings category '$category'")
        }
    }

    /** A category's stored [settings], read through its declaration. */
    fun read(category: String, settings: JSONObject, context: Context): SettingValues =
        SettingValues(nodes(category, context), settings)

    private fun field(name: String, label: String, description: String?, type: FieldType, required: Boolean = false,
                      config: Map<String, Any>? = null) =
        SettingNode.Field(FieldDefinition(name, label, description, type, false, config), required = required)

    private fun choice(name: String, label: String, description: String, values: List<String>,
                       labels: Map<String, String> = emptyMap(), required: Boolean = false) =
        field(name, label, description, FieldType.CHOICE, required, mapOf("options" to ChoiceSettings.storedOptions(values, labels)))

    /** A bounded whole number, set with a slider. */
    private fun scale(name: String, label: String, description: String, range: IntProgression) =
        field(name, label, description, FieldType.SCALE, required = true,
            config = mapOf("min" to range.first, "max" to range.last, "step" to range.step))
}
