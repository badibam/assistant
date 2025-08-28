package com.assistant.core.validation

import com.assistant.core.tools.ToolTypeManager

/**
 * Helper for centralized validation in UI components
 * Provides consistent validation patterns across the application
 * Respects discovery architecture - no hard-coded tool types
 */
object ValidationHelper {
    
    /**
     * Validate any tool data using centralized ToolType validation
     * Uses discovery pattern via ToolTypeManager
     * @param toolTypeName The tool type identifier (discovered dynamically)
     * @param data The data object to validate  
     * @param operation The operation being performed (create, update, delete)
     */
    suspend fun validateToolData(
        toolTypeName: String,
        data: Any, 
        operation: String
    ): ValidationResult {
        val toolType = ToolTypeManager.getToolType(toolTypeName)
        return toolType?.validateData(data, operation) 
            ?: ValidationResult.error("ToolType '$toolTypeName' not found")
    }
}