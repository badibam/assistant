package com.assistant.core.ai.enrichments

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.EnrichmentType
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Processor responsible for handling enrichment blocks in two forms:
 * 1. Preview text generation for user interface display
 * 2. DataCommand generation for AI prompt Level 4 inclusion
 *
 * Core logic:
 * - All enrichments generate textual summaries for AI orientation
 * - Only specific types generate DataCommands for Level 4 prompt inclusion:
 * * POINTER: Always generates query
 * * USE: Query for tool instance config
 * * MODIFY_CONFIG: Query for tool instance config
 * * CREATE: No query (just orientation)
 * * ORGANIZE: No query (just orientation)
 */
class EnrichmentProcessor(
    private val context: Context,
    private val coordinator: com.assistant.core.coordinator.Coordinator? = null // Optional for schema ID resolution
) {

    private val s = Strings.`for`(context = context)

    /**
     * Generate human-readable summary for enrichment display in messages
     */
    fun generateSummary(type: EnrichmentType, config: String): String {
        LogManager.aiEnrichment("EnrichmentProcessor.generateSummary() called with type=$type, config length=${config.length}", "DEBUG")

        return try {
            val configJson = JSONObject(config)

            val summary = when (type) {
                EnrichmentType.POINTER -> generatePointerSummary(configJson)
                EnrichmentType.USE -> generateUseSummary(configJson)
                EnrichmentType.CREATE -> generateCreateSummary(configJson)
                EnrichmentType.MODIFY_CONFIG -> generateModifyConfigSummary(configJson)
            }

            LogManager.aiEnrichment("Generated summary for $type: '$summary'", "DEBUG")
            summary
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to generate enrichment summary: ${e.message}", "ERROR", e)
            s.shared("ai_enrichment_invalid")
        }
    }

    /**
     * Check if enrichment should generate a DataCommand for Level 4 inclusion
     */
    fun shouldGenerateQuery(type: EnrichmentType, config: String): Boolean {
        LogManager.aiEnrichment("EnrichmentProcessor.shouldGenerateQuery() called with type=$type", "DEBUG")

        return try {
            val shouldGenerate = when (type) {
                EnrichmentType.POINTER, EnrichmentType.USE, EnrichmentType.MODIFY_CONFIG -> {
                    LogManager.aiEnrichment("$type enrichment always generates query", "DEBUG")
                    true
                }
                EnrichmentType.CREATE -> {
                    LogManager.aiEnrichment("CREATE enrichment never generates query", "DEBUG")
                    false
                }
            }

            LogManager.aiEnrichment("shouldGenerateQuery($type) = $shouldGenerate", "DEBUG")
            shouldGenerate
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to check query generation: ${e.message}", "ERROR", e)
            false
        }
    }

    /**
     * Generate DataCommands for prompt Level 4 inclusion (suspend version)
     * Returns multiple commands as enrichments can require both config and data
     * Requires coordinator for schema ID resolution
     */
    suspend fun generateCommands(
        type: EnrichmentType,
        config: String,
        isRelative: Boolean = false
    ): List<DataCommand> {
        LogManager.aiEnrichment("EnrichmentProcessor.generateCommands() called with type=$type, isRelative=$isRelative", "DEBUG")

        if (!shouldGenerateQuery(type, config)) {
            LogManager.aiEnrichment("Skipping query generation for $type (shouldGenerateQuery = false)", "DEBUG")
            return emptyList()
        }

        return try {
            val configJson = JSONObject(config)

            val queries = when (type) {
                EnrichmentType.POINTER -> generatePointerQueries(configJson, isRelative)
                EnrichmentType.USE -> generateUseQueries(configJson, isRelative)
                EnrichmentType.CREATE -> generateCreateQueries(configJson, isRelative)
                EnrichmentType.MODIFY_CONFIG -> generateModifyConfigQueries(configJson, isRelative)
                else -> {
                    LogManager.aiEnrichment("No query generator for type $type", "WARN")
                    emptyList()
                }
            }

            LogManager.aiEnrichment("Generated ${queries.size} DataCommands for $type", "DEBUG")
            queries.forEach { query ->
                LogManager.aiEnrichment("- id='${query.id}', type='${query.type}', isRelative=${query.isRelative}", "VERBOSE")
            }

            queries
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to generate enrichment queries: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // ========================================================================================
    // Summary Generation
    // ========================================================================================

    private fun generatePointerSummary(config: JSONObject): String {
        val path = config.optString("selected_path", "")
        val selectionLevel = config.optString("selection_level", "")
        val contextName = config.optString("selected_context", "GENERIC")
        val resourcesArray = config.optJSONArray("selected_resources")
        val selectedResources = mutableListOf<String>()
        if (resourcesArray != null) {
            for (i in 0 until resourcesArray.length()) {
                selectedResources.add(resourcesArray.getString(i))
            }
        }

        // Extract zone/tool from path
        val pathParts = path.split(".")
        val zoneName = if (pathParts.size > 1) pathParts[1] else s.shared("content_unnamed")
        val toolName = if (pathParts.size > 2) pathParts[2] else s.shared("content_unnamed")

        // Build comprehensive summary with context
        val parts = mutableListOf<String>()

        // 1. Base selection (Zone or Tool)
        when (selectionLevel) {
            "ZONE" -> {
                parts.add("${s.shared("ai_enrichment_pointer_zone")} '$zoneName'")
            }
            "INSTANCE" -> {
                parts.add("${s.shared("ai_enrichment_pointer_tool")} '$toolName'")
            }
            else -> {
                parts.add(s.shared("ai_enrichment_pointer_generic"))
            }
        }

        // 2. Context specification
        when (contextName) {
            "CONFIG" -> parts.add(s.shared("ai_enrichment_pointer_context_config"))
            "DATA" -> parts.add(s.shared("ai_enrichment_pointer_context_data"))
            // GENERIC: no context specification
        }

        // 3. Period (if present and relevant)
        val timestampSelection = config.optJSONObject("timestamp_selection")
        if (timestampSelection != null && contextName == "DATA") {
            val periodDesc = formatPointerPeriodDescription(timestampSelection)
            if (periodDesc.isNotEmpty()) {
                parts.add(periodDesc)
            }
        }

        // 4. Resources (if multiple or non-default)
        if (selectedResources.size > 1) {
            val resourcesDesc = selectedResources.joinToString(", ")
            parts.add("($resourcesDesc)")
        }

        return parts.joinToString(", ")
    }

    /**
     * Format period description for POINTER inline summary
     * Handles both absolute periods (CHAT) and relative periods (AUTOMATION)
     */
    private fun formatPointerPeriodDescription(timestampSelection: JSONObject): String {
        // Check for relative periods first (AUTOMATION)
        val minRelativePeriod = timestampSelection.optJSONObject("min_relative_period")
        val maxRelativePeriod = timestampSelection.optJSONObject("max_relative_period")

        if (minRelativePeriod != null || maxRelativePeriod != null) {
            // Relative period mode (AUTOMATION)
            val startDesc = if (minRelativePeriod != null) {
                val offset = minRelativePeriod.getInt("offset")
                val type = minRelativePeriod.getString("type")
                formatRelativePeriodLabel(offset, type)
            } else null

            val endDesc = if (maxRelativePeriod != null) {
                val offset = maxRelativePeriod.getInt("offset")
                val type = maxRelativePeriod.getString("type")
                formatRelativePeriodLabel(offset, type)
            } else null

            return when {
                startDesc != null && endDesc != null -> s.shared("ai_enrichment_pointer_period_range").format(startDesc, endDesc)
                startDesc != null -> s.shared("ai_enrichment_pointer_period_from").format(startDesc)
                endDesc != null -> s.shared("ai_enrichment_pointer_period_until").format(endDesc)
                else -> ""
            }
        }

        // Check for absolute periods, custom dates, or NOW markers (CHAT)
        val minPeriod = timestampSelection.optJSONObject("min_period")
        val maxPeriod = timestampSelection.optJSONObject("max_period")
        val minCustomDateTime = timestampSelection.optLong("min_custom_date_time", -1).takeIf { it != -1L }
        val maxCustomDateTime = timestampSelection.optLong("max_custom_date_time", -1).takeIf { it != -1L }
        val minIsNow = timestampSelection.optBoolean("min_is_now", false)
        val maxIsNow = timestampSelection.optBoolean("max_is_now", false)

        val startDate = when {
            minIsNow -> s.shared("period_now_label")  // "Maintenant"
            minCustomDateTime != null -> formatTimestamp(minCustomDateTime)
            minPeriod != null -> {
                val timestamp = minPeriod.getLong("timestamp")
                formatTimestamp(timestamp)
            }
            else -> null
        }

        val endDate = when {
            maxIsNow -> s.shared("period_now_label")  // "Maintenant"
            maxCustomDateTime != null -> formatTimestamp(maxCustomDateTime)
            maxPeriod != null -> {
                val timestamp = maxPeriod.getLong("timestamp")
                formatTimestamp(timestamp)
            }
            else -> null
        }

        return when {
            startDate != null && endDate != null -> s.shared("ai_enrichment_pointer_period_range").format(startDate, endDate)
            startDate != null -> s.shared("ai_enrichment_pointer_period_from").format(startDate)
            endDate != null -> s.shared("ai_enrichment_pointer_period_until").format(endDate)
            else -> ""
        }
    }

    /**
     * Format relative period label for inline display
     * Example: "il y a 2 semaines"
     */
    private fun formatRelativePeriodLabel(offset: Int, type: String): String {
        val absOffset = kotlin.math.abs(offset)
        val unitStr = when (type) {
            "DAY" -> if (absOffset == 1) s.shared("time_day") else s.shared("period_days")
            "WEEK" -> if (absOffset == 1) s.shared("period_week") else s.shared("period_weeks")
            "MONTH" -> if (absOffset == 1) s.shared("period_month") else s.shared("period_months")
            "YEAR" -> if (absOffset == 1) s.shared("period_year") else s.shared("period_years")
            else -> type.lowercase()
        }

        return if (offset == 0) {
            s.shared("period_now")
        } else if (offset < 0) {
            // Negative offset = in the past
            s.shared("ai_enrichment_pointer_relative_ago").format(absOffset, unitStr)
        } else {
            // Positive offset = in the future
            s.shared("ai_enrichment_pointer_relative_future").format(absOffset, unitStr)
        }
    }

    /**
     * Format timestamp for the enrichment's description: ISO 8601 with its offset, in the
     * app's timezone, like every date the AI reads.
     */
    private fun formatTimestamp(timestamp: Long): String =
        com.assistant.core.utils.DateTimeConverter.timestampToISO(
            timestamp, com.assistant.core.utils.AppConfigManager.getDateTimeConfig().getZoneId()
        )

    private fun generateUseSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("tool_instance_id", "")
        val operation = config.optString("operation", "modifier")
        // TODO: resolve tool instance name from ID for better readability
        return "$operation entrées $toolInstanceId"
    }

    private fun generateCreateSummary(config: JSONObject): String {
        val toolType = config.optString("tooltype", "outil")
        val zoneName = config.optString("zone_name", "")
        val suggestedName = config.optString("suggested_name", "")

        val name = if (suggestedName.isNotEmpty()) suggestedName else toolType
        val zone = if (zoneName.isNotEmpty()) " ${s.shared("ai_enrichment_zone_prefix")} $zoneName" else ""

        return "créer $name$zone"
    }

    private fun generateModifyConfigSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("tool_instance_id", "")
        val aspect = config.optString("aspect", "configuration")
        // TODO: resolve tool instance name from ID for better readability
        return "modifier $aspect de $toolInstanceId"
    }

    // TODO: Implement ORGANIZE enrichment type () - lower priority
    // private fun generateOrganizeSummary(config: JSONObject): String {
    // val action = config.optString("action", "organiser")
    // val elementId = config.optString("element_id", "")
    // return "$action $elementId"
    // }

    // TODO: Implement DOCUMENT enrichment type () - lower priority
    // private fun generateDocumentSummary(config: JSONObject): String {
    // val elementType = config.optString("element_type", "element")
    // val docType = config.optString("doc_type", "documentation")
    // return "$docType $elementType"
    // }

    // ========================================================================================
    // Schema ID Resolution
    // ========================================================================================

    /**
     * The tooltype of the tool [toolInstanceId], for the SCHEMA command of its config.
     *
     * @throws IllegalStateException if the tool cannot be read
     */
    private suspend fun resolveTooltype(toolInstanceId: String): String {
        if (coordinator == null) {
            throw IllegalStateException("Cannot resolve the tooltype: coordinator not available")
        }
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) {
            throw IllegalStateException("Failed to fetch tool instance $toolInstanceId: ${result.error}")
        }
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
            ?: throw IllegalStateException("Tool instance $toolInstanceId not found in response")
        return toolInstance["tooltype"] as? String
            ?: throw IllegalStateException("Tool instance $toolInstanceId has no tooltype")
    }

    /** The SCHEMA command of the config of a tool of [tooltype]. */
    private fun configSchemaQuery(tooltype: String, isRelative: Boolean) = DataCommand(
        id = buildQueryId("schema_config", mapOf("tooltype" to tooltype)),
        type = "SCHEMA",
        params = mapOf("tooltype" to tooltype),
        isRelative = isRelative
    )

    /** The SCHEMA command of the entries of the tool [toolInstanceId], its user's fields included. */
    private fun entriesSchemaQuery(toolInstanceId: String, isRelative: Boolean) = DataCommand(
        id = buildQueryId("schema_data", mapOf("tool_instance_id" to toolInstanceId)),
        type = "SCHEMA",
        params = mapOf("tool_instance_id" to toolInstanceId),
        isRelative = isRelative
    )

    // ========================================================================================
    // Query Generation
    // ========================================================================================

    /**
     * Generate DataCommands for POINTER enrichment based on context-aware selection
     *
     * Logic:
     * - GENERIC context: No automatic commands (AI must request explicitly)
     * - CONFIG context: Generate commands for config/config_schema resources
     * - DATA context: Generate commands for data/data_schema resources + temporal filters
     */
    private suspend fun generatePointerQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generatePointerQueries() called with isRelative=$isRelative", "DEBUG")

        val path = config.optString("selected_path", "")
        val selectionLevel = config.optString("selection_level", "")
        val contextName = config.optString("selected_context", "GENERIC")
        val resourcesArray = config.optJSONArray("selected_resources")
        val selectedResources = mutableListOf<String>()
        if (resourcesArray != null) {
            for (i in 0 until resourcesArray.length()) {
                selectedResources.add(resourcesArray.getString(i))
            }
        }

        LogManager.aiEnrichment("POINTER config: path='$path', level='$selectionLevel', context='$contextName', resources=$selectedResources", "VERBOSE")

        // Parse context enum
        val context = try {
            com.assistant.core.ui.selectors.data.PointerContext.valueOf(contextName)
        } catch (e: Exception) {
            LogManager.aiEnrichment("Invalid context '$contextName', defaulting to GENERIC", "WARN")
            com.assistant.core.ui.selectors.data.PointerContext.GENERIC
        }

        // GENERIC context: no automatic commands
        if (context == com.assistant.core.ui.selectors.data.PointerContext.GENERIC) {
            LogManager.aiEnrichment("GENERIC context: skipping automatic command generation", "DEBUG")
            return emptyList()
        }

        val queries = mutableListOf<DataCommand>()

        // Extract IDs from path
        val pathParts = path.split(".")
        LogManager.aiEnrichment("Path parts: ${pathParts.joinToString(", ")}", "VERBOSE")

        when (selectionLevel) {
            "ZONE" -> {
                // Path format: "zones.{zoneId}"
                val zoneId = if (pathParts.size > 1 && pathParts[0] == "zones") pathParts[1] else ""
                if (zoneId.isEmpty()) {
                    LogManager.aiEnrichment("Empty zone ID in path", "WARN")
                    return emptyList()
                }

                // Generate commands based on context (only CONFIG makes sense for ZONE level)
                when (context) {
                    com.assistant.core.ui.selectors.data.PointerContext.CONFIG -> {
                        if ("config" in selectedResources) {
                            queries.add(DataCommand(
                                id = buildQueryId("zone_config", mapOf("id" to zoneId)),
                                type = "ZONE_CONFIG",
                                params = mapOf("id" to zoneId),
                                isRelative = isRelative
                            ))
                        }
                        // Note: config_schema for zones not implemented yet
                    }
                    else -> {
                        LogManager.aiEnrichment("Context $context not supported for ZONE level", "WARN")
                    }
                }
            }
            "INSTANCE" -> {
                // Path format: "tools.{toolInstanceId}"
                val toolInstanceId = if (pathParts.size > 1 && pathParts[0] == "tools") pathParts[1] else ""
                if (toolInstanceId.isEmpty()) {
                    LogManager.aiEnrichment("Empty tool instance ID in path", "WARN")
                    return emptyList()
                }

                val baseParams = mutableMapOf<String, Any>("id" to toolInstanceId)

                // Generate commands based on context and selected resources
                when (context) {
                    com.assistant.core.ui.selectors.data.PointerContext.CONFIG -> {
                        // Config resource
                        if ("config" in selectedResources) {
                            queries.add(DataCommand(
                                id = buildQueryId("tool_config", baseParams),
                                type = "TOOL_CONFIG",
                                params = baseParams.toMap(),
                                isRelative = isRelative
                            ))
                        }

                        // Config schema resource
                        if ("config_schema" in selectedResources) {
                            queries.add(configSchemaQuery(resolveTooltype(toolInstanceId), isRelative))
                        }
                    }
                    com.assistant.core.ui.selectors.data.PointerContext.DATA -> {
                        // Add temporal parameters for DATA context
                        val dataParams = baseParams.toMutableMap()
                        addTemporalParams(dataParams, config, isRelative)

                        // Data resource
                        if ("data" in selectedResources) {
                            queries.add(DataCommand(
                                id = buildQueryId("tool_data", dataParams),
                                type = "TOOL_DATA",
                                params = dataParams.toMap(),
                                isRelative = isRelative
                            ))
                        }

                        // Data schema resource, the tool's user's fields included
                        if ("data_schema" in selectedResources) {
                            queries.add(entriesSchemaQuery(toolInstanceId, isRelative))
                        }
                    }
                    com.assistant.core.ui.selectors.data.PointerContext.GENERIC -> {
                        // Already handled above (returns empty)
                    }
                }
            }
        }

        LogManager.aiEnrichment("Generated ${queries.size} POINTER queries for context=$context, level=$selectionLevel", "DEBUG")
        return queries
    }

    private suspend fun generateUseQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generateUseQueries() called with isRelative=$isRelative", "DEBUG")

        val toolInstanceId = config.optString("tool_instance_id", "")
        if (toolInstanceId.isEmpty()) return emptyList()

        val queries = mutableListOf<DataCommand>()
        val baseParams = mapOf("id" to toolInstanceId)

        // USE enrichment: TOOL_CONFIG + SCHEMA(config) + SCHEMA(data) + TOOL_DATA_SAMPLE + TOOL_STATS
        queries.add(DataCommand(
            id = buildQueryId("tool_config", baseParams),
            type = "TOOL_CONFIG",
            params = baseParams,
            isRelative = isRelative
        ))

        queries.add(configSchemaQuery(resolveTooltype(toolInstanceId), isRelative))
        queries.add(entriesSchemaQuery(toolInstanceId, isRelative))

        queries.add(DataCommand(
            id = buildQueryId("tool_data_sample", baseParams),
            type = "TOOL_DATA_SAMPLE",
            params = baseParams,
            isRelative = isRelative
        ))
        queries.add(DataCommand(
            id = buildQueryId("tool_stats", baseParams),
            type = "TOOL_STATS",
            params = baseParams,
            isRelative = isRelative
        ))

        LogManager.aiEnrichment("Generated ${queries.size} USE queries for toolInstanceId=$toolInstanceId", "DEBUG")
        return queries
    }

    private fun generateCreateQueries(config: JSONObject, isRelative: Boolean): List<DataCommand> {
        LogManager.aiEnrichment("generateCreateQueries() called with isRelative=$isRelative", "DEBUG")

        // TODO: Implement CREATE enrichment with schema-driven tooltype selection
        // - UI provides the tooltype from the tooltype selection dialog
        // - Generate SCHEMA(tooltype)

        LogManager.aiEnrichment("CREATE enrichment - STUB implementation", "DEBUG")
        return emptyList()
    }

    private suspend fun generateModifyConfigQueries(config: JSONObject, isRelative: Boolean): List<DataCommand> {
        LogManager.aiEnrichment("generateModifyConfigQueries() called with isRelative=$isRelative", "DEBUG")

        val toolInstanceId = config.optString("tool_instance_id", "")
        if (toolInstanceId.isEmpty()) return emptyList()

        val queries = mutableListOf<DataCommand>()
        val baseParams = mapOf("id" to toolInstanceId)

        // MODIFY_CONFIG enrichment: SCHEMA(config) + TOOL_CONFIG
        queries.add(configSchemaQuery(resolveTooltype(toolInstanceId), isRelative))

        queries.add(DataCommand(
            id = buildQueryId("tool_config", baseParams),
            type = "TOOL_CONFIG",
            params = baseParams,
            isRelative = isRelative
        ))

        LogManager.aiEnrichment("Generated ${queries.size} MODIFY_CONFIG queries for toolInstanceId=$toolInstanceId", "DEBUG")
        return queries
    }

    // ========================================================================================
    // Utility Methods
    // ========================================================================================

    /**
     * The pointer's period as filters on timestamp, from its first instant to its last.
     *
     * Each bound is "NOW", a relative period ("-7_DAY", from an automation, resolved at each run)
     * or milliseconds (a date picked in a chat, or the start and last instant of a period);
     * the command transformer puts "NOW" and relative periods in milliseconds, taking the start
     * of a relative period for the lower bound and its end for the upper one.
     */
    private fun addTemporalParams(
        params: MutableMap<String, Any>,
        configJson: JSONObject?,
        isRelative: Boolean
    ) {
        val selection = configJson?.optJSONObject("timestamp_selection") ?: return

        fun relative(key: String): String? =
            selection.optJSONObject(key)?.let { "${it.getInt("offset")}_${it.getString("type")}" }
        fun custom(key: String): Long? = selection.optLong(key, -1).takeIf { it != -1L }
        fun period(key: String, end: Boolean): Long? = selection.optJSONObject(key)?.let {
            val period = com.assistant.core.ui.components.Period(
                it.getLong("timestamp"), com.assistant.core.ui.components.PeriodType.valueOf(it.getString("type"))
            )
            if (end) com.assistant.core.ui.components.getPeriodEndTimestamp(period) else period.timestamp
        }

        val start: Any? = when {
            selection.optBoolean("min_is_now", false) -> "NOW"
            isRelative -> relative("min_relative_period") ?: custom("min_custom_date_time")
            else -> custom("min_custom_date_time") ?: period("min_period", end = false)
        }
        val end: Any? = when {
            selection.optBoolean("max_is_now", false) -> "NOW"
            isRelative -> relative("max_relative_period") ?: custom("max_custom_date_time")
            else -> custom("max_custom_date_time") ?: period("max_period", end = true)
        }

        val filters = listOfNotNull(
            start?.let { mapOf("field" to "timestamp", "op" to ">=", "value" to it) },
            end?.let { mapOf("field" to "timestamp", "op" to "<=", "value" to it) }
        )
        if (filters.isNotEmpty()) params["filters"] = filters
        LogManager.aiEnrichment("addTemporalParams() - filters=$filters", "DEBUG")
    }

    private fun formatPeriodDescription(timestampData: JSONObject): String {
        val startTs = timestampData.optLong("start_timestamp", 0)
        val endTs = timestampData.optLong("end_timestamp", 0)

        if (startTs == 0L && endTs == 0L) return ""

        // TODO: Implement proper period formatting based on timestamps
        // For now, return basic description
        return if (startTs == endTs) {
            s.shared("ai_period_specific")
        } else {
            s.shared("ai_period_extended")
        }
    }

    private fun buildQueryId(prefix: String, params: Map<String, Any>): String {
        val sortedParams = params.toSortedMap()
        val paramString = sortedParams.map { "${it.key}_${it.value}" }.joinToString(".")
        return "$prefix.$paramString"
    }
}