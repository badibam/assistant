package com.assistant.core.tools

import com.assistant.core.tools.base.ToolTypeContract

/**
 * ToolTypeManager with automatic discovery via runtime scanning
 * Automatically discovers all @ToolType annotated classes
 */
object ToolTypeManager {
    
    private val toolTypes: Map<String, ToolTypeContract> by lazy {
        ToolTypeScanner.scanForToolTypes()
    }
    
    fun getToolType(id: String): ToolTypeContract? = toolTypes[id]
    
    fun getToolTypeName(id: String): String = getToolType(id)?.getDisplayName() ?: "Unknown Tool"
    
    fun getAllToolTypes(): Map<String, ToolTypeContract> = toolTypes
    
    fun isValidToolType(id: String): Boolean = toolTypes.containsKey(id)
}