package com.assistant.core.ai

import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * AICommandProcessor
 *
 * Transforms abstract AI command types into concrete coordinator operations (resource.operation format).
 * Execution and SystemMessage generation handled by CommandExecutor.
 *
 * Pipeline: AI Response → Abstract Commands → ExecutableCommands → CommandExecutor
 *
 * Abstract command types (used by AI):
 * - Query: TOOL_DATA, TOOL_CONFIG, TOOL_INSTANCES, ZONE_CONFIG, ZONES, SCHEMA
 * - Actions: CREATE_DATA, UPDATE_DATA, DELETE_DATA, CREATE_TOOL, UPDATE_TOOL, DELETE_TOOL,
 *           CREATE_ZONE, UPDATE_ZONE, DELETE_ZONE
 *
 * Concrete operations (used by coordinators):
 * - tool_data.list, tool_data.batch_create, tool_data.batch_update, tool_data.batch_delete
 * - tool_instances.list, tool_instances.get, tool_instances.create, tool_instances.update, tool_instances.delete
 * - zones.list, zones.get, zones.create, zones.update, zones.delete
 * - schemas.get
 */
object AICommandProcessor {

    /**
     * Process AI commands (both data and actions)
     * Transforms abstract command types to ExecutableCommands
     *
     * @param commands List of AI commands with abstract types
     * @param context Android context
     * @return List of ExecutableCommands ready for CommandExecutor
     * @throws IllegalArgumentException if command type is invalid or parameters are missing
     */
    suspend fun processCommands(
        commands: List<AICommand>,
        context: Context
    ): List<ExecutableCommand> {
        LogManager.aiPrompt("Processing ${commands.size} AI commands", "INFO")

        val executableCommands = mutableListOf<ExecutableCommand>()

        for (command in commands) {
            try {
                val executable = transformToExecutableCommand(command, context)
                executableCommands.add(executable)
                LogManager.aiPrompt("Transformed ${command.type} → ${executable.resource}.${executable.operation}", "DEBUG")
            } catch (e: Exception) {
                LogManager.aiPrompt("Failed to transform command ${command.type}: ${e.message}", "ERROR", e)
                throw IllegalArgumentException("Invalid AI command ${command.type}: ${e.message}", e)
            }
        }

        return executableCommands
    }

    /**
     * Transform a single AI command to an ExecutableCommand
     * Maps abstract command types to concrete resource.operation format
     *
     * @param command AI command with abstract type
     * @param context Android context
     * @return ExecutableCommand ready for CommandExecutor
     * @throws IllegalArgumentException if transformation fails
     */
    private fun transformToExecutableCommand(
        command: AICommand,
        context: Context
    ): ExecutableCommand {
        val params = command.params

        // Map abstract command type to resource.operation
        val (resource, operation) = when (command.type) {
            // Query commands
            "TOOL_DATA" -> {
                requireParam(params, "tool_instance_id", command.type)
                "tool_data" to "list"
            }
            "TOOL_CONFIG" -> {
                requireParam(params, "tool_instance_id", command.type)
                "tool_instances" to "get"
            }
            "TOOL_INSTANCES" -> {
                "tool_instances" to "list"
            }
            "ZONE_CONFIG" -> {
                requireParam(params, "zone_id", command.type)
                "zones" to "get"
            }
            "ZONES" -> {
                "zones" to "list"
            }
            "SCHEMA" -> {
                requireParam(params, "schema_id", command.type)
                "schemas" to "get"
            }

            // Action commands - Data operations (batch by default)
            "CREATE_DATA" -> {
                requireParam(params, "tool_instance_id", command.type)
                requireParam(params, "tooltype", command.type)
                requireParam(params, "entries", command.type)
                "tool_data" to "batch_create"
            }
            "UPDATE_DATA" -> {
                requireParam(params, "entries", command.type)
                "tool_data" to "batch_update"
            }
            "DELETE_DATA" -> {
                requireParam(params, "ids", command.type)
                "tool_data" to "batch_delete"
            }

            // Action commands - Tool operations
            "CREATE_TOOL" -> {
                requireParam(params, "zone_id", command.type)
                requireParam(params, "tool_type", command.type)
                requireParam(params, "config", command.type)
                "tool_instances" to "create"
            }
            "UPDATE_TOOL" -> {
                requireParam(params, "tool_instance_id", command.type)
                requireParam(params, "config", command.type)
                "tool_instances" to "update"
            }
            "DELETE_TOOL" -> {
                requireParam(params, "tool_instance_id", command.type)
                "tool_instances" to "delete"
            }

            // Action commands - Zone operations
            "CREATE_ZONE" -> {
                requireParam(params, "name", command.type)
                "zones" to "create"
            }
            "UPDATE_ZONE" -> {
                requireParam(params, "zone_id", command.type)
                "zones" to "update"
            }
            "DELETE_ZONE" -> {
                requireParam(params, "zone_id", command.type)
                "zones" to "delete"
            }

            else -> throw IllegalArgumentException("Unknown command type: ${command.type}")
        }

        // Convert JSONObject params to Map
        val paramsMap = mutableMapOf<String, Any>()
        params.keys().forEach { key ->
            paramsMap[key] = params.get(key)
        }

        // Create ExecutableCommand
        return ExecutableCommand(
            resource = resource,
            operation = operation,
            params = paramsMap
        )
    }

    /**
     * Validate that a required parameter exists in params
     *
     * @param params JSONObject containing command parameters
     * @param paramName Name of required parameter
     * @param commandType Command type for error message
     * @throws IllegalArgumentException if parameter is missing
     */
    private fun requireParam(params: JSONObject, paramName: String, commandType: String) {
        if (!params.has(paramName)) {
            throw IllegalArgumentException("Missing required parameter '$paramName' for command type $commandType")
        }
    }
}

/**
 * AI Command data class
 * Represents an abstract command from the AI before transformation to ExecutableCommand
 *
 * @property id Unique identifier for this command
 * @property type Abstract command type (e.g., "TOOL_DATA", "CREATE_DATA")
 * @property params Command parameters as JSONObject
 */
data class AICommand(
    val id: String,
    val type: String,
    val params: JSONObject
)