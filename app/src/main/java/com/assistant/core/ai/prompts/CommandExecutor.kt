package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of executing a single command
 */
data class CommandResult(
    val dataTitle: String,         // Title/header for data section in prompt
    val formattedData: String,     // JSON formatted data for prompt
    val systemMessage: String       // Summary message for conversation history
)

/**
 * Executes ExecutableCommands and formats results for prompt inclusion
 *
 * Replaces QueryExecutor as part of the command system restructure.
 * Used by both PromptManager (for Level 2/4) and AI command processing.
 *
 * Core responsibilities:
 * - Execute ExecutableCommand configurations to get actual data results
 * - Format results as JSON with metadata first for prompt inclusion
 * - Generate system messages summarizing query results
 * - Return CommandResult list for flexible handling by caller
 *
 * Note: Token validation and deduplication handled by PromptManager
 */
class CommandExecutor(private val context: Context) {

    private val coordinator = Coordinator(context)

    /**
     * Execute a list of ExecutableCommands and collect results
     *
     * Executes all commands regardless of individual failures.
     * Returns list of CommandResult with formatted data and system messages.
     *
     * @param commands The commands to execute
     * @param level The level name for logging purposes
     * @return List of CommandResult (one per successful command)
     */
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        level: String = "unknown"
    ): List<CommandResult> {
        LogManager.aiPrompt("CommandExecutor executing ${commands.size} commands for $level")

        if (commands.isEmpty()) {
            LogManager.aiPrompt("No commands to execute, returning empty list")
            return emptyList()
        }

        val results = mutableListOf<CommandResult>()

        for ((index, command) in commands.withIndex()) {
            LogManager.aiPrompt("Executing command ${index + 1}/${commands.size}: ${command.resource}.${command.operation}")

            val result = executeCommand(command)

            if (result != null) {
                results.add(result)
                LogManager.aiPrompt("Command ${index + 1} succeeded: ${result.systemMessage}")
            } else {
                LogManager.aiPrompt("Command ${index + 1} failed - continuing with remaining commands")
            }
        }

        LogManager.aiPrompt("CommandExecutor completed for $level: ${results.size}/${commands.size} commands successful")

        return results
    }

    /**
     * Execute a single ExecutableCommand through coordinator
     *
     * Routes to coordinator using resource.operation pattern and returns
     * CommandResult with formatted data and system message.
     */
    private suspend fun executeCommand(command: ExecutableCommand): CommandResult? {
        LogManager.aiPrompt("Executing ExecutableCommand: resource=${command.resource}, operation=${command.operation}")

        return withContext(Dispatchers.IO) {
            try {
                val commandString = "${command.resource}.${command.operation}"
                val paramsMap = command.params
                val paramsJson = org.json.JSONObject()
                paramsMap.forEach { (key, value) -> paramsJson.put(key, value) }

                LogManager.aiPrompt("Calling coordinator: $commandString with params: $paramsJson")

                val result = coordinator.processUserAction(commandString, paramsMap)

                if (result.isSuccess) {
                    val data = result.data ?: emptyMap()
                    val isActionCommand = command.operation in listOf("create", "update", "delete", "batch_create", "batch_update")

                    // For actions, data may be empty or minimal (just success/ID)
                    // For queries, empty data is unusual
                    if (data.isEmpty() && !isActionCommand) {
                        LogManager.aiPrompt("Query succeeded but returned empty data")
                        return@withContext CommandResult("", "", "Query executed but returned no data")
                    }

                    val dataTitle = generateDataTitle(command, data)
                    val formattedData = if (isActionCommand) "" else formatResultData(command, data)
                    val systemMessage = generateSystemMessage(command, data)

                    LogManager.aiPrompt("Command succeeded: $systemMessage")
                    return@withContext CommandResult(dataTitle, formattedData, systemMessage)
                } else {
                    LogManager.aiPrompt("Command failed: ${result.error}", "WARN")
                    return@withContext null
                }

            } catch (e: Exception) {
                LogManager.aiPrompt("Command execution failed: ${e.message}", "ERROR", e)
                return@withContext null
            }
        }
    }

    /**
     * Generate title/header for data section in prompt
     * Creates descriptive title with context (tool name, filters, period, etc.)
     * Returns empty string for action commands (create, update, delete, batch_create, batch_update)
     */
    private fun generateDataTitle(command: ExecutableCommand, data: Map<String, Any>): String {
        // No title needed for action commands
        if (command.operation in listOf("create", "update", "delete", "batch_create", "batch_update")) {
            return ""
        }

        return try {
            when (command.resource) {
                "tool_data" -> {
                    val toolName = data["toolInstanceName"] as? String ?: "unknown tool"
                    val count = data["count"] as? Int ?: 0
                    val parts = mutableListOf("Data from tool '$toolName'")

                    // Add period info if present
                    val startTime = command.params["startTime"] as? Long
                    val endTime = command.params["endTime"] as? Long
                    if (startTime != null && endTime != null) {
                        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(startTime))
                        val endDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(endTime))
                        parts.add("period: $startDate to $endDate")
                    }

                    // Add limit if present
                    val limit = command.params["limit"] as? Int
                    if (limit != null) {
                        parts.add("limit: $limit")
                    }

                    parts.add("($count records)")
                    parts.joinToString(", ")
                }
                "schemas" -> {
                    val schemaName = data["name"] as? String ?: data["id"] as? String ?: "unknown"
                    "Schema: $schemaName"
                }
                "tools" -> {
                    when (command.operation) {
                        "get" -> {
                            val toolName = data["name"] as? String ?: "unknown"
                            "Configuration for tool '$toolName'"
                        }
                        "list" -> {
                            val zoneId = command.params["zone_id"] as? String
                            if (zoneId != null) {
                                "Tool instances in zone"
                            } else {
                                "All tool instances"
                            }
                        }
                        "list_all" -> "All tool instances"
                        else -> ""
                    }
                }
                "zones" -> {
                    when (command.operation) {
                        "get" -> {
                            val zoneName = data["name"] as? String ?: "unknown"
                            "Configuration for zone '$zoneName'"
                        }
                        "list" -> "All zones"
                        else -> ""
                    }
                }
                else -> {
                    "Data from ${command.resource}.${command.operation}"
                }
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to generate data title: ${e.message}", "WARN")
            ""
        }
    }

    /**
     * Format result data as JSON with metadata first
     * Reorganizes data to put important metadata before bulk data
     */
    private fun formatResultData(command: ExecutableCommand, data: Map<String, Any>): String {
        return try {
            val reordered = mutableMapOf<String, Any>()

            // Extract metadata keys first based on command type
            when (command.resource) {
                "tool_data" -> {
                    // Metadata: toolInstanceName, count
                    // Bulk data: records
                    data["toolInstanceName"]?.let { reordered["toolInstanceName"] = it }
                    data["count"]?.let { reordered["count"] = it }
                    data["records"]?.let { reordered["records"] = it }
                }
                "schemas" -> {
                    // Metadata: id, name
                    // Bulk data: schema
                    data["id"]?.let { reordered["id"] = it }
                    data["name"]?.let { reordered["name"] = it }
                    data["schema"]?.let { reordered["schema"] = it }
                }
                "tools" -> {
                    // Config or list
                    data["id"]?.let { reordered["id"] = it }
                    data["name"]?.let { reordered["name"] = it }
                    data["toolType"]?.let { reordered["toolType"] = it }
                    // Add remaining fields
                    data.forEach { (key, value) ->
                        if (key !in reordered) reordered[key] = value
                    }
                }
                "zones" -> {
                    // List or single zone
                    data["id"]?.let { reordered["id"] = it }
                    data["name"]?.let { reordered["name"] = it }
                    // Add remaining fields
                    data.forEach { (key, value) ->
                        if (key !in reordered) reordered[key] = value
                    }
                }
                else -> {
                    // Default: keep original order
                    reordered.putAll(data)
                }
            }

            // Add any remaining keys not yet added
            data.forEach { (key, value) ->
                if (key !in reordered) reordered[key] = value
            }

            org.json.JSONObject(reordered as Map<*, *>).toString(2)
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to format result data: ${e.message}", "WARN")
            org.json.JSONObject(data).toString(2)
        }
    }

    /**
     * Generate system message summarizing query result or action
     * Creates human-readable summary for conversation history
     */
    private fun generateSystemMessage(command: ExecutableCommand, data: Map<String, Any>): String {
        return try {
            // Handle action commands (create, update, delete, batch operations)
            when (command.operation) {
                "create" -> {
                    val name = data["name"] as? String ?: data["id"] as? String
                    return when (command.resource) {
                        "tools" -> "Created tool instance${if (name != null) " '$name'" else ""}"
                        "zones" -> "Created zone${if (name != null) " '$name'" else ""}"
                        "tool_data" -> "Created data point${if (name != null) " '$name'" else ""}"
                        else -> "Created ${command.resource}${if (name != null) " '$name'" else ""}"
                    }
                }
                "update" -> {
                    val name = data["name"] as? String ?: data["id"] as? String
                    return when (command.resource) {
                        "tools" -> "Updated tool instance${if (name != null) " '$name'" else ""}"
                        "zones" -> "Updated zone${if (name != null) " '$name'" else ""}"
                        "tool_data" -> "Updated data point${if (name != null) " '$name'" else ""}"
                        else -> "Updated ${command.resource}${if (name != null) " '$name'" else ""}"
                    }
                }
                "delete" -> {
                    val name = data["name"] as? String ?: data["id"] as? String
                    return when (command.resource) {
                        "tools" -> "Deleted tool instance${if (name != null) " '$name'" else ""}"
                        "zones" -> "Deleted zone${if (name != null) " '$name'" else ""}"
                        "tool_data" -> "Deleted data point${if (name != null) " '$name'" else ""}"
                        else -> "Deleted ${command.resource}${if (name != null) " '$name'" else ""}"
                    }
                }
                "batch_create" -> {
                    val count = data["count"] as? Int ?: data["created_count"] as? Int ?: 0
                    val toolName = data["toolInstanceName"] as? String
                    return when (command.resource) {
                        "tool_data" -> "Created $count data points${if (toolName != null) " in tool '$toolName'" else ""}"
                        else -> "Batch created $count ${command.resource}"
                    }
                }
                "batch_update" -> {
                    val count = data["count"] as? Int ?: data["updated_count"] as? Int ?: 0
                    val toolName = data["toolInstanceName"] as? String
                    return when (command.resource) {
                        "tool_data" -> "Updated $count data points${if (toolName != null) " in tool '$toolName'" else ""}"
                        else -> "Batch updated $count ${command.resource}"
                    }
                }
            }

            // Handle query commands (get, list, etc.)
            when (command.resource) {
                "tool_data" -> {
                    val toolName = data["toolInstanceName"] as? String ?: "unknown tool"
                    val count = data["count"] as? Int ?: 0
                    "$count data points from tool '$toolName' added"
                }
                "schemas" -> {
                    val schemaName = data["name"] as? String ?: data["id"] as? String ?: "unknown schema"
                    "Schema '$schemaName' integrated"
                }
                "tools" -> {
                    when (command.operation) {
                        "get" -> {
                            val toolName = data["name"] as? String ?: "unknown tool"
                            "Configuration for tool '$toolName' integrated"
                        }
                        "list", "list_all" -> {
                            val tools = data["tools"] as? List<*>
                            val count = tools?.size ?: 0
                            "$count tool instances added"
                        }
                        else -> "Tool data integrated"
                    }
                }
                "zones" -> {
                    when (command.operation) {
                        "get" -> {
                            val zoneName = data["name"] as? String ?: "unknown zone"
                            "Zone '$zoneName' configuration integrated"
                        }
                        "list" -> {
                            val zones = data["zones"] as? List<*>
                            val count = zones?.size ?: 0
                            "$count zones added"
                        }
                        else -> "Zone data integrated"
                    }
                }
                else -> {
                    "Data from ${command.resource}.${command.operation} integrated"
                }
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to generate system message: ${e.message}", "WARN")
            "Command executed"
        }
    }
}