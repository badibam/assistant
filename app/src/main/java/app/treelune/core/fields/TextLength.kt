package app.treelune.core.fields

import android.content.Context
import app.treelune.core.strings.Strings
import app.treelune.core.validation.FieldLimits

/**
 * Text length options for TEXT custom fields.
 *
 * Provides a unified way to specify text field length constraints
 * instead of having separate field types for each length.
 *
 * Architecture:
 * - Single TEXT field type with configurable length parameter
 * - Maps to FieldLimits constants for consistency
 * - Default is UNLIMITED for maximum flexibility
 */
enum class TextLength {
    /**
     * Very short text (a few words).
     * Max length: 60 chars (FieldLimits.SHORT_LENGTH)
     * Example: Tags, identifiers, short labels
     */
    SHORT,

    /**
     * Short text (one paragraph).
     * Max length: 250 chars (FieldLimits.MEDIUM_LENGTH)
     * Example: Brief descriptions, comments
     */
    MEDIUM,

    /**
     * Long text (one page).
     * Max length: 1500 chars (FieldLimits.LONG_LENGTH)
     * Example: Detailed notes, longer content
     */
    LONG,

    /**
     * Unlimited text (no length constraint).
     * Max length: Int.MAX_VALUE (FieldLimits.UNLIMITED_LENGTH)
     * Example: Long-form content, documentation
     * DEFAULT value for TEXT fields
     */
    UNLIMITED;

    /**
     * Get the numeric limit for this text length.
     * Maps to FieldLimits constants for consistency across the app.
     *
     * @return Maximum character count for this length option
     */
    fun getLimit(): Int {
        return when (this) {
            SHORT -> FieldLimits.SHORT_LENGTH
            MEDIUM -> FieldLimits.MEDIUM_LENGTH
            LONG -> FieldLimits.LONG_LENGTH
            UNLIMITED -> FieldLimits.UNLIMITED_LENGTH
        }
    }

    /**
     * Get the localized display name for this text length.
     * Uses the string system to retrieve the display name.
     *
     * @param context Android context for string access
     * @return Localized display name for this length option
     */
    fun getDisplayName(context: Context): String {
        val s = Strings.`for`(context = context)
        return when (this) {
            SHORT -> s.shared("text_length_short_display_name")
            MEDIUM -> s.shared("text_length_medium_display_name")
            LONG -> s.shared("text_length_long_display_name")
            UNLIMITED -> s.shared("text_length_unlimited_display_name")
        }
    }

    companion object {
        /**
         * Get all available text length options.
         * @return List of all TextLength enum values
         */
        fun getAllLengths(): List<TextLength> = entries

        /**
         * Default text length for TEXT fields.
         * Set to UNLIMITED for maximum flexibility.
         */
        val DEFAULT = UNLIMITED

        /**
         * Parse TextLength from string name (case-insensitive).
         * Returns DEFAULT if parsing fails.
         *
         * @param value String representation of TextLength
         * @return Parsed TextLength or DEFAULT if invalid
         */
        fun fromString(value: String?): TextLength {
            if (value == null) return DEFAULT
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                DEFAULT
            }
        }
    }
}
