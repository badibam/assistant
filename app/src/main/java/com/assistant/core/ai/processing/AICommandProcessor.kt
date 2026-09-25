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
import com.assistant.core.validation.SchemaUtils
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

}