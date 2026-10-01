package com.assistant.core.coordinator

import android.content.Context
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.ZoneService
import com.assistant.core.services.ToolInstanceService
import com.assistant.core.services.ToolDataService
import com.assistant.core.services.AppConfigService
import com.assistant.core.services.BackupService
import com.assistant.core.services.SchemaService
import com.assistant.core.services.IconService
import com.assistant.core.services.ImportService
import com.assistant.core.services.FileService
import com.assistant.core.services.ReadingService
import com.assistant.core.services.ReferenceService
import com.assistant.core.services.VariableService
import com.assistant.core.ai.services.AISessionService
import com.assistant.core.ai.services.AIProviderConfigService
import com.assistant.core.ai.services.AutomationService
import com.assistant.core.notifications.NotificationService
import com.assistant.core.demo.DemoService
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager

/**
 * Registry for mapping resource names to services
 * Supports both core services and dynamic tool discovery
 */
class ServiceRegistry(private val context: Context) {
    
    // The core's services, each by its resource: what creates it, the one place a service is named
    private val coreServices = mapOf<String, (Context) -> ExecutableService>(
        "zones" to ::ZoneService,
        "tools" to ::ToolInstanceService,
        "tool_data" to ::ToolDataService,
        "app_config" to ::AppConfigService,
        "backup" to ::BackupService,
        "schemas" to ::SchemaService,
        "icons" to ::IconService,
        "references" to ::ReferenceService,
        "readings" to ::ReadingService,
        "variables" to ::VariableService,
        "imports" to ::ImportService,
        "files" to ::FileService,
        "ai_sessions" to ::AISessionService,
        "ai_provider_config" to ::AIProviderConfigService,
        "automations" to ::AutomationService,
        "notifications" to ::NotificationService,
        "demo" to ::DemoService
    )
    
    /**
     * Get service instance for resource name
     * @param resource Resource name (e.g. "zones", "tracking", "journal")
     * @return ExecutableService instance or null if not found
     */
    fun getService(resource: String): ExecutableService? {
        return try {
            // Try core services first
            coreServices[resource]?.invoke(context)
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