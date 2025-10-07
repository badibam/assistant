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
    val totalInputTokens: Int,              // Total input tokens (includes cache write + read + regular)
    val totalCacheWriteTokens: Int,
    val totalCacheReadTokens: Int,
    val totalOutputTokens: Int,
    val regularInputTokens: Int,            // Calculated: inputTokens - cacheWrite - cacheRead
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
    val inputTokens: Int,
    val cacheWriteTokens: Int,
    val cacheReadTokens: Int,
    val outputTokens: Int,
    val regularInputTokens: Int,
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
 * stored in SessionMessageEntity
 *
 * Cost calculation formula:
 * - Regular input cost = (inputTokens - cacheWriteTokens - cacheReadTokens) × inputCostPerToken / 1,000,000
 * - Cache write cost = cacheWriteTokens × cacheWriteCostPerToken / 1,000,000
 * - Cache read cost = cacheReadTokens × cacheReadCostPerToken / 1,000,000
 * - Output cost = outputTokens × outputCostPerToken / 1,000,000
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
            val totalInputTokens = messages.sumOf { it.inputTokens }
            val totalCacheWriteTokens = messages.sumOf { it.cacheWriteTokens }
            val totalCacheReadTokens = messages.sumOf { it.cacheReadTokens }
            val totalOutputTokens = messages.sumOf { it.outputTokens }

            // Calculate regular input tokens (not cached)
            val regularInputTokens = totalInputTokens - totalCacheWriteTokens - totalCacheReadTokens

            // Get model pricing
            val modelPrice = ModelPriceManager.getModelPrice(providerId, modelId)

            if (modelPrice == null) {
                LogManager.aiService("SessionCostCalculator - Model price not available for $providerId/$modelId", "WARN")
                return SessionCost(
                    modelId = modelId,
                    totalInputTokens = totalInputTokens,
                    totalCacheWriteTokens = totalCacheWriteTokens,
                    totalCacheReadTokens = totalCacheReadTokens,
                    totalOutputTokens = totalOutputTokens,
                    regularInputTokens = regularInputTokens,
                    inputCost = null,
                    cacheWriteCost = null,
                    cacheReadCost = null,
                    outputCost = null,
                    totalCost = null,
                    priceAvailable = false
                )
            }

            // Calculate costs (price per token is per million tokens)
            val inputCost = calculateCost(regularInputTokens, modelPrice.inputCostPerToken)
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

            LogManager.aiService("SessionCostCalculator - Total cost: \$${String.format("%.6f", totalCost)} (input=\$${String.format("%.6f", inputCost)}, cacheWrite=\$${String.format("%.6f", cacheWriteCost)}, cacheRead=\$${String.format("%.6f", cacheReadCost)}, output=\$${String.format("%.6f", outputCost)})")

            return SessionCost(
                modelId = modelId,
                totalInputTokens = totalInputTokens,
                totalCacheWriteTokens = totalCacheWriteTokens,
                totalCacheReadTokens = totalCacheReadTokens,
                totalOutputTokens = totalOutputTokens,
                regularInputTokens = regularInputTokens,
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

            val regularInputTokens = message.inputTokens - message.cacheWriteTokens - message.cacheReadTokens

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
                    regularInputTokens = regularInputTokens,
                    inputCost = null,
                    cacheWriteCost = null,
                    cacheReadCost = null,
                    outputCost = null,
                    totalCost = null,
                    priceAvailable = false
                )
            }

            // Calculate costs
            val inputCost = calculateCost(regularInputTokens, modelPrice.inputCostPerToken)
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
                regularInputTokens = regularInputTokens,
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
