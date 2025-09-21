package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.DataQuery
import com.assistant.core.ai.utils.TokenCalculator
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.schemas.ZoneSchemaProvider
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes DataQueries and validates result sizes
 * Used by both PromptManager (for Level 2/4) and AI dataRequests processing
 *
 * Core responsibilities:
 * - Execute DataQuery configurations to get actual data results
 * - Validate result token sizes against configured limits
 * - Log warnings for oversized results (but don't auto-remove for now)
 * - Return formatted content for prompt inclusion
 */
class QueryExecutor(private val context: Context) {

    private val coordinator = Coordinator(context)

    /**
     * Execute a list of DataQueries and return formatted content
     * Validates token sizes and logs warnings for oversized results
     *
     * @param queries The queries to execute
     * @param level The level name for logging
     * @param previousQueries Queries from previous levels for cross-level deduplication
     */
    suspend fun executeQueries(
        queries: List<DataQuery>,
        level: String = "unknown",
        previousQueries: List<DataQuery> = emptyList()
    ): String {
        LogManager.aiPrompt("QueryExecutor executing ${queries.size} queries for $level (${previousQueries.size} previous queries)")

        if (queries.isEmpty()) {
            LogManager.aiPrompt("No queries to execute, returning empty content")
            return ""
        }

        // Deduplicate current queries against previous queries
        val allQueries = previousQueries + queries
        val deduplicatedQueries = QueryDeduplicator.deduplicateQueries(allQueries)

        // Extract only the queries that weren't in previous levels
        val queriesToExecute = deduplicatedQueries.filter { it in queries }

        LogManager.aiPrompt("After deduplication: ${queriesToExecute.size}/${queries.size} queries to execute")

        val results = mutableListOf<String>()
        val failedQueries = mutableListOf<DataQuery>()
        val oversizedQueries = mutableListOf<Pair<DataQuery, Int>>()

        queriesToExecute.forEach { query ->
            try {
                LogManager.aiPrompt("Executing query: ${query.id} (type: ${query.type})")

                // Execute the query via coordinator
                val queryResult = executeQuery(query)

                if (queryResult != null) {
                    // Validate token size
                    val tokens = TokenCalculator.estimateTokens(queryResult, "default", context)
                    val limit = TokenCalculator.getTokenLimit(context, isQuery = true, isTotal = false)

                    if (tokens > limit) {
                        LogManager.aiPrompt("Query ${query.id} result exceeds token limit: $tokens > $limit tokens", "WARN")
                        oversizedQueries.add(query to tokens)

                        // TODO: Implement proper oversized query handling based on context
                        // For now, LOG WARNING but include the result anyway
                        LogManager.aiPrompt("Including oversized query result anyway (TODO: implement proper handling)")
                        results.add("# Query: ${query.id} (⚠️ ${tokens} tokens, limit: ${limit})\n$queryResult")
                    } else {
                        LogManager.aiPrompt("Query ${query.id} executed successfully: $tokens tokens")
                        results.add("# Query: ${query.id}\n$queryResult")
                    }
                } else {
                    LogManager.aiPrompt("Query ${query.id} returned null result", "WARN")
                    failedQueries.add(query)
                }

            } catch (e: Exception) {
                LogManager.aiPrompt("Failed to execute query ${query.id}: ${e.message}", "ERROR", e)
                failedQueries.add(query)
            }
        }

        // Summary logging
        if (failedQueries.isNotEmpty()) {
            LogManager.aiPrompt("${failedQueries.size} queries failed execution", "WARN")
            // TODO: Implement failed query cleanup strategy
        }

        if (oversizedQueries.isNotEmpty()) {
            LogManager.aiPrompt("${oversizedQueries.size} queries exceeded token limits", "WARN")
            oversizedQueries.forEach { (query, tokens) ->
                LogManager.aiPrompt("Oversized: ${query.id} = $tokens tokens", "WARN")
            }
            // TODO: Implement oversized query handling strategy
            // - CHAT: Dialog confirmation with user
            // - AUTOMATION: Auto-refuse and remove
            // - Different strategies for Level 2 vs Level 4
        }

        val finalContent = results.joinToString("\n\n")
        val totalTokens = TokenCalculator.estimateTokens(finalContent, "default", context)

        LogManager.aiPrompt("Query execution completed: ${results.size}/${queries.size} successful, total: $totalTokens tokens")

        return finalContent
    }

    /**
     * Execute a single DataQuery and return raw result
     */
    private suspend fun executeQuery(query: DataQuery): String? {
        LogManager.aiPrompt("Executing DataQuery: id=${query.id}, type=${query.type}, isRelative=${query.isRelative}")

        return withContext(Dispatchers.IO) {
            try {
                // Resolve relative parameters if needed
                val resolvedParams = if (query.isRelative) {
                    resolveRelativeParams(query.params)
                } else {
                    query.params
                }

                LogManager.aiPrompt("Resolved params for ${query.id}: $resolvedParams")

                // Execute query based on type
                when (query.type) {
                    // === SYSTEM LEVEL (Level 1) ===
                    "SYSTEM_SCHEMAS" -> executeSystemSchemasQuery(resolvedParams)
                    "SYSTEM_DOC" -> executeSystemDocQuery(resolvedParams)
                    "APP_CONFIG" -> executeAppConfigQuery(resolvedParams)

                    // === USER CONTEXT LEVEL (Level 2) ===
                    "USER_TOOLS_CONTEXT" -> executeUserToolsContextQuery(resolvedParams)

                    // === APP STATE LEVEL (Level 3) ===
                    "APP_STATE" -> executeAppStateQuery(resolvedParams)

                    // === ZONE LEVEL (Level 4) ===
                    "ZONE_CONFIG" -> executeZoneConfigQuery(resolvedParams)
                    "ZONE_STATS" -> executeZoneStatsQuery(resolvedParams)

                    // === TOOL INSTANCE LEVEL (Level 4) ===
                    "TOOL_CONFIG" -> executeToolConfigQuery(resolvedParams)
                    "TOOL_DATA_FULL" -> executeToolDataFullQuery(resolvedParams)
                    "TOOL_DATA_SAMPLE" -> executeToolDataSampleQuery(resolvedParams)
                    "TOOL_DATA_FIELD" -> executeToolDataFieldQuery(resolvedParams)
                    "TOOL_STATS" -> executeToolStatsQuery(resolvedParams)

                    else -> {
                        LogManager.aiPrompt("Unknown query type: ${query.type}", "WARN")
                        null
                    }
                }

            } catch (e: Exception) {
                LogManager.aiPrompt("Query execution failed: ${e.message}", "ERROR", e)
                null
            }
        }
    }

    // ===============================
    // === SYSTEM LEVEL (Level 1) ===
    // ===============================

    /**
     * Execute SYSTEM_SCHEMAS query - get base schemas for all tool types
     */
    private suspend fun executeSystemSchemasQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing SYSTEM_SCHEMAS query with params: $params")

        // TODO: Query all tool types via ToolTypeManager and get base schemas
        // Include schema_id for deduplication
        return """
            # System Schemas (Level 1)
            Base schemas for all available tool types.

            TODO: Implement via ToolTypeManager.getAllToolTypes() + getConfigSchema()
        """.trimIndent()
    }

    /**
     * Execute SYSTEM_DOC query - get fixed system documentation
     */
    private suspend fun executeSystemDocQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing SYSTEM_DOC query with params: $params")

        // TODO: Load system documentation from resources or database
        return """
            # System Documentation (Level 1)
            Fixed system documentation and AI role definition.

            TODO: Load from resources or dedicated storage
        """.trimIndent()
    }

    /**
     * Execute APP_CONFIG query - get global app configuration
     */
    private suspend fun executeAppConfigQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing APP_CONFIG query with params: $params")

        return try {
            // Get format configuration (contains temporal and display settings)
            val configResult = coordinator.processUserAction("app_config.get", mapOf("category" to "format"))

            if (configResult.isSuccess) {
                val configData = configResult.data
                LogManager.aiPrompt("APP_CONFIG query successful, data keys: ${configData?.keys}")

                // Format configuration for AI context
                val formattedConfig = StringBuilder()
                formattedConfig.appendLine("## Configuration Application")
                formattedConfig.appendLine("Paramètres globaux de l'application.")
                formattedConfig.appendLine()

                configData?.forEach { (key, value) ->
                    formattedConfig.appendLine("- **$key**: $value")
                }

                formattedConfig.toString().trim()
            } else {
                LogManager.aiPrompt("APP_CONFIG query failed: ${configResult.error}", "WARN")
                """
                    ## Configuration Application
                    Erreur lors du chargement : ${configResult.error}
                """.trimIndent()
            }

        } catch (e: Exception) {
            LogManager.aiPrompt("APP_CONFIG query exception: ${e.message}", "ERROR", e)
            """
                ## Configuration Application
                Exception lors du chargement : ${e.message}
            """.trimIndent()
        }
    }

    // ===================================
    // === USER CONTEXT LEVEL (Level 2) ===
    // ===================================

    /**
     * Execute USER_TOOLS_CONTEXT query - get tools with include_in_ai_context=true
     */
    private suspend fun executeUserToolsContextQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing USER_TOOLS_CONTEXT query with params: $params")

        return try {
            val result = StringBuilder()
            result.appendLine("## Données utilisateur à prendre en compte dans toutes les réponses")
            result.appendLine()

            // Get all zones
            val zonesResult = coordinator.processUserAction("zones.list", emptyMap())
            if (!zonesResult.isSuccess) {
                LogManager.aiPrompt("Failed to get zones: ${zonesResult.error}", "WARN")
                return "## Données utilisateur à prendre en compte dans toutes les réponses\nErreur lors du chargement des zones: ${zonesResult.error}"
            }

            val zones = zonesResult.data?.get("zones") as? List<Map<String, Any>> ?: emptyList()
            LogManager.aiPrompt("Found ${zones.size} zones")

            var totalToolsFound = 0
            var totalToolsIncluded = 0

            // For each zone, get tools and filter by include_in_ai_context=true
            zones.forEach { zone ->
                val zoneId = zone["id"] as? String ?: return@forEach
                val zoneName = zone["name"] as? String ?: "Zone sans nom"

                LogManager.aiPrompt("Processing zone: $zoneName ($zoneId)")

                // Get tools for this zone
                val toolsResult = coordinator.processUserAction("tools.list", mapOf("zone_id" to zoneId))
                if (!toolsResult.isSuccess) {
                    LogManager.aiPrompt("Failed to get tools for zone $zoneId: ${toolsResult.error}", "WARN")
                    return@forEach
                }

                val tools = toolsResult.data?.get("tool_instances") as? List<Map<String, Any>> ?: emptyList()
                totalToolsFound += tools.size
                LogManager.aiPrompt("Found ${tools.size} tools in zone $zoneName")

                // Filter tools with include_in_ai_context=true and get their data
                tools.forEach { tool ->
                    val toolInstanceId = tool["id"] as? String ?: return@forEach
                    val toolType = tool["tooltype"] as? String ?: return@forEach
                    val toolName = tool["name"] as? String ?: "Outil sans nom"
                    val configJson = tool["config"] as? String ?: return@forEach

                    // Parse config to check include_in_ai_context
                    val config = try {
                        if (configJson.isNotEmpty()) {
                            org.json.JSONObject(configJson).let { json ->
                                json.keys().asSequence().associateWith { json.get(it) }
                            }
                        } else emptyMap()
                    } catch (e: Exception) {
                        LogManager.aiPrompt("Failed to parse config for tool $toolInstanceId: ${e.message}", "WARN")
                        emptyMap()
                    }

                    val includeInAiContext = config["include_in_ai_context"] as? Boolean ?: false
                    LogManager.aiPrompt("Tool $toolName ($toolType): include_in_ai_context = $includeInAiContext")

                    if (includeInAiContext) {
                        totalToolsIncluded++
                        result.appendLine("### Données de [$toolType](tooltype) : [\"$toolName\"](titre de l'instance)")
                        result.appendLine()

                        // Get all data for this tool instance, ordered by timestamp
                        val dataResult = coordinator.processUserAction("tool_data.get", mapOf(
                            "toolInstanceId" to toolInstanceId,
                            "orderBy" to "timestamp",
                            "orderDirection" to "ASC"
                        ))

                        if (dataResult.isSuccess) {
                            val dataEntries = dataResult.data?.get("entries") as? List<Map<String, Any>> ?: emptyList()
                            LogManager.aiPrompt("Found ${dataEntries.size} data entries for tool $toolName")

                            dataEntries.forEach { entry ->
                                val entryName = entry["name"] as? String ?: "Entrée sans nom"
                                val timestamp = entry["timestamp"] as? Long ?: 0L
                                val dataField = entry["data"] as? String ?: "{}"

                                // Format timestamp to readable date
                                val date = if (timestamp > 0) {
                                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                        .format(java.util.Date(timestamp))
                                } else "Date inconnue"

                                result.appendLine("#### $entryName ($date)")
                                result.appendLine(dataField)
                                result.appendLine()
                            }
                        } else {
                            LogManager.aiPrompt("Failed to get data for tool $toolInstanceId: ${dataResult.error}", "WARN")
                            result.appendLine("Erreur lors du chargement des données: ${dataResult.error}")
                            result.appendLine()
                        }
                    }
                }
            }

            LogManager.aiPrompt("USER_TOOLS_CONTEXT completed: $totalToolsIncluded/$totalToolsFound tools included")

            if (totalToolsIncluded == 0) {
                result.appendLine("Aucun outil configuré pour inclusion dans l'IA.")
            }

            result.toString().trim()

        } catch (e: Exception) {
            LogManager.aiPrompt("USER_TOOLS_CONTEXT query exception: ${e.message}", "ERROR", e)
            """
                ## Données utilisateur à prendre en compte dans toutes les réponses
                Exception lors du chargement : ${e.message}
            """.trimIndent()
        }
    }

    // ===============================
    // === APP STATE LEVEL (Level 3) ===
    // ===============================

    /**
     * Execute APP_STATE query - get zones + tool instances metadata + permissions
     */
    private suspend fun executeAppStateQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing APP_STATE query with params: $params")

        // TODO: Query zones, tool instances metadata, AI permissions via coordinator
        return """
            # App State (Level 3)
            Current zones, tool instances metadata, and AI permissions.

            TODO: Query zones.list + tools.list + extract metadata from configs
        """.trimIndent()
    }

    // ===============================
    // === ZONE LEVEL (Level 4) ===
    // ===============================

    /**
     * Execute ZONE_CONFIG query - get zone config + instances with full configs
     */
    private suspend fun executeZoneConfigQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing ZONE_CONFIG query with params: $params")

        val zoneId = params["zone_id"] as? String
        if (zoneId == null) {
            LogManager.aiPrompt("ZONE_CONFIG query missing zone_id parameter", "WARN")
            return "## Configuration Zone\nErreur: zone_id manquant"
        }

        return try {
            val result = StringBuilder()

            // Get zone configuration
            val zoneResult = coordinator.processUserAction("zones.get", mapOf("id" to zoneId))
            if (!zoneResult.isSuccess) {
                LogManager.aiPrompt("Failed to get zone $zoneId: ${zoneResult.error}", "WARN")
                return "## Configuration Zone\nErreur lors du chargement de la zone: ${zoneResult.error}"
            }

            val zoneData = zoneResult.data?.get("zone") as? Map<String, Any>
            val zoneName = zoneData?.get("name") as? String ?: "Zone sans nom"
            val zoneConfig = zoneData?.get("config") as? String ?: "{}"

            result.appendLine("## Configuration Zone : $zoneName")
            result.appendLine()

            // Add zone schema
            val zoneSchemaProvider = ZoneSchemaProvider.create(context)
            val zoneSchema = zoneSchemaProvider.getSchema("config", context)
            if (zoneSchema == null) {
                LogManager.aiPrompt("Failed to get zone config schema", "ERROR")
                return "## Configuration Zone : $zoneName\nErreur: impossible de récupérer le schéma de configuration"
            }

            result.appendLine("### Schéma de configuration")
            result.appendLine("```json")
            result.appendLine(zoneSchema as CharSequence)
            result.appendLine("```")
            result.appendLine()

            // Add current zone configuration
            result.appendLine("### Configuration actuelle")
            result.appendLine("```json")
            result.appendLine(zoneConfig)
            result.appendLine("```")
            result.appendLine()

            // Get tools in this zone
            val toolsResult = coordinator.processUserAction("tools.list", mapOf("zone_id" to zoneId))
            if (!toolsResult.isSuccess) {
                LogManager.aiPrompt("Failed to get tools for zone $zoneId: ${toolsResult.error}", "WARN")
                result.appendLine("Erreur lors du chargement des outils: ${toolsResult.error}")
                return result.toString().trim()
            }

            val tools = toolsResult.data?.get("tool_instances") as? List<Map<String, Any>> ?: emptyList()
            LogManager.aiPrompt("Found ${tools.size} tools in zone $zoneName")

            result.appendLine("### Outils configurés")
            result.appendLine()

            if (tools.isEmpty()) {
                result.appendLine("Aucun outil configuré dans cette zone.")
            } else {
                tools.forEach { tool ->
                    val toolType = tool["tooltype"] as? String ?: "unknown"
                    val toolName = tool["name"] as? String ?: "Outil sans nom"
                    val toolConfig = tool["config"] as? String ?: "{}"

                    result.appendLine("#### [$toolType] $toolName")
                    result.appendLine("```json")
                    result.appendLine(toolConfig)
                    result.appendLine("```")
                    result.appendLine()
                }
            }

            LogManager.aiPrompt("ZONE_CONFIG completed for zone $zoneName with ${tools.size} tools")
            result.toString().trim()

        } catch (e: Exception) {
            LogManager.aiPrompt("ZONE_CONFIG query exception: ${e.message}", "ERROR", e)
            """
                ## Configuration Zone
                Exception lors du chargement : ${e.message}
            """.trimIndent()
        }
    }

    /**
     * Execute ZONE_STATS query - get zone statistics
     */
    private suspend fun executeZoneStatsQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing ZONE_STATS query with params: $params")

        // TODO: Aggregate statistics across all tools in zone
        return """
            # Zone Statistics (Level 4)
            Aggregated statistics for all tools in zone.

            TODO: tool_data.stats for all instances in zone + aggregate
        """.trimIndent()
    }

    // ===================================
    // === TOOL INSTANCE LEVEL (Level 4) ===
    // ===================================

    /**
     * Execute TOOL_CONFIG query - get instance config + resolved data schema
     */
    private suspend fun executeToolConfigQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing TOOL_CONFIG query with params: $params")

        // TODO: Get tool instance config + resolve schema based on config
        // Include schema_id for deduplication
        return """
            # Tool Configuration (Level 4)
            Tool instance config and resolved data schema.

            TODO: tools.get + resolve schema + include schema_id
        """.trimIndent()
    }

    /**
     * Execute TOOL_DATA_FULL query - get all data for instance
     */
    private suspend fun executeToolDataFullQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing TOOL_DATA_FULL query with params: $params")

        // TODO: Get all data with period filtering if specified
        return """
            # Tool Data Full (Level 4)
            All data entries for tool instance.

            TODO: tool_data.get with period filtering
        """.trimIndent()
    }

    /**
     * Execute TOOL_DATA_SAMPLE query - get data sample with AI_LIMITS
     */
    private suspend fun executeToolDataSampleQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing TOOL_DATA_SAMPLE query with params: $params")

        // TODO: Get sample according to AI_LIMITS + period + importance
        // Mark clearly as sample with total count
        return """
            # Tool Data Sample (Level 4)
            Sample of data entries for tool instance.

            TODO: tool_data.get with sampling + period + AI_LIMITS + sample marking
        """.trimIndent()
    }

    /**
     * Execute TOOL_DATA_FIELD query - get specific field data
     */
    private suspend fun executeToolDataFieldQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing TOOL_DATA_FIELD query with params: $params")

        // TODO: Extract specific field according to mode (sample_entries vs distinct_values)
        // Respect period filtering and AI_LIMITS
        return """
            # Tool Data Field (Level 4)
            Specific field data from tool instance.

            TODO: tool_data.get + field extraction + mode handling + AI_LIMITS
        """.trimIndent()
    }

    /**
     * Execute TOOL_STATS query - get tool instance metadata and statistics
     */
    private suspend fun executeToolStatsQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing TOOL_STATS query with params: $params")

        // TODO: Get count, period coverage, last entry, etc.
        return """
            # Tool Statistics (Level 4)
            Metadata and statistics for tool instance.

            TODO: tool_data.stats + extract metadata
        """.trimIndent()
    }

    /**
     * Resolve relative parameters to absolute timestamps at execution time
     * Used for automation queries that adapt to current date/time
     */
    private fun resolveRelativeParams(params: Map<String, Any>): Map<String, Any> {
        LogManager.aiPrompt("Resolving relative parameters: $params")

        val resolvedParams = params.toMutableMap()

        // TODO: Implement full relative parameter resolution
        // For now, return params as-is with logging
        params.forEach { (key, value) ->
            when (value) {
                "current_week" -> {
                    LogManager.aiPrompt("TODO: Resolve $key = $value to actual timestamps")
                    // resolvedParams[key] = getCurrentWeekTimestamps()
                }
                "current_day" -> {
                    LogManager.aiPrompt("TODO: Resolve $key = $value to actual timestamps")
                    // resolvedParams[key] = getCurrentDayTimestamps()
                }
                "current_selection" -> {
                    LogManager.aiPrompt("TODO: Resolve $key = $value to actual timestamps")
                    // Keep as-is for now
                }
            }
        }

        LogManager.aiPrompt("Resolved params: $resolvedParams")
        return resolvedParams
    }

}