package app.treelune.core.coordinator

/**
 * Possible sources of an operation
 */
enum class OperationSource {
    USER,        // Action initiated by user via UI
    AI,          // Command sent by AI
    SCHEDULER,   // Scheduled/automatic task
    TRIGGER,     // Automatic trigger (threshold, event)
    CASCADE      // Consequence of another operation
}