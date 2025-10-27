package com.assistant.core.ai.providers

import android.content.Context
import com.assistant.core.utils.LogManager

/**
 * Registry for AI providers with configuration management
 * Manages provider discovery, configuration, and active provider selection
 */
class AIProviderRegistry(private val context: Context) {

    // TODO: Load providers dynamically via discovery pattern
    // Initialize providers with detailed logging for debugging Android compatibility issues
    private val providers = buildList {
        try {
            LogManager.aiService("Initializing ClaudeStandardProvider...")
            add(ClaudeStandardProvider(context))
            LogManager.aiService("ClaudeStandardProvider initialized successfully")
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize ClaudeStandardProvider: ${e.message}", "ERROR", e)
        }

        try {
            LogManager.aiService("Initializing ClaudeEconomicProvider...")
            add(ClaudeEconomicProvider(context))
            LogManager.aiService("ClaudeEconomicProvider initialized successfully")
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize ClaudeEconomicProvider: ${e.message}", "ERROR", e)
        }

        try {
            LogManager.aiService("Initializing OpenAIStandardProvider...")
            add(OpenAIStandardProvider(context))
            LogManager.aiService("OpenAIStandardProvider initialized successfully")
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize OpenAIStandardProvider: ${e.message}", "ERROR", e)
        }

        try {
            LogManager.aiService("Initializing OpenAIEconomicProvider...")
            add(OpenAIEconomicProvider(context))
            LogManager.aiService("OpenAIEconomicProvider initialized successfully")
        } catch (e: Exception) {
            LogManager.aiService("Failed to initialize OpenAIEconomicProvider: ${e.message}", "ERROR", e)
        }

        // DeepSeekProvider()
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

    /**
     * Get provider configuration
     */
    fun getProviderConfig(providerId: String): String? {
        // TODO: Load from app config or database
        LogManager.aiService("Getting provider config for: $providerId")
        return "{}" // Mock empty config for now
    }

    /**
     * Check if provider is configured
     * NO FALLBACK - must be explicitly configured via AIProviderConfigService
     */
    fun isProviderConfigured(@Suppress("UNUSED_PARAMETER") providerId: String): Boolean {
        // TODO: Load from database via coordinator
        // For now always return false - configuration must be done explicitly
        return false
    }

    /**
     * Check if provider is active
     * NO FALLBACK - must be explicitly activated via AIProviderConfigService
     */
    fun isProviderActive(providerId: String): Boolean {
        return getActiveProviderId() == providerId
    }

    /**
     * Get active provider ID
     * NO FALLBACK - returns null if no provider is configured
     */
    fun getActiveProviderId(): String? {
        // TODO: Load from app config via coordinator
        LogManager.aiService("Getting active provider ID")
        return null // NO FALLBACK - must be explicitly configured
    }

    /**
     * Set active provider
     */
    fun setActiveProvider(providerId: String): Boolean {
        LogManager.aiService("Setting active provider: $providerId")

        val provider = getProvider(providerId)
        if (provider == null) {
            LogManager.aiService("Provider not found: $providerId", "ERROR")
            return false
        }

        if (!isProviderConfigured(providerId)) {
            LogManager.aiService("Provider not configured: $providerId", "ERROR")
            return false
        }

        // TODO: Save to app config
        LogManager.aiService("Active provider set: $providerId")
        return true
    }
}