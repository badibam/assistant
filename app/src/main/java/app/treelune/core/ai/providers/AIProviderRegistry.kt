package app.treelune.core.ai.providers

import android.content.Context
import app.treelune.core.utils.LogManager

/**
 * Registry for AI providers with configuration management
 * Manages provider discovery, configuration, and active provider selection
 */
class AIProviderRegistry(private val context: Context) {

    // TODO: Load providers dynamically via discovery pattern
    // A provider that fails to build is logged and left out; the others stay available
    private val providers = buildList {
        try {
            add(ClaudeStandardProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize ClaudeStandardProvider: ${e.message}", "ERROR", e)
        }

        try {
            add(ClaudeEconomicProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize ClaudeEconomicProvider: ${e.message}", "ERROR", e)
        }

        try {
            add(OpenAIStandardProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize OpenAIStandardProvider: ${e.message}", "ERROR", e)
        }

        try {
            add(OpenAIEconomicProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize OpenAIEconomicProvider: ${e.message}", "ERROR", e)
        }

        try {
            add(DeepSeekStandardProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize DeepSeekStandardProvider: ${e.message}", "ERROR", e)
        }

        try {
            add(DeepSeekEconomicProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize DeepSeekEconomicProvider: ${e.message}", "ERROR", e)
        }

        try {
            add(OpenAICompatibleStandardProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize OpenAICompatibleStandardProvider: ${e.message}", "ERROR", e)
        }

        try {
            add(OpenAICompatibleEconomicProvider(context))
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize OpenAICompatibleEconomicProvider: ${e.message}", "ERROR", e)
        }
    }

    /**
     * Get provider by ID
     */
    fun getProvider(providerId: String): AIProvider? {
        return providers.find { it.getProviderId() == providerId }
    }

    /**
     * Get all available providers
     */
    fun getAllProviders(): List<AIProvider> {
        return providers
    }
}
