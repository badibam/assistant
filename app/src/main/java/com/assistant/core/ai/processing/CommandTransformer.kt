package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import com.assistant.core.ui.components.getPeriodEndTimestamp
import com.assistant.core.ui.components.resolveRelativePeriod
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.LogManager

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
 * - Resolve relative periods to absolute timestamps when isRelative=true
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
    fun transformToExecutable(
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
                    "TOOL_DATA" -> transformToolDataCommand(command, s, reference)
                    "TOOL_STATS" -> transformToolStatsCommand(command)
                    "TOOL_DATA_SAMPLE" -> transformToolDataSampleCommand(command)
                    "ZONE_CONFIG" -> transformZoneConfigCommand(command)
                    "ZONES" -> transformZonesCommand(command)
                    "TOOL_INSTANCES" -> transformToolInstancesCommand(command)
                    "CURRENT_DATETIME" -> transformCurrentDatetimeCommand(command)
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
        LogManager.aiPrompt("transformAppStateCommand() - generating zones.list + tools.list_all", "VERBOSE")

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
            )
        )
    }

    private fun transformSchemaCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformSchemaCommand() - routing to schemas.get", "VERBOSE")

        val schemaId = command.params["id"] as? String
        if (schemaId.isNullOrEmpty()) {
            LogManager.aiPrompt("SCHEMA command missing id parameter", "WARN")
            return null
        }

        val params = mutableMapOf<String, Any>("id" to schemaId)

        // Add toolInstanceId if present (required for data/execution schemas with custom fields)
        command.params["toolInstanceId"]?.let {
            params["toolInstanceId"] = it
            LogManager.aiPrompt("SCHEMA command includes toolInstanceId for enrichment", "VERBOSE")
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

    private fun transformToolDataCommand(command: DataCommand, s: StringsContext, reference: Long): ExecutableCommand? {
        LogManager.aiPrompt("transformToolDataCommand() - routing to tool_data.get", "VERBOSE")

        val toolInstanceId = command.params["id"] as? String
        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiPrompt("TOOL_DATA command missing id parameter", "WARN")
            return null
        }

        val params = mutableMapOf<String, Any>("toolInstanceId" to toolInstanceId)

        // Apply temporal parameter resolution
        applyTemporalParameters(params, command, s, reference)

        // Add pagination if specified
        command.params["limit"]?.let { params["limit"] = it }
        command.params["page"]?.let { params["page"] = it }

        // Add fields filter if specified (field selection for data queries)
        command.params["fields"]?.let { params["fields"] = it }

        return ExecutableCommand(
            resource = "tool_data",
            operation = "get",
            params = params
        )
    }


    private fun transformToolStatsCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolStatsCommand() - STUB implementation", "DEBUG")

        // TODO: Transform TOOL_STATS command to tool_data.stats call
        // - Similar to TOOL_DATA but with aggregate functions
        // - Generate appropriate groupBy and functions parameters
        // - IMPORTANT: Apply temporal parameters via applyTemporalParameters(params, command, s, reference)

        return null
    }

    private fun transformToolDataSampleCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolDataSampleCommand() - STUB implementation", "DEBUG")

        // TODO: Transform TOOL_DATA_SAMPLE command to tool_data.get with sampling
        // - Add default limit for sampling (e.g., limit: 10)
        // - Use recent data ordering (orderBy: timestamp DESC)
        // - IMPORTANT: Apply temporal parameters via applyTemporalParameters(params, command, s, reference)

        return null
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

    private fun transformCurrentDatetimeCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformCurrentDatetimeCommand() - routing to app_config.get_current_datetime", "VERBOSE")

        return ExecutableCommand(
            resource = "app_config",
            operation = "get_current_datetime",
            params = emptyMap()
        )
    }

    // ========================================================================================
    // Helper Methods
    // ========================================================================================

    /**
     * Apply temporal parameters to command params
     * Handles period_start/period_end (the documented form) and absolute startTime/endTime
     *
     * Three formats are accepted for period_start/period_end, as documented in the AI prompt (Part E):
     * - Relative period: "offset_PeriodType" (e.g. "-7_DAY" = 7 days ago, "0_WEEK" = current week).
     *   Applies the user's dayStartHour and weekStartDay, so the AI never handles calendars.
     * - NOW marker: [reference] itself, not a period, so no period normalization.
     * - ISO 8601: a fixed date, with or without offset, resolved in the app timezone.
     *
     * For absolute timestamps (startTime/endTime): milliseconds, as a number. Rare, and never
     * produced by the AI, whose commands are all marked relative.
     *
     * Anything else throws: an unreadable period must reach the AI as an error, never as a
     * silently missing filter, which would widen the query to the whole history.
     */
    private fun applyTemporalParameters(params: MutableMap<String, Any>, command: DataCommand, s: StringsContext, reference: Long) {
        LogManager.aiPrompt("applyTemporalParameters() - CALLED with command.isRelative=${command.isRelative}, command.params=${command.params}", "DEBUG")

        val periodStart = command.params["period_start"] as? String
        if (periodStart != null) {
            params["startTime"] = resolvePeriodBound(periodStart, isEnd = false, s = s, reference = reference)
        } else {
            command.params["startTime"]?.let { params["startTime"] = requireTimestamp(it, "startTime", s) }
        }

        val periodEnd = command.params["period_end"] as? String
        if (periodEnd != null) {
            params["endTime"] = resolvePeriodBound(periodEnd, isEnd = true, s = s, reference = reference)
        } else {
            command.params["endTime"]?.let { params["endTime"] = requireTimestamp(it, "endTime", s) }
        }

        LogManager.aiPrompt("applyTemporalParameters() - EXIT with params=$params", "DEBUG")
    }

    /**
     * Resolve one bound of a temporal filter to a UTC timestamp.
     *
     * Everything resolves against [reference], never the clock: see resolveRelativePeriod.
     *
     * isEnd selects which edge of a relative period is taken: a relative start is the first
     * instant of the period, a relative end is its last one, so "-1_DAY" on both bounds
     * covers all of yesterday.
     *
     * @throws IllegalArgumentException if the value matches none of the three documented formats
     */
    private fun resolvePeriodBound(value: String, isEnd: Boolean, s: StringsContext, reference: Long): Long {
        if (value == "NOW") {
            return reference
        }

        if (DateTimeConverter.looksLikeISO8601(value)) {
            val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
            return try {
                DateTimeConverter.isoToTimestamp(value, timezone)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(s.shared("ai_error_period_invalid_iso").format(value), e)
            }
        }

        // Relative period: "offset_PeriodType"
        val parts = value.split("_")
        if (parts.size != 2) {
            throw IllegalArgumentException(s.shared("ai_error_period_unknown_format").format(value))
        }
        val offset = parts[0].toIntOrNull()
            ?: throw IllegalArgumentException(s.shared("ai_error_period_unknown_format").format(value))
        val type = try {
            PeriodType.valueOf(parts[1])
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                s.shared("ai_error_period_unknown_type")
                    .format(parts[1], PeriodType.entries.joinToString(", ") { it.name }),
                e
            )
        }

        val resolved = resolveRelativePeriod(RelativePeriod(offset = offset, type = type), reference)
        return if (isEnd) getPeriodEndTimestamp(resolved) else resolved.timestamp
    }

    /**
     * Read an absolute startTime/endTime as a millisecond timestamp.
     *
     * A non-numeric value used to travel through untouched and be read further down as 0,
     * producing a wrong time range with no error anywhere.
     *
     * @throws IllegalArgumentException if the value is not a number
     */
    private fun requireTimestamp(value: Any, paramName: String, s: StringsContext): Long {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            else -> throw IllegalArgumentException(
                s.shared("ai_error_timestamp_not_a_number").format(paramName, value.toString())
            )
        }
    }

}
