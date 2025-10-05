package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of executing a single command for prompt formatting
 */
data class PromptCommandResult(
    val dataTitle: String,         // Title/header for data section in prompt
    val formattedData: String      // JSON formatted data for prompt
)

/**
 * Complete execution result including prompt data and system message
 */
data class CommandExecutionResult(
    val promptResults: List<PromptCommandResult>,  // For prompt inclusion
    val systemMessage: SystemMessage                // For conversation history
)

/**
 * Executes ExecutableCommands and formats results for prompt inclusion and conversation history
 *
 * Unified command executor used by:
 * - PromptManager (user enrichments Level 2/4)
 * - AICommandProcessor (AI data/action commands)
 *
 * Core responsibilities:
 * - Execute ExecutableCommand configurations via coordinator
 * - Format results as JSON with metadata first for prompt inclusion
 * - Generate SystemMessage with aggregate results for conversation history
 * - Track success/failure status for each command
 *
 * Note: Token validation and deduplication handled by PromptManager
 */
class CommandExecutor(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val s = Strings.`for`(context = context)

    /**
     * Execute commands and return complete result with prompt data + SystemMessage
     *
     * @param commands The commands to execute
     * @param messageType Type of SystemMessage (DATA_ADDED or ACTIONS_EXECUTED)
     * @param level The level name for logging purposes
     * @return CommandExecutionResult with prompt data and system message
     */
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        messageType: SystemMessageType,
        level: String = "unknown"
    ): CommandExecutionResult {
        LogManager.aiPrompt("CommandExecutor executing ${commands.size} commands for $level", "DEBUG")

        if (commands.isEmpty()) {
            LogManager.aiPrompt("No commands to execute, returning empty result", "DEBUG")
            return CommandExecutionResult(
                promptResults = emptyList(),
                systemMessage = SystemMessage(
                    type = messageType,
                    commandResults = emptyList(),
                    summary = s.shared("ai_system_no_commands")
                )
            )
        }

        val promptResults = mutableListOf<PromptCommandResult>()
        val commandResults = mutableListOf<com.assistant.core.ai.data.CommandResult>()
        var successCount = 0
        var failedCount = 0

        for ((index, command) in commands.withIndex()) {
            LogManager.aiPrompt("Executing command ${index + 1}/${commands.size}: ${command.resource}.${command.operation}", "DEBUG")

            val result = executeCommand(command)

            if (result != null) {
                promptResults.add(result.promptResult)
                commandResults.add(result.commandResult)
                if (result.commandResult.status == CommandStatus.SUCCESS) {
                    successCount++
                } else {
                    failedCount++
                }
                LogManager.aiPrompt("Command ${index + 1} succeeded", "DEBUG")
            } else {
                failedCount++
                commandResults.add(
                    com.assistant.core.ai.data.CommandResult(
                        command = "${command.resource}.${command.operation}",
                        status = CommandStatus.FAILED,
                        details = "Execution failed"
                    )
                )
                LogManager.aiPrompt("Command ${index + 1} failed - continuing with remaining commands", "WARN")
            }
        }

        // Generate summary based on message type
        val summary = generateSummary(messageType, successCount, failedCount)

        LogManager.aiPrompt("CommandExecutor completed for $level: $successCount succeeded, $failedCount failed", "INFO")

        return CommandExecutionResult(
            promptResults = promptResults,
            systemMessage = SystemMessage(
                type = messageType,
                commandResults = commandResults,
                summary = summary
            )
        )
    }

    /**
     * Internal result combining prompt data and command status
     */
    private data class InternalCommandResult(
        val promptResult: PromptCommandResult,
        val commandResult: com.assistant.core.ai.data.CommandResult
    )

    /**
     * Execute a single ExecutableCommand through coordinator
     *
     * Routes to coordinator using resource.operation pattern and returns
     * InternalCommandResult with prompt data and command status.
     */
    private suspend fun executeCommand(command: ExecutableCommand): InternalCommandResult? {
        LogManager.aiPrompt("Executing ExecutableCommand: resource=${command.resource}, operation=${command.operation}", "VERBOSE")

        return withContext(Dispatchers.IO) {
            try {
                val commandString = "${command.resource}.${command.operation}"
                val paramsMap = command.params
                val paramsJson = org.json.JSONObject()
                paramsMap.forEach { (key, value) -> paramsJson.put(key, value) }

                LogManager.aiPrompt("Calling coordinator: $commandString with params: $paramsJson", "VERBOSE")

                val result = coordinator.processUserAction(commandString, paramsMap)

                if (result.isSuccess) {
                    val data = result.data ?: emptyMap()
                    val isActionCommand = command.operation in listOf("create", "update", "delete", "batch_create", "batch_update", "batch_delete")

                    // For actions, data may be empty or minimal (just success/ID)
                    // For queries, empty data is unusual
                    if (data.isEmpty() && !isActionCommand) {
                        LogManager.aiPrompt("Query succeeded but returned empty data", "WARN")
                        return@withContext InternalCommandResult(
                            promptResult = PromptCommandResult("", ""),
                            commandResult = com.assistant.core.ai.data.CommandResult(
                                command = commandString,
                                status = CommandStatus.SUCCESS,
                                details = "No data returned"
                            )
                        )
                    }

                    val dataTitle = generateDataTitle(command, data)
                    val formattedData = if (isActionCommand) "" else formatResultData(command, data)

                    LogManager.aiPrompt("Command succeeded", "DEBUG")
                    return@withContext InternalCommandResult(
                        promptResult = PromptCommandResult(dataTitle, formattedData),
                        commandResult = com.assistant.core.ai.data.CommandResult(
                            command = commandString,
                            status = CommandStatus.SUCCESS,
                            details = null
                        )
                    )
                } else {
                    LogManager.aiPrompt("Command failed: ${result.error}", "WARN")
                    return@withContext InternalCommandResult(
                        promptResult = PromptCommandResult("", ""),
                        commandResult = com.assistant.core.ai.data.CommandResult(
                            command = commandString,
                            status = CommandStatus.FAILED,
                            details = result.error
                        )
                    )
                }

            } catch (e: Exception) {
                LogManager.aiPrompt("Command execution failed: ${e.message}", "ERROR", e)
                return@withContext null
            }
        }
    }

    /**
     * Generate human-readable summary for SystemMessage
     */
    private fun generateSummary(type: SystemMessageType, successCount: Int, failedCount: Int): String {
        return when (type) {
            SystemMessageType.DATA_ADDED -> {
                when {
                    failedCount == 0 -> s.shared("ai_system_queries_success").format(successCount)
                    successCount == 0 -> s.shared("ai_system_queries_all_failed").format(failedCount)
                    else -> s.shared("ai_system_queries_partial").format(successCount, failedCount)
                }
            }
            SystemMessageType.ACTIONS_EXECUTED -> {
                when {
                    failedCount == 0 -> s.shared("ai_system_actions_success").format(successCount)
                    successCount == 0 -> s.shared("ai_system_actions_all_failed").format(failedCount)
                    else -> s.shared("ai_system_actions_partial").format(successCount, failedCount)
                }
            }
            SystemMessageType.LIMIT_REACHED -> {
                // LIMIT_REACHED messages should provide their own summary directly
                // This case should not be reached in normal flow
                "Limit reached"
            }
            SystemMessageType.FORMAT_ERROR -> {
                // FORMAT_ERROR messages provide their own summary with error details
                // This case should not be reached in normal flow
                "Format error"
            }
            SystemMessageType.NETWORK_ERROR, SystemMessageType.SESSION_TIMEOUT -> {
                // These messages should never reach here (filtered from prompts)
                // But provide fallback just in case
                s.shared("ai_error_system_generic")
            }
        }
    }

    /**
     * Generate title/header for data section in prompt
     * Creates descriptive title with context (tool name, filters, period, etc.)
     * Returns empty string for action commands
     */
    private suspend fun generateDataTitle(command: ExecutableCommand, data: Map<String, Any>): String {
        // No title needed for action commands
        if (command.operation in listOf("create", "update", "delete", "batch_create", "batch_update", "batch_delete")) {
            return ""
        }

        return try {
            when (command.resource) {
                "tool_data" -> {
                    // Resolve tool instance name from ID in command params
                    // Note: UserCommandProcessor transforms "id" → "toolInstanceId"
                    LogManager.aiPrompt("tool_data command params keys: ${command.params.keys}", "VERBOSE")
                    LogManager.aiPrompt("toolInstanceId=${command.params["toolInstanceId"]}, id=${command.params["id"]}", "VERBOSE")

                    val toolInstanceId = command.params["toolInstanceId"] as? String
                        ?: command.params["id"] as? String

                    LogManager.aiPrompt("Resolved toolInstanceId: $toolInstanceId", "VERBOSE")

                    val toolName = if (toolInstanceId != null) {
                        val name = resolveToolInstanceName(toolInstanceId)
                        LogManager.aiPrompt("Resolved tool name: $name", "VERBOSE")
                        name
                    } else {
                        LogManager.aiPrompt("No toolInstanceId found in params!", "WARN")
                        "unknown tool"
                    }

                    val count = data["count"] as? Int
                        ?: (data["entries"] as? List<*>)?.size
                        ?: 0
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
                            // ToolInstanceService.handleGetById returns data["tool_instance"]["name"]
                            val toolInstance = data["tool_instance"] as? Map<*, *>
                            val toolName = toolInstance?.get("name") as? String ?: "unknown"
                            "Configuration for tool '$toolName'"
                        }
                        "list" -> {
                            val zoneId = command.params["zone_id"] as? String
                            if (zoneId != null) {
                                val zoneName = resolveZoneName(zoneId)
                                "Tool instances in zone '$zoneName'"
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
                            // ZoneService.handleGet returns data["zone"]["name"]
                            val zone = data["zone"] as? Map<*, *>
                            val zoneName = zone?.get("name") as? String ?: "unknown"
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
     * Resolve tool instance name from ID via coordinator
     */
    private suspend fun resolveToolInstanceName(toolInstanceId: String): String {
        return try {
            val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))

            if (result.isSuccess) {
                // tools.get returns { "tool_instance": { "name": "...", ... } }
                val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
                val name = toolInstance?.get("name") as? String
                name ?: "unknown tool"
            } else {
                LogManager.aiPrompt("Failed to resolve tool instance name for $toolInstanceId: ${result.error}", "WARN")
                "unknown tool"
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Error resolving tool instance name: ${e.message}", "WARN", e)
            "unknown tool"
        }
    }

    /**
     * Resolve zone name from ID via coordinator
     */
    private suspend fun resolveZoneName(zoneId: String): String {
        return try {
            val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
            if (result.isSuccess) {
                // zones.get returns { "zone": { "name": "...", ... } }
                val zone = result.data?.get("zone") as? Map<*, *>
                val name = zone?.get("name") as? String
                name ?: "unknown zone"
            } else {
                LogManager.aiPrompt("Failed to resolve zone name for $zoneId: ${result.error}", "WARN")
                "unknown zone"
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Error resolving zone name: ${e.message}", "WARN")
            "unknown zone"
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
}