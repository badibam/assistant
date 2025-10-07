package com.assistant.core.ai.utils

import android.content.Context
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Model price information from LiteLLM pricing database
 */
data class ModelPrice(
    val modelId: String,
    val inputCostPerToken: Double,
    val outputCostPerToken: Double,
    val cacheWriteCostPerToken: Double?,  // Generic (Claude, OpenAI, etc.)
    val cacheReadCostPerToken: Double?,   // Generic (Claude, OpenAI, etc.)
    val provider: String,
    val maxInputTokens: Int?,
    val maxOutputTokens: Int?
)

/**
 * Singleton manager for AI model pricing
 *
 * Fetches and caches model pricing data from LiteLLM's public pricing database
 * Similar pattern to AppConfigManager but for model pricing
 *
 * Usage:
 * - Initialize at app startup: ModelPriceManager.initialize(context)
 * - Get price: ModelPriceManager.getModelPrice(providerId, modelId)
 * - Refresh pricing: ModelPriceManager.refresh() (called automatically when fetching models)
 */
object ModelPriceManager {

    private const val LITELLM_PRICING_URL = "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json"
    private const val TIMEOUT_SECONDS = 30L

    // In-memory cache of model prices indexed by LiteLLM model ID
    @Volatile
    private var priceCache: Map<String, ModelPrice> = emptyMap()

    @Volatile
    private var isInitialized = false

    @Volatile
    private var lastFetchTimestamp: Long = 0

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Initialize pricing cache from LiteLLM database
     * Must be called at app startup
     *
     * Fetches asynchronously - failures are logged but don't block initialization
     */
    suspend fun initialize(context: Context) {
        if (isInitialized) return

        LogManager.aiService("ModelPriceManager.initialize() - Starting fetch from LiteLLM")

        try {
            fetchAndCachePricing(context)
            isInitialized = true
            LogManager.aiService("ModelPriceManager initialized: ${priceCache.size} models loaded")
        } catch (e: Exception) {
            LogManager.aiService("ModelPriceManager initialization failed: ${e.message}", "WARN", e)
            // Initialize with empty cache on failure - allows app to continue
            priceCache = emptyMap()
            isInitialized = true
        }
    }

    /**
     * Refresh pricing data from LiteLLM
     * Called automatically when providers fetch available models
     */
    suspend fun refresh() {
        LogManager.aiService("ModelPriceManager.refresh() - Refreshing pricing data")

        try {
            // Only fetch if last fetch was more than 1 hour ago (avoid excessive API calls)
            val now = System.currentTimeMillis()
            if (now - lastFetchTimestamp < 3600_000) {
                LogManager.aiService("ModelPriceManager.refresh() - Skipping, last fetch was ${(now - lastFetchTimestamp) / 1000}s ago")
                return
            }

            fetchAndCachePricing(null)
            LogManager.aiService("ModelPriceManager refreshed: ${priceCache.size} models loaded")
        } catch (e: Exception) {
            LogManager.aiService("ModelPriceManager refresh failed: ${e.message}", "WARN", e)
            // Keep existing cache on failure
        }
    }

    /**
     * Get model price by provider and model ID
     *
     * @param providerId Provider identifier (e.g., "claude", "openai")
     * @param modelId Provider-specific model ID
     * @return ModelPrice if available, null if model not found or pricing unavailable
     */
    fun getModelPrice(providerId: String, modelId: String): ModelPrice? {
        if (!isInitialized) {
            LogManager.aiService("ModelPriceManager.getModelPrice() called before initialization", "WARN")
            return null
        }

        // Map provider model ID to LiteLLM ID
        val liteLLMId = mapProviderModelToLiteLLMId(providerId, modelId) ?: return null

        val price = priceCache[liteLLMId]

        if (price == null) {
            LogManager.aiService("ModelPriceManager.getModelPrice() - Price not found for provider=$providerId, modelId=$modelId, mapped=$liteLLMId", "DEBUG")
        }

        return price
    }

