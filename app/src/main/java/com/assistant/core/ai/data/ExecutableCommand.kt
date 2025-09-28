package com.assistant.core.ai.data

/**
 * Executable command structure for the coordinator dispatch system
 *
 * Represents a command that has been processed and is ready for execution
 * through the CommandDispatcher system using the resource.operation pattern.
 *
 * This is the final stage of the command processing pipeline:
 * EnrichmentBlock → DataCommand → ExecutableCommand → coordinator.processUserAction()
 *
 * Examples:
 * - resource="zones", operation="create", params=mapOf("name" to "Santé")
 * - resource="tool_data", operation="get", params=mapOf("toolInstanceId" to "123")
 * - resource="tools", operation="list", params=mapOf("zone_id" to "456")
 */
data class ExecutableCommand(
    val resource: String,         // Resource name for CommandDispatcher routing ("zones", "tool_data", etc.)
    val operation: String,        // Operation name for service execution ("get", "create", "update", "delete")
    val params: Map<String, Any>  // Resolved parameters ready for coordinator execution
)