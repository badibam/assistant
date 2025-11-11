package com.assistant.core.fields

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Validates field definitions for custom fields.
 *
 * Validation rules:
 * - Name uniqueness (no collision with other fields in the instance)
 * - Type/config coherence (required config for certain types)
 * - Inter-field constraints (min < max for numeric types)
 * - Name format validation (snake_case, ASCII)
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
     * @return ValidationResult with success status and error message if invalid
     */
    fun validate(fieldDef: FieldDefinition, existingFields: List<FieldDefinition>): ValidationResult {
        // Validate name format
        val nameValidation = validateNameFormat(fieldDef.name)
        if (!nameValidation.isValid) {
            return nameValidation
        }

        // Validate name uniqueness
        val uniquenessValidation = validateNameUniqueness(fieldDef.name, existingFields)
        if (!uniquenessValidation.isValid) {
            return uniquenessValidation
        }

        // Validate display name is not empty
        if (fieldDef.displayName.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_display_name_empty"
            )
        }

        // Validate type/config coherence
        val configValidation = validateTypeConfig(fieldDef.type, fieldDef.config)
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
    private fun validateNameFormat(name: String): ValidationResult {
        if (name.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_name_empty"
            )
        }

        if (!name.matches(Regex("^[a-z][a-z0-9_]*[a-z0-9]$")) && !name.matches(Regex("^[a-z]$"))) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_name_format"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates name uniqueness within existing fields.
     */
    private fun validateNameUniqueness(name: String, existingFields: List<FieldDefinition>): ValidationResult {
        val collision = existingFields.find { it.name == name }
        if (collision != null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_name_duplicate",
                errorMessageArgs = arrayOf(name)
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates that the config is appropriate for the field type.
     *
     * Checks type-specific config requirements and constraints.
     */
    private fun validateTypeConfig(type: FieldType, config: Map<String, Any>?): ValidationResult {
        return when (type) {
            FieldType.TEXT_SHORT, FieldType.TEXT_LONG, FieldType.TEXT_UNLIMITED -> {
                // Text types do not require config (should be null)
                if (config != null) {
                    ValidationResult(
                        isValid = false,
                        errorMessage = "field_validation_config_not_null",
                        errorMessageArgs = arrayOf(type.name)
                    )
                } else {
                    ValidationResult(isValid = true)
                }
            }

            FieldType.NUMERIC -> validateNumericConfig(config)
            FieldType.SCALE -> validateScaleConfig(config)
            FieldType.CHOICE -> validateChoiceConfig(config)
            FieldType.BOOLEAN -> validateBooleanConfig(config)
            FieldType.RANGE -> validateRangeConfig(config)
            FieldType.DATE -> validateDateConfig(config)
            FieldType.TIME -> validateTimeConfig(config)
            FieldType.DATETIME -> validateDateTimeConfig(config)
        }
    }

    /**
     * Validates NUMERIC field config.
     * Config: {unit?, min?, max?, decimals?, step?}
     */
    private fun validateNumericConfig(config: Map<String, Any>?): ValidationResult {
        // Config is optional for NUMERIC
        if (config == null) return ValidationResult(isValid = true)

        // Validate min <= max if both defined
        val min = config["min"] as? Number
        val max = config["max"] as? Number
        if (min != null && max != null && min.toDouble() > max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_numeric_min_max"
            )
        }

        // Validate decimals >= 0
        val decimals = config["decimals"] as? Number
        if (decimals != null && decimals.toInt() < 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_numeric_decimals"
            )
        }

        // Validate step > 0 if defined
        val step = config["step"] as? Number
        if (step != null && step.toDouble() <= 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_numeric_step"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates SCALE field config.
     * Config: {min (required), max (required), min_label?, max_label?, step?}
     */
    private fun validateScaleConfig(config: Map<String, Any>?): ValidationResult {
        // Config is required for SCALE
        if (config == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_scale_min_max_required"
            )
        }

        // min and max are required
        val min = config["min"] as? Number
        val max = config["max"] as? Number
        if (min == null || max == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_scale_min_max_required"
            )
        }

        // min < max (strict)
        if (min.toDouble() >= max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_scale_min_max_order"
            )
        }

        // Validate step > 0 if defined
        val step = config["step"] as? Number
        if (step != null && step.toDouble() <= 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_scale_step"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates CHOICE field config.
     * Config: {options (required, min 2), multiple?, allow_custom?}
     */
    private fun validateChoiceConfig(config: Map<String, Any>?): ValidationResult {
        // Config is required for CHOICE
        if (config == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_choice_options_required"
            )
        }

        // options array is required
        val options = config["options"] as? List<*>
        if (options == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_choice_options_required"
            )
        }

        // Minimum 2 options
        if (options.size < 2) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_choice_options_min"
            )
        }

        // No duplicate options
        if (options.size != options.toSet().size) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_choice_options_duplicate"
            )
        }

        // Validate multiple is boolean if present
        val multiple = config["multiple"]
        if (multiple != null && multiple !is Boolean) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_choice_multiple_type"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates BOOLEAN field config.
     * Config: {true_label?, false_label?}
     */
    private fun validateBooleanConfig(config: Map<String, Any>?): ValidationResult {
        // Config is optional for BOOLEAN
        if (config == null) return ValidationResult(isValid = true)

        // Validate labels are non-empty if provided
        val trueLabel = config["true_label"] as? String
        if (trueLabel != null && trueLabel.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_boolean_label_empty"
            )
        }

        val falseLabel = config["false_label"] as? String
        if (falseLabel != null && falseLabel.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_boolean_label_empty"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates RANGE field config.
     * Config: {min?, max?, unit?, decimals?}
     */
    private fun validateRangeConfig(config: Map<String, Any>?): ValidationResult {
        // Config is optional for RANGE
        if (config == null) return ValidationResult(isValid = true)

        // Validate min <= max if both defined
        val min = config["min"] as? Number
        val max = config["max"] as? Number
        if (min != null && max != null && min.toDouble() > max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_range_min_max"
            )
        }

        // Validate decimals >= 0
        val decimals = config["decimals"] as? Number
        if (decimals != null && decimals.toInt() < 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_range_decimals"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates DATE field config.
     * Config: {min?, max?}
     */
    private fun validateDateConfig(config: Map<String, Any>?): ValidationResult {
        // Config is optional for DATE
        if (config == null) return ValidationResult(isValid = true)

        // Validate min and max are valid ISO 8601 dates
        val minStr = config["min"] as? String
        val maxStr = config["max"] as? String

        val minDate = if (minStr != null) {
            try {
                LocalDate.parse(minStr, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "field_validation_date_min_max_format"
                )
            }
        } else null

        val maxDate = if (maxStr != null) {
            try {
                LocalDate.parse(maxStr, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "field_validation_date_min_max_format"
                )
            }
        } else null

        // Validate min <= max
        if (minDate != null && maxDate != null && minDate.isAfter(maxDate)) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_date_min_max_order"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates TIME field config.
     * Config: {format?}
     */
    private fun validateTimeConfig(config: Map<String, Any>?): ValidationResult {
        // Config is optional for TIME
        if (config == null) return ValidationResult(isValid = true)

        // Validate format is "24h" or "12h"
        val format = config["format"] as? String
        if (format != null && format !in listOf("24h", "12h")) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_time_format"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates DATETIME field config.
     * Config: {min?, max?, time_format?}
     */
    private fun validateDateTimeConfig(config: Map<String, Any>?): ValidationResult {
        // Config is optional for DATETIME
        if (config == null) return ValidationResult(isValid = true)

        // Validate time_format is "24h" or "12h"
        val timeFormat = config["time_format"] as? String
        if (timeFormat != null && timeFormat !in listOf("24h", "12h")) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_datetime_format"
            )
        }

        // Validate min and max are valid ISO 8601 datetimes
        val minStr = config["min"] as? String
        val maxStr = config["max"] as? String

        val minDateTime = if (minStr != null) {
            try {
                LocalDateTime.parse(minStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (e: DateTimeParseException) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "field_validation_datetime_min_max_format"
                )
            }
        } else null

        val maxDateTime = if (maxStr != null) {
            try {
                LocalDateTime.parse(maxStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (e: DateTimeParseException) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "field_validation_datetime_min_max_format"
                )
            }
        } else null

        // Validate min <= max
        if (minDateTime != null && maxDateTime != null && minDateTime.isAfter(maxDateTime)) {
            return ValidationResult(
                isValid = false,
                errorMessage = "field_validation_datetime_min_max_order"
            )
        }

        return ValidationResult(isValid = true)
    }
}

/**
 * Result of field validation.
 *
 * @property isValid Whether the field definition is valid
 * @property errorMessage Error message string key if invalid (null if valid)
 * @property errorMessageArgs Optional arguments for the error message (for placeholders)
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val errorMessageArgs: Array<Any>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ValidationResult

        if (isValid != other.isValid) return false
        if (errorMessage != other.errorMessage) return false
        if (errorMessageArgs != null) {
            if (other.errorMessageArgs == null) return false
            if (!errorMessageArgs.contentEquals(other.errorMessageArgs)) return false
        } else if (other.errorMessageArgs != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isValid.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (errorMessageArgs?.contentHashCode() ?: 0)
        return result
    }
}
