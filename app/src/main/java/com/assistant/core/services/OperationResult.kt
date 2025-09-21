package com.assistant.core.services

/**
 * Result of a service operation
 *
 * Standard return type for all ExecutableService implementations.
 * Supports multi-step operations for heavy computations.
 */
data class OperationResult(
    val success: Boolean,
    val data: Map<String, Any>? = null,
    val error: String? = null,
    val cancelled: Boolean = false,
    // Multi-step operation support
    val requiresBackground: Boolean = false,
    val requiresContinuation: Boolean = false
) {
    companion object {
        fun success(
            data: Map<String, Any>? = null,
            requiresBackground: Boolean = false,
            requiresContinuation: Boolean = false
        ) = OperationResult(true, data, requiresBackground = requiresBackground, requiresContinuation = requiresContinuation)

        fun error(message: String) = OperationResult(false, error = message)
        fun cancelled() = OperationResult(false, cancelled = true)
    }
}