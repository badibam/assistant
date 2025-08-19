package com.assistant.core.tools

import com.assistant.core.tools.base.ToolTypeContract
import com.assistant.tools.tracking.TrackingToolType

/**
 * Manual ToolTypeManager for now
 * TODO: Replace with generated version from annotation processor
 */
object ToolTypeManager {
    
    private val toolTypes = mapOf<String, ToolTypeContract>(
        "tracking" to TrackingToolType
    )
    
    fun getToolType(id: String): ToolTypeContract? = toolTypes[id]
    
    fun getToolTypeName(id: String): String = getToolType(id)?.getDisplayName() ?: "Unknown Tool"
    
    fun getAllToolTypes(): Map<String, ToolTypeContract> = toolTypes
    
    fun isValidToolType(id: String): Boolean = toolTypes.containsKey(id)
}