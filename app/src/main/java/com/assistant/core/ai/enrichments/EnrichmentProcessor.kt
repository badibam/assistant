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
 *   * 🔍 POINTER: Always generates query
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
        val contextName = config.optString("selectedContext", "GENERIC")
        val resourcesArray = config.optJSONArray("selectedResources")
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
            "EXECUTIONS" -> parts.add(s.shared("ai_enrichment_pointer_context_executions"))
            // GENERIC: no context specification
        }

        // 3. Period (if present and relevant)
        val timestampSelection = config.optJSONObject("timestampSelection")
        if (timestampSelection != null && (contextName == "DATA" || contextName == "EXECUTIONS")) {
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
        val minRelativePeriod = timestampSelection.optJSONObject("minRelativePeriod")
        val maxRelativePeriod = timestampSelection.optJSONObject("maxRelativePeriod")

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

        // Check for absolute periods or custom dates (CHAT)
        val minPeriod = timestampSelection.optJSONObject("minPeriod")
        val maxPeriod = timestampSelection.optJSONObject("maxPeriod")
        val minCustomDateTime = timestampSelection.optLong("minCustomDateTime", -1).takeIf { it != -1L }
        val maxCustomDateTime = timestampSelection.optLong("maxCustomDateTime", -1).takeIf { it != -1L }

        val startDate = when {
            minCustomDateTime != null -> formatTimestamp(minCustomDateTime)
            minPeriod != null -> {
                val timestamp = minPeriod.getLong("timestamp")
                formatTimestamp(timestamp)
            }
            else -> null
        }

        val endDate = when {
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
     * Format timestamp for inline display
     */
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(timestamp))
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
     * Throws IllegalStateException if resolution fails
     */
    private suspend fun resolveSchemaIds(toolInstanceId: String): Pair<String, String> {
        if (coordinator == null) {
            throw IllegalStateException("Cannot resolve schema IDs: coordinator not available")
        }

        val result = coordinator.processUserAction("tools.get", mapOf(
            "tool_instance_id" to toolInstanceId
        ))

        if (!result.isSuccess) {
            throw IllegalStateException("Failed to fetch tool instance $toolInstanceId: ${result.error}")
        }

        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
            ?: throw IllegalStateException("Tool instance $toolInstanceId not found in response")

        val configJson = toolInstance["config_json"] as? String
        if (configJson.isNullOrBlank()) {
            throw IllegalStateException("Tool instance $toolInstanceId has no config_json")
        }

        val config = JSONObject(configJson)
        val configSchemaId = config.optString("schema_id", "")
        val dataSchemaId = config.optString("data_schema_id", "")

        if (configSchemaId.isEmpty()) {
            throw IllegalStateException("Tool instance $toolInstanceId missing schema_id in config")
        }
        if (dataSchemaId.isEmpty()) {
            throw IllegalStateException("Tool instance $toolInstanceId missing data_schema_id in config")
        }

        LogManager.aiEnrichment("Resolved schema IDs for $toolInstanceId: config=$configSchemaId, data=$dataSchemaId", "DEBUG")
        return Pair(configSchemaId, dataSchemaId)
    }

    /**
     * Resolve execution schema ID from tool instance config
     * Returns empty string if not available (tooltype doesn't support executions)
     */
    private suspend fun resolveExecutionSchemaId(toolInstanceId: String): String {
        if (coordinator == null) {
            LogManager.aiEnrichment("Cannot resolve execution schema ID: coordinator not available", "WARN")
            return ""
        }

        val result = coordinator.processUserAction("tools.get", mapOf(
            "tool_instance_id" to toolInstanceId
        ))

        if (!result.isSuccess) {
            LogManager.aiEnrichment("Failed to fetch tool instance $toolInstanceId for execution schema ID: ${result.error}", "WARN")
            return ""
        }

        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        if (toolInstance == null) {
            LogManager.aiEnrichment("Tool instance $toolInstanceId not found in response", "WARN")
            return ""
        }

        val configJson = toolInstance["config_json"] as? String
        if (configJson.isNullOrBlank()) {
            LogManager.aiEnrichment("Tool instance $toolInstanceId has no config_json", "WARN")
            return ""
        }

        val config = JSONObject(configJson)
        val executionSchemaId = config.optString("execution_schema_id", "")

        LogManager.aiEnrichment("Resolved execution schema ID for $toolInstanceId: '$executionSchemaId'", "DEBUG")
        return executionSchemaId
    }

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
     * - EXECUTIONS context: Generate commands for executions/executions_schema resources + temporal filters
     */
    private suspend fun generatePointerQueries(
        config: JSONObject,
        isRelative: Boolean
    ): List<DataCommand> {
        LogManager.aiEnrichment("generatePointerQueries() called with isRelative=$isRelative", "DEBUG")

        val path = config.optString("selectedPath", "")
        val selectionLevel = config.optString("selectionLevel", "")
        val contextName = config.optString("selectedContext", "GENERIC")
        val resourcesArray = config.optJSONArray("selectedResources")
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
                        // Resolve schema IDs if needed
                        val needsSchemaId = "config_schema" in selectedResources
                        val configSchemaId = if (needsSchemaId) {
                            resolveSchemaIds(toolInstanceId).first
                        } else ""

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
                        if ("config_schema" in selectedResources && configSchemaId.isNotEmpty()) {
                            queries.add(DataCommand(
                                id = buildQueryId("schema_config", mapOf("id" to configSchemaId)),
                                type = "SCHEMA",
                                params = mapOf("id" to configSchemaId),
                                isRelative = isRelative
                            ))
                        }
                    }
                    com.assistant.core.ui.selectors.data.PointerContext.DATA -> {
                        // Resolve schema IDs if needed
                        val needsSchemaId = "data_schema" in selectedResources
                        val dataSchemaId = if (needsSchemaId) {
                            resolveSchemaIds(toolInstanceId).second
                        } else ""

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

                        // Data schema resource
                        if ("data_schema" in selectedResources && dataSchemaId.isNotEmpty()) {
                            queries.add(DataCommand(
                                id = buildQueryId("schema_data", mapOf("id" to dataSchemaId)),
                                type = "SCHEMA",
                                params = mapOf("id" to dataSchemaId),
                                isRelative = isRelative
                            ))
                        }
                    }
                    com.assistant.core.ui.selectors.data.PointerContext.EXECUTIONS -> {
                        // Resolve execution schema ID if needed
                        val needsSchemaId = "executions_schema" in selectedResources
                        val executionSchemaId = if (needsSchemaId) {
                            resolveExecutionSchemaId(toolInstanceId)
                        } else ""

                        // Add temporal parameters for EXECUTIONS context
                        val executionParams = baseParams.toMutableMap()
                        addTemporalParams(executionParams, config, isRelative)

                        // Executions resource
                        if ("executions" in selectedResources) {
                            queries.add(DataCommand(
                                id = buildQueryId("tool_executions", executionParams),
                                type = "TOOL_EXECUTIONS",
                                params = executionParams.toMap(),
                                isRelative = isRelative
                            ))
                        }

                        // Executions schema resource
                        if ("executions_schema" in selectedResources && executionSchemaId.isNotEmpty()) {
                            queries.add(DataCommand(
                                id = buildQueryId("schema_execution", mapOf("id" to executionSchemaId)),
                                type = "SCHEMA",
                                params = mapOf("id" to executionSchemaId),
                                isRelative = isRelative
                            ))
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

        // Resolve schema IDs from tool instance config (throws if fails)
        val (configSchemaId, dataSchemaId) = resolveSchemaIds(toolInstanceId)

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
        // Resolve schema IDs from tool instance config (throws if fails)
        val (configSchemaId, _) = resolveSchemaIds(toolInstanceId)

        queries.add(DataCommand(
            id = buildQueryId("schema_config", mapOf("id" to configSchemaId)),
            type = "SCHEMA",
            params = mapOf("id" to configSchemaId),
            isRelative = isRelative
        ))

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
        LogManager.aiEnrichment("addTemporalParams() - CALLED with isRelative=$isRelative, configJson=$configJson", "DEBUG")

        val timestampSelection = configJson?.optJSONObject("timestampSelection")
        if (timestampSelection == null) {
            LogManager.aiEnrichment("addTemporalParams() - No timestampSelection found in config, RETURNING", "DEBUG")
            return
        }

        LogManager.aiEnrichment("addTemporalParams() - Found timestampSelection: $timestampSelection", "DEBUG")

        if (isRelative) {
            LogManager.aiEnrichment("addTemporalParams() - RELATIVE mode (AUTOMATION)", "DEBUG")

            // AUTOMATION mode: can use relative periods OR absolute custom dates
            // They are mutually exclusive per side (start/end) but can be mixed
            val minRelativePeriod = timestampSelection.optJSONObject("minRelativePeriod")
            val maxRelativePeriod = timestampSelection.optJSONObject("maxRelativePeriod")
            val minCustomDateTime = timestampSelection.optLong("minCustomDateTime", -1).takeIf { it != -1L }
            val maxCustomDateTime = timestampSelection.optLong("maxCustomDateTime", -1).takeIf { it != -1L }

            LogManager.aiEnrichment("addTemporalParams() - minRelativePeriod=$minRelativePeriod", "DEBUG")
            LogManager.aiEnrichment("addTemporalParams() - maxRelativePeriod=$maxRelativePeriod", "DEBUG")
            LogManager.aiEnrichment("addTemporalParams() - minCustomDateTime=$minCustomDateTime", "DEBUG")
            LogManager.aiEnrichment("addTemporalParams() - maxCustomDateTime=$maxCustomDateTime", "DEBUG")

            // Handle start time
            if (minRelativePeriod != null) {
                // Relative start: encode as "offset_TYPE" for CommandTransformer
                val periodStart = "${minRelativePeriod.getInt("offset")}_${minRelativePeriod.getString("type")}"
                params["period_start"] = periodStart
                LogManager.aiEnrichment("addTemporalParams() - Added relative period_start: $periodStart", "DEBUG")
            } else if (minCustomDateTime != null) {
                // Absolute start: use directly as timestamp
                params["startTime"] = minCustomDateTime
                LogManager.aiEnrichment("addTemporalParams() - Added absolute startTime: $minCustomDateTime", "DEBUG")
            }

            // Handle end time
            if (maxRelativePeriod != null) {
                // Relative end: encode as "offset_TYPE" for CommandTransformer
                val periodEnd = "${maxRelativePeriod.getInt("offset")}_${maxRelativePeriod.getString("type")}"
                params["period_end"] = periodEnd
                LogManager.aiEnrichment("addTemporalParams() - Added relative period_end: $periodEnd", "DEBUG")
            } else if (maxCustomDateTime != null) {
                // Absolute end: use directly as timestamp
                params["endTime"] = maxCustomDateTime
                LogManager.aiEnrichment("addTemporalParams() - Added absolute endTime: $maxCustomDateTime", "DEBUG")
            }

            if (params.isEmpty()) {
                LogManager.aiEnrichment("addTemporalParams() - No temporal parameters found", "WARN")
            }
        } else {
            LogManager.aiEnrichment("addTemporalParams() - ABSOLUTE mode (CHAT)", "DEBUG")
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