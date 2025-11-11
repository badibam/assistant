package com.assistant.core.fields

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * Validates field values for custom fields.
 *
 * This validator handles business constraints that are not expressible in JSON Schema.
 * It is called by ToolDataService.execute() after SchemaValidator, before persistence.
 *
 * Main use case: RANGE type with start <= end constraint
 * Other types are generally handled by JSON Schema validation.
 *
 * Philosophy: Only validate constraints that JSON Schema cannot express.
 * Trust JSON Schema for type/format/required/min/max/pattern validation.
 */
object FieldValueValidator {

    /**
     * Validates a field value against its definition.
     *
     * @param fieldDef The field definition containing type and config
     * @param value The value to validate (can be null if field is optional)
     * @param context Android context for string resources
     * @return ValidationResult with success status and error message if invalid
     */
    fun validate(fieldDef: FieldDefinition, value: Any?, context: Context): ValidationResult {
        // Null values are handled by JSON Schema (required constraint)
        if (value == null) return ValidationResult(isValid = true)

        return when (fieldDef.type) {
            // Text types: fully handled by JSON Schema (maxLength)
            FieldType.TEXT_SHORT,
            FieldType.TEXT_LONG,
            FieldType.TEXT_UNLIMITED -> ValidationResult(isValid = true)

            // NUMERIC: fully handled by JSON Schema (min, max, multipleOf)
            FieldType.NUMERIC -> ValidationResult(isValid = true)

            // SCALE: fully handled by JSON Schema (min, max, multipleOf)
            FieldType.SCALE -> ValidationResult(isValid = true)

            // CHOICE: fully handled by JSON Schema (enum, uniqueItems for multiple)
            FieldType.CHOICE -> ValidationResult(isValid = true)

            // BOOLEAN: fully handled by JSON Schema (type: boolean)
            FieldType.BOOLEAN -> ValidationResult(isValid = true)

            // RANGE: requires custom validation for start <= end
            FieldType.RANGE -> validateRangeValue(value, context)

            // DATE: min/max might need custom validation if JSON Schema insufficient
            // For now, trust JSON Schema format validation
            FieldType.DATE -> ValidationResult(isValid = true)

            // TIME: fully handled by JSON Schema (pattern)
            FieldType.TIME -> ValidationResult(isValid = true)

            // DATETIME: min/max might need custom validation if JSON Schema insufficient
            // For now, trust JSON Schema format validation
            FieldType.DATETIME -> ValidationResult(isValid = true)
        }
    }

    /**
     * Validates RANGE value: start <= end
     *
     * JSON Schema can validate that both start and end are present and within min/max bounds,
     * but cannot validate the relationship between start and end.
     */
    private fun validateRangeValue(value: Any?, context: Context): ValidationResult {
        val s = Strings.`for`(context = context)

        // Value should be a Map with "start" and "end" keys
        val rangeMap = value as? Map<*, *>
        if (rangeMap == null) {
            // This should not happen if JSON Schema validation passed
            return ValidationResult(isValid = true)
        }

        val start = (rangeMap["start"] as? Number)?.toDouble()
        val end = (rangeMap["end"] as? Number)?.toDouble()

        // Both should be present (JSON Schema ensures this)
        if (start == null || end == null) {
            return ValidationResult(isValid = true)
        }

        // Validate start <= end
        if (start > end) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_value_range_start_end")
            )
        }

        return ValidationResult(isValid = true)
    }
}
