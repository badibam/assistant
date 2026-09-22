package com.assistant.core.fields

import android.content.Context
import com.assistant.core.fields.migration.FieldChange
import com.assistant.core.fields.migration.FieldConfigComparator
import com.assistant.core.strings.Strings

/**
 * Validates field definitions for custom fields.
 *
 * Validation rules:
 * - Name uniqueness (no collision with other fields in the instance)
 * - Type/config coherence (required config for certain types)
 * - Inter-field constraints (min < max for numeric types)
 * - Name format validation (snake_case, ASCII)
 * - Migration constraints (no type changes)
 *
 * Philosophy: Single validation point with full trust.
 * No re-validation during schema generation.
 */
object FieldConfigValidator {

    /**
     * Validates a field definition.
     *
     * @param fieldDef The field definition to validate
     * @param existingFields List of existing fields (for uniqueness check, excluding the field being edited)
     * @param context Android context for string translation
     * @return ValidationResult with success status and translated error message if invalid
     */
    fun validate(fieldDef: FieldDefinition, existingFields: List<FieldDefinition>, context: Context): ValidationResult {
        val s = Strings.`for`(context = context)

        // A field that has no technical name yet is one being created: the service assigns it,
        // from the display name and clear of the names already taken. Nothing to check here.
        if (fieldDef.name.isNotEmpty()) {
            val nameValidation = validateNameFormat(fieldDef.name, s)
            if (!nameValidation.isValid) {
                return nameValidation
            }

            val uniquenessValidation = validateNameUniqueness(fieldDef.name, existingFields, s)
            if (!uniquenessValidation.isValid) {
                return uniquenessValidation
            }
        }

        // Validate display name is not empty
        if (fieldDef.displayName.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_display_name_empty")
            )
        }

        // Validate type/config coherence
        val configValidation = validateTypeConfig(fieldDef.type, fieldDef.config, s)
        if (!configValidation.isValid) {
            return configValidation
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates the format of a field name.
     *
     * Requirements:
     * - Not empty
     * - snake_case format
     * - Only ASCII lowercase letters, numbers, and underscores
     * - Does not start or end with underscore
     * - Does not start with a number
     */
    private fun validateNameFormat(name: String, s: com.assistant.core.strings.StringsContext): ValidationResult {
        if (name.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_name_empty")
            )
        }

        if (!name.matches(Regex("^[a-z][a-z0-9_]*[a-z0-9]$")) && !name.matches(Regex("^[a-z]$"))) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_name_format")
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates name uniqueness within existing fields.
     */
    private fun validateNameUniqueness(name: String, existingFields: List<FieldDefinition>, s: com.assistant.core.strings.StringsContext): ValidationResult {
        val collision = existingFields.find { it.name == name }
        if (collision != null) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_name_duplicate").format(name)
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates that the config is appropriate for the field type.
     *
     * Checks type-specific config requirements and constraints.
     */
    private fun validateTypeConfig(type: FieldType, config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        return when (type) {
            FieldType.TEXT -> validateTextConfig(config, s)

            FieldType.NUMERIC -> validateNumericConfig(config, s)
            FieldType.SCALE -> validateScaleConfig(config, s)
            FieldType.CHOICE -> validateChoiceConfig(config, s)
            FieldType.BOOLEAN -> validateBooleanConfig(config, s)
            FieldType.RANGE -> validateRangeConfig(config, s)
            FieldType.DATE -> validateDateConfig(config, s)
            FieldType.TIME -> validateTimeConfig(config, s)
            FieldType.DATETIME -> validateDateTimeConfig(config, s)
        }
    }

    /**
     * Validates TEXT field config.
     * Config: {length: "SHORT" | "MEDIUM" | "LONG" | "UNLIMITED"} (optional, default: UNLIMITED)
     */
    private fun validateTextConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is optional for TEXT (defaults to UNLIMITED)
        if (config == null) return ValidationResult(isValid = true)

        // Validate length if provided
        val lengthStr = config["length"] as? String
        if (lengthStr != null) {
            // Check if length is a valid TextLength enum value
            try {
                TextLength.valueOf(lengthStr.uppercase())
            } catch (e: IllegalArgumentException) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = s.shared("field_validation_text_length_invalid").format(lengthStr)
                )
            }
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates NUMERIC field config.
     * Config: {unit?, min?, max?, decimals?, step?}
     */
    private fun validateNumericConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is optional for NUMERIC
        if (config == null) return ValidationResult(isValid = true)

        // Validate min <= max if both defined
        val min = config["min"] as? Number
        val max = config["max"] as? Number
        if (min != null && max != null && min.toDouble() > max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_numeric_min_max")
            )
        }

