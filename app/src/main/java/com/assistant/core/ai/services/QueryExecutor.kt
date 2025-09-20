package com.assistant.core.ai.services

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
     */
    suspend fun executeQueries(queries: List<DataQuery>, level: String = "unknown"): String {
        LogManager.aiPrompt("QueryExecutor executing ${queries.size} queries for $level")

        if (queries.isEmpty()) {
            LogManager.aiPrompt("No queries to execute, returning empty content")
            return ""
        }

        val results = mutableListOf<String>()
        val failedQueries = mutableListOf<DataQuery>()
        val oversizedQueries = mutableListOf<Pair<DataQuery, Int>>()

        queries.forEach { query ->
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
                    "TOOL_DATA" -> executeToolDataQuery(resolvedParams)
                    "TOOL_CONFIG" -> executeToolConfigQuery(resolvedParams)
                    "ZONE_DATA" -> executeZoneDataQuery(resolvedParams)
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

    /**
     * Execute TOOL_DATA query via coordinator
     */
    private suspend fun executeToolDataQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing TOOL_DATA query with params: $params")

        // TODO: Convert params to proper format and call coordinator
        // For now, return mock data
        return """
            # Tool Data Results
            Sample tool data results would appear here.
            Params used: $params

            This is mock data - TODO: implement real query execution via coordinator.
        """.trimIndent()
    }

    /**
     * Execute TOOL_CONFIG query via coordinator
     */
    private suspend fun executeToolConfigQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing TOOL_CONFIG query with params: $params")

        // TODO: Convert params to proper format and call coordinator
        // For now, return mock data
        return """
            # Tool Configuration Results
            Sample tool configuration results would appear here.
            Params used: $params

            This is mock data - TODO: implement real query execution via coordinator.
        """.trimIndent()
    }

    /**
     * Execute ZONE_DATA query via coordinator
     */
    private suspend fun executeZoneDataQuery(params: Map<String, Any>): String? {
        LogManager.aiPrompt("Executing ZONE_DATA query with params: $params")

        // TODO: Convert params to proper format and call coordinator
        // For now, return mock data
        return """
            # Zone Data Results
            Sample zone data results would appear here.
            Params used: $params

            This is mock data - TODO: implement real query execution via coordinator.
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

    /**
     * Estimate total token size for a list of queries (before execution)
     * Used for preview/validation purposes
     */
    suspend fun estimateQueryListSize(queries: List<DataQuery>): Int {
        LogManager.aiPrompt("Estimating token size for ${queries.size} queries")

        // Very rough estimation based on query complexity
        // TODO: More sophisticated estimation based on query types and params
        val estimatedTokens = queries.sumOf { query ->
            when (query.type) {
                "TOOL_DATA" -> 500 // Average tool data query result
                "TOOL_CONFIG" -> 200 // Tool config typically smaller
                "ZONE_DATA" -> 1000 // Zone data can be larger
                else -> 300 // Default estimation
            }
        }

        LogManager.aiPrompt("Estimated total tokens for query list: $estimatedTokens")
        return estimatedTokens
    }
}