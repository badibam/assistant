package com.assistant.core.transcription.service

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.database.AppDatabase
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.transcription.database.TranscriptionProviderConfigEntity
import com.assistant.core.transcription.providers.TranscriptionProviderRegistry
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Transcription Provider configuration service (ExecutableService)
 *
 * Responsibilities:
 * - CRUD operations for transcription provider configurations
 * - Provider activation/deactivation
 * - Configuration validation
 *
 * Available operations:
 * - transcription_provider_config.get, .set, .list, .delete
 * - transcription_provider_config.set_active, .get_active
 */
class TranscriptionProviderConfigService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    // ========================================================================================
    // ExecutableService Implementation
    // ========================================================================================

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.service("TranscriptionProviderConfigService.execute() called: $operation")

        return withContext(Dispatchers.IO) {
            try {
                when (operation) {
                    "get" -> getProviderConfig(params, token)
                    "set" -> setProviderConfig(params, token)
                    "list" -> listProviders(params, token)
                    "delete" -> deleteProviderConfig(params, token)
                    "set_active" -> setActiveProvider(params, token)
                    "get_active" -> getActiveProvider(params, token)
                    else -> {
                        LogManager.service("Unknown operation: $operation", "ERROR")
                        OperationResult.error("Unknown operation: $operation")
                    }
                }
            } catch (e: Exception) {
                LogManager.service("TranscriptionProviderConfigService error: ${e.message}", "ERROR", e)
                OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
            }
        }
    }

    // ========================================================================================
    // Operation Implementations
    // ========================================================================================

    private suspend fun getProviderConfig(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("providerId"))

        LogManager.service("Getting config for transcription provider: $providerId")

        try {
            val database = AppDatabase.getDatabase(context)
            val configEntity = database.transcriptionDao().getProviderConfig(providerId)

            if (configEntity == null) {
                LogManager.service("Transcription provider config not found: $providerId", "WARN")
                return OperationResult.error(s.shared("error_provider_not_configured").format(providerId))
            }

            return OperationResult.success(mapOf(
                "providerId" to configEntity.providerId,
                "displayName" to configEntity.displayName,
                "config" to configEntity.configJson,
                "isConfigured" to configEntity.isConfigured,
                "isActive" to configEntity.isActive,
                "createdAt" to configEntity.createdAt,
                "updatedAt" to configEntity.updatedAt
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to get transcription provider config: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    private suspend fun setProviderConfig(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("providerId"))
        val configJson = params.optString("config").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("config"))

        LogManager.service("Setting config for transcription provider: $providerId")

        try {
            // Get provider from registry to validate config
            val registry = TranscriptionProviderRegistry(context)
            val provider = registry.getProvider(providerId)
                ?: return OperationResult.error(s.shared("error_unknown_provider").format(providerId))

            // Validate configuration against provider schema
            val schema = provider.getSchema("transcription_provider_${providerId}_config", context)
                ?: return OperationResult.error(s.shared("error_schema_not_found"))

            // Parse config JSON and convert to Map
            val configData = try {
                val json = JSONObject(configJson)
                json.keys().asSequence().associateWith { json.get(it) }
            } catch (e: Exception) {
                return OperationResult.error(s.shared("error_invalid_json").format(e.message ?: ""))
            }

            val validation = SchemaValidator.validate(schema, configData, context)
            if (!validation.isValid) {
                LogManager.service("Transcription provider config validation failed: ${validation.errorMessage}", "WARN")
                return OperationResult.error(validation.errorMessage ?: s.shared("validation_error"))
            }

            // Check if config already exists
            val database = AppDatabase.getDatabase(context)
            val existingConfig = database.transcriptionDao().getProviderConfig(providerId)
            val now = System.currentTimeMillis()

            val configEntity = TranscriptionProviderConfigEntity(
                providerId = providerId,
                displayName = provider.getDisplayName(),
                configJson = configJson,
                isConfigured = true,
                isActive = existingConfig?.isActive ?: false,
                createdAt = existingConfig?.createdAt ?: now,
                updatedAt = now
            )

            database.transcriptionDao().insertProviderConfig(configEntity)

            LogManager.service("Successfully set config for transcription provider: $providerId", "INFO")

            return OperationResult.success(mapOf(
                "providerId" to providerId,
                "isConfigured" to true,
                "updatedAt" to now
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to set transcription provider config: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    private suspend fun listProviders(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.service("Listing all transcription providers")

        try {
            val database = AppDatabase.getDatabase(context)
            val configEntities = database.transcriptionDao().getAllProviderConfigs()

            // Get all available providers from registry
            val registry = TranscriptionProviderRegistry(context)
            val availableProviders = registry.getAllProviders()

            // Combine registry providers with DB configs
            val providers = availableProviders.map { provider ->
                val config = configEntities.find { it.providerId == provider.getProviderId() }

                mapOf(
                    "id" to provider.getProviderId(),
                    "displayName" to provider.getDisplayName(),
                    "isConfigured" to (config?.isConfigured ?: false),
                    "isActive" to (config?.isActive ?: false),
                    "hasConfig" to (config != null)
                )
            }

            LogManager.service("Found ${providers.size} transcription providers")

            return OperationResult.success(mapOf(
                "providers" to providers
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to list transcription providers: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    private suspend fun deleteProviderConfig(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("providerId"))

        LogManager.service("Deleting config for transcription provider: $providerId")

        try {
            val database = AppDatabase.getDatabase(context)

            // Check if provider exists
            val config = database.transcriptionDao().getProviderConfig(providerId)
            if (config == null) {
                LogManager.service("Transcription provider config not found: $providerId", "WARN")
                return OperationResult.error(s.shared("error_provider_not_configured").format(providerId))
            }

            // If provider is active, deactivate it first
            if (config.isActive) {
                LogManager.service("Provider is active, deactivating before deletion: $providerId", "INFO")
                database.transcriptionDao().deactivateAllProviders()
            }

            database.transcriptionDao().deleteProviderConfigById(providerId)

            LogManager.service("Successfully deleted transcription provider config: $providerId", "INFO")

            return OperationResult.success(mapOf(
                "providerId" to providerId,
                "deleted" to true
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to delete transcription provider config: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    private suspend fun setActiveProvider(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_param_required").format("providerId"))

        LogManager.service("Setting active transcription provider: $providerId")

        try {
            val database = AppDatabase.getDatabase(context)

            // Check if provider config exists and is configured
            val config = database.transcriptionDao().getProviderConfig(providerId)
            if (config == null) {
                LogManager.service("Transcription provider config not found: $providerId", "WARN")
                return OperationResult.error(s.shared("error_provider_not_configured").format(providerId))
            }

            if (!config.isConfigured) {
                LogManager.service("Transcription provider not configured: $providerId", "WARN")
                return OperationResult.error(s.shared("error_provider_not_configured").format(providerId))
            }

            // Deactivate all providers first
            database.transcriptionDao().deactivateAllProviders()

            // Activate the target provider
            database.transcriptionDao().activateProvider(providerId)

            LogManager.service("Successfully set active transcription provider: $providerId", "INFO")

            return OperationResult.success(mapOf(
                "activeProviderId" to providerId
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to set active transcription provider: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    private suspend fun getActiveProvider(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.service("Getting active transcription provider")

        try {
            val database = AppDatabase.getDatabase(context)
            val activeConfig = database.transcriptionDao().getActiveProviderConfig()

            if (activeConfig == null) {
                LogManager.service("No active transcription provider found", "DEBUG")
                return OperationResult.success(mapOf(
                    "hasActiveProvider" to false
                ))
            }

            return OperationResult.success(mapOf(
                "hasActiveProvider" to true,
                "activeProviderId" to activeConfig.providerId,
                "displayName" to activeConfig.displayName,
                "isConfigured" to activeConfig.isConfigured
            ))
        } catch (e: Exception) {
            LogManager.service("Failed to get active transcription provider: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_generic").format(e.message ?: ""))
        }
    }

    /**
     * Verbalize transcription provider config operation
     * Transcription provider configuration is typically not exposed to AI actions
     */
    override fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return s.shared("action_verbalize_unknown")
    }
}
