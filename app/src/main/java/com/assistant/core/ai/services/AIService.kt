package com.assistant.core.ai.services

import android.content.Context
import com.assistant.core.ai.data.AIMessage
import com.assistant.core.ai.providers.AIProvider
import com.assistant.core.ai.providers.AIProviderRegistry
import com.assistant.core.ai.prompts.PromptResult
import com.assistant.core.coordinator.OperationResult
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Central AI service that interfaces with multiple AI providers
 *
 * Responsibilities:
 * - Abstract provider management (Claude, OpenAI, etc.)
 * - Convert PromptResult to provider-specific format
 * - Parse provider responses to AIMessage
 * - Handle provider errors and fallbacks
 * - Manage provider-specific configurations
 */
class AIService(private val context: Context) {

    private val providerRegistry = AIProviderRegistry(context)

    /**
     * Send prompt to AI provider and return parsed response
     */
    suspend fun query(promptResult: PromptResult, providerId: String): OperationResult {
        LogManager.aiService("AIService.query() called with provider: $providerId, tokens: ${promptResult.totalTokens}")

        return withContext(Dispatchers.IO) {
            try {
                // Get active provider
                val provider = providerRegistry.getProvider(providerId)
                if (provider == null) {
                    LogManager.aiService("Provider not found: $providerId", "ERROR")
                    return@withContext OperationResult.error("Provider not found: $providerId")
                }

                LogManager.aiService("Using provider: ${provider.getDisplayName()}")

                // Get provider configuration
                val providerConfig = providerRegistry.getProviderConfig(providerId)
                if (providerConfig == null) {
                    LogManager.aiService("Provider configuration not found: $providerId", "ERROR")
                    return@withContext OperationResult.error("Provider configuration not found: $providerId")
                }

                // Validate provider configuration using SchemaValidator
                val configValidation = SchemaValidator.validate(provider, providerConfig, context, schemaType = "config")
                if (!configValidation.isValid) {
                    LogManager.aiService("Provider configuration validation failed: ${configValidation.errorMessage}", "ERROR")
                    return@withContext OperationResult.error("Provider configuration invalid: ${configValidation.errorMessage}")
                }

                // Send query to provider
                LogManager.aiService("Sending prompt to ${provider.getDisplayName()}: ${promptResult.prompt.length} characters")
                val aiResponse = provider.query(promptResult.prompt, providerConfig)

                LogManager.aiService("Received response from ${provider.getDisplayName()}: success=${aiResponse.success}")

                if (aiResponse.success) {
                    // Parse response to AIMessage
                    val aiMessage = parseAIResponse(aiResponse.content)
                    if (aiMessage != null) {
                        LogManager.aiService("Successfully parsed AI response to AIMessage")
                        OperationResult.success(mapOf(
                            "aiMessage" to aiMessage,
                            "aiMessageJson" to aiResponse.content,
                            "provider" to providerId,
                            "tokensUsed" to aiResponse.tokensUsed
                        ))
                    } else {
                        LogManager.aiService("Failed to parse AI response", "ERROR")
                        OperationResult.error("Failed to parse AI response")
                    }
                } else {
                    LogManager.aiService("AI provider returned error: ${aiResponse.errorMessage}", "ERROR")
                    OperationResult.error("AI provider error: ${aiResponse.errorMessage}")
                }

            } catch (e: Exception) {
                LogManager.aiService("AIService query failed: ${e.message}", "ERROR", e)
                OperationResult.error("AI query failed: ${e.message}")
            }
        }
    }

    /**
     * Get list of available providers
     */
    fun getAvailableProviders(): List<AIProviderInfo> {
        return providerRegistry.getAllProviders().map { provider ->
            AIProviderInfo(
                id = provider.getProviderId(),
                displayName = provider.getDisplayName(),
                isConfigured = providerRegistry.isProviderConfigured(provider.getProviderId()),
                isActive = providerRegistry.isProviderActive(provider.getProviderId())
            )
        }
    }

    /**
     * Get active provider ID
     */
    fun getActiveProviderId(): String? {
        return providerRegistry.getActiveProviderId()
    }

    /**
     * Set active provider
     */
    suspend fun setActiveProvider(providerId: String): OperationResult {
        LogManager.aiService("Setting active provider: $providerId")

        return try {
            val success = providerRegistry.setActiveProvider(providerId)
            if (success) {
                LogManager.aiService("Successfully set active provider: $providerId")
                OperationResult.success()
            } else {
                LogManager.aiService("Failed to set active provider: $providerId", "ERROR")
                OperationResult.error("Failed to set active provider")
            }
        } catch (e: Exception) {
            LogManager.aiService("Error setting active provider: ${e.message}", "ERROR", e)
            OperationResult.error("Error setting active provider: ${e.message}")
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
            val preText = json.getString("preText")

            // Parse optional fields
            val validationRequest = json.optJSONObject("validationRequest")?.let {
                ValidationRequest(
                    message = it.getString("message"),
                    status = it.optString("status", null)?.let { status ->
                        ValidationStatus.valueOf(status)
                    }
                )
            }

            val dataRequests = json.optJSONArray("dataRequests")?.let { array ->
                (0 until array.length()).map { index ->
                    val requestJson = array.getJSONObject(index)
                    DataQuery(
                        id = requestJson.getString("id"),
                        type = requestJson.getString("type"),
                        params = parseParams(requestJson.getJSONObject("params")),
                        isRelative = requestJson.optBoolean("isRelative", false)
                    )
                }
            }

            val actions = json.optJSONArray("actions")?.let { array ->
                (0 until array.length()).map { index ->
                    val actionJson = array.getJSONObject(index)
                    AIAction(
                        id = actionJson.getString("id"),
                        command = actionJson.getString("command"),
                        params = parseParams(actionJson.getJSONObject("params")),
                        saveResultAs = actionJson.optString("saveResultAs", null),
                        status = ActionStatus.PENDING
                    )
                }
            }

            val postText = json.optString("postText", null)

            val communicationModule = json.optJSONObject("communicationModule")?.let {
                // TODO: Parse communication module
                // For now, return null
                null
            }

            val aiMessage = AIMessage(
                preText = preText,
                validationRequest = validationRequest,
                dataRequests = dataRequests,
                actions = actions,
                postText = postText,
                communicationModule = communicationModule
            )

            LogManager.aiService("Successfully parsed AIMessage: preText present, ${actions?.size ?: 0} actions, ${dataRequests?.size ?: 0} dataRequests")

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
    val tokensUsed: Int = 0
)