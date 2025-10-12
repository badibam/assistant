package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * AI command processor for validating and processing commands from AI responses
 *
 * Core responsibilities:
 * - Validate security constraints for AI-generated commands
 * - Enforce data access limits and token management
 * - Process both dataCommands (queries) and actionCommands (actions)
 * - Apply automation-specific resolution for relative parameters
 * - Manage execution permissions and safety checks
 * - Implement cascade failure logic for action commands (stop on first action failure)
 */
class AICommandProcessor(private val context: Context) {

    /**
     * Process AI data commands (queries) with security validation
     *
     * All AI dataCommands are marked as relative (isRelative=true) to ensure:
     * - AI uses relative period format: period_start/period_end with "offset_TYPE" (e.g., "-7_DAY")
     * - Automatic resolution using user's dayStartHour and weekStartDay configuration
     * - AI doesn't need to handle timestamps, timezones, or calendar calculations
     *
     * @param commands List of DataCommands from AI for data retrieval
     * @return List of ExecutableCommands ready for coordinator dispatch
     */
    fun processDataCommands(commands: List<DataCommand>): List<ExecutableCommand> {
        LogManager.aiService("AICommandProcessor processing ${commands.size} data commands from AI", "DEBUG")

        // TODO: Add AI-specific validations for data commands
        // 1. Token limit enforcement per command (prevent excessive data loading)
        // 2. Data access permissions checking (verify AI can access requested data)
        // 3. Parameter sanitization (validate dates, IDs, limits)
        // 4. Rate limiting for repeated queries

        // Force isRelative=true for all AI dataCommands to enable relative period resolution
        val relativeCommands = commands.map { command ->
            command.copy(isRelative = true)
        }

        LogManager.aiService("Marked ${relativeCommands.size} AI dataCommands as relative", "DEBUG")

        // Delegate transformation to shared CommandTransformer
        val executableCommands = CommandTransformer.transformToExecutable(relativeCommands, context)

        LogManager.aiService("AICommandProcessor generated ${executableCommands.size} executable data commands", "DEBUG")
        return executableCommands
    }

    /**
     * Process AI action commands with strict security validation
     *
     * IMPORTANT: Actions use cascade failure logic - if any action fails during execution,
     * all subsequent actions are cancelled to prevent inconsistent application state.
     * This is different from data commands which continue on individual failures.
     *
     * @param commands List of DataCommands from AI for action execution
     * @return List of ExecutableCommands ready for coordinator dispatch
     */
    suspend fun processActionCommands(commands: List<DataCommand>): List<ExecutableCommand> {
        LogManager.aiService("AICommandProcessor processing ${commands.size} action commands from AI", "DEBUG")

        // TODO: Implement AI action command strict validations
        // 1. Permission level checking (autonomous/validation_required/forbidden/ask_first)
        // 2. Action scope validation (verify action targets valid resources)
        // 3. Parameter sanitization and validation (prevent malicious data)
        // 4. Rate limiting and resource protection (prevent abuse)
        // 5. Batch operation limits (max items per batch)
        // 6. CASCADE FAILURE enforcement (handled at execution level by CommandExecutor)

        val executableCommands = mutableListOf<ExecutableCommand>()

        for (command in commands) {
            try {
                val executableCommand = transformActionCommand(command)
                executableCommand?.let { executableCommands.add(it) }
            } catch (e: Exception) {
                LogManager.aiService("Failed to transform action command ${command.type}: ${e.message}", "ERROR", e)
            }
        }

        LogManager.aiService("AICommandProcessor generated ${executableCommands.size} executable action commands", "DEBUG")
        return executableCommands
    }

    // ========================================================================================
    // Private Transformation Methods
    // ========================================================================================

