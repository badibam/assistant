package com.assistant.core.ai.data

/**
 * System message structure for AI operation results
 * Used for data requests and action execution feedback
 */
data class SystemMessage(
    val type: SystemMessageType,
    val commandResults: List<CommandResult>,
    val summary: String // Human-readable summary for UI display
)

enum class SystemMessageType {
    DATA_ADDED,      // Data requests completed and added to context
    ACTIONS_EXECUTED // AI actions execution results
}

/**
 * Individual command execution result
 */
data class CommandResult(
    val command: String,    // Command executed (e.g., "tool_data.get")
    val status: CommandStatus,
    val details: String?    // Error message or result summary
)

enum class CommandStatus {
    SUCCESS,
    FAILED,
    CANCELLED
}