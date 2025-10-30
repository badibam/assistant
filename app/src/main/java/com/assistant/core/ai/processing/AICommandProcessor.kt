package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.SchemaUtils
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
     * @return TransformationResult with executable commands and transformation errors
     */
    fun processDataCommands(commands: List<DataCommand>): TransformationResult {
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
        val result = CommandTransformer.transformToExecutable(relativeCommands, context)

        LogManager.aiService("AICommandProcessor generated ${result.executableCommands.size} executable data commands, ${result.errors.size} errors", "DEBUG")
        return result
    }

    /**
     * Process AI action commands with strict security validation
     *
     * IMPORTANT: Actions use cascade failure logic - if any action fails during execution,
     * all subsequent actions are cancelled to prevent inconsistent application state.
     * This is different from data commands which continue on individual failures.
     *
     * @param commands List of DataCommands from AI for action execution
     * @return TransformationResult with executable commands and transformation errors
     */
    suspend fun processActionCommands(commands: List<DataCommand>): TransformationResult {
        LogManager.aiService("AICommandProcessor processing ${commands.size} action commands from AI", "DEBUG")

        // TODO: Implement AI action command strict validations
        // 1. Permission level checking (autonomous/validation_required/forbidden/ask_first)
        // 2. Action scope validation (verify action targets valid resources)
        // 3. Parameter sanitization and validation (prevent malicious data)
        // 4. Rate limiting and resource protection (prevent abuse)
        // 5. Batch operation limits (max items per batch)
        // 6. CASCADE FAILURE enforcement (handled at execution level by CommandExecutor)

        val executableCommands = mutableListOf<ExecutableCommand>()
        val errors = mutableListOf<String>()

        for ((index, command) in commands.withIndex()) {
            try {
                // FIRST: Strip root-level system-managed fields from data entries
                val cleanedCommand = stripRootLevelSystemManagedFields(command)

                // SECOND: Inject tooltype by deducing from toolInstanceId (single source of truth)
                val enrichedCommand = injectTooltypeIfNeeded(cleanedCommand)

                // THIRD: Transform to executable command
                val executableCommand = transformActionCommand(enrichedCommand)
                if (executableCommand == null) {
                    errors.add("Command[$index] (${command.type}): transformation returned null")
                } else {
                    executableCommands.add(executableCommand)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                LogManager.aiService("Failed to transform action command ${command.type}: $error", "ERROR", e)
                errors.add("Command[$index] (${command.type}): $error")
            }
        }

        LogManager.aiService("AICommandProcessor generated ${executableCommands.size} executable action commands, ${errors.size} errors", "DEBUG")
        return TransformationResult(executableCommands, errors)
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
                    params = enrichedParams,
                    isActionCommand = true
                )
            }
            "UPDATE_DATA" -> {
                val enrichedParams = enrichWithSchemaId(command.params)
                ExecutableCommand(
                    resource = "tool_data",
                    operation = "batch_update",
                    params = enrichedParams,
                    isActionCommand = true
                )
            }
            "DELETE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_delete",
                params = command.params,
                isActionCommand = true
            )

            // Tool instance actions
            "CREATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "create",
                    params = transformedParams,
                    isActionCommand = true
                )
            }
            "UPDATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "update",
                    params = transformedParams,
                    isActionCommand = true
                )
            }
            "DELETE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "delete",
                params = command.params,
                isActionCommand = true
            )

            // Zone actions
            "CREATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "create",
                params = command.params,
                isActionCommand = true
            )
            "UPDATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "update",
                params = command.params,
                isActionCommand = true
            )
            "DELETE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "delete",
                params = command.params,
                isActionCommand = true
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
                params = command.params,
                isActionCommand = true
            )
            "UPDATE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_update",
                params = command.params,
                isActionCommand = true
            )
            "DELETE_DATA" -> ExecutableCommand(
                resource = "tool_data",
                operation = "batch_delete",
                params = command.params,
                isActionCommand = true
            )

            // Tool instance actions
            "CREATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "create",
                    params = transformedParams,
                    isActionCommand = true
                )
            }
            "UPDATE_TOOL" -> {
                val transformedParams = transformToolParams(command.params)
                ExecutableCommand(
                    resource = "tools",
                    operation = "update",
                    params = transformedParams,
                    isActionCommand = true
                )
            }
            "DELETE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "delete",
                params = command.params,
                isActionCommand = true
            )

            // Zone actions
            "CREATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "create",
                params = command.params,
                isActionCommand = true
            )
            "UPDATE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "update",
                params = command.params,
                isActionCommand = true
            )
            "DELETE_ZONE" -> ExecutableCommand(
                resource = "zones",
                operation = "delete",
                params = command.params,
                isActionCommand = true
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
     * Note: SystemManaged fields are stripped by stripSystemManagedFromCommand()
     * before this enrichment step.
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
            // Note: params already normalized by JsonNormalizer in AIMessage.parseParams()
            // All JSONArray → List, all JSONObject → Map conversions already done
            @Suppress("UNCHECKED_CAST")
            val entries = params["entries"] as? List<*>

            if (entries == null) {
                LogManager.aiService(
                    "No entries found to enrich with schema_id (type: ${params["entries"]?.javaClass?.simpleName})",
                    "WARN"
                )
                return params
            }

            if (entries.isEmpty()) {
                LogManager.aiService("Empty entries list, cannot enrich", "WARN")
                return params
            }

            // Add schema_id to each entry for validation
            // Note: SystemManaged fields already stripped by stripRootLevelSystemManagedFields()
            val enrichedEntries = entries.map { entry ->
                if (entry is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val mutableEntry = (entry as Map<String, Any>).toMutableMap()

                    // Add schema_id for validation
                    mutableEntry["schema_id"] = dataSchemaId

                    mutableEntry
                } else {
                    LogManager.aiService(
                        "Unexpected entry type: ${entry?.javaClass?.simpleName}",
                        "WARN"
                    )
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
     * Strip root-level system-managed fields from data entries
     *
     * System-managed fields (id, created_at, updated_at, schema_id, tooltype) are:
     * - Marked with "systemManaged": true in BaseDataSchema
     * - Only present at the ROOT LEVEL of each entry
     * - Never present in nested objects (e.g., entry.data.schema_id is a DIFFERENT field for variant selection)
     *
     * This function strips ONLY at entry root level, NEVER recursively into nested objects.
     * This is critical because nested objects like entry.data contain business logic fields
     * that should never be stripped (e.g., data.schema_id for variant selection in Messages tool).
     *
     * Called BEFORE injectTooltypeIfNeeded() so that even if AI provides tooltype, it's stripped
     * and then correctly re-injected from the database (single source of truth).
     *
     * @param command DataCommand from AI with potential systemManaged fields
     * @return Cleaned DataCommand with root-level systemManaged fields removed
     */
    private fun stripRootLevelSystemManagedFields(command: DataCommand): DataCommand {
        // Only strip for CREATE_DATA and UPDATE_DATA commands
        if (command.type != "CREATE_DATA" && command.type != "UPDATE_DATA") {
            return command
        }

        val entries = command.params["entries"] as? List<*> ?: return command

        // System-managed fields from BaseDataSchema (only at entry root level)
        // Note: tooltype is stripped here and re-injected by injectTooltypeIfNeeded()
        val rootSystemManagedFields = setOf("id", "created_at", "updated_at", "schema_id", "tooltype")

        val cleanedEntries = entries.map { entry ->
            if (entry is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val mutableEntry = (entry as Map<String, Any>).toMutableMap()

                // Strip only at root level - do NOT recurse into nested objects
                rootSystemManagedFields.forEach { field ->
                    if (mutableEntry.containsKey(field)) {
                        mutableEntry.remove(field)
                        LogManager.aiService("Stripped root-level systemManaged field: $field", "DEBUG")
                    }
                }

                mutableEntry
            } else {
                entry
            }
        }

        return command.copy(params = command.params.toMutableMap().apply {
            put("entries", cleanedEntries)
        })
    }

    /**
     * Inject tooltype into command params by deducing from toolInstanceId
     *
     * AI should never provide tooltype - it's automatically deduced from the tool instance.
     * This ensures AI cannot spoof tooltype and provides single source of truth.
     *
     * Called AFTER stripRootLevelSystemManagedFields(), so even if AI provided tooltype,
     * it's been removed and we inject the correct one from the database.
     *
     * @param command DataCommand potentially missing tooltype
     * @return Command with tooltype injected at root level
     */
    private suspend fun injectTooltypeIfNeeded(command: DataCommand): DataCommand {
        val toolInstanceId = command.params["toolInstanceId"] as? String
            ?: return command

        // Fetch tool instance to get tooltype
        val coordinator = Coordinator(context)
        val result = coordinator.processUserAction(
            "tools.get",
            mapOf("tool_instance_id" to toolInstanceId)
        )

        if (!result.isSuccess) {
            return command
        }

        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        val tooltype = toolInstance?.get("tool_type") as? String  // Note: DB column is "tool_type" not "tooltype"
            ?: return command

        // Inject tooltype at root level
        val enrichedParams = command.params.toMutableMap()
        enrichedParams["tooltype"] = tooltype

        LogManager.aiService("Injected tooltype '$tooltype' from tool instance $toolInstanceId", "DEBUG")

        return command.copy(params = enrichedParams)
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