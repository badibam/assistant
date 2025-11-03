package com.assistant.core.ai.utils

import android.content.Context
import com.assistant.core.ai.data.SessionCostBreakdown
import com.assistant.core.ai.data.SessionTokens
import com.assistant.core.ai.database.AISessionEntity
import com.assistant.core.ai.database.SessionMessageEntity
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Session cost calculation data
 */
data class SessionCost(
    val modelId: String,
    val totalUncachedInputTokens: Int,      // Sum of uncached input tokens (from API)
    val totalCacheWriteTokens: Int,         // Sum of cache write tokens
    val totalCacheReadTokens: Int,          // Sum of cache read tokens
    val totalOutputTokens: Int,             // Sum of output tokens
    val inputCost: Double?,                 // null if price unavailable
    val cacheWriteCost: Double?,
    val cacheReadCost: Double?,
    val outputCost: Double?,
    val totalCost: Double?,                 // null if price unavailable
    val priceAvailable: Boolean
)

/**
 * Pure logic utility for calculating AI session costs
 *
 * Uses ModelPriceManager for pricing data and calculates costs based on token usage
 * stored in SessionMessageEntity.
 *
 * IMPORTANT: API providers (Claude, OpenAI) return inputTokens as UNCACHED tokens only.
 * The total input from API = inputTokens (uncached) + cacheCreationTokens + cacheReadTokens.
 *
 * Cost calculation formula:
 * - Input cost = inputTokens × inputCostPerToken (inputTokens already uncached from API)
 * - Cache write cost = cacheWriteTokens × cacheWriteCostPerToken
 * - Cache read cost = cacheReadTokens × cacheReadCostPerToken
 * - Output cost = outputTokens × outputCostPerToken
 *
 * Returns null costs if model price not available in LiteLLM database
 */
object SessionCostCalculator {

    /**
     * Calculate total cost for a session using stored tokensJson and costJson
     *
     * **Fast path**: Uses pre-calculated data from AISessionEntity if available (tokensJson + costJson).
     * This avoids reloading messages and recalculating costs for every display.
     *
     * **Fallback**: If tokensJson is null (old sessions before migration), returns null.
     * In this case, caller should use the messages-based overload to calculate from scratch.
     *
     * @param session AISessionEntity with tokensJson and costJson fields
     * @return SessionCost if tokens available, null if needs fallback calculation
     */
    fun calculateSessionCost(session: AISessionEntity): SessionCost? {
        try {
            // Fast path: use stored tokens and costs
            val tokensJson = session.tokensJson
            val costJson = session.costJson

            if (tokensJson == null) {
                LogManager.aiService("SessionCostCalculator.calculateSessionCost(AISessionEntity) - No tokensJson, needs fallback", "DEBUG")
                return null // Caller should use messages-based overload
            }

            // Parse tokens
            val tokens = SessionTokens.fromJson(tokensJson)

            // Parse costs if available
            val costBreakdown = SessionCostBreakdown.fromJson(costJson)

            if (costBreakdown != null) {
                // Full cost data available
                LogManager.aiService(
                    "SessionCostCalculator.calculateSessionCost(AISessionEntity) - Using stored data: " +
                    "tokens=${tokens.totalUncachedInputTokens + tokens.totalCacheWriteTokens + tokens.totalCacheReadTokens + tokens.totalOutputTokens}, " +
                    "cost=\$${String.format("%.3f", costBreakdown.totalCost)}",
                    "DEBUG"
                )

                return SessionCost(
                    modelId = costBreakdown.modelId,
                    totalUncachedInputTokens = tokens.totalUncachedInputTokens,
                    totalCacheWriteTokens = tokens.totalCacheWriteTokens,
                    totalCacheReadTokens = tokens.totalCacheReadTokens,
                    totalOutputTokens = tokens.totalOutputTokens,
                    inputCost = costBreakdown.inputCost,
                    cacheWriteCost = costBreakdown.cacheWriteCost,
                    cacheReadCost = costBreakdown.cacheReadCost,
                    outputCost = costBreakdown.outputCost,
                    totalCost = costBreakdown.totalCost,
                    priceAvailable = true
                )
            } else {
                // Only tokens available, no cost (price was unavailable at time of calculation)
                LogManager.aiService(
                    "SessionCostCalculator.calculateSessionCost(AISessionEntity) - Using stored tokens only (no cost): " +
                    "tokens=${tokens.totalUncachedInputTokens + tokens.totalCacheWriteTokens + tokens.totalCacheReadTokens + tokens.totalOutputTokens}",
                    "DEBUG"
                )

                return SessionCost(
                    modelId = "", // Unknown without costJson
                    totalUncachedInputTokens = tokens.totalUncachedInputTokens,
                    totalCacheWriteTokens = tokens.totalCacheWriteTokens,
                    totalCacheReadTokens = tokens.totalCacheReadTokens,
                    totalOutputTokens = tokens.totalOutputTokens,
                    inputCost = null,
                    cacheWriteCost = null,
                    cacheReadCost = null,
                    outputCost = null,
                    totalCost = null,
                    priceAvailable = false
                )
            }

        } catch (e: Exception) {
            LogManager.aiService("SessionCostCalculator.calculateSessionCost(AISessionEntity) - Error: ${e.message}", "ERROR", e)
            return null
        }
    }
}
