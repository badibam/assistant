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
     * Text field with configurable length.
     * Config: {length: "SHORT" | "MEDIUM" | "LONG" | "UNLIMITED"} (default: UNLIMITED)
     * Length options:
     * - SHORT: 60 chars (a few words)
     * - MEDIUM: 250 chars (one paragraph)
     * - LONG: 1500 chars (one page)
     * - UNLIMITED: no limit (default)
     * Example: Notes, descriptions, comments with flexible length
     */
    TEXT,

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
     * Config: {options (required, min 2), multiple?, ordered?, open?, option_colors?}
     * - multiple: the value is a list of options, in no particular order
     * - ordered: the value is a list of options in the order chosen (a ranking); excludes multiple and open
     * - open: a value outside the options is added to them by the write that brings it; closed, it is refused
     * - option_colors: a TagColor name per option; a colored choice shows as tags
     * Tags are a multiple open choice.
     * Example: Categories, tags, selections, rankings
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
    DATETIME,

    /**
     * A length of time, stored as a whole number of milliseconds with no unit.
     * Config: {precision?: DurationUnit (default MINUTE), form?: "COMPOSED" | "SINGLE" (default COMPOSED)}
     * The precision is the smallest unit entered and shown; the form writes "1 h 25 min" or "85 min".
     * Example: Sleep, time spent on an activity, a workout
     */
    DURATION;

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
            TEXT -> s.shared("field_type_text_display_name")
            NUMERIC -> s.shared("field_type_numeric_display_name")
            SCALE -> s.shared("field_type_scale_display_name")
            CHOICE -> s.shared("field_type_choice_display_name")
            BOOLEAN -> s.shared("field_type_boolean_display_name")
            RANGE -> s.shared("field_type_range_display_name")
            DATE -> s.shared("field_type_date_display_name")
            TIME -> s.shared("field_type_time_display_name")
            DATETIME -> s.shared("field_type_datetime_display_name")
            DURATION -> s.shared("field_type_duration_display_name")
        }
    }

    /**
     * The config keys whose change restricts which values are allowed, without changing what a
     * stored value means. Narrowing one of them leaves the entries that still fit and takes only
     * the ones that no longer do.
     *
     * A key that redefines the meaning of a value is not listed here: a SCALE's range is the unit
     * its values are read against, and a CHOICE's multiple flag decides whether a value is one
     * thing or a list. Those changes take every value, and their own FieldChange says so.
     *
     * A field type added later answers this question instead of asking for a detector of its own.
     */
    val restrictingConfigKeys: Set<String>
        get() = when (this) {
            TEXT -> setOf("length")
            NUMERIC -> setOf("min", "max", "decimals")
            else -> emptySet()
        }

    /**
     * Whether a stored value still fits a config.
     *
     * Asked of each entry when a restricting key changed, so that widening a bound touches
     * nothing and narrowing one takes only what falls outside. A type with no restricting keys is
     * never asked.
     */
    fun permits(value: Any?, config: Map<String, Any>?): Boolean {
        if (value == null) return true

        return when (this) {
            TEXT -> {
                val length = TextLength.fromString(config?.get("length") as? String)
                length == TextLength.UNLIMITED || (value as? String)?.length?.let { it <= length.getLimit() } ?: true
            }

            NUMERIC -> {
                val number = (value as? Number)?.toDouble() ?: return true
                val min = (config?.get("min") as? Number)?.toDouble()
                val max = (config?.get("max") as? Number)?.toDouble()
                val decimals = (config?.get("decimals") as? Number)?.toInt() ?: 0

                when {
                    min != null && number < min -> false
                    max != null && number > max -> false
                    // More decimals than the config allows: the value cannot be written back
                    // as it stands, so it no longer fits.
                    decimalsOf(number) > decimals -> false
                    else -> true
                }
            }

            else -> true
        }
    }

    /** How many decimals a number actually carries, trailing zeros not counted. */
    private fun decimalsOf(value: Double): Int {
        val text = java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        val dot = text.indexOf('.')
        return if (dot < 0) 0 else text.length - dot - 1
    }

    companion object {
        /**
         * Get all available field types.
         * @return List of all FieldType enum values
         */
        fun getAllTypes(): List<FieldType> = entries
    }
}
