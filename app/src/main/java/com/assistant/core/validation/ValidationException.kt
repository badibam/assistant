package com.assistant.core.validation

/**
 * Exception thrown when schema validation or processing fails
 * Used to indicate critical validation errors that should not be ignored
 */
class ValidationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)