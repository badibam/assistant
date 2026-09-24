package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.FieldPatternGrammar
import com.assistant.core.validation.SchemaUtils
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject
import java.time.ZoneId

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

    private val s = Strings.`for`(context = context)

    /**
     * Process AI data commands (queries) with security validation
     *
     * All AI dataCommands are marked as relative (isRelative=true) to ensure:
     * - AI uses relative period format: period_start/period_end with "offset_TYPE" (e.g., "-7_DAY")
     * - Automatic resolution using user's dayStartHour and weekStartDay configuration
     * - AI doesn't need to handle timestamps, timezones, or calendar calculations
     *
     * @param commands List of DataCommands from AI for data retrieval
     * @param reference Instant relative periods resolve against: an automation's scheduled time,
     *   so a run catching up on a past day reads that day; the clock for a chat.
     * @return TransformationResult with executable commands and transformation errors
     */
    fun processDataCommands(commands: List<DataCommand>, reference: Long): TransformationResult {
        LogManager.aiService("AICommandProcessor processing ${commands.size} data commands from AI", "DEBUG")

        // VALIDATION: Check that TOOL_DATA commands include 'fields' parameter
        val validationErrors = mutableListOf<String>()
        for ((index, command) in commands.withIndex()) {
            if (command.type == "TOOL_DATA") {
                val fields = command.params["fields"]
                if (fields == null) {
                    val errorMsg = s.shared("ai_error_command_prefix")
                        .format(index, command.type, s.shared("ai_error_tool_data_fields_missing"))
                    validationErrors.add(errorMsg)
                    LogManager.aiService(errorMsg, "WARN")
                } else if (fields !is List<*> || fields.isEmpty()) {
                    val errorMsg = s.shared("ai_error_command_prefix")
                        .format(index, command.type, s.shared("ai_error_tool_data_fields_not_array"))
                    validationErrors.add(errorMsg)
                    LogManager.aiService(errorMsg, "WARN")
                } else {
                    // Field paths are read through the same grammar the filtering uses, so a path
                    // that passes here cannot be dropped in silence when the result is built.
                    val invalid = FieldPatternGrammar.parse(fields.map { it.toString() }).invalid
                    for (path in invalid) {
                        val errorMsg = s.shared("ai_error_command_prefix")
                            .format(index, command.type, s.shared("ai_error_field_invalid_pattern").format(path))
                        validationErrors.add(errorMsg)
                        LogManager.aiService(errorMsg, "WARN")
                    }
                }

                // Parameters the prompt used to document and the transformer never read.
                // They were dropped in silence: a nested 'period' left the query unfiltered over
                // the whole history, which an AI reading the old delete example took for one month.
                // The shapes may have been learned in past sessions, so they are named and refused.
                if (command.params.containsKey("period")) {
                    val errorMsg = s.shared("ai_error_command_prefix")
                        .format(index, command.type, s.shared("ai_error_param_period_object"))
                    validationErrors.add(errorMsg)
                    LogManager.aiService(errorMsg, "WARN")
                }
                if (command.params.containsKey("offset")) {
                    val errorMsg = s.shared("ai_error_command_prefix")
                        .format(index, command.type, s.shared("ai_error_param_offset"))
                    validationErrors.add(errorMsg)
                    LogManager.aiService(errorMsg, "WARN")
                }
            }
        }

        // If validation errors exist, return early without transformation
        if (validationErrors.isNotEmpty()) {
            LogManager.aiService("AICommandProcessor validation failed with ${validationErrors.size} errors", "WARN")
            return TransformationResult(
                executableCommands = emptyList(),
                errors = validationErrors
            )
        }

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
        val result = CommandTransformer.transformToExecutable(relativeCommands, context, reference)

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
                // FIRST: Inject tooltype by deducing from toolInstanceId, for the validation to
                // pick the tool's schemas. System-managed fields the AI may send are the service's
                // business: it drops or ignores them (SystemManagedFields).
                val enrichedCommand = injectTooltypeIfNeeded(command)

                // SECOND: Transform to executable command
                val executableCommand = transformActionCommand(enrichedCommand)
                if (executableCommand == null) {
                    errors.add(s.shared("ai_error_command_prefix")
                        .format(index, command.type, s.shared("ai_error_command_transformation_null")))
                } else {
                    executableCommands.add(executableCommand)
                }
            } catch (e: Exception) {
                val error = e.message ?: s.shared("ai_error_command_unexpected")
                LogManager.aiService("Failed to transform action command ${command.type}: $error", "ERROR", e)
                errors.add(s.shared("ai_error_command_prefix").format(index, command.type, error))
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
     */
    private suspend fun transformActionCommand(command: DataCommand): ExecutableCommand? {
        return when (command.type) {
            // Tool data actions - batch operations by default (per AI.md line 182)
            // Schema ID enrichment: automatically inject data_schema_id from tool instance config
            "CREATE_DATA" -> {
                LogManager.aiService("CREATE_DATA original params keys: ${command.params.keys}", "DEBUG")
                val enrichedParams = enrichWithSchemaId(resolveDatesToMilliseconds(command.params))
                LogManager.aiService("CREATE_DATA enriched params keys: ${enrichedParams.keys}", "DEBUG")
                ExecutableCommand(
                    resource = "tool_data",
                    operation = "batch_create",
                    params = enrichedParams,
                    isActionCommand = true
                )
            }
            "UPDATE_DATA" -> {
                val enrichedParams = enrichWithSchemaId(resolveDatesToMilliseconds(command.params))
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
                val transformedParams = command.params
                ExecutableCommand(
                    resource = "tools",
                    operation = "create",
                    params = transformedParams,
                    isActionCommand = true
                )
            }
            "UPDATE_TOOL" -> {
                val transformedParams = command.params
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
                val transformedParams = command.params
                ExecutableCommand(
                    resource = "tools",
                    operation = "create",
                    params = transformedParams,
                    isActionCommand = true
                )
            }
            "UPDATE_TOOL" -> {
                val transformedParams = command.params
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
     * A schema_id the AI sent is replaced by the one read here.
     *
     * @param params Original params from AI command
     * @return Enriched params with schema_id added to each entry
     */
    /**
     * Turn the dates the model writes into milliseconds, the form everything past this point
     * speaks (docs/design/date-boundary.md). ISO 8601 is the model's form, and this is the last
     * place it is understood: the dispatcher, the services and the database see numbers only.
     *
     * Reaches what the service used to reach -- the data and custom_fields payloads and the
     * timestamp beside them -- and reaches it inside each entry of a batch as well, which the
     * service never could: batchCreateEntries reads an entry's timestamp with getLong, so an ISO
     * string sent in a batch failed there rather than converting.
     *
     * An unreadable date throws, and processActionCommands turns that into an error the model
     * reads. Refusing is the point: a date silently left as text would reach the database as a
     * string in a column of numbers.
     */
    private fun resolveDatesToMilliseconds(params: Map<String, Any?>): Map<String, Any?> {
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        val resolved = params.toMutableMap()

        convertPayloadDates(resolved, timezone)

        (params["entries"] as? List<*>)?.let { entries ->
            resolved["entries"] = entries.map { entry ->
                if (entry is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val singleEntry = (entry as Map<String, Any?>).toMutableMap()
                    convertPayloadDates(singleEntry, timezone)
                    singleEntry
                } else {
                    entry
                }
            }
        }

        return resolved
    }

    /**
     * Convert the date-bearing parts of one entry in place: the two payloads recursively, and the
     * timestamp that sits beside them.
     */
    private fun convertPayloadDates(entry: MutableMap<String, Any?>, timezone: ZoneId) {
        for (key in listOf("data", "custom_fields")) {
            val payload = entry[key] ?: continue
            val json = when (payload) {
                is JSONObject -> payload
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    JsonUtils.toJSONObject(payload as Map<String, Any?>)
                }
                else -> continue
            }
            entry[key] = JsonUtils.toMap(DateTimeConverter.isoToTimestamps(json, timezone))
        }

        (entry["timestamp"] as? String)?.let { iso ->
            entry["timestamp"] = DateTimeConverter.isoToTimestamp(iso, timezone)
        }
    }

    private suspend fun enrichWithSchemaId(params: Map<String, Any?>): Map<String, Any?> {
        val toolInstanceId = params["tool_instance_id"] as? String

        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiService("Cannot enrich schema_id: tool_instance_id missing", "ERROR")
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

            // tools.get returns { "tool_instance": { "config": { ... }, ... } }
            val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
            if (toolInstance == null) {
                LogManager.aiService(
                    "Tool instance $toolInstanceId not found in result",
                    "ERROR"
                )
                return params
            }

            val config = (toolInstance["config"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            if (config == null || config.length() == 0) {
                LogManager.aiService(
                    "Tool instance $toolInstanceId has no config",
                    "ERROR"
                )
                return params
            }
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
     * Inject tooltype into command params by deducing from toolInstanceId
     *
     * AI should never provide tooltype - it's automatically deduced from the tool instance.
     * This ensures AI cannot spoof tooltype and provides single source of truth.
     *
     * A tooltype the AI provided is overwritten by the one read from the database.
     *
     * @param command DataCommand potentially missing tooltype
     * @return Command with tooltype injected at root level
     */
    private suspend fun injectTooltypeIfNeeded(command: DataCommand): DataCommand {
        val toolInstanceId = command.params["tool_instance_id"] as? String
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
        val tooltype = toolInstance?.get("tooltype") as? String  // Note: DB column is "tooltype" not "tooltype"
            ?: return command

        // Inject tooltype at root level
        val enrichedParams = command.params.toMutableMap()
        enrichedParams["tooltype"] = tooltype

        LogManager.aiService("Injected tooltype '$tooltype' from tool instance $toolInstanceId", "DEBUG")

        return command.copy(params = enrichedParams)
    }

}