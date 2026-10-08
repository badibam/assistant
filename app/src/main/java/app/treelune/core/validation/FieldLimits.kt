package app.treelune.core.validation

/**
 * Field length constants for all schema definitions.
 * A TEXT custom field picks one through its config.length: SHORT, MEDIUM, LONG or UNLIMITED.
 */
object FieldLimits {
    /** SHORT - identifiers, names, labels */
    const val SHORT_LENGTH = 60

    /** MEDIUM - descriptions, text values */
    const val MEDIUM_LENGTH = 250

    /** LONG - long content */
    const val LONG_LENGTH = 1500

    /** UNLIMITED - no limits */
    const val UNLIMITED_LENGTH = Int.MAX_VALUE
}