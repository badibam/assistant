package com.assistant.core.fields

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * Types of custom fields available for tool instances.
 *
 * All components (validator, schema provider, renderer) use when(type) to handle each type.
 * Each type has specific configuration requirements and validation rules.
 *
 * Architecture:
 * - Generic and reusable field system
 * - Custom fields use the same infrastructure as native tooltype fields
 * - Two validation levels: config validation (FieldConfigValidator) and value validation (FieldValueValidator)
 */
enum class FieldType {
    /**
     * Short text for identifiers, names, short labels.
     * Config: null (no configuration required)
     * Max length: 60 chars (FieldLimits.SHORT_LENGTH) - fixed, not configurable
     * Example: Names, short titles, visible identifiers
     */
    TEXT_SHORT,

    /**
     * Long text for substantial content.
     * Config: null (no configuration required)
     * Max length: 1500 chars (FieldLimits.LONG_LENGTH) - fixed, not configurable
     * Example: Detailed descriptions, comments, long notes
     */
    TEXT_LONG,

    /**
     * Text field with no length limit.
     * Config: null (no configuration required)
     * Example: Long notes, transcriptions, detailed descriptions
     */
    TEXT_UNLIMITED,

    /**
     * Numeric value with optional unit and constraints.
     * Config: {unit?, min?, max?, decimals?, step?}
     * Example: Measurements, quantities, scores
     */
    NUMERIC,

    /**
     * Graduated scale between min and max with labels.
     * Config: {min (required), max (required), min_label?, max_label?, step?}
     * Example: Mood (1-10), satisfaction, intensity
     */
    SCALE,

    /**
     * Single or multiple choice from predefined options.
     * Config: {options (required, min 2), multiple?, allow_custom? (not implemented V1)}
     * Example: Categories, tags, selections
     */
    CHOICE,

    /**
     * Boolean true/false value.
     * Config: {true_label?, false_label?}
     * Example: Flags, binary options, confirmations
     */
    BOOLEAN,

    /**
     * Range between two numeric values.
     * Config: {min?, max?, unit?, decimals?}
     * Example: Time ranges converted to minutes, age ranges, intervals
     */
    RANGE,

    /**
     * Date (day/month/year).
     * Config: {min?, max?}
     * Format: ISO 8601 (YYYY-MM-DD)
     * Example: Event dates, birthdays, deadlines
     */
    DATE,

    /**
     * Time (hh:mm).
     * Config: {format?}
     * Format: HH:MM (always 24h in storage)
     * Example: Wake times, schedules, time of day
     */
    TIME,

    /**
     * Combined date and time.
     * Config: {min?, max?, time_format?}
     * Format: ISO 8601 (YYYY-MM-DDTHH:MM:SS)
     * Example: Precise timestamps, appointments, dated events
     */
    DATETIME;

    /**
     * Get the localized display name for this field type.
     * Uses the string system to retrieve the display name.
     *
     * @param context Android context for string access
     * @return Localized display name for this type
     */
    fun getDisplayName(context: Context): String {
        val s = Strings.`for`(context = context)
        return when (this) {
            TEXT_SHORT -> s.shared("field_type_text_short_display_name")
            TEXT_LONG -> s.shared("field_type_text_long_display_name")
            TEXT_UNLIMITED -> s.shared("field_type_text_unlimited_display_name")
            NUMERIC -> s.shared("field_type_numeric_display_name")
            SCALE -> s.shared("field_type_scale_display_name")
            CHOICE -> s.shared("field_type_choice_display_name")
            BOOLEAN -> s.shared("field_type_boolean_display_name")
            RANGE -> s.shared("field_type_range_display_name")
            DATE -> s.shared("field_type_date_display_name")
            TIME -> s.shared("field_type_time_display_name")
            DATETIME -> s.shared("field_type_datetime_display_name")
        }
    }

    /**
     * Get the localized description for this field type.
     * Uses the string system to retrieve the description.
     *
     * @param context Android context for string access
     * @return Localized description for this type
     */
    fun getDescription(context: Context): String {
        val s = Strings.`for`(context = context)
        return when (this) {
            TEXT_SHORT -> s.shared("field_type_text_short_description")
            TEXT_LONG -> s.shared("field_type_text_long_description")
            TEXT_UNLIMITED -> s.shared("field_type_text_unlimited_description")
            NUMERIC -> s.shared("field_type_numeric_description")
            SCALE -> s.shared("field_type_scale_description")
            CHOICE -> s.shared("field_type_choice_description")
            BOOLEAN -> s.shared("field_type_boolean_description")
            RANGE -> s.shared("field_type_range_description")
            DATE -> s.shared("field_type_date_description")
            TIME -> s.shared("field_type_time_description")
            DATETIME -> s.shared("field_type_datetime_description")
        }
    }

    companion object {
        /**
         * Get all available field types.
         * @return List of all FieldType enum values
         */
        fun getAllTypes(): List<FieldType> = entries
    }
}
