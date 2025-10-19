package com.assistant.core.ai.utils

import android.content.Context
import com.assistant.core.ai.database.SessionMessageEntity
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager

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
 * Message cost calculation data (same structure as SessionCost but for a single message)
 */
data class MessageCost(
    val messageId: String,
    val modelId: String,
    val inputTokens: Int,                   // Uncached input tokens (from API)
    val cacheWriteTokens: Int,              // Cache write tokens
    val cacheReadTokens: Int,               // Cache read tokens
    val outputTokens: Int,                  // Output tokens
    val inputCost: Double?,
    val cacheWriteCost: Double?,
    val cacheReadCost: Double?,
    val outputCost: Double?,
    val totalCost: Double?,
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
     * Calculate total cost for a session
     *
     * @param messages List of session messages (AI messages have token data, USER/SYSTEM have zeros)
     * @param providerId Provider identifier (e.g., "claude", "openai")
     * @param modelId Provider-specific model ID
     * @return SessionCost with totals and costs, or null if critical error
     */
    fun calculateSessionCost(
        messages: List<SessionMessageEntity>,
        providerId: String,
        modelId: String
    ): SessionCost? {
        try {
            LogManager.aiService("SessionCostCalculator.calculateSessionCost() - Calculating for provider=$providerId, model=$modelId, messages=${messages.size}")

            // Sum token usage across all AI messages
            // Note: inputTokens from API are already uncached (API total = uncached + cache_write + cache_read)
            val totalUncachedInputTokens = messages.sumOf { it.inputTokens }
            val totalCacheWriteTokens = messages.sumOf { it.cacheWriteTokens }
            val totalCacheReadTokens = messages.sumOf { it.cacheReadTokens }
            val totalOutputTokens = messages.sumOf { it.outputTokens }

            LogManager.aiService("SessionCostCalculator - Token totals: uncachedInput=$totalUncachedInputTokens, cacheWrite=$totalCacheWriteTokens, cacheRead=$totalCacheReadTokens, output=$totalOutputTokens", "DEBUG")

            // Get model pricing
            val modelPrice = ModelPriceManager.getModelPrice(providerId, modelId)

            LogManager.aiService("SessionCostCalculator - ModelPrice: inputCost=${modelPrice?.inputCostPerToken}, cacheWriteCost=${modelPrice?.cacheWriteCostPerToken}, cacheReadCost=${modelPrice?.cacheReadCostPerToken}, outputCost=${modelPrice?.outputCostPerToken}", "DEBUG")

            if (modelPrice == null) {
                LogManager.aiService("SessionCostCalculator - Model price not available for $providerId/$modelId", "WARN")
                return SessionCost(
                    modelId = modelId,
                    totalUncachedInputTokens = totalUncachedInputTokens,
                    totalCacheWriteTokens = totalCacheWriteTokens,
                    totalCacheReadTokens = totalCacheReadTokens,
                    totalOutputTokens = totalOutputTokens,
                    inputCost = null,
                    cacheWriteCost = null,
                    cacheReadCost = null,
                    outputCost = null,
                    totalCost = null,
                    priceAvailable = false
                )
            }

            // Calculate costs (inputTokens already uncached from API)
            val inputCost = calculateCost(totalUncachedInputTokens, modelPrice.inputCostPerToken)
            val cacheWriteCost = if (modelPrice.cacheWriteCostPerToken != null) {
                calculateCost(totalCacheWriteTokens, modelPrice.cacheWriteCostPerToken)
            } else {
                // Fallback: if provider doesn't have separate cache write price, use input price
                calculateCost(totalCacheWriteTokens, modelPrice.inputCostPerToken)
            }
            val cacheReadCost = if (modelPrice.cacheReadCostPerToken != null) {
                calculateCost(totalCacheReadTokens, modelPrice.cacheReadCostPerToken)
            } else {
                // Cache read usually free or very cheap, default to 0 if not specified
                0.0
            }
            val outputCost = calculateCost(totalOutputTokens, modelPrice.outputCostPerToken)

            val totalCost = inputCost + cacheWriteCost + cacheReadCost + outputCost

            LogManager.aiService("SessionCostCalculator - Total cost: \$${String.format("%.3f", totalCost)} (input=\$${String.format("%.3f", inputCost)}, cacheWrite=\$${String.format("%.3f", cacheWriteCost)}, cacheRead=\$${String.format("%.3f", cacheReadCost)}, output=\$${String.format("%.3f", outputCost)})")

            return SessionCost(
                modelId = modelId,
                totalUncachedInputTokens = totalUncachedInputTokens,
                totalCacheWriteTokens = totalCacheWriteTokens,
                totalCacheReadTokens = totalCacheReadTokens,
                totalOutputTokens = totalOutputTokens,
                inputCost = inputCost,
                cacheWriteCost = cacheWriteCost,
                cacheReadCost = cacheReadCost,
                outputCost = outputCost,
                totalCost = totalCost,
                priceAvailable = true
            )

        } catch (e: Exception) {
            LogManager.aiService("SessionCostCalculator - Error calculating session cost: ${e.message}", "ERROR", e)
            return null
        }
    }

    /**
     * Calculate cost for a single message
     *
     * @param message Session message entity (typically an AI message with token data)
     * @param providerId Provider identifier
     * @param modelId Provider-specific model ID
     * @return MessageCost or null if critical error
     */
    fun calculateMessageCost(
        message: SessionMessageEntity,
        providerId: String,
        modelId: String
    ): MessageCost? {
        try {
            LogManager.aiService("SessionCostCalculator.calculateMessageCost() - Calculating for message ${message.id}")

            // Get model pricing
            val modelPrice = ModelPriceManager.getModelPrice(providerId, modelId)

            if (modelPrice == null) {
                LogManager.aiService("SessionCostCalculator - Model price not available for $providerId/$modelId", "WARN")
                return MessageCost(
                    messageId = message.id,
                    modelId = modelId,
                    inputTokens = message.inputTokens,
                    cacheWriteTokens = message.cacheWriteTokens,
                    cacheReadTokens = message.cacheReadTokens,
                    outputTokens = message.outputTokens,
                    inputCost = null,
                    cacheWriteCost = null,
                    cacheReadCost = null,
                    outputCost = null,
                    totalCost = null,
                    priceAvailable = false
                )
            }

            // Calculate costs (inputTokens already uncached from API)
            val inputCost = calculateCost(message.inputTokens, modelPrice.inputCostPerToken)
            val cacheWriteCost = if (modelPrice.cacheWriteCostPerToken != null) {
                calculateCost(message.cacheWriteTokens, modelPrice.cacheWriteCostPerToken)
            } else {
                calculateCost(message.cacheWriteTokens, modelPrice.inputCostPerToken)
            }
            val cacheReadCost = if (modelPrice.cacheReadCostPerToken != null) {
                calculateCost(message.cacheReadTokens, modelPrice.cacheReadCostPerToken)
            } else {
                0.0
            }
            val outputCost = calculateCost(message.outputTokens, modelPrice.outputCostPerToken)

            val totalCost = inputCost + cacheWriteCost + cacheReadCost + outputCost

            return MessageCost(
                messageId = message.id,
                modelId = modelId,
                inputTokens = message.inputTokens,
                cacheWriteTokens = message.cacheWriteTokens,
                cacheReadTokens = message.cacheReadTokens,
                outputTokens = message.outputTokens,
                inputCost = inputCost,
                cacheWriteCost = cacheWriteCost,
                cacheReadCost = cacheReadCost,
                outputCost = outputCost,
                totalCost = totalCost,
                priceAvailable = true
            )

        } catch (e: Exception) {
            LogManager.aiService("SessionCostCalculator - Error calculating message cost: ${e.message}", "ERROR", e)
            return null
        }
    }

    // ========================================================================================
    // Private Implementation
    // ========================================================================================

    /**
     * Calculate cost from token count and cost per token
     *
     * @param tokens Number of tokens
     * @param costPerToken Cost per token (LiteLLM prices are per token, need to divide by 1M for total)
     * @return Cost in USD
     */
    private fun calculateCost(tokens: Int, costPerToken: Double): Double {
        // LiteLLM pricing is already per-token (e.g., 3e-06 for Claude Sonnet input)
        // So we just multiply: tokens × costPerToken
        return tokens * costPerToken
    }
}
