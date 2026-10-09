package app.treelune.core.ai.processing

import android.content.Context
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.ExecutableCommand
import app.treelune.core.fields.ToolFields
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager

/**
 * Result of command transformation with executable commands and errors
 */
data class TransformationResult(
    val executableCommands: List<ExecutableCommand>,
    val errors: List<String>  // Detailed error messages for failed transformations
)

/**
 * Command Transformer - pure transformation logic for DataCommands
 *
 * Core responsibilities:
 * - Transform abstract command types to concrete resource.operation format
 * - Put a query's period and its filters' dates and durations in their stored form (FilterValues)
 * - Map parameters from enrichment/AI format to service-compatible format
 * - Handle pagination, filtering, and temporal parameters
 *
 * Used by both UserCommandProcessor and AICommandProcessor to avoid duplication.
 * Each processor adds its own validation/security logic before calling transformer.
 */
object CommandTransformer {

    /**
     * Transform DataCommands to ExecutableCommands
     *
     * @param commands List of DataCommands to transform
     * @param context Android context for period resolution
     * @param reference Instant the relative periods and the NOW marker resolve against.
     *   The clock for anything a user is watching; an automation's scheduled time for its run,
     *   so a run catching up on a past day reads that day and not today.
     * @return TransformationResult with executable commands and errors
     */
    suspend fun transformToExecutable(
        commands: List<DataCommand>,
        context: Context,
        reference: Long
    ): TransformationResult {
        LogManager.aiPrompt("CommandTransformer transforming ${commands.size} commands", "DEBUG")

        val s = Strings.`for`(context = context)
        val executableCommands = mutableListOf<ExecutableCommand>()
        val errors = mutableListOf<String>()

        for ((index, command) in commands.withIndex()) {
            try {
                // APP_STATE is special: generates multiple executable commands
                if (command.type == "APP_STATE") {
                    val appStateCommands = transformAppStateCommand(command)
                    executableCommands.addAll(appStateCommands)
                    continue
                }

                val executableCommand = when (command.type) {
                    "SCHEMA" -> transformSchemaCommand(command)
                    "TOOL_CONFIG" -> transformToolConfigCommand(command)
                    "TOOL_DATA" -> transformToolDataCommand(command, context, s, reference)
                    "ZONE_CONFIG" -> transformZoneConfigCommand(command)
                    "ZONES" -> transformZonesCommand(command)
                    "TOOL_INSTANCES" -> transformToolInstancesCommand(command)
                    "CURRENT_DATETIME" -> transformCurrentDatetimeCommand(command)
                    "ICONS" -> transformIconsCommand(command)
                    "VARIABLES" -> ExecutableCommand(
                        resource = "variables",
                        operation = if (command.params["zone_id"] is String) "list" else "list_all",
                        params = command.params.filterKeys { it == "zone_id" }.mapValues { it.value!! }
                    )
                    "READING" -> transformReadingCommand(command, s, reference)
                    "FILE" -> transformFileCommand(command)
                    "IMPORT_PLAN" -> transformImportPlanCommand(command)
                    // The settings of a category the service opens to the AI (AppSettings.OPEN_TO_AI)
                    "APP_CONFIG" -> ExecutableCommand(
                        resource = "app_config",
                        operation = "get",
                        params = command.params.filterKeys { it == "category" }.mapValues { it.value!! }
                    )
                    else -> {
                        val error = s.shared("ai_error_command_unknown_type").format(command.type)
                        LogManager.aiPrompt("Unknown command type: ${command.type}", "WARN")
                        errors.add(s.shared("ai_error_command_prefix").format(index, command.type, error))
                        null
                    }
                }

                if (executableCommand == null && command.type != "APP_STATE") {
                    // Command returned null (validation failed inside transform function)
                    // Error already logged by the specific transform function
                    errors.add(s.shared("ai_error_command_prefix").format(index, command.type, s.shared("ai_error_command_invalid_params")))
                } else {
                    executableCommand?.let { executableCommands.add(it) }
                }

            } catch (e: Exception) {
                val error = e.message ?: s.shared("ai_error_command_unexpected")
                LogManager.aiPrompt("Failed to transform command ${command.type}: $error", "ERROR", e)
                errors.add(s.shared("ai_error_command_prefix").format(index, command.type, error))
            }
        }

        LogManager.aiPrompt("CommandTransformer generated ${executableCommands.size} executable commands, ${errors.size} errors", "DEBUG")
        return TransformationResult(executableCommands, errors)
    }

