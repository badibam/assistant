package com.assistant.core.ai.utils

import android.content.Context
import com.assistant.core.ai.data.CallPricing
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Model price information from LiteLLM pricing database, per token.
 * A price missing from the list is null, never 0: a missing price is not a free one.
 */
data class ModelPrice(
    val modelId: String,
    val inputCostPerToken: Double?,
    val outputCostPerToken: Double?,
    val cacheWriteCostPerToken: Double?,
    val cacheReadCostPerToken: Double?,
    // Higher prices for a call whose input exceeds a size, lowest threshold first
    val tiers: List<PriceTier> = emptyList()
) {
    /**
     * The prices that apply to one call, given its whole input: uncached, written to and read
     * from the cache. Above a tier's threshold, the tier's prices replace those it lists; the
     * others keep the base price, as LiteLLM lists only what changes.
     */
    fun forCall(inputTokens: Int): CallPricing {
        val tier = tiers.lastOrNull { inputTokens > it.aboveInputTokens }
        return CallPricing(
            modelId = modelId,
            inputPrice = tier?.inputCostPerToken ?: inputCostPerToken,
            cacheWritePrice = tier?.cacheWriteCostPerToken ?: cacheWriteCostPerToken,
            cacheReadPrice = tier?.cacheReadCostPerToken ?: cacheReadCostPerToken,
            outputPrice = tier?.outputCostPerToken ?: outputCostPerToken
        )
    }
}

/** Prices LiteLLM lists as `..._above_<N>k_tokens`: they apply above N thousand input tokens. */
data class PriceTier(
    val aboveInputTokens: Int,
    val inputCostPerToken: Double?,
    val outputCostPerToken: Double?,
    val cacheWriteCostPerToken: Double?,
    val cacheReadCostPerToken: Double?
)

/**
 * Prices of AI models, from LiteLLM's public list, available whenever a call needs them.
 *
 * The last list downloaded is kept on the phone and read back on first use, network or not,
 * whichever way the app was started (screen or background automation). A list older than a day
 * is downloaded again in the background. A model missing from a list older than an hour is
 * looked up in a fresh download at once: it may be newer than the copy.
 *
 * A model absent even from a fresh list has no price: its calls get an unknown cost.
 */
object ModelPriceManager {

    private const val LITELLM_PRICING_URL = "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json"
    private const val FILE_NAME = "model_prices.json"
    private const val TIMEOUT_SECONDS = 30L
    private const val STALE_AFTER_MS = 24 * 3600_000L
    private const val MISSING_MODEL_RETRY_AFTER_MS = 3600_000L
    private val TIER_KEY = Regex("(?:input_cost_per_token|output_cost_per_token|cache_creation_input_token_cost|cache_read_input_token_cost)_above_(\\d+)k_tokens")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    // The list in memory, as last read from the phone or downloaded; null until first use
    @Volatile private var prices: Map<String, ModelPrice>? = null
    @Volatile private var fetchedAt: Long = 0L

    /**
     * Price of a model, by its exact ID in the provider's config, or null if unknown.
     */
    suspend fun getModelPrice(context: Context, modelId: String): ModelPrice? =
        getModelPrice(context.applicationContext.filesDir, modelId)

    /** Same, with the copy kept in [dir]. */
    internal suspend fun getModelPrice(dir: File, modelId: String): ModelPrice? {
        val file = File(dir, FILE_NAME)
        lock.withLock {
            if (prices == null) {
                if (file.exists()) readCopy(file) else download(file)
            }
        }

        val age = System.currentTimeMillis() - fetchedAt
        prices?.get(modelId)?.let { price ->
            if (age > STALE_AFTER_MS) backgroundScope.launch {
                // Calls in a row each ask; the first download serves them all
                lock.withLock { if (System.currentTimeMillis() - fetchedAt > STALE_AFTER_MS) download(file) }
            }
            return price
        }

        // Missing: the copy may predate the model
        if (age > MISSING_MODEL_RETRY_AFTER_MS) {
            lock.withLock { if (System.currentTimeMillis() - fetchedAt > MISSING_MODEL_RETRY_AFTER_MS) download(file) }
        }
        return prices?.get(modelId).also {
            if (it == null) LogManager.aiService("ModelPriceManager: no price for model '$modelId'", "WARN")
        }
    }

    /** Read the copy kept on the phone. Its age is its file's. */
    private suspend fun readCopy(file: File) = withContext(Dispatchers.IO) {
        try {
            prices = parse(file.readText())
            fetchedAt = file.lastModified()
            LogManager.aiService("ModelPriceManager: ${prices?.size} prices read from the phone's copy")
        } catch (e: Exception) {
            LogManager.aiService("ModelPriceManager: phone's copy unreadable (${e.message}), downloading", "WARN")
            download(file)
        }
    }

    /**
     * Download the list and keep it on the phone. A failure keeps whatever list was there:
     * its prices are still the best known, and the next call tries again.
     */
    private suspend fun download(file: File) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(LITELLM_PRICING_URL).get().build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                response.body?.string() ?: throw Exception("Empty response body")
            }
            val parsed = parse(body)

            // Written aside then moved, so a cut write never leaves a broken copy
            val partial = File(file.parentFile, "$FILE_NAME.part")
            partial.writeText(body)
            if (!partial.renameTo(file)) throw Exception("Could not replace ${file.name}")

            prices = parsed
            fetchedAt = System.currentTimeMillis()
            LogManager.aiService("ModelPriceManager: ${parsed.size} prices downloaded")
        } catch (e: Exception) {
            LogManager.aiService("ModelPriceManager: download failed: ${e.message}", "WARN")
            if (prices == null) prices = emptyMap()
        }
    }

    /** Drop the list in memory, as a new process would start. */
    internal fun forgetForTest() {
        prices = null
        fetchedAt = 0L
    }

    internal fun parse(json: String): Map<String, ModelPrice> {
        val list = JSONObject(json)
        val parsed = mutableMapOf<String, ModelPrice>()
        list.keys().forEach { modelId ->
            val model = list.optJSONObject(modelId) ?: return@forEach
            fun price(key: String): Double? = if (model.has(key)) model.optDouble(key).takeUnless { it.isNaN() } else null

            // Only the plain tier keys: the _flex, _priority and _batches ones price modes the app does not use
            val thresholds = model.keys().asSequence()
                .mapNotNull { TIER_KEY.matchEntire(it)?.groupValues?.get(1)?.toInt() }
                .distinct().sorted().toList()

            parsed[modelId] = ModelPrice(
                modelId = modelId,
                inputCostPerToken = price("input_cost_per_token"),
                outputCostPerToken = price("output_cost_per_token"),
                cacheWriteCostPerToken = price("cache_creation_input_token_cost"),
                cacheReadCostPerToken = price("cache_read_input_token_cost"),
                tiers = thresholds.map { k ->
                    PriceTier(
                        aboveInputTokens = k * 1000,
                        inputCostPerToken = price("input_cost_per_token_above_${k}k_tokens"),
                        outputCostPerToken = price("output_cost_per_token_above_${k}k_tokens"),
                        cacheWriteCostPerToken = price("cache_creation_input_token_cost_above_${k}k_tokens"),
                        cacheReadCostPerToken = price("cache_read_input_token_cost_above_${k}k_tokens")
                    )
                }
            )
        }
        return parsed
    }
}
