package com.assistant.core.ai.data

import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Token breakdown for an AI session
 * Stored in AISessionEntity.tokensJson
 * Updated incrementally as messages are added
 */
@Serializable
data class SessionTokens(
    val totalUncachedInputTokens: Int = 0,
    val totalCacheWriteTokens: Int = 0,
    val totalCacheReadTokens: Int = 0,
    val totalOutputTokens: Int = 0
) {
    /**
     * Serialize to JSON string for database storage
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("total_uncached_input_tokens", totalUncachedInputTokens)
            put("total_cache_write_tokens", totalCacheWriteTokens)
            put("total_cache_read_tokens", totalCacheReadTokens)
            put("total_output_tokens", totalOutputTokens)
        }.toString()
    }

    /**
     * Add tokens from a message to this breakdown
     */
    fun addMessage(
        inputTokens: Int,
        cacheWriteTokens: Int,
        cacheReadTokens: Int,
        outputTokens: Int
    ): SessionTokens {
        return SessionTokens(
            totalUncachedInputTokens = this.totalUncachedInputTokens + inputTokens,
            totalCacheWriteTokens = this.totalCacheWriteTokens + cacheWriteTokens,
            totalCacheReadTokens = this.totalCacheReadTokens + cacheReadTokens,
            totalOutputTokens = this.totalOutputTokens + outputTokens
        )
    }

    companion object {
        /**
         * Parse from JSON string stored in database
         * Returns empty SessionTokens if parsing fails
         */
        fun fromJson(json: String?): SessionTokens {
            if (json.isNullOrEmpty()) return SessionTokens()

            return try {
                val obj = JSONObject(json)
                SessionTokens(
                    totalUncachedInputTokens = obj.optInt("total_uncached_input_tokens", 0),
                    totalCacheWriteTokens = obj.optInt("total_cache_write_tokens", 0),
                    totalCacheReadTokens = obj.optInt("total_cache_read_tokens", 0),
                    totalOutputTokens = obj.optInt("total_output_tokens", 0)
                )
            } catch (e: Exception) {
                SessionTokens()
            }
        }
    }
}

/**
 * Cost breakdown for an AI session
 * Stored in AISessionEntity.costJson
 * Only available if model prices are known
 */
@Serializable
data class SessionCostBreakdown(
    val modelId: String,
    val inputCost: Double,
    val cacheWriteCost: Double,
    val cacheReadCost: Double,
    val outputCost: Double,
    val totalCost: Double
) {
    /**
     * Serialize to JSON string for database storage
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("model_id", modelId)
            put("input_cost", inputCost)
            put("cache_write_cost", cacheWriteCost)
            put("cache_read_cost", cacheReadCost)
            put("output_cost", outputCost)
            put("total_cost", totalCost)
        }.toString()
    }

    companion object {
        /**
         * Parse from JSON string stored in database
         * Returns null if parsing fails or JSON is empty
         */
        fun fromJson(json: String?): SessionCostBreakdown? {
            if (json.isNullOrEmpty()) return null

            return try {
                val obj = JSONObject(json)
                SessionCostBreakdown(
                    modelId = obj.optString("model_id", ""),
                    inputCost = obj.optDouble("input_cost", 0.0),
                    cacheWriteCost = obj.optDouble("cache_write_cost", 0.0),
                    cacheReadCost = obj.optDouble("cache_read_cost", 0.0),
                    outputCost = obj.optDouble("output_cost", 0.0),
                    totalCost = obj.optDouble("total_cost", 0.0)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
