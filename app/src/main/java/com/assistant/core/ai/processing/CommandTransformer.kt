package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.utils.LogManager

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
     * @return List of ExecutableCommands ready for coordinator dispatch
     */
    fun transformToExecutable(
        commands: List<DataCommand>,
        context: Context
    ): List<ExecutableCommand> {
        LogManager.aiPrompt("CommandTransformer transforming ${commands.size} commands", "DEBUG")

        val executableCommands = mutableListOf<ExecutableCommand>()

        for (command in commands) {
            try {
                val executableCommand = when (command.type) {
                    "SCHEMA" -> transformSchemaCommand(command)
                    "TOOL_CONFIG" -> transformToolConfigCommand(command)
                    "TOOL_DATA" -> transformToolDataCommand(command)
                    "TOOL_STATS" -> transformToolStatsCommand(command)
                    "TOOL_DATA_SAMPLE" -> transformToolDataSampleCommand(command)
                    "ZONE_CONFIG" -> transformZoneConfigCommand(command)
                    "ZONES" -> transformZonesCommand(command)
                    "TOOL_INSTANCES" -> transformToolInstancesCommand(command)
                    else -> {
                        LogManager.aiPrompt("Unknown command type: ${command.type}", "WARN")
                        null
                    }
                }

                executableCommand?.let { executableCommands.add(it) }

            } catch (e: Exception) {
                LogManager.aiPrompt("Failed to transform command ${command.type}: ${e.message}", "ERROR", e)
            }
        }

        LogManager.aiPrompt("CommandTransformer generated ${executableCommands.size} executable commands", "DEBUG")
        return executableCommands
    }

    // ========================================================================================
    // Transformation Methods
    // ========================================================================================

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

        // Handle temporal parameters
        if (command.isRelative) {
            // Resolve relative periods to absolute timestamps
            val periodStart = command.params["period_start"] as? String
            val periodEnd = command.params["period_end"] as? String

            if (periodStart != null && periodEnd != null) {
                val (startTime, endTime) = resolveRelativePeriods(periodStart, periodEnd)
                if (startTime != null) params["startTime"] = startTime
                if (endTime != null) params["endTime"] = endTime
            }
        } else {
            // Use absolute timestamps directly
            command.params["startTime"]?.let { params["startTime"] = it }
            command.params["endTime"]?.let { params["endTime"] = it }
        }

        // Add pagination if specified
        command.params["limit"]?.let { params["limit"] = it }
        command.params["page"]?.let { params["page"] = it }

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

        return null
    }

    private fun transformToolDataSampleCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("transformToolDataSampleCommand() - STUB implementation", "DEBUG")

        // TODO: Transform TOOL_DATA_SAMPLE command to tool_data.get with sampling
        // - Add default limit for sampling (e.g., limit: 10)
        // - Use recent data ordering (orderBy: timestamp DESC)

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

    // ========================================================================================
    // Helper Methods
    // ========================================================================================

    /**
     * Resolve relative periods to absolute timestamps
     * Format: "offset_PeriodType" (e.g., "-1_WEEK", "0_DAY")
     * Returns (startTimestamp, endTimestamp)
     */
    private fun resolveRelativePeriods(periodStart: String, periodEnd: String): Pair<Long?, Long?> {
        try {
            val (startOffset, startType) = periodStart.split("_")
            val (endOffset, endType) = periodEnd.split("_")

            val startRelativePeriod = com.assistant.core.ui.components.RelativePeriod(
                offset = startOffset.toInt(),
                type = com.assistant.core.ui.components.PeriodType.valueOf(startType)
            )
            val endRelativePeriod = com.assistant.core.ui.components.RelativePeriod(
                offset = endOffset.toInt(),
                type = com.assistant.core.ui.components.PeriodType.valueOf(endType)
            )

            // Resolve to absolute periods
            val startPeriod = com.assistant.core.ui.components.resolveRelativePeriod(startRelativePeriod)
            val endPeriod = com.assistant.core.ui.components.resolveRelativePeriod(endRelativePeriod)

            // Start timestamp = beginning of start period
            val startTimestamp = startPeriod.timestamp

            // End timestamp = end of end period
            val endTimestamp = com.assistant.core.ui.components.getPeriodEndTimestamp(endPeriod)

            LogManager.aiPrompt("Resolved relative periods: $periodStart -> $startTimestamp, $periodEnd -> $endTimestamp", "DEBUG")
            return Pair(startTimestamp, endTimestamp)

        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to resolve relative periods: ${e.message}", "ERROR", e)
            return Pair(null, null)
        }
    }
}
