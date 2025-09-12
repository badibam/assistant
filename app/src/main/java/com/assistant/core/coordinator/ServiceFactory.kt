package com.assistant.core.coordinator

import android.content.Context
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.ZoneService
import com.assistant.core.services.ToolInstanceService
import com.assistant.core.services.ToolDataService
import com.assistant.core.services.AppConfigService
import com.assistant.core.services.IconPreloadService
import com.assistant.core.services.BackupService
import kotlin.reflect.KClass

/**
 * Factory for creating service instances
 * Centralizes service creation logic without modifying existing services
 */
object ServiceFactory {
    
    /**
     * Create service instance from class
     */
    fun create(serviceClass: KClass<*>, context: Context): ExecutableService {
        return when (serviceClass) {
            ZoneService::class -> ZoneService(context)
            ToolInstanceService::class -> ToolInstanceService(context)
            ToolDataService::class -> ToolDataService(context)
            AppConfigService::class -> AppConfigService(context)
            IconPreloadService::class -> IconPreloadService(context)
            BackupService::class -> BackupService()
            else -> throw IllegalArgumentException("Unknown service class: ${serviceClass.simpleName}")
        }
    }
}