    // ========================================================================================
    // Transformation Methods
    // ========================================================================================

    /**
     * Transform APP_STATE command to multiple executable commands
     * Returns zones + tool instances for complete application structure
     *
     * By default, returns minimal tool instance data (without config_json) to save tokens.
     * Use include_config parameter to include full configuration.
     */
    private fun transformAppStateCommand(command: DataCommand): List<ExecutableCommand> {
        LogManager.aiPrompt("transformAppStateCommand() - generating zones.list + tools.list_all + variables.list_all", "VERBOSE")

        // Read include_config parameter (default false for minimal snapshot)
        val includeConfig = command.params["include_config"] as? Boolean ?: false

        return listOf(
            ExecutableCommand(
                resource = "zones",
                operation = "list",
                params = emptyMap()
            ),
            ExecutableCommand(
                resource = "tools",
                operation = "list_all",
                params = mapOf("include_config" to includeConfig)
            ),
            ExecutableCommand(
                resource = "variables",
                operation = "list_all",
                params = emptyMap()
            )
        )
    }

    /**
     * A variable read at instants: `variable` (its name) and `at`, required, each an ISO 8601
     * date-time or a relative date resolved against [reference], in milliseconds for the service.
     */
    private fun transformReadingCommand(command: DataCommand, s: StringsContext, reference: Long): ExecutableCommand? {
        val name = command.params["variable"] as? String ?: return null
        val resolver = TimeResolver.at(reference)
        val at = (command.params["at"] as? List<*> ?: throw IllegalArgumentException(s.shared("service_error_reading_param").format("at"))).map { instant ->
            when {
                TimePoint.isRelative(instant) -> resolver.instant(TimePoint.read(instant!!) { s.shared(it) })
                instant is String -> DateTimeConverter.isoToTimestamp(instant, resolver.zone)
                instant is Number -> instant.toLong()
                else -> throw IllegalArgumentException(s.shared("ai_error_date_unreadable").format(instant.toString()))
            }
        }
        return ExecutableCommand(resource = "readings", operation = "read", params = mapOf("variable" to name, "at" to at))
    }

    /** What an import of a joined file would do, column by column: the file read by the service itself. */
    private fun transformImportPlanCommand(command: DataCommand): ExecutableCommand? {
        val file = command.params["file"] as? String ?: return null
        val toolInstanceId = command.params["tool_instance_id"] as? String ?: return null
        return ExecutableCommand(resource = "imports", operation = "detect", params = mapOf("file" to file, "tool_instance_id" to toolInstanceId))
    }

    /** A joined file read by lines: `id`, and `start_line` and `lines`, whole numbers a JSON reader may hand over as 1.0. */
    private fun transformFileCommand(command: DataCommand): ExecutableCommand? {
        val id = command.params["id"] as? String ?: return null
        fun whole(key: String): Int? = (command.params[key] as? Number)?.takeIf { it.toDouble() % 1.0 == 0.0 }?.toInt()
        return ExecutableCommand(resource = "files", operation = "read", params = buildMap {
            put("id", id)
            whole("start_line")?.let { put("start_line", it) }
            whole("lines")?.let { put("lines", it) }
        })
    }

    private fun transformSchemaCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformSchemaCommand() - routing to schemas.get", "VERBOSE")

        // A schema is asked for by what it describes: a tool type's config, a tool's entries,
        // or anything else by its name
        val params = listOf("tooltype", "tool_instance_id", "id")
            .mapNotNull { key -> (command.params[key] as? String)?.takeIf { it.isNotEmpty() }?.let { key to it as Any } }
            .toMap()
        if (params.isEmpty()) {
            LogManager.aiPrompt("SCHEMA command names neither a tooltype, a tool_instance_id nor an id", "WARN")
            return null
        }

