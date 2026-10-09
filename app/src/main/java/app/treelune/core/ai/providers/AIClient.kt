package app.treelune.core.ai.providers

import org.json.JSONObject

import app.treelune.core.utils.JsonUtils
import android.content.Context
import app.treelune.core.ai.data.*
import app.treelune.core.ai.providers.AIProvider
import app.treelune.core.ai.providers.AIProviderRegistry
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Client - pure logic for interfacing with AI providers
 *
 * Responsibilities:
 * - Convert PromptResult to provider-specific format
 * - Handle provider errors (NO FALLBACKS without explicit configuration)
 * - Uses AIProviderConfigService for configurations (no direct DB access)
 */
class AIClient(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val providerRegistry = AIProviderRegistry(context)
    private val s = Strings.`for`(context = context)

    /**
     * Send prompt data to AI provider and return raw response
     * Provider transforms PromptData to its specific format and handles prompt construction
     *
     * @param promptData The raw prompt data (L1-L3 + messages)
     * @param providerId Optional provider ID to use. If null, uses active provider (for CHAT). For AUTOMATION, this should be the automation's configured provider.
     * @return AIResponse with raw JSON string (not parsed)
     */
    suspend fun query(promptData: PromptData, providerId: String? = null): AIResponse {
        LogManager.aiService("AIClient.query() called with PromptData: ${promptData.sessionMessages.size} messages, providerId: $providerId", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                // Determine which provider to use
                val effectiveProviderId: String = if (providerId != null) {
                    // Use specified provider (AUTOMATION case)
                    LogManager.aiService("Using specified provider: $providerId", "DEBUG")
                    providerId
                } else {
                    // Get active provider ID (CHAT case)
                    val activeResult = coordinator.processUserAction("ai_provider_config.get_active")

                    if (!activeResult.isSuccess) {
                        LogManager.aiService("Failed to get active provider: ${activeResult.error}", "ERROR")
                        return@withContext AIResponse(
                            success = false,
                            content = "",
                            errorMessage = s.shared("ai_error_no_provider_configured"),
                            failure = AIFailure.CONFIG
                        )
                    }

                    val hasActiveProvider = activeResult.data?.get("has_active_provider") as? Boolean ?: false
                    if (!hasActiveProvider) {
                        LogManager.aiService("No active AI provider configured", "ERROR")
                        return@withContext AIResponse(
                            success = false,
                            content = "",
                            errorMessage = s.shared("ai_error_no_provider_configured"),
                            failure = AIFailure.CONFIG
                        )
                    }

                    val activeProviderId = activeResult.data?.get("active_provider_id") as? String
                    if (activeProviderId.isNullOrEmpty()) {
                        LogManager.aiService("Active provider ID is empty", "ERROR")
                        return@withContext AIResponse(
                            success = false,
                            content = "",
                            errorMessage = s.shared("ai_error_no_provider_configured"),
                            failure = AIFailure.CONFIG
                        )
                    }

                    LogManager.aiService("Using active provider: $activeProviderId", "DEBUG")
                    activeProviderId
                }

                // Get provider from registry
                val provider = providerRegistry.getProvider(effectiveProviderId)
                if (provider == null) {
                    LogManager.aiService("Provider not found in registry: $effectiveProviderId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = s.shared("ai_error_provider_not_found").format(effectiveProviderId),
                        failure = AIFailure.CONFIG
                    )
                }

                LogManager.aiService("Using provider: ${provider.getDisplayName()}", "DEBUG")

                // Get provider configuration via coordinator
                val configResult = coordinator.processUserAction("ai_provider_config.get", mapOf(
                    "provider_id" to effectiveProviderId
                ))

                if (!configResult.isSuccess) {
                    LogManager.aiService("Provider configuration not found: $effectiveProviderId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = s.shared("ai_error_provider_not_configured").format(effectiveProviderId),
                        failure = AIFailure.CONFIG
                    )
                }

                @Suppress("UNCHECKED_CAST")
                val providerConfig = (configResult.data?.get("config") as? Map<String, Any?>)
                    ?.let { JsonUtils.toJSONObject(it).toString() } ?: "{}"
                val isConfigured = configResult.data?.get("is_configured") as? Boolean ?: false

                if (!isConfigured) {
                    LogManager.aiService("Provider not configured: $effectiveProviderId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = s.shared("ai_error_provider_not_configured").format(effectiveProviderId),
                        failure = AIFailure.CONFIG
                    )
                }

                // A key sealed on another phone: said as such, never sent as an empty key
                if ((configResult.data?.get(app.treelune.core.secrets.SecretSettings.UNREADABLE) as? List<*>).orEmpty().isNotEmpty()) {
                    LogManager.aiService("Provider secret unreadable on this phone: $effectiveProviderId", "ERROR")
                    return@withContext AIResponse(
                        success = false,
                        content = "",
                        errorMessage = s.shared("ai_error_provider_secret_unreadable").format(provider.getDisplayName()),
                        failure = AIFailure.CONFIG
                    )
                }

                // A history with images goes whole or not at all: refused, and said, when the
                // model does not read them, when nothing says it does, when there are too many,
                // or when an image's file is missing; never sent without them
                val imageIds = promptData.sessionMessages.flatMap { it.promptParts.orEmpty() }
                    .filterIsInstance<app.treelune.core.ai.data.PromptPart.Image>().map { it.imageId }
                val imageRefusal = ImageInput.refusal(imageIds.size, provider.readsImages(JSONObject(providerConfig), context)) { s.shared(it) }
                    ?: imageIds.firstOrNull { !app.treelune.core.ai.enrichments.AttachedImages.file(context, it).exists() }
                        ?.let { s.shared("ai_image_error_missing") }
                if (imageRefusal != null) {
                    LogManager.aiService("Request with ${imageIds.size} images refused: $imageRefusal", "WARN")
                    return@withContext AIResponse(success = false, content = "", errorMessage = imageRefusal, failure = AIFailure.CONFIG)
                }

                // Send query to provider with PromptData
                LogManager.aiService("Sending PromptData to ${provider.getDisplayName()}", "DEBUG")
                val aiResponse = provider.query(promptData, providerConfig)

                LogManager.aiService("Received response from ${provider.getDisplayName()}: success=${aiResponse.success}", "DEBUG")

                aiResponse

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogManager.aiService("AIClient query failed: ${e.message}", "ERROR", e)
                AIResponse(
                    success = false,
                    content = "",
                    errorMessage = s.shared("ai_error_ai_query_failed").format(e.message ?: ""),
                    failure = aiFailureOf(e)
                )
            }
        }
    }

    /**
     * Get list of available providers via coordinator
     */
    suspend fun getAvailableProviders(): List<AIProviderInfo> {
        val result = coordinator.processUserAction("ai_provider_config.list")

        return if (result.isSuccess) {
            val providers = result.data?.get("providers") as? List<*> ?: emptyList<Any>()
            providers.mapNotNull { item ->
                val providerMap = item as? Map<*, *> ?: return@mapNotNull null
                AIProviderInfo(
                    id = providerMap["id"] as? String ?: return@mapNotNull null,
                    displayName = providerMap["display_name"] as? String ?: "",
                    isConfigured = providerMap["is_configured"] as? Boolean ?: false,
                    isActive = providerMap["is_active"] as? Boolean ?: false
                )
            }
        } else {
            LogManager.aiService("Failed to get providers: ${result.error}", "ERROR")
            emptyList()
        }
    }

    /**
     * Get active provider ID via coordinator
     */
    suspend fun getActiveProviderId(): String? {
        val result = coordinator.processUserAction("ai_provider_config.get_active")
        return if (result.isSuccess) {
            result.data?.get("active_provider_id") as? String
        } else {
            LogManager.aiService("Failed to get active provider: ${result.error}", "ERROR")
            null
        }
    }
}

/**
 * Information about an AI provider
 */
data class AIProviderInfo(
    val id: String,
    val displayName: String,
    val isConfigured: Boolean,
    val isActive: Boolean
)

/**
 * AI provider response structure
 */
data class AIResponse(
    val success: Boolean,
    val content: String,
    val errorMessage: String? = null,
    // Why the call failed, when it did. Null on success. Read by AIEventProcessor to decide
    // between waiting for the network and stopping the session.
    val failure: AIFailure? = null,
    val tokensUsed: Int = 0,
    // Cache metrics (generic, supported by multiple providers, 0 if not supported)
    val cacheWriteTokens: Int = 0,  // Cache write/creation tokens (Claude, OpenAI, etc.)
    val cacheReadTokens: Int = 0,   // Cache read tokens (Claude, OpenAI, etc.)
    val inputTokens: Int = 0
)