        // Validate decimals >= 0
        val decimals = config["decimals"] as? Number
        if (decimals != null && decimals.toInt() < 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_numeric_decimals")
            )
        }

        // Validate step > 0 if defined
        val step = config["step"] as? Number
        if (step != null && step.toDouble() <= 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_numeric_step")
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates SCALE field config.
     * Config: {min (required), max (required), min_label?, max_label?, step?}
     */
    private fun validateScaleConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is required for SCALE
        if (config == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_scale_min_max_required")
            )
        }

        // min and max are required
        val min = config["min"] as? Number
        val max = config["max"] as? Number
        if (min == null || max == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_scale_min_max_required")
            )
        }

        // min < max (strict)
        if (min.toDouble() >= max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_scale_min_max_order")
            )
        }

        // Validate step > 0 if defined
        val step = config["step"] as? Number
        if (step != null) {
            val stepValue = step.toDouble()
            if (stepValue <= 0) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = s.shared("field_validation_scale_step")
                )
            }

            // Validate that (max - min) is divisible by step
            val range = max.toDouble() - min.toDouble()
            val epsilon = stepValue * 1e-10 // Floating point tolerance
            val remainder = range % stepValue
            if (remainder > epsilon && (stepValue - remainder) > epsilon) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = s.shared("field_validation_scale_step_range_mismatch")
                )
            }
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates CHOICE field config.
     * Config: {options (required, min 2), multiple?, allow_custom?}
     */
    private fun validateChoiceConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is required for CHOICE
        if (config == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_options_required")
            )
        }

        // options array is required
        val options = config["options"] as? List<*>
        if (options == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_options_required")
            )
        }

        // Minimum 2 options
        if (options.size < 2) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_options_min")
            )
        }

        // No duplicate options
        if (options.size != options.toSet().size) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_options_duplicate")
            )
        }

        // Validate multiple is boolean if present
        val multiple = config["multiple"]
        if (multiple != null && multiple !is Boolean) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_multiple_type")
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates BOOLEAN field config.
     * Config: {true_label?, false_label?}
     */
    private fun validateBooleanConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is optional for BOOLEAN
        if (config == null) return ValidationResult(isValid = true)

        // Validate labels are non-empty if provided
        val trueLabel = config["true_label"] as? String
        if (trueLabel != null && trueLabel.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_boolean_label_empty")
            )
        }

        val falseLabel = config["false_label"] as? String
        if (falseLabel != null && falseLabel.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_boolean_label_empty")
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates RANGE field config.
     * Config: {min?, max?, unit?, decimals?}
     */
    private fun validateRangeConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is optional for RANGE
        if (config == null) return ValidationResult(isValid = true)

        // Validate min <= max if both defined
        val min = config["min"] as? Number
        val max = config["max"] as? Number
        if (min != null && max != null && min.toDouble() > max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_range_min_max")
            )
        }

        // Validate decimals >= 0
        val decimals = config["decimals"] as? Number
        if (decimals != null && decimals.toInt() < 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_range_decimals")
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates DATE field config.
     * Config: {min?, max?}
     */
    private fun validateDateConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // DATE has nothing to configure: the schema declares an empty config object.
        return ValidationResult(isValid = true)
    }

    /**
     * Validates TIME field config.
     * Config: {format?}
     */
    private fun validateTimeConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is optional for TIME
        if (config == null) return ValidationResult(isValid = true)

        // Validate format is "24h" or "12h"
        val format = config["format"] as? String
        if (format != null && format !in listOf("24h", "12h")) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_time_format")
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates DATETIME field config.
     * Config: {min?, max?, time_format?}
     */
    private fun validateDateTimeConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is optional for DATETIME
        if (config == null) return ValidationResult(isValid = true)

        val timeFormat = config["time_format"] as? String
        if (timeFormat != null && timeFormat !in listOf("24h", "12h")) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_datetime_format")
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates that no field types have changed between old and new configurations.
     *
     * This validation is required for AI-driven configuration updates to prevent
     * data corruption. Changing a field's type would make existing values invalid
     * (e.g., text stored in a numeric field).
     *
     * Type changes are detected by FieldConfigComparator as FieldChange.TypeChanged
     * for fields with the same name but different types.
     *
     * @param oldFields Previous field configuration
     * @param newFields New field configuration
     * @param context Android context for string translation
     * @return ValidationResult with success if no type changes detected
     */
    fun validateNoTypeChanges(
        oldFields: List<FieldDefinition>,
        newFields: List<FieldDefinition>,
        context: Context
    ): ValidationResult {
        val s = Strings.`for`(context = context)

        // Detect all changes using comparator
        val changes = FieldConfigComparator.compare(oldFields, newFields)

        // Check for type changes
        val typeChanges = changes.filterIsInstance<FieldChange.TypeChanged>()
        if (typeChanges.isNotEmpty()) {
            val changedFields = typeChanges.joinToString(", ") {
                "${it.name} (${it.oldType} → ${it.newType})"
            }
            return ValidationResult(
                isValid = false,
                errorMessage = "${s.shared("error_field_type_changed")}: $changedFields"
            )
        }

        return ValidationResult(isValid = true)
    }
}

/**
 * Result of field validation.
 *
 * @property isValid Whether the field definition is valid
 * @property errorMessage Translated error message if invalid (null if valid)
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