        return ExecutableCommand(
            resource = "schemas",
            operation = "get",
            params = params
        )
    }

    private fun transformToolConfigCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolConfigCommand() - routing to tools.get", "VERBOSE")

        val toolInstanceId = command.params["id"] as? String
        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiPrompt("TOOL_CONFIG command missing id parameter", "WARN")
            return null
        }

        return ExecutableCommand(
            resource = "tools",
            operation = "get",
            params = mapOf("tool_instance_id" to toolInstanceId)
        )
    }

    private suspend fun transformToolDataCommand(command: DataCommand, context: Context, s: StringsContext, reference: Long): ExecutableCommand? {
        LogManager.aiPrompt("transformToolDataCommand() - routing to tool_data.get", "VERBOSE")

        val toolInstanceId = command.params["id"] as? String
        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiPrompt("TOOL_DATA command missing id parameter", "WARN")
            return null
        }

        val params = mutableMapOf<String, Any>("tool_instance_id" to toolInstanceId)

        // The period, then the value filters, with their dates and durations in the stored form
        // as the tool's fields type them; relative dates resolve against the reference
        val resolver = TimeResolver.at(reference)
        @Suppress("UNCHECKED_CAST")
        val periodFilters = (command.params["period"] as? Map<String, Any?>)
            ?.let { EntryPeriod.fromJson(JsonUtils.toJSONObject(it)) { key -> s.shared(key) }.timestampFilters(resolver) }
            ?: emptyList()
        val valueFilters = (command.params["filters"] as? List<*>)?.let { filters ->
            FilterValues.toStored(filters, ToolFields.filterable(toolInstanceId, context, s), resolver) { s.shared(it) }
        } ?: emptyList()
        if (periodFilters.isNotEmpty() || valueFilters.isNotEmpty()) params["filters"] = periodFilters + valueFilters

        // Add pagination if specified
        command.params["limit"]?.let { params["limit"] = it }
        command.params["page"]?.let { params["page"] = it }

        // Add fields filter if specified (field selection for data queries)
        command.params["fields"]?.let { params["fields"] = it }

        // Only the entries with a DURATION field running, to find a stopwatch to stop
        command.params["running"]?.let { params["running"] = it }

        return ExecutableCommand(
            resource = "tool_data",
            operation = "get",
            params = params
        )
    }



    private fun transformZoneConfigCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformZoneConfigCommand() - routing to zones.get", "VERBOSE")

        val zoneId = command.params["id"] as? String
        if (zoneId.isNullOrEmpty()) {
            LogManager.aiPrompt("ZONE_CONFIG command missing id parameter", "WARN")
            return null
        }

        return ExecutableCommand(
            resource = "zones",
            operation = "get",
            params = mapOf("zone_id" to zoneId)
        )
    }

    private fun transformZonesCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformZonesCommand() - routing to zones.list", "VERBOSE")

        return ExecutableCommand(
            resource = "zones",
            operation = "list",
            params = emptyMap()
        )
    }

    private fun transformToolInstancesCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolInstancesCommand() - routing to tools service", "VERBOSE")

        // If zone_id present: filter by zone (tools.list)
        // If no zone_id: return all tool instances (tools.list_all)
        val zoneId = command.params["zone_id"] as? String

        return if (!zoneId.isNullOrEmpty()) {
            ExecutableCommand(
                resource = "tools",
                operation = "list",
                params = mapOf("zone_id" to zoneId)
            )
        } else {
            ExecutableCommand(
                resource = "tools",
                operation = "list_all",
                params = emptyMap()
            )
        }
    }

    /**
     * ICONS with neither categories nor query is the overview; with either, a search. The
     * lists go through as given, and the service refuses what is not a list.
     */
    private fun transformIconsCommand(command: DataCommand): ExecutableCommand {
        val searchParams = command.params.filterKeys { it == "categories" || it == "query" }
        return ExecutableCommand(
            resource = "icons",
            operation = if (searchParams.isEmpty()) "overview" else "search",
            params = searchParams
        )
    }

    private fun transformCurrentDatetimeCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformCurrentDatetimeCommand() - routing to app_config.get_current_datetime", "VERBOSE")

        return ExecutableCommand(
            resource = "app_config",
            operation = "get_current_datetime",
            params = emptyMap()
        )
    }
}
