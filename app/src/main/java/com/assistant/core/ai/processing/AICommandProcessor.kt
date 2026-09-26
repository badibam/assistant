package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.ai.prompts.ModelValues
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.validation.FieldPatternGrammar
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.JsonUtils
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

    private val s = Strings.`for`(context = context)

    /**
     * Process AI data commands (queries) with security validation
     *
     * All AI dataCommands are marked as relative (isRelative=true) to ensure:
     * - AI uses relative period format in its filters on dates: "offset_TYPE" (e.g., "-7_DAY")
     * - Automatic resolution using user's dayStartHour and weekStartDay configuration
     * - AI doesn't need to handle timestamps, timezones, or calendar calculations
     *
     * @param commands List of DataCommands from AI for data retrieval
     * @param reference Instant relative periods resolve against: an automation's scheduled time,
     *   so a run catching up on a past day reads that day; the clock for a chat.
     * @return TransformationResult with executable commands and transformation errors
     */
    suspend fun processDataCommands(commands: List<DataCommand>, reference: Long): TransformationResult {
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

                // A parameter the transformer does not read would be dropped in silence, and the
                // query run without it: a period the AI believed set would read the whole history.
                // So anything outside the documented ones is refused, named.
                for (param in command.params.keys - TOOL_DATA_PARAMS) {
                    val errorMsg = s.shared("ai_error_command_prefix").format(
                        index, command.type,
                        s.shared("service_error_param_unknown").format(param, TOOL_DATA_PARAMS.joinToString(", "))
                    )
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
            "CREATE_DATA" -> {
                LogManager.aiService("CREATE_DATA original params keys: ${command.params.keys}", "DEBUG")
                val enrichedParams = toStoredForm(command.params)
                LogManager.aiService("CREATE_DATA enriched params keys: ${enrichedParams.keys}", "DEBUG")
                ExecutableCommand(
                    resource = "tool_data",
                    operation = "batch_create",
                    params = enrichedParams,
                    isActionCommand = true
                )
            }
            "UPDATE_DATA" -> {
                val enrichedParams = toStoredForm(command.params)
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
            "START_DURATION" -> durationCommand(command, "start_duration")
            "STOP_DURATION" -> durationCommand(command, "stop_duration")

            // Tool instance actions
            "CREATE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "create",
                params = configToStoredForm(command.params),
                isActionCommand = true
            )
            "UPDATE_TOOL" -> ExecutableCommand(
                resource = "tools",
                operation = "update",
                params = configToStoredForm(command.params),
                isActionCommand = true
            )
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
            "START_DURATION" -> durationCommand(command, "start_duration")
            "STOP_DURATION" -> durationCommand(command, "stop_duration")

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
     * A stopwatch command: the model names the entry by its id and the DURATION field by the
     * same path it reads it with ("data.value", "extra.sleep"); the service takes the entry,
     * the object the field lives in, and its name.
     *
     * @throws IllegalArgumentException when [field] is not a path inside data or extra
     */
    private fun durationCommand(command: DataCommand, operation: String): ExecutableCommand {
        val path = command.params["field"] as? String ?: ""
        val parsed = FieldPatternGrammar.parse(listOf(path))
        val (container, names) = parsed.inside.entries.singleOrNull()
            ?.takeIf { it.key == "data" || it.key == "extra" }
            ?.toPair()
            ?: throw IllegalArgumentException(s.shared("ai_error_duration_field_path").format(path))

        return ExecutableCommand(
            resource = "tool_data",
            operation = operation,
            params = mapOf(
                "tool_instance_id" to command.params["tool_instance_id"],
                "id" to command.params["id"],
                "container" to container,
                "field" to names.single()
            ),
            isActionCommand = true
        )
    }

    /**
     * [params] with the config's dates and durations turned into milliseconds, where the
     * config schema of its tooltype marks them (a Messages tool's delays, its schedule's dates),
     * and those of "fill_values" where the new config's entry schema marks them.
     * The tooltype is the one injectTooltypeIfNeeded read from the tool for an update.
     */
    private fun configToStoredForm(params: Map<String, Any?>): Map<String, Any?> {
        val config = params["config"] ?: return params
        val tooltype = params["tooltype"] as? String ?: return params
        val toolType = ToolTypeManager.getToolType(tooltype) ?: return params
        val schema = JSONObject(com.assistant.core.tools.ToolConfigSettings.schema(toolType, "${tooltype}_config", context).content)
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        val storedConfig = ModelValues.fromModel(config, schema, zone)
        return params.toMutableMap().apply {
            put("config", storedConfig)
            // The values given for fields the change makes required are entry values: read with
            // the schema of the entries the new config makes
            params["fill_values"]?.let { fill ->
                @Suppress("UNCHECKED_CAST")
                val newConfig = JsonUtils.toJSONObject(storedConfig as Map<String, Any?>)
                val entrySchema = JSONObject(BaseSchemas.getEntrySchemaOrThrow(toolType, newConfig, params["tool_instance_id"] as? String, context))
                put("fill_values", ModelValues.fromModel(fill, entrySchema, zone))
            }
        }
    }

    /**
     * Turn the dates and durations the model writes into milliseconds, the form everything past
     * this point speaks (docs/design/date-boundary.md). ISO 8601 is the model's form, and this is
     * the last place it is understood: the dispatcher, the services and the database see numbers.
     *
     * Where a value is a date or a duration is read from the tool's entry schema -- the one its
     * writes are validated against -- so a fixed field of the tool type, a user's field and the
     * core's timestamp are all found, and a text that merely looks like a date is left alone.
     * Converted: the entry the params stand for, and each entry of a batch.
     *
     * @throws IllegalArgumentException on a date or duration that does not read, and
     *   IllegalStateException when the tool or its schema cannot be read; processActionCommands
     *   turns either into an error the model reads. A value silently left as text would only be
     *   refused later, further from what caused it.
     */
    private suspend fun toStoredForm(params: Map<String, Any?>): Map<String, Any?> {
        val toolInstanceId = params["tool_instance_id"] as? String
            ?: throw IllegalStateException(s.shared("service_error_missing_tool_instance_id"))
        val schema = JSONObject(loadEntrySchema(toolInstanceId))
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()

        @Suppress("UNCHECKED_CAST")
        val resolved = (ModelValues.fromModel(params, schema, zone) as Map<String, Any?>).toMutableMap()
        (params["entries"] as? List<*>)?.let { entries ->
            resolved["entries"] = entries.map { ModelValues.fromModel(it, schema, zone) }
        }
        return resolved
    }

    /** The entry schema of the tool [toolInstanceId], generated from its current config. */
    private suspend fun loadEntrySchema(toolInstanceId: String): String {
        val result = Coordinator(context).processUserAction(
            "tools.get",
            mapOf("tool_instance_id" to toolInstanceId)
        )
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val toolType = (toolInstance?.get("tooltype") as? String)?.let { ToolTypeManager.getToolType(it) }
        if (!result.isSuccess || config == null || toolType == null) {
            throw IllegalStateException(s.shared("service_error_tool_instance_not_found"))
        }
        return BaseSchemas.getEntrySchemaOrThrow(toolType, config, toolInstanceId, context)
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

    companion object {
        /** The parameters of a TOOL_DATA query, as the L1 prompt documents them. */
        val TOOL_DATA_PARAMS = setOf("id", "fields", "filters", "limit", "page", "running")
    }
}
