package com.assistant.core.validation

/**
 * Result of a validation operation
 * Simple data class with success/error states
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        fun success(): ValidationResult = ValidationResult(true)
        
        fun error(message: String): ValidationResult = ValidationResult(false, message)
    }
}