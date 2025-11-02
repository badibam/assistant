package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
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
     * @return TransformationResult with executable commands and errors
     */
    fun transformToExecutable(
        commands: List<DataCommand>,
        context: Context
    ): TransformationResult {
        LogManager.aiPrompt("CommandTransformer transforming ${commands.size} commands", "DEBUG")

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
                    "TOOL_DATA" -> transformToolDataCommand(command)
                    "TOOL_EXECUTIONS" -> transformToolExecutionsCommand(command)
                    "TOOL_STATS" -> transformToolStatsCommand(command)
                    "TOOL_DATA_SAMPLE" -> transformToolDataSampleCommand(command)
                    "ZONE_CONFIG" -> transformZoneConfigCommand(command)
                    "ZONES" -> transformZonesCommand(command)
                    "TOOL_INSTANCES" -> transformToolInstancesCommand(command)
                    "CURRENT_DATETIME" -> transformCurrentDatetimeCommand(command)
                    else -> {
                        val error = "Type inconnu: ${command.type}"
                        LogManager.aiPrompt("Unknown command type: ${command.type}", "WARN")
                        errors.add("Command[$index] (${command.type}): $error")
                        null
                    }
                }

                if (executableCommand == null && command.type != "APP_STATE") {
                    // Command returned null (validation failed inside transform function)
                    // Error already logged by the specific transform function
                    errors.add("Command[$index] (${command.type}): paramètres invalides ou manquants")
                } else {
                    executableCommand?.let { executableCommands.add(it) }
                }

            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                LogManager.aiPrompt("Failed to transform command ${command.type}: $error", "ERROR", e)
                errors.add("Command[$index] (${command.type}): $error")
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

        return ExecutableCommand(
            resource = "schemas",
            operation = "get",
            params = mapOf("id" to schemaId)
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

    private fun transformToolDataCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolDataCommand() - routing to tool_data.get", "VERBOSE")

        val toolInstanceId = command.params["id"] as? String
        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiPrompt("TOOL_DATA command missing id parameter", "WARN")
            return null
        }

        val params = mutableMapOf<String, Any>("toolInstanceId" to toolInstanceId)

        // Apply temporal parameter resolution
        applyTemporalParameters(params, command)

        // Add pagination if specified
        command.params["limit"]?.let { params["limit"] = it }
        command.params["page"]?.let { params["page"] = it }

        return ExecutableCommand(
            resource = "tool_data",
            operation = "get",
            params = params
        )
    }

    private fun transformToolExecutionsCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolExecutionsCommand() - routing to tool_executions.get", "VERBOSE")

        val toolInstanceId = command.params["id"] as? String
        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiPrompt("TOOL_EXECUTIONS command missing id parameter", "WARN")
            return null
        }

        val params = mutableMapOf<String, Any>("toolInstanceId" to toolInstanceId)

        // Apply temporal parameter resolution (filters on execution_time)
        applyTemporalParameters(params, command)

        // Add pagination if specified
        command.params["limit"]?.let { params["limit"] = it }
        command.params["page"]?.let { params["page"] = it }

        // Add status filter if specified
        command.params["status"]?.let { params["status"] = it }

        // Add templateDataId filter if specified
        command.params["templateDataId"]?.let { params["templateDataId"] = it }

        return ExecutableCommand(
            resource = "tool_executions",
            operation = "get",
            params = params
        )
    }

    private fun transformToolStatsCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolStatsCommand() - STUB implementation", "DEBUG")

        // TODO: Transform TOOL_STATS command to tool_data.stats call
        // - Similar to TOOL_DATA but with aggregate functions
        // - Generate appropriate groupBy and functions parameters
        // - IMPORTANT: Apply temporal parameters via applyTemporalParameters(params, command)

        return null
    }

    private fun transformToolDataSampleCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolDataSampleCommand() - STUB implementation", "DEBUG")

        // TODO: Transform TOOL_DATA_SAMPLE command to tool_data.get with sampling
        // - Add default limit for sampling (e.g., limit: 10)
        // - Use recent data ordering (orderBy: timestamp DESC)
        // - IMPORTANT: Apply temporal parameters via applyTemporalParameters(params, command)

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
     * Handles both relative periods (period_start/period_end) and absolute timestamps (startTime/endTime)
     *
     * For relative periods:
     * - Format: "offset_PeriodType" (e.g., "-7_DAY" = 7 days ago, "0_WEEK" = current week)
     * - Automatically applies user's dayStartHour and weekStartDay configuration
     * - AI doesn't need to handle timezones or calendar calculations
     *
     * For absolute timestamps:
     * - Direct Long values in milliseconds
     * - Used only when explicitly provided (not recommended for AI)
     */
    private fun applyTemporalParameters(params: MutableMap<String, Any>, command: DataCommand) {
        LogManager.aiPrompt("applyTemporalParameters() - CALLED with command.isRelative=${command.isRelative}, command.params=${command.params}", "DEBUG")

        // Handle start time - can be relative OR absolute
        val periodStart = command.params["period_start"] as? String
        val startTimeAbsolute = command.params["startTime"]

        if (periodStart != null) {
            // Relative start period: resolve to absolute timestamp
            LogManager.aiPrompt("applyTemporalParameters() - Found relative period_start: $periodStart", "DEBUG")
            val (offset, type) = periodStart.split("_")
            val relativePeriod = com.assistant.core.ui.components.RelativePeriod(
                offset = offset.toInt(),
                type = com.assistant.core.ui.components.PeriodType.valueOf(type)
            )
            val resolvedPeriod = com.assistant.core.ui.components.resolveRelativePeriod(relativePeriod)
            val startTime = resolvedPeriod.timestamp
            params["startTime"] = startTime
            LogManager.aiPrompt("applyTemporalParameters() - Resolved period_start to startTime=$startTime", "DEBUG")
        } else if (startTimeAbsolute != null) {
            // Absolute start timestamp: use directly
            params["startTime"] = startTimeAbsolute
            LogManager.aiPrompt("applyTemporalParameters() - Using absolute startTime=$startTimeAbsolute", "DEBUG")
        }

        // Handle end time - can be relative OR absolute
        val periodEnd = command.params["period_end"] as? String
        val endTimeAbsolute = command.params["endTime"]

        if (periodEnd != null) {
            // Relative end period: resolve to absolute timestamp
            LogManager.aiPrompt("applyTemporalParameters() - Found relative period_end: $periodEnd", "DEBUG")
            val (offset, type) = periodEnd.split("_")
            val relativePeriod = com.assistant.core.ui.components.RelativePeriod(
                offset = offset.toInt(),
                type = com.assistant.core.ui.components.PeriodType.valueOf(type)
            )
            val resolvedPeriod = com.assistant.core.ui.components.resolveRelativePeriod(relativePeriod)
            val endTime = com.assistant.core.ui.components.getPeriodEndTimestamp(resolvedPeriod)
            params["endTime"] = endTime
            LogManager.aiPrompt("applyTemporalParameters() - Resolved period_end to endTime=$endTime", "DEBUG")
        } else if (endTimeAbsolute != null) {
            // Absolute end timestamp: use directly
            params["endTime"] = endTimeAbsolute
            LogManager.aiPrompt("applyTemporalParameters() - Using absolute endTime=$endTimeAbsolute", "DEBUG")
        }

        LogManager.aiPrompt("applyTemporalParameters() - EXIT with params=$params", "DEBUG")
    }

}
