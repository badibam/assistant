package com.assistant.core.fields

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
     * Config: {unit?, min?, max?, decimals?, step?, default_value?}
     * Example: Measurements, quantities, scores
     */
    NUMERIC,

    /**
     * Graduated scale between min and max with labels.
     * Config: {min (required), max (required), min_label?, max_label?, step?, default_value?}
     * Example: Mood (1-10), satisfaction, intensity
     */
    SCALE,

    /**
     * Single or multiple choice from predefined options.
     * Config: {options (required, min 2), multiple?, allow_custom? (not implemented V1), default_value?}
     * Example: Categories, tags, selections
     */
    CHOICE,

    /**
     * Boolean true/false value.
     * Config: {true_label?, false_label?, default_value?}
     * Example: Flags, binary options, confirmations
     */
    BOOLEAN,

    /**
     * Range between two numeric values.
     * Config: {min?, max?, unit?, decimals?, default_value?}
     * Example: Time ranges converted to minutes, age ranges, intervals
     */
    RANGE,

    /**
     * Date (day/month/year).
     * Config: {min?, max?, default_value?}
     * Format: ISO 8601 (YYYY-MM-DD)
     * Example: Event dates, birthdays, deadlines
     */
    DATE,

    /**
     * Time (hh:mm).
     * Config: {format?, default_value?}
     * Format: HH:MM (always 24h in storage)
     * Example: Wake times, schedules, time of day
     */
    TIME,

    /**
     * Combined date and time.
     * Config: {min?, max?, time_format?, default_value?}
     * Format: ISO 8601 (YYYY-MM-DDTHH:MM:SS)
     * Example: Precise timestamps, appointments, dated events
     */
    DATETIME
}
