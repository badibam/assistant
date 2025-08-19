package com.assistant.core.tools.base

/**
 * Contract for tool type implementations
 * Defines the mandatory static metadata that each tool type must provide
 */
interface ToolTypeContract {
    
    /**
     * Human-readable display name for this tool type
     */
    fun getDisplayName(): String
    
    /**
     * Default configuration JSON for new instances of this tool type
     */
    fun getDefaultConfig(): String
    
    /**
     * JSON Schema describing the configuration structure for this tool type
     * Used for AI validation and UI generation
     */
    fun getConfigSchema(): String
    
    /**
     * List of operations this tool type supports
     */
    fun getAvailableOperations(): List<String>
}