    /**
     * Map provider-specific model ID to LiteLLM model ID
     *
     * LiteLLM uses standardized model IDs across providers
     * This function handles provider-specific naming variations
     *
     * Examples:
     * - Claude: "claude-3-5-sonnet-20241022" → "claude-3-5-sonnet-20241022" (direct match)
     * - OpenAI: "gpt-4o" → "gpt-4o" (direct match)
     * - Custom names → Standard LiteLLM IDs
     *
     * @param providerId Provider identifier
     * @param modelId Provider-specific model ID
     * @return LiteLLM standardized model ID, or null if mapping not found
     */
    fun mapProviderModelToLiteLLMId(providerId: String, modelId: String): String? {
        return when (providerId) {
            "claude" -> mapClaudeModel(modelId)
            "openai" -> mapOpenAIModel(modelId)
            // Extensible for other providers
            else -> {
                // Fallback: try model ID directly
                LogManager.aiService("ModelPriceManager.mapProviderModelToLiteLLMId() - Unknown provider $providerId, trying direct model ID", "DEBUG")
                modelId
            }
        }
    }

    // ========================================================================================
    // Private Implementation
    // ========================================================================================

    /**
     * Fetch pricing data from LiteLLM and cache in memory
     */
    private suspend fun fetchAndCachePricing(context: Context?) = withContext(Dispatchers.IO) {
        val s = context?.let { Strings.`for`(context = it) }

        LogManager.aiService("ModelPriceManager.fetchAndCachePricing() - Fetching from $LITELLM_PRICING_URL")

        val request = Request.Builder()
            .url(LITELLM_PRICING_URL)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorMsg = s?.shared("error_model_prices_fetch_failed")
                ?: "Failed to fetch model prices"
            LogManager.aiService("$errorMsg: HTTP ${response.code}", "ERROR")
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw Exception("Empty response body")

        // Parse JSON
        val pricingJson = JSONObject(responseBody)
        val newCache = mutableMapOf<String, ModelPrice>()

        pricingJson.keys().forEach { modelId ->
            try {
                val modelData = pricingJson.getJSONObject(modelId)

                val inputCost = modelData.optDouble("input_cost_per_token", 0.0)
                val outputCost = modelData.optDouble("output_cost_per_token", 0.0)
                val cacheWriteCost = modelData.optDouble("cache_creation_input_token_cost", Double.NaN)
                val cacheReadCost = modelData.optDouble("cache_read_input_token_cost", Double.NaN)
                val provider = modelData.optString("litellm_provider", "unknown")
                val maxInputTokens = modelData.optInt("max_input_tokens", -1)
                val maxOutputTokens = modelData.optInt("max_output_tokens", -1)

                newCache[modelId] = ModelPrice(
                    modelId = modelId,
                    inputCostPerToken = inputCost,
                    outputCostPerToken = outputCost,
                    cacheWriteCostPerToken = if (cacheWriteCost.isNaN()) null else cacheWriteCost,
                    cacheReadCostPerToken = if (cacheReadCost.isNaN()) null else cacheReadCost,
                    provider = provider,
                    maxInputTokens = if (maxInputTokens > 0) maxInputTokens else null,
                    maxOutputTokens = if (maxOutputTokens > 0) maxOutputTokens else null
                )
            } catch (e: Exception) {
                LogManager.aiService("Failed to parse pricing for model $modelId: ${e.message}", "WARN")
                // Continue with other models
            }
        }

        priceCache = newCache
        lastFetchTimestamp = System.currentTimeMillis()

        LogManager.aiService("ModelPriceManager.fetchAndCachePricing() - Cached ${newCache.size} model prices")
    }

    /**
     * Map Claude model names to LiteLLM IDs
     * Generally direct match, but can handle variations
     */
    private fun mapClaudeModel(modelId: String): String {
        // Claude model IDs typically match LiteLLM directly
        // e.g., "claude-3-5-sonnet-20241022" → "claude-3-5-sonnet-20241022"
        return modelId
    }

    /**
     * Map OpenAI model names to LiteLLM IDs
     * Generally direct match, but can handle variations
     */
    private fun mapOpenAIModel(modelId: String): String {
        // OpenAI model IDs typically match LiteLLM directly
        // e.g., "gpt-4o" → "gpt-4o"
        return modelId
    }

    /**
     * Get all cached model IDs (for debugging)
     */
    fun getCachedModelIds(): List<String> {
        return priceCache.keys.toList()
    }

    /**
     * Check if pricing data is available
     */
    fun isPricingAvailable(): Boolean {
        return isInitialized && priceCache.isNotEmpty()
    }
}
