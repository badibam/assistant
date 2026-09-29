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
import com.assistant.core.services.ReadingService
import com.assistant.core.services.ReferenceService
import com.assistant.core.services.VariableService
import com.assistant.core.ai.services.AISessionService
import com.assistant.core.ai.services.AIProviderConfigService
import com.assistant.core.ai.services.AutomationService
import com.assistant.core.notifications.NotificationService
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
            BackupService::class -> BackupService(context)
            SchemaService::class -> SchemaService(context)
            IconService::class -> IconService(context)
            ReferenceService::class -> ReferenceService(context)
            ReadingService::class -> ReadingService(context)
            VariableService::class -> VariableService(context)
            AISessionService::class -> AISessionService(context)
            AIProviderConfigService::class -> AIProviderConfigService(context)
            AutomationService::class -> AutomationService(context)
            NotificationService::class -> NotificationService(context)
            else -> throw IllegalArgumentException("Unknown service class: ${serviceClass.simpleName}")
        }
    }
}