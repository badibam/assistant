package com.assistant.core.ai.enrichments

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.EnrichmentType
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.AppConfigManager
import org.json.JSONObject

/**
 * Processor responsible for handling enrichment blocks in two forms:
 * 1. Preview text generation for user interface display
 * 2. DataCommand generation for AI prompt Level 4 inclusion
 *
 * Core logic:
 * - All enrichments generate textual summaries for AI orientation
 * - Only specific types generate DataCommands for Level 4 prompt inclusion:
 *   * 🔍 POINTER: Query if importance != 'optionnelle'
 *   * 📝 USE: Query for tool instance config
 *   * 🔧 MODIFY_CONFIG: Query for tool instance config
 *   * ✨ CREATE: No query (just orientation)
 *   * 📁 ORGANIZE: No query (just orientation)
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
            val configJson = JSONObject(config)

            val shouldGenerate = when (type) {
                EnrichmentType.POINTER -> {
                    val importance = configJson.optString("importance", "important")
                    val result = importance != "optionnelle"
                    LogManager.aiEnrichment("POINTER enrichment importance='$importance', shouldGenerate=$result", "DEBUG")
                    result
                }
                EnrichmentType.USE, EnrichmentType.MODIFY_CONFIG -> {
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
                LogManager.aiEnrichment("  - id='${query.id}', type='${query.type}', isRelative=${query.isRelative}", "VERBOSE")
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
        val path = config.optString("selectedPath", "")
        val selectionLevel = config.optString("selectionLevel", "")
        val fieldSpecificData = config.optJSONObject("fieldSpecificData")

        // Extract context from path
        val pathParts = path.split(".")
        val zoneContext = if (pathParts.size > 1) pathParts[1] else ""
        val toolContext = if (pathParts.size > 2) pathParts[2] else ""

        // Build base description
        val baseDescription = when (selectionLevel) {
            "ZONE" -> "${s.shared("ai_data_zone")} $zoneContext"
            "INSTANCE" -> "${s.shared("ai_data_tool")} $toolContext zone $zoneContext"
            "FIELD" -> "${s.shared("ai_data_field")} $toolContext zone $zoneContext"
            else -> s.shared("ai_data_generic")
        }

        // Add temporal context if available
        return if (fieldSpecificData?.has("timestampData") == true) {
            val timestampData = fieldSpecificData.getJSONObject("timestampData")
            val periodDescription = formatPeriodDescription(timestampData)
            "$baseDescription $periodDescription"
        } else {
            baseDescription
        }
    }

    private fun generateUseSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("toolInstanceId", "")
        val operation = config.optString("operation", "modifier")
        // TODO: resolve tool instance name from ID for better readability
        return "$operation entrées $toolInstanceId"
    }

    private fun generateCreateSummary(config: JSONObject): String {
        val toolType = config.optString("toolType", "outil")
        val zoneName = config.optString("zoneName", "")
        val suggestedName = config.optString("suggestedName", "")

        val name = if (suggestedName.isNotEmpty()) suggestedName else toolType
        val zone = if (zoneName.isNotEmpty()) " ${s.shared("ai_enrichment_zone_prefix")} $zoneName" else ""

        return "créer $name$zone"
    }

    private fun generateModifyConfigSummary(config: JSONObject): String {
        val toolInstanceId = config.optString("toolInstanceId", "")
        val aspect = config.optString("aspect", "configuration")
        // TODO: resolve tool instance name from ID for better readability
        return "modifier $aspect de $toolInstanceId"
    }

    // TODO: Implement ORGANIZE enrichment type (📁) - lower priority
    // private fun generateOrganizeSummary(config: JSONObject): String {
    //     val action = config.optString("action", "organiser")
    //     val elementId = config.optString("elementId", "")
    //     return "$action $elementId"
    // }

    // TODO: Implement DOCUMENT enrichment type (📚) - lower priority
    // private fun generateDocumentSummary(config: JSONObject): String {
    //     val elementType = config.optString("elementType", "élément")
    //     val docType = config.optString("docType", "documentation")
    //     return "$docType $elementType"
    // }

    // ========================================================================================
    // Schema ID Resolution
    // ========================================================================================

    /**
     * Resolve schema IDs from tool instance config
     * Returns Pair(config_schema_id, data_schema_id) or null if resolution fails
     */
    private suspend fun resolveSchemaIds(toolInstanceId: String): Pair<String, String>? {
        if (coordinator == null) {
            LogManager.aiEnrichment("Cannot resolve schema IDs: coordinator not available", "WARN")
            return null
        }

        try {
            val result = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))

            if (!result.isSuccess) {
                LogManager.aiEnrichment("Failed to fetch tool instance $toolInstanceId: ${result.error}", "WARN")
                return null
            }

            val toolInstance = result.data?.get("tool") as? Map<*, *>
            val configJson = toolInstance?.get("config") as? String

            if (configJson.isNullOrEmpty()) {
                LogManager.aiEnrichment("Tool instance $toolInstanceId has no config", "WARN")
                return null
            }

            val config = JSONObject(configJson)
            val configSchemaId = config.optString("schema_id", "")
            val dataSchemaId = config.optString("data_schema_id", "")

            if (configSchemaId.isEmpty() || dataSchemaId.isEmpty()) {
                LogManager.aiEnrichment("Tool instance $toolInstanceId missing schema IDs (config=$configSchemaId, data=$dataSchemaId)", "WARN")
                return null
            }

            LogManager.aiEnrichment("Resolved schema IDs for $toolInstanceId: config=$configSchemaId, data=$dataSchemaId", "DEBUG")
            return Pair(configSchemaId, dataSchemaId)

        } catch (e: Exception) {
            LogManager.aiEnrichment("Error resolving schema IDs for $toolInstanceId: ${e.message}", "ERROR", e)
            return null
        }
    }

    // ========================================================================================
    // Query Generation
    // ========================================================================================

    private suspend fun generatePointerQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generatePointerQueries() called with isRelative=$isRelative", "DEBUG")

        val path = config.optString("selectedPath", "")
        val selectionLevel = config.optString("selectionLevel", "")
        val includeData = config.optBoolean("includeData", false) // Toggle for real data

        LogManager.aiEnrichment("POINTER queries config: path='$path', selectionLevel='$selectionLevel', includeData=$includeData", "VERBOSE")

        // Extract zone and tool info from path
        val pathParts = path.split(".")
        LogManager.aiEnrichment("Path parts: ${pathParts.joinToString(", ")}", "VERBOSE")

        val queries = mutableListOf<DataCommand>()

        when (selectionLevel) {
            "ZONE" -> {
                // Zone selected: ZONE_CONFIG + TOOL_INSTANCES
                // Path format: "zones.{zoneId}"
                val zoneId = if (pathParts.size > 1 && pathParts[0] == "zones") pathParts[1] else ""
                if (zoneId.isNotEmpty()) {
                    queries.add(DataCommand(
                        id = buildQueryId("zone_config", mapOf("id" to zoneId)),
                        type = "ZONE_CONFIG",
                        params = mapOf("id" to zoneId),
                        isRelative = isRelative
                    ))
                    queries.add(DataCommand(
                        id = buildQueryId("tool_instances", mapOf("zone_id" to zoneId)),
                        type = "TOOL_INSTANCES",
                        params = mapOf("zone_id" to zoneId),
                        isRelative = isRelative
                    ))
                }
            }
            "INSTANCE" -> {
                // Instance selected: SCHEMA(config) + SCHEMA(data) + TOOL_CONFIG + TOOL_DATA_SAMPLE + TOOL_STATS + optionally TOOL_DATA
                // Path format: "tools.{toolInstanceId}"
                val toolInstanceId = if (pathParts.size > 1 && pathParts[0] == "tools") pathParts[1] else ""
                if (toolInstanceId.isNotEmpty()) {
                    val baseParams = mutableMapOf<String, Any>("id" to toolInstanceId)

                    // Resolve schema IDs from tool instance config
                    val schemaIds = resolveSchemaIds(toolInstanceId)
                    if (schemaIds != null) {
                        val (configSchemaId, dataSchemaId) = schemaIds

                        queries.add(DataCommand(
                            id = buildQueryId("schema_config", mapOf("id" to configSchemaId)),
                            type = "SCHEMA",
                            params = mapOf("id" to configSchemaId),
                            isRelative = isRelative
                        ))
                        queries.add(DataCommand(
                            id = buildQueryId("schema_data", mapOf("id" to dataSchemaId)),
                            type = "SCHEMA",
                            params = mapOf("id" to dataSchemaId),
                            isRelative = isRelative
                        ))
                    } else {
                        LogManager.aiEnrichment("POINTER INSTANCE: Cannot resolve schema IDs for $toolInstanceId - SCHEMA queries skipped", "WARN")
                    }

                    queries.add(DataCommand(
                        id = buildQueryId("tool_config", baseParams),
                        type = "TOOL_CONFIG",
                        params = baseParams.toMap(),
                        isRelative = isRelative
                    ))

                    // Add temporal parameters for data queries
                    val dataParams = baseParams.toMutableMap()
                    addTemporalParams(dataParams, config, isRelative)

                    queries.add(DataCommand(
                        id = buildQueryId("tool_data_sample", dataParams),
                        type = "TOOL_DATA_SAMPLE",
                        params = dataParams.toMap(),
                        isRelative = isRelative
                    ))
                    queries.add(DataCommand(
                        id = buildQueryId("tool_stats", dataParams),
                        type = "TOOL_STATS",
                        params = dataParams.toMap(),
                        isRelative = isRelative
                    ))

                    // Optional real data if toggle enabled
                    if (includeData) {
                        queries.add(DataCommand(
                            id = buildQueryId("tool_data", dataParams),
                            type = "TOOL_DATA",
                            params = dataParams.toMap(),
                            isRelative = isRelative
                        ))
                    }
                }
            }
        }

        LogManager.aiEnrichment("Generated ${queries.size} POINTER queries for selectionLevel=$selectionLevel", "DEBUG")
        return queries
    }

    private suspend fun generateUseQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generateUseQueries() called with isRelative=$isRelative", "DEBUG")

        val toolInstanceId = config.optString("toolInstanceId", "")
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

        // Resolve schema IDs from tool instance config
        val schemaIds = resolveSchemaIds(toolInstanceId)
        if (schemaIds != null) {
            val (configSchemaId, dataSchemaId) = schemaIds

            queries.add(DataCommand(
                id = buildQueryId("schema_config", mapOf("id" to configSchemaId)),
                type = "SCHEMA",
                params = mapOf("id" to configSchemaId),
                isRelative = isRelative
            ))
            queries.add(DataCommand(
                id = buildQueryId("schema_data", mapOf("id" to dataSchemaId)),
                type = "SCHEMA",
                params = mapOf("id" to dataSchemaId),
                isRelative = isRelative
            ))
        } else {
            LogManager.aiEnrichment("USE: Cannot resolve schema IDs for $toolInstanceId - SCHEMA queries skipped", "WARN")
        }

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
        // - UI provides config_schema_id from tooltype selection dialog
        // - Load config schema to extract data_schema_id
        // - Generate SCHEMA(config_schema_id) + SCHEMA(data_schema_id)

        LogManager.aiEnrichment("CREATE enrichment - STUB implementation", "DEBUG")
        return emptyList()
    }

    private suspend fun generateModifyConfigQueries(config: JSONObject, isRelative: Boolean): List<DataCommand> {
        LogManager.aiEnrichment("generateModifyConfigQueries() called with isRelative=$isRelative", "DEBUG")

        val toolInstanceId = config.optString("toolInstanceId", "")
        if (toolInstanceId.isEmpty()) return emptyList()

        val queries = mutableListOf<DataCommand>()
        val baseParams = mapOf("id" to toolInstanceId)

        // MODIFY_CONFIG enrichment: SCHEMA(config) + TOOL_CONFIG
        // Resolve schema IDs from tool instance config
        val schemaIds = resolveSchemaIds(toolInstanceId)
        if (schemaIds != null) {
            val (configSchemaId, _) = schemaIds

            queries.add(DataCommand(
                id = buildQueryId("schema_config", mapOf("id" to configSchemaId)),
                type = "SCHEMA",
                params = mapOf("id" to configSchemaId),
                isRelative = isRelative
            ))
        } else {
            LogManager.aiEnrichment("MODIFY_CONFIG: Cannot resolve schema IDs for $toolInstanceId - SCHEMA query skipped", "WARN")
        }

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
     * Add temporal parameters to query params from timestampSelection in config
     * Handles both absolute periods (CHAT) and relative periods (AUTOMATION)
     */
    private fun addTemporalParams(
        params: MutableMap<String, Any>,
        configJson: JSONObject?,
        isRelative: Boolean
    ) {
        val timestampSelection = configJson?.optJSONObject("timestampSelection")
        if (timestampSelection == null) {
            LogManager.aiEnrichment("No timestampSelection found in config", "DEBUG")
            return
        }

        LogManager.aiEnrichment("Found timestampSelection: $timestampSelection", "VERBOSE")

        if (isRelative) {
            // AUTOMATION mode: use relative periods
            val minRelativePeriod = timestampSelection.optJSONObject("minRelativePeriod")
            val maxRelativePeriod = timestampSelection.optJSONObject("maxRelativePeriod")

            if (minRelativePeriod != null || maxRelativePeriod != null) {
                // Encode relative periods for UserCommandProcessor to resolve later
                val periodStart = minRelativePeriod?.let {
                    "${it.getInt("offset")}_${it.getString("type")}"
                } ?: "0_DAY"

                val periodEnd = maxRelativePeriod?.let {
                    "${it.getInt("offset")}_${it.getString("type")}"
                } ?: "0_DAY"

                params["period_start"] = periodStart
                params["period_end"] = periodEnd
                LogManager.aiEnrichment("Added relative period parameters: start=$periodStart, end=$periodEnd", "DEBUG")
            }
        } else {
            // CHAT mode: calculate absolute timestamps
            // Min timestamp (start of range)
            val startTs = when {
                timestampSelection.has("minCustomDateTime") ->
                    timestampSelection.getLong("minCustomDateTime")
                timestampSelection.has("minPeriod") -> {
                    val period = timestampSelection.getJSONObject("minPeriod")
                    period.getLong("timestamp")  // Start of min period
                }
                else -> null
            }

            // Max timestamp (end of range - must calculate end of max period)
            val endTs = when {
                timestampSelection.has("maxCustomDateTime") ->
                    timestampSelection.getLong("maxCustomDateTime")
                timestampSelection.has("maxPeriod") -> {
                    val period = timestampSelection.getJSONObject("maxPeriod")
                    val periodTimestamp = period.getLong("timestamp")
                    val periodType = com.assistant.core.ui.components.PeriodType.valueOf(period.getString("type"))
                    val periodObj = com.assistant.core.ui.components.Period(periodTimestamp, periodType)
                    // Calculate end of period
                    com.assistant.core.ui.components.getPeriodEndTimestamp(periodObj)
                }
                else -> null
            }

            if (startTs != null) {
                params["startTime"] = startTs
                LogManager.aiEnrichment("Added startTime parameter: $startTs", "DEBUG")
            }
            if (endTs != null) {
                params["endTime"] = endTs
                LogManager.aiEnrichment("Added endTime parameter: $endTs", "DEBUG")
            }
        }
    }

    private fun formatPeriodDescription(timestampData: JSONObject): String {
        val startTs = timestampData.optLong("startTimestamp", 0)
        val endTs = timestampData.optLong("endTimestamp", 0)

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