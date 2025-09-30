package com.assistant.core.ai.enrichments

import android.content.Context
import com.assistant.core.ai.data.DataCommand
import com.assistant.core.ai.data.EnrichmentType
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
class EnrichmentProcessor(private val context: Context) {

    private val s = Strings.`for`(context = context)

    /**
     * Generate human-readable summary for enrichment display in messages
     */
    fun generateSummary(type: EnrichmentType, config: String): String {
        LogManager.aiEnrichment("EnrichmentProcessor.generateSummary() called with type=$type, config length=${config.length}")

        return try {
            val configJson = JSONObject(config)

            val summary = when (type) {
                EnrichmentType.POINTER -> generatePointerSummary(configJson)
                EnrichmentType.USE -> generateUseSummary(configJson)
                EnrichmentType.CREATE -> generateCreateSummary(configJson)
                EnrichmentType.MODIFY_CONFIG -> generateModifyConfigSummary(configJson)
            }

            LogManager.aiEnrichment("Generated summary for $type: '$summary'")
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
        LogManager.aiEnrichment("EnrichmentProcessor.shouldGenerateQuery() called with type=$type")

        return try {
            val configJson = JSONObject(config)

            val shouldGenerate = when (type) {
                EnrichmentType.POINTER -> {
                    val importance = configJson.optString("importance", "important")
                    val result = importance != "optionnelle"
                    LogManager.aiEnrichment("POINTER enrichment importance='$importance', shouldGenerate=$result")
                    result
                }
                EnrichmentType.USE, EnrichmentType.MODIFY_CONFIG -> {
                    LogManager.aiEnrichment("$type enrichment always generates query")
                    true
                }
                EnrichmentType.CREATE -> {
                    LogManager.aiEnrichment("CREATE enrichment never generates query")
                    false
                }
            }

            LogManager.aiEnrichment("shouldGenerateQuery($type) = $shouldGenerate")
            shouldGenerate
        } catch (e: Exception) {
            LogManager.aiEnrichment("Failed to check query generation: ${e.message}", "ERROR", e)
            false
        }
    }

    /**
     * Generate DataCommands for prompt Level 4 inclusion
     * Returns multiple commands as enrichments can require both config and data
     */
    fun generateCommands(
        type: EnrichmentType,
        config: String,
        isRelative: Boolean = false
    ): List<DataCommand> {
        LogManager.aiEnrichment("EnrichmentProcessor.generateCommands() called with type=$type, isRelative=$isRelative")

        if (!shouldGenerateQuery(type, config)) {
            LogManager.aiEnrichment("Skipping query generation for $type (shouldGenerateQuery = false)")
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
                    LogManager.aiEnrichment("No query generator for type $type")
                    emptyList()
                }
            }

            LogManager.aiEnrichment("Generated ${queries.size} DataCommands for $type:")
            queries.forEach { query ->
                LogManager.aiEnrichment("  - id='${query.id}', type='${query.type}', isRelative=${query.isRelative}")
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
        val zone = if (zoneName.isNotEmpty()) " zone $zoneName" else ""

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
    // Query Generation
    // ========================================================================================

    private fun generatePointerQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generatePointerQueries() called with isRelative=$isRelative")

        val path = config.optString("selectedPath", "")
        val selectionLevel = config.optString("selectionLevel", "")
        val includeData = config.optBoolean("includeData", false) // Toggle for real data

        LogManager.aiEnrichment("POINTER queries config: path='$path', selectionLevel='$selectionLevel', includeData=$includeData")

        // Extract zone and tool info from path
        val pathParts = path.split(".")
        LogManager.aiEnrichment("Path parts: ${pathParts.joinToString(", ")}")

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

                    // TODO: Get schema IDs from tool instance config - for now using placeholders
                    queries.add(DataCommand(
                        id = buildQueryId("schema_config", mapOf("id" to "config_schema_id")),
                        type = "SCHEMA",
                        params = mapOf("id" to "config_schema_id"), // TODO: resolve from tool instance
                        isRelative = isRelative
                    ))
                    queries.add(DataCommand(
                        id = buildQueryId("schema_data", mapOf("id" to "data_schema_id")),
                        type = "SCHEMA",
                        params = mapOf("id" to "data_schema_id"), // TODO: resolve from tool instance
                        isRelative = isRelative
                    ))

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

        LogManager.aiEnrichment("Generated ${queries.size} POINTER queries for selectionLevel=$selectionLevel")
        return queries
    }

    private fun generateUseQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generateUseQueries() called with isRelative=$isRelative")

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

        // TODO: Get schema IDs from tool instance config - for now using placeholders
        queries.add(DataCommand(
            id = buildQueryId("schema_config", mapOf("id" to "config_schema_id")),
            type = "SCHEMA",
            params = mapOf("id" to "config_schema_id"), // TODO: resolve from tool instance
            isRelative = isRelative
        ))
        queries.add(DataCommand(
            id = buildQueryId("schema_data", mapOf("id" to "data_schema_id")),
            type = "SCHEMA",
            params = mapOf("id" to "data_schema_id"), // TODO: resolve from tool instance
            isRelative = isRelative
        ))

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

        LogManager.aiEnrichment("Generated ${queries.size} USE queries for toolInstanceId=$toolInstanceId")
        return queries
    }

    private fun generateCreateQueries(config: JSONObject, isRelative: Boolean): List<DataCommand> {
        LogManager.aiEnrichment("generateCreateQueries() called with isRelative=$isRelative")

        // TODO: Implement CREATE enrichment with schema-driven tooltype selection
        // - UI provides config_schema_id from tooltype selection dialog
        // - Load config schema to extract data_schema_id
        // - Generate SCHEMA(config_schema_id) + SCHEMA(data_schema_id)

        LogManager.aiEnrichment("CREATE enrichment - STUB implementation")
        return emptyList()
    }

    private fun generateModifyConfigQueries(config: JSONObject, isRelative: Boolean): List<DataCommand> {
        LogManager.aiEnrichment("generateModifyConfigQueries() called with isRelative=$isRelative")

        val toolInstanceId = config.optString("toolInstanceId", "")
        if (toolInstanceId.isEmpty()) return emptyList()

        val queries = mutableListOf<DataCommand>()
        val baseParams = mapOf("id" to toolInstanceId)

        // MODIFY_CONFIG enrichment: SCHEMA(config) + TOOL_CONFIG
        // TODO: Get schema ID from tool instance config - for now using placeholder
        queries.add(DataCommand(
            id = buildQueryId("schema_config", mapOf("id" to "config_schema_id")),
            type = "SCHEMA",
            params = mapOf("id" to "config_schema_id"), // TODO: resolve from tool instance
            isRelative = isRelative
        ))

        queries.add(DataCommand(
            id = buildQueryId("tool_config", baseParams),
            type = "TOOL_CONFIG",
            params = baseParams,
            isRelative = isRelative
        ))

        LogManager.aiEnrichment("Generated ${queries.size} MODIFY_CONFIG queries for toolInstanceId=$toolInstanceId")
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
            LogManager.aiEnrichment("No timestampSelection found in config")
            return
        }

        LogManager.aiEnrichment("Found timestampSelection: $timestampSelection")

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
                LogManager.aiEnrichment("Added relative period parameters: start=$periodStart, end=$periodEnd")
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
                LogManager.aiEnrichment("Added startTime parameter: $startTs")
            }
            if (endTs != null) {
                params["endTime"] = endTs
                LogManager.aiEnrichment("Added endTime parameter: $endTs")
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