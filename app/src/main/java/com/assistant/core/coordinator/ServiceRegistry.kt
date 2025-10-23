package com.assistant.core.coordinator

import android.content.Context
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.ZoneService
import com.assistant.core.services.ToolInstanceService
import com.assistant.core.services.ToolDataService
import com.assistant.core.services.AppConfigService
import com.assistant.core.services.IconPreloadService
import com.assistant.core.services.BackupService
import com.assistant.core.services.SchemaService
import com.assistant.core.ai.services.AISessionService
import com.assistant.core.ai.services.AIProviderConfigService
import com.assistant.core.ai.services.AutomationService
import com.assistant.core.transcription.service.TranscriptionProviderConfigService
import com.assistant.core.transcription.service.TranscriptionService
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
import kotlin.reflect.KClass

/**
 * Registry for mapping resource names to services
 * Supports both core services and dynamic tool discovery
 */
class ServiceRegistry(private val context: Context) {
    
    // Core services mapping
    private val coreServices = mapOf<String, KClass<*>>(
        "zones" to ZoneService::class,
        "tools" to ToolInstanceService::class,
        "tool_data" to ToolDataService::class,
        "app_config" to AppConfigService::class,
        "icon_preload" to IconPreloadService::class,
        "backup" to BackupService::class,
        "schemas" to SchemaService::class,
        "ai_sessions" to AISessionService::class,
        "ai_provider_config" to AIProviderConfigService::class,
        "automations" to AutomationService::class,
        "transcription_provider_config" to TranscriptionProviderConfigService::class,
        "transcription" to TranscriptionService::class
    )
    
    /**
     * Get service instance for resource name
     * @param resource Resource name (e.g. "zones", "tracking", "journal")
     * @return ExecutableService instance or null if not found
     */
    fun getService(resource: String): ExecutableService? {
        return try {
            // Try core services first
            coreServices[resource]?.let { serviceClass ->
                ServiceFactory.create(serviceClass, context)
            }
            // Try tool services via discovery
            ?: ToolTypeManager.getServiceForToolType(resource, context)
        } catch (e: Exception) {
            LogManager.service("Failed to get service for resource: $resource", "WARN", e)
            null
        }
    }
    
    /**
     * Check if service exists for resource
     */
    fun hasService(resource: String): Boolean {
        return coreServices.containsKey(resource) || 
               ToolTypeManager.isValidToolType(resource)
    }
    
    /**
     * Get all available resource names
     */
    fun getAllResources(): Set<String> {
        return coreServices.keys + ToolTypeManager.getAllToolTypes().keys
    }
}