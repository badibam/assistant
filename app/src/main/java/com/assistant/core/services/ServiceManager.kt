package com.assistant.core.services

import android.content.Context

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
        return services.getOrPut(serviceName) {
            createService(serviceName)
        }
    }
    
    /**
     * Create service instances
     */
    private fun createService(serviceName: String): Any {
        return when (serviceName) {
            "zone_service" -> ZoneService(context)
            "backup_service" -> BackupService()
            // Add other services as needed
            else -> throw IllegalArgumentException("Unknown service: $serviceName")
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