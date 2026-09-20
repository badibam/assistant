package com.assistant.core.ai.providers

import com.assistant.core.utils.JsonUtils
import android.content.Context
import com.assistant.core.ai.data.*
import com.assistant.core.ai.providers.AIProvider
import com.assistant.core.ai.providers.AIProviderRegistry
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI Client - pure logic for interfacing with AI providers
 *
 * Responsibilities:
 * - Convert PromptResult to provider-specific format
 * - Parse provider responses to AIMessage
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

                // Send query to provider with PromptData
                LogManager.aiService("Sending PromptData to ${provider.getDisplayName()}", "DEBUG")
                val aiResponse = provider.query(promptData, providerConfig)

                LogManager.aiService("Received response from ${provider.getDisplayName()}: success=${aiResponse.success}", "DEBUG")

                aiResponse

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

    // ========================================================================================
    // Private Implementation
    // ========================================================================================

    /**
     * Parse AI provider response JSON to AIMessage object
     */
    private fun parseAIResponse(responseJson: String): AIMessage? {
        return try {
            LogManager.aiService("Parsing AI response JSON: ${responseJson.length} characters")

            val json = JSONObject(responseJson)

            // Parse required preText
            val preText = json.getString("pre_text")

            // Parse optional validationRequest (boolean: true = validation required)
            val validationRequest = if (json.has("validation_request")) {
                json.optBoolean("validation_request", false)
            } else null

            val dataCommands = json.optJSONArray("data_commands")?.let { array ->
                (0 until array.length()).map { index ->
                    val commandJson = array.getJSONObject(index)
                    DataCommand(
                        id = commandJson.getString("id"),
                        type = commandJson.getString("type"),
                        params = parseParams(commandJson.getJSONObject("params")),
                        isRelative = commandJson.optBoolean("is_relative", false)
                    )
                }
            }

            val actionCommands = json.optJSONArray("action_commands")?.let { array ->
                (0 until array.length()).map { index ->
                    val commandJson = array.getJSONObject(index)
                    DataCommand(
                        id = commandJson.getString("id"),
                        type = commandJson.getString("type"),
                        params = parseParams(commandJson.getJSONObject("params")),
                        isRelative = commandJson.optBoolean("is_relative", false)
                    )
                }
            }

            val postText = json.optString("post_text", "").takeIf { it.isNotEmpty() }

            val communicationModule = json.optJSONObject("communication_module")?.let { moduleJson ->
                try {
                    val type = moduleJson.getString("type")
                    val dataJson = moduleJson.getJSONObject("data")
                    val data = parseParams(dataJson)

                    // Validate via CommunicationModuleSchemas
                    val schema = CommunicationModuleSchemas.getSchema(type, context)
                    if (schema == null) {
                        LogManager.aiService("Unknown communication module type: $type", "WARN")
                        return@let null
                    }

                    val validation = com.assistant.core.validation.SchemaValidator.validate(schema, data, context)
                    if (!validation.isValid) {
                        LogManager.aiService("Invalid communication module data for type $type: ${validation.errorMessage}", "WARN")
                        return@let null
                    }

                    // Create appropriate module instance
                    when (type) {
                        "MultipleChoice" -> CommunicationModule.MultipleChoice(type, data)
                        "Validation" -> CommunicationModule.Validation(type, data)
                        else -> {
                            LogManager.aiService("Unsupported communication module type: $type", "WARN")
                            null
                        }
                    }
                } catch (e: Exception) {
                    LogManager.aiService("Failed to parse communication module: ${e.message}", "WARN", e)
                    null
                }
            }

            val aiMessage = AIMessage(
                preText = preText,
                validationRequest = validationRequest,
                dataCommands = dataCommands,
                actionCommands = actionCommands,
                postText = postText,
                keepControl = null,
                communicationModule = communicationModule,
                completed = null
            )

            LogManager.aiService("Successfully parsed AIMessage: preText present, ${actionCommands?.size ?: 0} actionCommands, ${dataCommands?.size ?: 0} dataCommands")

            aiMessage

        } catch (e: Exception) {
            LogManager.aiService("Failed to parse AI response: ${e.message}", "ERROR", e)
            null
        }
    }

    private fun parseParams(paramsJson: JSONObject): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        paramsJson.keys().forEach { key ->
            params[key] = paramsJson.get(key)
        }
        return params
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