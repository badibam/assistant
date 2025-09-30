package com.assistant.core.ai.processing

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.ExecutableCommand
import com.assistant.core.utils.LogManager

/**
 * User command processor for transforming DataCommands from user enrichments
 *
 * Core responsibilities:
 * - Resolve relative periods to absolute timestamps for chat context
 * - Transform UI abstractions to concrete coordinator parameters
 * - Maintain conversation context stability across days
 * - Convert enrichment parameters to service-compatible formats
 *
 * Used in chat sessions where periods like "cette semaine" should be fixed
 * to absolute timestamps to ensure conversation consistency.
 */
class UserCommandProcessor(private val context: Context) {

    /**
     * Process user commands from enrichment blocks into executable commands
     *
     * @param commands List of DataCommands from user enrichments
     * @return List of ExecutableCommands ready for coordinator dispatch
     */
    fun processCommands(commands: List<DataCommand>): List<ExecutableCommand> {
        LogManager.aiPrompt("UserCommandProcessor processing ${commands.size} user commands", "DEBUG")

        val executableCommands = mutableListOf<ExecutableCommand>()

        for (command in commands) {
            try {
                val executableCommand = when (command.type) {
                    "SCHEMA" -> processSchemaCommand(command)
                    "TOOL_CONFIG" -> processToolConfigCommand(command)
                    "TOOL_DATA" -> processToolDataCommand(command)
                    "TOOL_STATS" -> processToolStatsCommand(command)
                    "TOOL_DATA_SAMPLE" -> processToolDataSampleCommand(command)
                    "ZONE_CONFIG" -> processZoneConfigCommand(command)
                    "ZONES" -> processZonesCommand(command)
                    "TOOL_INSTANCES" -> processToolInstancesCommand(command)
                    else -> {
                        LogManager.aiPrompt("Unknown command type: ${command.type}", "WARN")
                        null
                    }
                }

                executableCommand?.let { executableCommands.add(it) }

            } catch (e: Exception) {
                LogManager.aiPrompt("Failed to process command ${command.type}: ${e.message}", "ERROR", e)
            }
        }

        LogManager.aiPrompt("UserCommandProcessor generated ${executableCommands.size} executable commands", "DEBUG")
        return executableCommands
    }

    // ========================================================================================
    // Command Processing Methods - STUBS
    // ========================================================================================

    private fun processSchemaCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processSchemaCommand() - routing to schemas.get", "VERBOSE")

        // Route SCHEMA command to SchemaService
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

    private fun processToolConfigCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processToolConfigCommand() - routing to tools.get", "VERBOSE")

        // Route TOOL_CONFIG command to ToolInstanceService
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

    private fun processToolDataCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processToolDataCommand() - routing to tool_data.get", "VERBOSE")

        // Extract toolInstanceId from params
        val toolInstanceId = command.params["id"] as? String
        if (toolInstanceId.isNullOrEmpty()) {
            LogManager.aiPrompt("TOOL_DATA command missing id parameter", "WARN")
            return null
        }

        // Build params for ToolDataService
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

    private fun processToolStatsCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processToolStatsCommand() - STUB implementation", "DEBUG")

        // TODO: Transform TOOL_STATS command to tool_data.stats call
        // - Similar to TOOL_DATA but with aggregate functions
        // - Generate appropriate groupBy and functions parameters

        return null
    }

    private fun processToolDataSampleCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processToolDataSampleCommand() - STUB implementation", "DEBUG")

        // TODO: Transform TOOL_DATA_SAMPLE command to tool_data.get with sampling
        // - Add default limit for sampling (e.g., limit: 10)
        // - Use recent data ordering (orderBy: timestamp DESC)

        return null
    }

    private fun processZoneConfigCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processZoneConfigCommand() - routing to zones.get", "VERBOSE")

        // Route ZONE_CONFIG command to ZoneService
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

    private fun processZonesCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processZonesCommand() - routing to zones.list", "VERBOSE")

        // Route ZONES command to ZoneService.list
        // No parameters needed, returns all zones
        return ExecutableCommand(
            resource = "zones",
            operation = "list",
            params = emptyMap()
        )
    }

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

    private fun processToolInstancesCommand(command: DataCommand): ExecutableCommand? {
        LogManager.aiPrompt("processToolInstancesCommand() - routing to tools service", "VERBOSE")

        // Route TOOL_INSTANCES command to ToolInstanceService
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
}