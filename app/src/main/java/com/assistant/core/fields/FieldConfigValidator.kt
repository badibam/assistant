package com.assistant.core.fields

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
                errorMessage = "Le nom d'affichage ne peut pas être vide"
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
                errorMessage = "Le nom technique du champ ne peut pas être vide"
            )
        }

        if (!name.matches(Regex("^[a-z][a-z0-9_]*[a-z0-9]$")) && !name.matches(Regex("^[a-z]$"))) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Le nom technique doit être en format snake_case (lettres minuscules, chiffres, underscores)"
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
                errorMessage = "Un champ avec le nom technique '$name' existe déjà"
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Validates that the config is appropriate for the field type.
     *
     * V1: Only TEXT_UNLIMITED supported, config must be null
     * Future: Add validation for other types (SCALE requires min/max/labels, CHOICE requires options, etc.)
     */
    private fun validateTypeConfig(type: FieldType, config: Map<String, Any>?): ValidationResult {
        return when (type) {
            FieldType.TEXT_UNLIMITED -> {
                // TEXT_UNLIMITED does not require config (should be null)
                if (config != null) {
                    ValidationResult(
                        isValid = false,
                        errorMessage = "Le type TEXT_UNLIMITED ne nécessite pas de configuration"
                    )
                } else {
                    ValidationResult(isValid = true)
                }
            }
            // Future types validation will be added here with when branches
            // Example for future SCALE type:
            // FieldType.SCALE -> {
            //     if (config == null) {
            //         return ValidationResult(false, "SCALE type requires config with min, max, min_label, max_label")
            //     }
            //     val min = config["min"] as? Number
            //     val max = config["max"] as? Number
            //     if (min == null || max == null) {
            //         return ValidationResult(false, "SCALE config must have min and max")
            //     }
            //     if (min.toDouble() >= max.toDouble()) {
            //         return ValidationResult(false, "SCALE min must be less than max")
            //     }
            //     ValidationResult(isValid = true)
            // }
        }
    }
}

/**
 * Result of field validation.
 *
 * @property isValid Whether the field definition is valid
 * @property errorMessage Human-readable error message if invalid (null if valid)
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
