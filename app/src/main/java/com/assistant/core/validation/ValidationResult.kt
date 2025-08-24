package com.assistant.core.validation

/**
 * Result of a validation operation
 * Used to communicate validation success/failure with detailed error messages
 */
sealed class ValidationResult {
    
    /**
     * Validation succeeded
     */
    object Success : ValidationResult()
    
    /**
     * Validation failed with error message
     */
    data class Error(val message: String) : ValidationResult()
    
    // Convenience properties
    val isValid: Boolean
        get() = when (this) {
            is Success -> true
            is Error -> false
        }
    
    val errorMessage: String?
        get() = when (this) {
            is Success -> null
            is Error -> message
        }
    
    companion object {
        fun success() = Success
        fun error(message: String) = Error(message)
    }
}