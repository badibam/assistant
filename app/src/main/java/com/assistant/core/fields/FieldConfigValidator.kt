package com.assistant.core.fields

import android.content.Context
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

        // A default value is a value of the field itself: one of its options, within its bounds
        fieldDef.defaultValue?.let { default ->
            val schema = com.assistant.core.validation.Schema(
                id = "default_value_${fieldDef.name}",
                displayName = fieldDef.displayName,
                description = "",
                category = com.assistant.core.validation.SchemaCategory.TOOL_DATA,
                content = org.json.JSONObject()
                    .put("type", "object")
                    .put("properties", org.json.JSONObject().put(DEFAULT_KEY, FieldValueSchema.of(fieldDef)))
                    .toString()
            )
            val checked = com.assistant.core.validation.SchemaValidator.validate(schema, mapOf(DEFAULT_KEY to default), context)
            if (!checked.isValid) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = s.shared("field_validation_default_value").format(fieldDef.displayName, checked.errorMessage ?: "")
                )
            }
        }

        return ValidationResult(isValid = true)
    }

    /** The key a default value is checked under, alone in its object. */
    private const val DEFAULT_KEY = "default_value"

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
            FieldType.DURATION -> validateDurationConfig(config, s)
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
     * Config: {unit?, min?, max?, decimals, step?}
     */
    private fun validateNumericConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {

        // Validate min <= max if both defined
        val min = config?.get("min") as? Number
        val max = config?.get("max") as? Number
        if (min != null && max != null && min.toDouble() > max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_numeric_min_max")
            )
        }

        // Every number says how many decimals it takes: 0 is a whole number
        val decimals = config?.get("decimals") as? Number
        if (decimals == null || decimals.toInt() < 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_numeric_decimals")
            )
        }

        // Validate step > 0 if defined
        val step = config?.get("step") as? Number
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

        // step > 0, 1 when none is set
        val step = (config["step"] as? Number)?.toDouble() ?: 1.0
        if (step <= 0) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_scale_step")
            )
        }

        // Both ends on the step: the value schema checks a scale value with multipleOf, which
        // counts from 0, so the stops the slider offers from min must be multiples of the step.
        if (!isMultipleOf(min.toDouble(), step) || !isMultipleOf(max.toDouble(), step)) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_scale_bounds_off_step")
            )
        }

        return ValidationResult(isValid = true)
    }

    /** Whether [value] is a whole number of [step]s, computed on the decimal text so 0.3 is one of 0.1. */
    internal fun isMultipleOf(value: Double, step: Double): Boolean =
        java.math.BigDecimal(value.toString()).remainder(java.math.BigDecimal(step.toString())).signum() == 0

    /**
     * Validates CHOICE field config.
     * Config: {options (required, min 2, each {value, color?}), multiple?, ordered?, open?}
     */
    private fun validateChoiceConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is required for CHOICE
        if (config == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_options_required")
            )
        }

        // options array is required, each option a group with a value
        val stored = config["options"] as? List<*>
        if (stored == null || stored.any { (it as? Map<*, *>)?.get("value") !is String }) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_options_required")
            )
        }
        val options = stored.map { (it as Map<*, *>)["value"] as String }

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

        // Validate the flags are booleans if present
        for (flag in listOf("multiple", "ordered", "open")) {
            val value = config[flag]
            if (value != null && value !is Boolean) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = s.shared("field_validation_choice_flag_type").format(flag)
                )
            }
        }

        // A ranking orders the options it has: it can neither take several unordered answers
        // nor grow new options as it is filled in.
        if (config["ordered"] == true && (config["multiple"] == true || config["open"] == true)) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_choice_ordered_exclusive")
            )
        }

        // A color is a name of the tag color vocabulary
        for (option in stored) {
            val color = (option as Map<*, *>)["color"] ?: continue
            if (com.assistant.core.themes.TagColor.entries.none { it.name == color }) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = s.shared("field_validation_choice_color_unknown").format(color.toString())
                )
            }
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
     * Config: {min?, max?, unit?, decimals}
     */
    private fun validateRangeConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Validate min <= max if both defined
        val min = config?.get("min") as? Number
        val max = config?.get("max") as? Number
        if (min != null && max != null && min.toDouble() > max.toDouble()) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_range_min_max")
            )
        }

        // Every range says how many decimals its bounds take: 0 is a whole number
        val decimals = config?.get("decimals") as? Number
        if (decimals == null || decimals.toInt() < 0) {
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
     * Validates DURATION field config.
     * Config: {precision?, form?}
     */
    private fun validateDurationConfig(config: Map<String, Any>?, s: com.assistant.core.strings.StringsContext): ValidationResult {
        // Config is optional for DURATION (minutes, composed)
        if (config == null) return ValidationResult(isValid = true)

        val precision = config["precision"]
        if (precision != null && DurationUnit.entries.none { it.name == precision }) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_duration_precision").format(precision.toString())
            )
        }

        val form = config["form"]
        if (form != null && DurationForm.entries.none { it.name == form }) {
            return ValidationResult(
                isValid = false,
                errorMessage = s.shared("field_validation_duration_form").format(form.toString())
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
