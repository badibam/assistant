package com.assistant.core.validation

/**
 * Field length constants for all schema definitions
 * Maps directly to FieldType text variants for consistency
 */
object FieldLimits {
    /** FieldType.TEXT - identifiers, names, labels */
    const val SHORT_LENGTH = 60

    /** FieldType.TEXT_MEDIUM - descriptions, text values */
    const val MEDIUM_LENGTH = 250

    /** FieldType.TEXT_LONG - long content */
    const val LONG_LENGTH = 1500

    /** FieldType.TEXT_UNLIMITED - no limits */
    const val UNLIMITED_LENGTH = Int.MAX_VALUE
}