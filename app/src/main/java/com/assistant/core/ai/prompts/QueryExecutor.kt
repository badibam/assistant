package com.assistant.core.ai.prompts

import android.content.Context
import com.assistant.core.ai.data.DataQuery
import com.assistant.core.ai.utils.TokenCalculator
import com.assistant.core.coordinator.Coordinator
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

        // TODO: Call coordinator with app_config.get
        return """
            # App Configuration (Level 1)
            Global app settings for AI context.

            TODO: Call coordinator.processUserAction("app_config.get", {})
        """.trimIndent()
    }

    // ===================================
    // === USER CONTEXT LEVEL (Level 2) ===
    // ===================================

    /**
     * Execute USER_TOOLS_CONTEXT query - get tools with include_in_ai_context=true
     */
    private suspend fun executeUserToolsContextQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing USER_TOOLS_CONTEXT query with params: $params")

        // TODO: Query tools with include_in_ai_context=true via coordinator
        // Generate samples according to importance and AI_LIMITS
        return """
            # User Tools Context (Level 2)
            Tools marked for AI context inclusion.

            TODO: Query via coordinator + filter by include_in_ai_context + generate samples
        """.trimIndent()
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

        // TODO: Query zone config + all tool instances with full configs
        // Include resolved schemas for each instance
        return """
            # Zone Configuration (Level 4)
            Zone config and all tool instances with complete configurations.

            TODO: zones.get + tools.list + full configs + resolved schemas
        """.trimIndent()
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