    /**
     * Transform action command types to executable commands
     * Maps abstract AI action types to concrete resource.operation format
     *
     * Note: Parameter naming inconsistency exists between services:
     * - tools.* uses tool_instance_id (snake_case)
     * - tool_data.* uses toolInstanceId (camelCase)
     * This will be unified in a future refactoring
     */
    private suspend fun transformActionCommand(command: DataCommand): ExecutableCommand? {
        return when (command.type) {
            // Tool data actions - batch operations by default (per AI.md line 182)
            // Schema ID enrichment: automatically inject data_schema_id from tool instance config
            "CREATE_DATA" -> {
                LogManager.aiService("CREATE_DATA original params keys: ${command.params.keys}", "DEBUG")
                val enrichedParams = enrichWithSchemaId(command.params)
                LogManager.aiService("CREATE_DATA enriched params keys: ${enrichedParams.keys}", "DEBUG")
                ExecutableCommand(
                    resource = "tool_data",
                    operation = "batch_create",
                    params = enrichedParams
                )
            }
            "UPDATE_DATA" -> {
                val enrichedParams = enrichWithSchemaId(command.params)
                ExecutableCommand(
                    resource = "tool_data",
                    operation = "batch_update",
                    params = enrichedParams
                )
            }
            "DELETE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_delete",
                params = command.params
            )

            // Tool instance actions
            "CREATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "create",
                    params = transformedParams
                )
            }
            "UPDATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "update",
                    params = transformedParams
                )
            }
            "DELETE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "delete",
                params = command.params
            )

            // Zone actions
            "CREATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "create",
                params = command.params
            )
            "UPDATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "update",
                params = command.params
            )
            "DELETE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "delete",
                params = command.params
            )

            else -> {
                LogManager.aiService("Unknown action command type: ${command.type}", "WARN")
                null
            }
        }
    }

    /**
     * Transform a single action command for verbalization (without enrichment)
     * Public method for ActionVerbalizerHelper to transform actions
     *
     * @param command DataCommand to transform
     * @return ExecutableCommand ready for verbalization, or null if unknown type
     */
    fun transformActionForVerbalization(command: DataCommand): ExecutableCommand? {
        return when (command.type) {
            // Tool data actions
            "CREATE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_create",
                params = command.params
            )
            "UPDATE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_update",
                params = command.params
            )
            "DELETE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_delete",
                params = command.params
            )

            // Tool instance actions
            "CREATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "create",
                    params = transformedParams
                )
            }
            "UPDATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "update",
                    params = transformedParams
                )
            }
            "DELETE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "delete",
                params = command.params
            )

            // Zone actions
            "CREATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "create",
                params = command.params
            )
            "UPDATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "update",
                params = command.params
            )
            "DELETE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "delete",
                params = command.params
            )

            else -> {
                LogManager.aiService("Unknown action command type for verbalization: ${command.type}", "WARN")
                null
            }
        }
    }

    /**
     * Enrich CREATE_DATA/UPDATE_DATA params with schema_id from tool instance config
     *
     * AI doesn't need to specify schema_id - we automatically fetch it from the
     * tool instance's data_schema_id configuration field.
     *
     * @param params Original params from AI command
     * @return Enriched params with schema_id added to each entry
     */
    private suspend fun enrichWithSchemaId(params: Map<String, Any>): Map<String, Any> {
        val toolInstanceId = params["toolInstanceId"] as? String

        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiService("Cannot enrich schema_id: toolInstanceId missing", "ERROR")
            return params
        }

        try {
            // Get tool instance config to extract data_schema_id
            val coordinator = Coordinator(context)
            val result = coordinator.processUserAction(
                "tools.get",
                mapOf("tool_instance_id" to toolInstanceId)
            )

            if (!result.isSuccess) {
                LogManager.aiService(
                    "Failed to fetch tool instance for schema enrichment: ${result.error}",
                    "ERROR"
                )
                return params
            }

            // tools.get returns { "tool_instance": { "config_json": "...", ... } }
            val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
            if (toolInstance == null) {
                LogManager.aiService(
                    "Tool instance $toolInstanceId not found in result",
                    "ERROR"
                )
                return params
            }

            val configJson = toolInstance["config_json"] as? String
            if (configJson.isNullOrEmpty()) {
                LogManager.aiService(
                    "Tool instance $toolInstanceId has no config_json",
                    "ERROR"
                )
                return params
            }

            val config = JSONObject(configJson)
            val dataSchemaId = config.optString("data_schema_id")

            if (dataSchemaId.isEmpty()) {
                LogManager.aiService(
                    "Tool instance $toolInstanceId config has no data_schema_id",
                    "ERROR"
                )
                return params
            }

            // Enrich entries with schema_id
            val entries = params["entries"] as? List<*>
            if (entries == null) {
                LogManager.aiService("No entries found to enrich with schema_id", "WARN")
                return params
            }

            val enrichedEntries = entries.map { entry ->
                if (entry is Map<*, *>) {
                    entry.toMutableMap().apply {
                        put("schema_id", dataSchemaId)
                    }
                } else {
                    entry
                }
            }

            LogManager.aiService(
                "Enriched ${enrichedEntries.size} entries with schema_id: $dataSchemaId",
                "DEBUG"
            )

            return params.toMutableMap().apply {
                put("entries", enrichedEntries)
            }

        } catch (e: Exception) {
            LogManager.aiService(
                "Exception during schema enrichment: ${e.message}",
                "ERROR",
                e
            )
            return params
        }
    }

    /**
     * Transform tool params from AI format to service format
     * Converts "config" object to "config_json" string for ToolInstanceService
     *
     * @param params Original params from AI command with "config" as object
     * @return Transformed params with "config_json" as JSON string
     */
    private fun transformToolParams(params: Map<String, Any>): Map<String, Any> {
        val config = params["config"]

        if (config == null) {
            LogManager.aiService("CREATE_TOOL/UPDATE_TOOL missing config parameter", "WARN")
            return params
        }

        return params.toMutableMap().apply {
            // Remove "config" key
            remove("config")

            // Add "config_json" key with JSON string
            val configJson = when (config) {
                is Map<*, *> -> JSONObject(config as Map<String, Any>).toString()
                is String -> config  // Already a JSON string
                else -> {
                    LogManager.aiService("Unexpected config type: ${config::class.java.simpleName}", "WARN")
                    config.toString()
                }
            }
            put("config_json", configJson)

            LogManager.aiService("Transformed config object to config_json string", "DEBUG")
        }
    }
}