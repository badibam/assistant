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
            put("totalUncachedInputTokens", totalUncachedInputTokens)
            put("totalCacheWriteTokens", totalCacheWriteTokens)
            put("totalCacheReadTokens", totalCacheReadTokens)
            put("totalOutputTokens", totalOutputTokens)
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
                    totalUncachedInputTokens = obj.optInt("totalUncachedInputTokens", 0),
                    totalCacheWriteTokens = obj.optInt("totalCacheWriteTokens", 0),
                    totalCacheReadTokens = obj.optInt("totalCacheReadTokens", 0),
                    totalOutputTokens = obj.optInt("totalOutputTokens", 0)
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
            put("modelId", modelId)
            put("inputCost", inputCost)
            put("cacheWriteCost", cacheWriteCost)
            put("cacheReadCost", cacheReadCost)
            put("outputCost", outputCost)
            put("totalCost", totalCost)
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
                    modelId = obj.optString("modelId", ""),
                    inputCost = obj.optDouble("inputCost", 0.0),
                    cacheWriteCost = obj.optDouble("cacheWriteCost", 0.0),
                    cacheReadCost = obj.optDouble("cacheReadCost", 0.0),
                    outputCost = obj.optDouble("outputCost", 0.0),
                    totalCost = obj.optDouble("totalCost", 0.0)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
