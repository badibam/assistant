package com.assistant.core.commands

/**
 * Represents a JSON command - universal format for AI, App, and internal communication
 */
data class Command(
    val action: String,                    // "create->tool_instance", "execute->tools->tracking->add_entry"
    val params: Map<String, Any> = emptyMap(), // Command parameters
    val id: String? = null,               // Optional command ID for reference
    // AI-specific fields (optional)
    val description: String? = null,       // Human-readable description (from AI)
    val reason: String? = null            // Why this command is being executed (from AI)
)

/**
 * Result of command execution
 */
data class CommandResult(
    val commandIndex: Int? = null,        // Index in batch if applicable
    val commandId: String? = null,        // Original command ID if provided
    val status: CommandStatus,
    val message: String? = null,
    val requestedData: Map<String, Any>? = null, // Data requested by command
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
    VALIDATION_REQUIRED,     // User validation needed before execution
    PERMISSION_DENIED,       // Command not allowed
    INVALID_FORMAT,          // Malformed command
    UNKNOWN_ACTION          // Action not recognized
}