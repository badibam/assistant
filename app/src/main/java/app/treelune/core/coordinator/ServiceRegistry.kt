package app.treelune.core.coordinator

import android.content.Context
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.ZoneService
import app.treelune.core.services.ToolInstanceService
import app.treelune.core.services.ToolDataService
import app.treelune.core.services.AppConfigService
import app.treelune.core.services.BackupService
import app.treelune.core.services.SchemaService
import app.treelune.core.services.IconService
import app.treelune.core.services.ImportService
import app.treelune.core.services.FileService
import app.treelune.core.services.ReadingService
import app.treelune.core.services.ReferenceService
import app.treelune.core.services.VariableService
import app.treelune.core.ai.services.AISessionService
import app.treelune.core.ai.services.AIProviderConfigService
import app.treelune.core.ai.services.AutomationService
import app.treelune.core.notifications.NotificationService
import app.treelune.core.demo.DemoService
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.LogManager

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