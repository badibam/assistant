package com.assistant.core.ai.prompts

import com.assistant.core.ai.data.DataQuery
import com.assistant.core.utils.LogManager
import java.security.MessageDigest

/**
 * Handles query deduplication across all 4 prompt levels
 *
 * Three phases of deduplication:
 * 1. Remove identical queries (same hash)
 * 2. Remove included queries (business logic inclusion)
 * 3. Merge identical schemas (post-execution by schema_id)
 *
 * Preserves order for cache efficiency - first query in list has priority
 */
object QueryDeduplicator {

    /**
     * Complete deduplication pipeline for pre-execution queries
     * Combines phases 1 and 2 while preserving order
     */
    fun deduplicateQueries(orderedQueries: List<DataQuery>): List<DataQuery> {
        LogManager.aiPrompt("QueryDeduplicator processing ${orderedQueries.size} queries")

        // Phase 1: Remove identical queries (same hash ID)
        val withoutDuplicates = removeIdenticalQueries(orderedQueries)
        LogManager.aiPrompt("After removing identical: ${withoutDuplicates.size} queries")

        // Phase 2: Remove included queries (business logic)
        val withoutInclusions = removeIncludedQueries(withoutDuplicates)
        LogManager.aiPrompt("After removing included: ${withoutInclusions.size} queries")

        LogManager.aiPrompt("Query deduplication completed: ${orderedQueries.size} → ${withoutInclusions.size}")
        return withoutInclusions
    }

    /**
     * Phase 1: Remove queries with identical content (same hash)
     * First occurrence in list is kept (order preservation)
     */
    fun removeIdenticalQueries(queries: List<DataQuery>): List<DataQuery> {
        val seenHashes = mutableSetOf<String>()
        val result = mutableListOf<DataQuery>()

        queries.forEach { query ->
            val hash = generateQueryHash(query)
            if (hash !in seenHashes) {
                seenHashes.add(hash)
                result.add(query)
            } else {
                LogManager.aiPrompt("Removed identical query: ${query.type} (hash: ${hash.take(8)}...)")
            }
        }

        return result
    }

    /**
     * Phase 2: Remove queries that are included by other queries
     * Business logic inclusion - more general queries include specific ones
     */
    fun removeIncludedQueries(queries: List<DataQuery>): List<DataQuery> {
        val result = mutableListOf<DataQuery>()

        queries.forEach { candidate ->
            val isIncluded = result.any { existing ->
                queryIncludes(existing, candidate)
            }

            if (!isIncluded) {
                result.add(candidate)
                LogManager.aiPrompt("Kept query: ${candidate.type}")
            } else {
                LogManager.aiPrompt("Removed included query: ${candidate.type}")
            }
        }

        return result
    }

    /**
     * Generate deterministic hash for query identity
     * Hash includes: type + sorted params + isRelative
     */
    private fun generateQueryHash(query: DataQuery): String {
        // Sort parameters for deterministic hash
        val sortedParams = query.params.toSortedMap().toString()
        val content = "${query.type}|$sortedParams|${query.isRelative}"

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            LogManager.aiPrompt("Hash generation failed, using fallback: ${e.message}", "WARN")
            content.hashCode().toString()
        }
    }

    /**
     * Check if query1 includes/supersedes query2
     * Business logic for inclusion relationships
     */
    private fun queryIncludes(query1: DataQuery, query2: DataQuery): Boolean {
        // TODO: Implement business logic inclusion rules
        // For now, stub implementation with basic type matching

        // Identical queries are included (already handled in phase 1)
        if (query1.type == query2.type && query1.params == query2.params) {
            return true
        }

        // Different types might have inclusion relationships
        // TODO: Implement specific rules:
        // - ZONE_CONFIG includes TOOL_CONFIG (tools from that zone)
        // - TOOL_DATA_FULL includes TOOL_DATA_SAMPLE (same instance)
        // - TOOL_DATA_SAMPLE includes TOOL_DATA_FIELD (same instance, larger scope)
        // - Larger periods include smaller periods
        // - More fields include fewer fields

        LogManager.aiPrompt("TODO: Implement inclusion logic for ${query1.type} vs ${query2.type}")
        return false
    }

    /**
     * Phase 3: Merge query results with identical schema_id
     * Called after query execution when schema information is available
     *
     * @param results List of query execution results with schema information
     * @return Merged results with schema deduplication
     */
    fun mergeIdenticalSchemas(results: List<QueryResult>): List<QueryResult> {
        LogManager.aiPrompt("QueryDeduplicator merging schemas for ${results.size} results")

        val schemaGroups = mutableMapOf<String, MutableList<QueryResult>>()
        val noSchemaResults = mutableListOf<QueryResult>()

        // Group results by schema_id
        results.forEach { result ->
            val schemaId = result.schemaId
            if (schemaId != null) {
                schemaGroups.getOrPut(schemaId) { mutableListOf() }.add(result)
            } else {
                noSchemaResults.add(result)
            }
        }

        // Merge groups with multiple results
        val mergedResults = mutableListOf<QueryResult>()

        schemaGroups.forEach { (schemaId, groupResults) ->
            if (groupResults.size > 1) {
                val merged = mergeSchemaGroup(schemaId, groupResults)
                mergedResults.add(merged)
                LogManager.aiPrompt("Merged ${groupResults.size} results for schema: $schemaId")
            } else {
                mergedResults.add(groupResults.first())
            }
        }

        // Add results without schemas
        mergedResults.addAll(noSchemaResults)

        LogManager.aiPrompt("Schema merging completed: ${results.size} → ${mergedResults.size}")
        return mergedResults
    }

    /**
     * Merge multiple query results that share the same schema_id
     */
    private fun mergeSchemaGroup(schemaId: String, results: List<QueryResult>): QueryResult {
        // TODO: Implement smart merging logic
        // - Combine instance lists
        // - Merge display names
        // - Preserve first result as base

        val baseResult = results.first()
        val instanceNames = results.flatMap { it.instanceNames ?: emptyList() }.distinct()

        return QueryResult(
            content = "## ${baseResult.schemaDisplayName} (instances: ${instanceNames.joinToString(", ")})\n${baseResult.content}",
            schemaId = schemaId,
            schemaDisplayName = baseResult.schemaDisplayName,
            instanceNames = instanceNames
        )
    }
}

/**
 * Result of query execution with schema information for deduplication
 */
data class QueryResult(
    val content: String,
    val schemaId: String? = null,
    val schemaDisplayName: String? = null,
    val instanceNames: List<String>? = null
)