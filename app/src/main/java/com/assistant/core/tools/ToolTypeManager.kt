package com.assistant.core.tools

import android.content.Context
import com.assistant.core.tools.base.ToolTypeContract
import com.assistant.core.services.ExecutableService
import android.util.Log

/**
 * ToolTypeManager with automatic discovery via runtime scanning
 * Automatically discovers all @ToolType annotated classes
 */
object ToolTypeManager {
    
    private val toolTypes: Map<String, ToolTypeContract> by lazy {
        val types = ToolTypeScanner.scanForToolTypes()
        Log.d("ToolTypeManager", "Loaded tool types: ${types.keys}")
        types
    }
    
    fun getToolType(id: String): ToolTypeContract? = toolTypes[id]
    
    fun getToolTypeName(id: String): String = getToolType(id)?.getDisplayName() ?: "Unknown Tool"
    
    fun getAllToolTypes(): Map<String, ToolTypeContract> = toolTypes
    
    fun isValidToolType(id: String): Boolean = toolTypes.containsKey(id)
    
    /**
     * Get service instance for a tool type
     * @param toolTypeId Tool type identifier (e.g., "tracking")
     * @param context Android context for service creation
     * @return Service instance or null if tool type doesn't have a service
     */
    fun getServiceForToolType(toolTypeId: String, context: Context): ExecutableService? {
        Log.d("ToolTypeManager", "getServiceForToolType: $toolTypeId")
        val toolType = getToolType(toolTypeId)
        Log.d("ToolTypeManager", "Found tool type: $toolType")
        val service = toolType?.getService(context)
        Log.d("ToolTypeManager", "Created service: $service")
        return service
    }
    
    /**
     * Get DAO instance for a tool type
     * @param toolTypeId Tool type identifier (e.g., "tracking")
     * @param context Android context for DAO creation
     * @return DAO instance or null if tool type doesn't have a DAO
     */
    fun getDaoForToolType(toolTypeId: String, context: Context): Any? {
        return getToolType(toolTypeId)?.getDao(context)
    }
    
    /**
     * Get all database entities from discovered tool types
     * Used for Room database setup via discovery
     * @return List of all entity classes from all tool types
     */
    fun getAllDatabaseEntities(): List<Class<*>> {
        return toolTypes.values.flatMap { it.getDatabaseEntities() }
    }
}