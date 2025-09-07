package com.assistant.core.services

import android.content.Context
import com.assistant.core.tools.ToolTypeManager
import android.util.Log

/**
 * Service Manager - manages core services
 * Provides access to singleton services like ZoneService, BackupService, etc.
 */
class ServiceManager(private val context: Context) {
    private val services = mutableMapOf<String, Any>()
    
    /**
     * Get a service by name
     */
    fun getService(serviceName: String): Any? {
        Log.d("ServiceManager", "Getting service: $serviceName")
        return services.getOrPut(serviceName) {
            val service = createService(serviceName)
            Log.d("ServiceManager", "Created service: $serviceName -> ${service.javaClass.simpleName}")
            service
        }
    }
    
    /**
     * Create service instances
     */
    private fun createService(serviceName: String): Any {
        return when (serviceName) {
            // Core services (hardcoded)
            "zone_service" -> ZoneService(context)
            "tool_instance_service" -> ToolInstanceService(context)
            "tool_data_service" -> ToolDataService(context)
            "backup_service" -> BackupService()
            
            // Tool services (discovered via ToolTypeManager)
            else -> {
                // Convert "tracking_service" -> "tracking"
                val toolTypeId = serviceName.removeSuffix("_service")
                Log.d("ServiceManager", "Looking for tool type: $toolTypeId")
                val service = ToolTypeManager.getServiceForToolType(toolTypeId, context)
                Log.d("ServiceManager", "ToolTypeManager returned: $service")
                service ?: throw IllegalArgumentException("Unknown service: $serviceName")
            }
        }
    }
    
    /**
     * Check if service exists
     */
    fun hasService(serviceName: String): Boolean {
        return try {
            createService(serviceName)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}