package com.assistant.core.coordinator

/**
 * Possible states of the Coordinator
 */
enum class CoordinatorState {
    IDLE,                    // Waiting, can accept new operations
    OPERATION_IN_PROGRESS,   // Operation currently executing
    VALIDATION_REQUIRED,     // Waiting for user validation
    AI_DIALOGUE,            // AI dialogue session active
    ERROR                   // Error state, requires intervention
}

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