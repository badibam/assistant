package com.assistant.core.ai.prompts

import com.assistant.core.ai.data.DataQuery
import com.assistant.core.utils.LogManager
import java.security.MessageDigest

/**
 * Handles query deduplication across all 4 prompt levels
 *
 * Two phases of deduplication:
 * 1. Remove identical queries (same hash)
 * 2. Remove included queries (business logic inclusion)
 *
 * Preserves order for cache efficiency - first query in list has priority
 */
object QueryDeduplicator {

    /**
     * Complete deduplication pipeline for pre-execution queries
     * Combines identical removal and business logic inclusion while preserving order
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
     * Check if query1 includes/supersedes query2 based on business logic
     * Implements smart inclusion rules to avoid redundant queries
     */
    private fun queryIncludes(query1: DataQuery, query2: DataQuery): Boolean {
        // Identical queries are included (already handled in phase 1)
        if (query1.type == query2.type && query1.params == query2.params) {
            return true
        }

        // Business logic inclusion rules for different query types
        return when {
            // Zone config queries include tool config queries for same zone
            query1.type == "ZONE_CONFIG" && query2.type == "TOOL_CONFIG" -> {
                val zoneId1 = query1.params["zoneId"] as? String
                val toolZoneId = getZoneIdForTool(query2.params["toolInstanceId"] as? String)
                zoneId1 == toolZoneId
            }

            // Full tool data includes sample data for same instance
            query1.type == "TOOL_DATA_FULL" && query2.type == "TOOL_DATA_SAMPLE" -> {
                query1.params["toolInstanceId"] == query2.params["toolInstanceId"]
            }

            // Sample data includes field data for same instance and broader scope
            query1.type == "TOOL_DATA_SAMPLE" && query2.type == "TOOL_DATA_FIELD" -> {
                query1.params["toolInstanceId"] == query2.params["toolInstanceId"]
            }

            // Larger time periods include smaller periods for same tool instance
            sameToolInstanceWithLargerPeriod(query1, query2) -> true

            else -> {
                LogManager.aiPrompt("No inclusion rule found for ${query1.type} vs ${query2.type}")
                false
            }
        }
    }

    /**
     * Helper to determine if query1 covers a larger time period than query2 for same tool
     */
    private fun sameToolInstanceWithLargerPeriod(query1: DataQuery, query2: DataQuery): Boolean {
        if (query1.params["toolInstanceId"] != query2.params["toolInstanceId"]) return false

        val start1 = query1.params["startTimestamp"] as? Long ?: return false
        val end1 = query1.params["endTimestamp"] as? Long ?: return false
        val start2 = query2.params["startTimestamp"] as? Long ?: return false
        val end2 = query2.params["endTimestamp"] as? Long ?: return false

        return start1 <= start2 && end1 >= end2
    }

    /**
     * Helper to get zone ID for a tool instance
     * Currently returns null to disable zone-based inclusion optimization
     */
    private fun getZoneIdForTool(@Suppress("UNUSED_PARAMETER") toolInstanceId: String?): String? {
        // This would need to be implemented to query the tool instance's zone
        // For now, return null to disable this optimization
        return null
    }
}