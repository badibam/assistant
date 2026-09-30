package com.assistant.core.commands

/**
 * Result of command execution
 */
data class CommandResult(
    val status: CommandStatus,
    val message: String? = null,
    val data: Map<String, Any>? = null,   // Result data from operation
    val error: String? = null
)

/**
 * Status of command execution
 */
enum class CommandStatus {
    SUCCESS,                  // Command executed successfully
    ERROR,                   // Command failed with error
    CANCELLED,               // Command was cancelled
    UNKNOWN_ACTION          // Action not recognized
}