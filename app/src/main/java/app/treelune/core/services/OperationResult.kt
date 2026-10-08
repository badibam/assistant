package app.treelune.core.services

/**
 * Result of a service operation
 *
 * Standard return type for all ExecutableService implementations.
 */
data class OperationResult(
    val success: Boolean,
    val data: Map<String, Any>? = null,
    val error: String? = null,
    val cancelled: Boolean = false
) {
    companion object {
        fun success(data: Map<String, Any>? = null) = OperationResult(true, data)

        fun error(message: String) = OperationResult(false, error = message)

        /** A refusal that also says what the caller needs to act on it, such as what a change would cost. */
        fun error(message: String, data: Map<String, Any>) = OperationResult(false, data = data, error = message)
        fun cancelled() = OperationResult(false, cancelled = true)
    }
}