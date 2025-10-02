package com.assistant.core.ai.services

import android.content.Context
import com.assistant.core.ai.database.AIProviderConfigEntity
import com.assistant.core.ai.providers.AIProviderRegistry
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.database.AppDatabase
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.SchemaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI Provider configuration service (ExecutableService)
 *
 * Responsibilities:
 * - CRUD operations for AI provider configurations
 * - Provider activation/deactivation
 * - Configuration validation
 *
 * Available operations:
 * - ai_provider_config.get, .set, .list, .delete
 * - ai_provider_config.set_active, .get_active
 */
class AIProviderConfigService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    // ========================================================================================
    // ExecutableService Implementation
    // ========================================================================================

    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.aiService("AIProviderConfigService.execute() called: $operation")

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
                        LogManager.aiService("Unknown operation: $operation", "ERROR")
                        OperationResult.error("Unknown operation: $operation")
                    }
                }
            } catch (e: Exception) {
                LogManager.aiService("AIProviderConfigService error: ${e.message}", "ERROR", e)
                OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
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
            ?: return OperationResult.error(s.shared("ai_error_param_provider_id_required"))

        LogManager.aiService("Getting config for provider: $providerId")

        try {
            val database = AppDatabase.getDatabase(context)
            val configEntity = database.aiDao().getProviderConfig(providerId)

            if (configEntity == null) {
                LogManager.aiService("Provider config not found: $providerId", "WARN")
                return OperationResult.error(s.shared("ai_error_provider_not_configured").format(providerId))
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
            LogManager.aiService("Failed to get provider config: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
        }
    }

    private suspend fun setProviderConfig(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_provider_id_required"))
        val configJson = params.optString("config").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_config_required"))

        LogManager.aiService("Setting config for provider: $providerId")

        try {
            // Get provider from registry to validate config
            val registry = AIProviderRegistry(context)
            val provider = registry.getProvider(providerId)
                ?: return OperationResult.error(s.shared("ai_error_unknown_provider").format(providerId))

            // Validate configuration against provider schema
            val schema = provider.getSchema("ai_provider_${providerId}_config", context)
                ?: return OperationResult.error(s.shared("ai_error_schema_not_found"))

            // Parse config JSON and convert to Map
            val configData = try {
                val json = JSONObject(configJson)
                json.keys().asSequence().associateWith { json.get(it) }
            } catch (e: Exception) {
                return OperationResult.error(s.shared("ai_error_invalid_json").format(e.message ?: ""))
            }

            val validation = SchemaValidator.validate(schema, configData, context)
            if (!validation.isValid) {
                LogManager.aiService("Provider config validation failed: ${validation.errorMessage}", "WARN")
                return OperationResult.error(validation.errorMessage ?: s.shared("validation_error"))
            }

            // Check if config already exists
            val database = AppDatabase.getDatabase(context)
            val existingConfig = database.aiDao().getProviderConfig(providerId)
            val now = System.currentTimeMillis()

            val configEntity = AIProviderConfigEntity(
                providerId = providerId,
                displayName = provider.getDisplayName(),
                configJson = configJson,
                isConfigured = true,
                isActive = existingConfig?.isActive ?: false,
                createdAt = existingConfig?.createdAt ?: now,
                updatedAt = now
            )

            database.aiDao().insertProviderConfig(configEntity)

            LogManager.aiService("Successfully set config for provider: $providerId", "INFO")

            return OperationResult.success(mapOf(
                "providerId" to providerId,
                "isConfigured" to true,
                "updatedAt" to now
            ))
        } catch (e: Exception) {
            LogManager.aiService("Failed to set provider config: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
        }
    }

    private suspend fun listProviders(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.aiService("Listing all providers")

        try {
            val database = AppDatabase.getDatabase(context)
            val configEntities = database.aiDao().getAllProviderConfigs()

            // Get all available providers from registry
            val registry = AIProviderRegistry(context)
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

            LogManager.aiService("Found ${providers.size} providers")

            return OperationResult.success(mapOf(
                "providers" to providers
            ))
        } catch (e: Exception) {
            LogManager.aiService("Failed to list providers: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
        }
    }

    private suspend fun deleteProviderConfig(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_provider_id_required"))

        LogManager.aiService("Deleting config for provider: $providerId")

        try {
            val database = AppDatabase.getDatabase(context)

            // Check if provider exists
            val config = database.aiDao().getProviderConfig(providerId)
            if (config == null) {
                LogManager.aiService("Provider config not found: $providerId", "WARN")
                return OperationResult.error(s.shared("ai_error_provider_not_configured").format(providerId))
            }

            // If provider is active, deactivate it first
            if (config.isActive) {
                LogManager.aiService("Provider is active, deactivating before deletion: $providerId", "INFO")
                database.aiDao().deactivateAllProviders()
            }

            database.aiDao().deleteProviderConfigById(providerId)

            LogManager.aiService("Successfully deleted provider config: $providerId", "INFO")

            return OperationResult.success(mapOf(
                "providerId" to providerId,
                "deleted" to true
            ))
        } catch (e: Exception) {
            LogManager.aiService("Failed to delete provider config: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
        }
    }

    private suspend fun setActiveProvider(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        val providerId = params.optString("providerId").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_provider_id_required"))

        LogManager.aiService("Setting active provider: $providerId")

        try {
            val database = AppDatabase.getDatabase(context)

            // Check if provider config exists and is configured
            val config = database.aiDao().getProviderConfig(providerId)
            if (config == null) {
                LogManager.aiService("Provider config not found: $providerId", "WARN")
                return OperationResult.error(s.shared("ai_error_provider_not_configured").format(providerId))
            }

            if (!config.isConfigured) {
                LogManager.aiService("Provider not configured: $providerId", "WARN")
                return OperationResult.error(s.shared("ai_error_provider_not_configured").format(providerId))
            }

            // Deactivate all providers first
            database.aiDao().deactivateAllProviders()

            // Activate the target provider
            database.aiDao().activateProvider(providerId)

            LogManager.aiService("Successfully set active provider: $providerId", "INFO")

            return OperationResult.success(mapOf(
                "activeProviderId" to providerId
            ))
        } catch (e: Exception) {
            LogManager.aiService("Failed to set active provider: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
        }
    }

    private suspend fun getActiveProvider(
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        LogManager.aiService("Getting active provider")

        try {
            val database = AppDatabase.getDatabase(context)
            val activeConfig = database.aiDao().getActiveProviderConfig()

            if (activeConfig == null) {
                LogManager.aiService("No active provider found", "DEBUG")
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
            LogManager.aiService("Failed to get active provider: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_provider_config").format(e.message ?: ""))
        }
    }
}