package com.assistant.core.ai.utils

import android.content.Context
import com.assistant.core.services.AppConfigService
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.database.entities.DefaultAILimitsSettings
import com.assistant.core.utils.LogManager
import org.json.JSONObject

/**
 * Token calculation utility - Single source of truth for all token estimations
 *
 * Used by:
 * - AIQueryProcessor: For individual query result validation
 * - PromptManager: For prompt level validation (Level 1-4 + messages)
 * - AIService: For total prompt validation before API call
 *
 * Provides conservative estimates to avoid prompt rejections
 */
object TokenCalculator {

    /**
     * Estimate tokens for text content using provider-specific or default ratios
     *
     * @param text Content to estimate
     * @param providerId Provider ID ("claude", "openai", etc.) or "default"
     * @param context Context for config access
     * @return Estimated token count
     */
    suspend fun estimateTokens(text: String, providerId: String = "default", context: Context): Int {
        if (text.isEmpty()) return 0

        return try {
            val config = getAILimitsConfig(context)
            val charsPerToken = getCharsPerTokenRatio(config, providerId)

            val charCount = text.length
            val estimatedTokens = (charCount.toDouble() / charsPerToken).toInt()

            LogManager.coordination("Token estimation: $charCount chars → $estimatedTokens tokens (ratio: $charsPerToken)")

            // Add small buffer for safety (5%)
            (estimatedTokens * 1.05).toInt()

        } catch (e: Exception) {
            LogManager.coordination("Token estimation failed: ${e.message}", "ERROR", e)
            // Fallback: very conservative estimate (3 chars per token)
            (text.length / 3)
        }
    }

    /**
     * Estimate tokens for structured data (query results, JSON objects)
     *
     * @param data Structured data to estimate
     * @param providerId Provider ID for ratio calculation
     * @param context Context for config access
     * @return Estimated token count
     */
    suspend fun estimateDataTokens(data: Any, providerId: String = "default", context: Context): Int {
        val textRepresentation = when (data) {
            is String -> data
            is List<*> -> data.joinToString("\n") { it.toString() }
            is Map<*, *> -> data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            is JSONObject -> data.toString(2) // Pretty format for readability
            else -> data.toString()
        }

        return estimateTokens(textRepresentation, providerId, context)
    }

    /**
     * Get token limits configuration for specific context
     *
     * @param context Context for config access
     * @param promptLevel Prompt level (1-4) or null for query limits
     * @return Token limit for the specified context
     */
    suspend fun getTokenLimit(context: Context, isQuery: Boolean = true, isTotal: Boolean = false): Int {
        return try {
            val config = getAILimitsConfig(context)

            // TODO: Get provider-specific limits from active provider configuration
            // Provider should override these defaults with their specific limits
            // Example: activeProvider.getMaxTokens() overrides defaults

            when {
                isTotal -> config.optInt("defaultPromptMaxTokens", 15000)
                isQuery -> config.optInt("defaultQueryMaxTokens", 2000)
                else -> config.optInt("defaultPromptMaxTokens", 15000)
            }
        } catch (e: Exception) {
            LogManager.coordination("Failed to get token limit: ${e.message}", "ERROR", e)
            throw e // Don't provide fallbacks - let it fail explicitly
        }
    }

    /**
     * Check if content exceeds token limits
     *
     * @param content Content to check
     * @param context Context for config access
     * @param promptLevel Prompt level or null for query
     * @param providerId Provider ID for estimation
     * @return TokenLimitResult with details
     */
    suspend fun checkTokenLimit(
        content: String,
        context: Context,
        isQuery: Boolean = true,
        providerId: String = "default"
    ): TokenLimitResult {
        val estimatedTokens = estimateTokens(content, providerId, context)
        val limit = getTokenLimit(context, isQuery)

        return TokenLimitResult(
            estimatedTokens = estimatedTokens,
            limit = limit,
            exceeds = estimatedTokens > limit,
            ratio = estimatedTokens.toDouble() / limit
        )
    }

    // ========================================================================================
    // Private utility methods
    // ========================================================================================

    private suspend fun getAILimitsConfig(context: Context): JSONObject {
        val appConfigService = AppConfigService(context)
        val configJson = appConfigService.getCategorySettings(AppSettingCategories.AI_LIMITS)

        if (configJson == null) {
            throw IllegalStateException("AI_LIMITS configuration not found - app configuration incomplete")
        }

        return configJson
    }

    private fun getCharsPerTokenRatio(config: JSONObject, providerId: String): Double {
        // TODO: Get provider-specific overrides from provider configuration
        // Provider configs should have their own charsPerToken values that override defaults
        // Example: provider.getConfig().optDouble("charsPerToken", defaultValue)

        return config.optDouble("defaultCharsPerToken", 4.5)
    }
}

/**
 * Result of token limit check
 */
data class TokenLimitResult(
    val estimatedTokens: Int,
    val limit: Int,
    val exceeds: Boolean,
    val ratio: Double
) {
    /**
     * Get human-readable description of the limit status
     */
    fun getDescription(): String {
        val percentage = (ratio * 100).toInt()
        return when {
            exceeds -> "Dépassement: $estimatedTokens tokens (limite: $limit) - ${percentage}%"
            ratio > 0.8 -> "Proche limite: $estimatedTokens tokens (limite: $limit) - ${percentage}%"
            else -> "Dans limite: $estimatedTokens tokens (limite: $limit) - ${percentage}%"
        }
    }
}