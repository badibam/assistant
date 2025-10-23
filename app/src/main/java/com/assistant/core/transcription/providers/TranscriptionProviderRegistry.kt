package com.assistant.core.transcription.providers

import android.content.Context
import com.assistant.core.database.AppDatabase
import com.assistant.core.transcription.providers.vosk.VoskProvider
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registry for transcription providers
 * Similar to AIProviderRegistry pattern
 *
 * Responsibilities:
 * - Discover and register available transcription providers
 * - Provide access to providers by ID
 * - Manage active provider selection
 */
class TranscriptionProviderRegistry(private val context: Context) {

    private val providers: List<TranscriptionProvider> = listOf(
        VoskProvider(context)
        // Future: WhisperLocalProvider, WhisperCloudProvider, etc.
    )

    /**
     * Get a specific provider by ID
     *
     * @param providerId Provider identifier
     * @return Provider instance or null if not found
     */
    fun getProvider(providerId: String): TranscriptionProvider? {
        val provider = providers.find { it.getProviderId() == providerId }
        if (provider == null) {
            LogManager.service("Transcription provider not found: $providerId", "WARN")
        }
        return provider
    }

    /**
     * Get all available providers
     *
     * @return List of all registered providers
     */
    fun getAllProviders(): List<TranscriptionProvider> {
        LogManager.service("Getting all transcription providers (count: ${providers.size})")
        return providers
    }

    /**
     * Get the currently active provider from database
     *
     * @return Active provider instance or null if none active
     */
    suspend fun getActiveProvider(): TranscriptionProvider? {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(context)
                val activeConfig = database.transcriptionDao().getActiveProviderConfig()

                if (activeConfig == null) {
                    LogManager.service("No active transcription provider configured", "DEBUG")
                    return@withContext null
                }

                val provider = getProvider(activeConfig.providerId)
                if (provider == null) {
                    LogManager.service(
                        "Active provider ID ${activeConfig.providerId} not found in registry",
                        "ERROR"
                    )
                }

                provider
            } catch (e: Exception) {
                LogManager.service("Failed to get active transcription provider: ${e.message}", "ERROR", e)
                null
            }
        }
    }

    /**
     * Set the active provider by ID
     * Provider must be configured before activation
     *
     * @param providerId Provider ID to activate
     * @return true if activation successful
     */
    suspend fun setActiveProvider(providerId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(context)
                val config = database.transcriptionDao().getProviderConfig(providerId)

                if (config == null) {
                    LogManager.service("Cannot activate unconfigured provider: $providerId", "WARN")
                    return@withContext false
                }

                if (!config.isConfigured) {
                    LogManager.service("Cannot activate provider with incomplete config: $providerId", "WARN")
                    return@withContext false
                }

                // Deactivate all providers first
                database.transcriptionDao().deactivateAllProviders()

                // Activate target provider
                database.transcriptionDao().activateProvider(providerId)

                LogManager.service("Successfully activated transcription provider: $providerId", "INFO")
                true
            } catch (e: Exception) {
                LogManager.service("Failed to set active transcription provider: ${e.message}", "ERROR", e)
                false
            }
        }
    }
}
