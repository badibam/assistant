package com.assistant.core.tools

import android.content.Context

/**
 * Unified tool type manager with automatic discovery
 * Provides access to tool type metadata without instantiation
 * Handles: names, default configs, available operations
 */
object ToolTypeManager {
    
    private var toolTypeRegistry: Map<String, ToolTypeInfo> = emptyMap()
    private var initialized = false
    
    /**
     * Metadata about a discovered tool type
     */
    data class ToolTypeInfo(
        val toolTypeId: String,
        val displayName: String,
        val defaultConfig: String,
        val availableOperations: List<String>
    )
    
    /**
     * Initialize by discovering all available tool types
     */
    fun initialize(context: Context) {
        if (!initialized) {
            toolTypeRegistry = discoverToolTypes()
            initialized = true
        }
    }
    
    /**
     * Get tool type display name for UI
     */
    fun getToolTypeName(toolTypeId: String): String {
        return toolTypeRegistry[toolTypeId]?.displayName 
            ?: toolTypeId.replaceFirstChar { it.uppercase() }
    }
    
    /**
     * Get all available tool types and their display names
     */
    fun getAllToolTypeNames(): Map<String, String> {
        return toolTypeRegistry.mapValues { it.value.displayName }
    }
    
    /**
     * Get default configuration for a tool type
     */
    fun getDefaultConfig(toolTypeId: String): String? {
        return toolTypeRegistry[toolTypeId]?.defaultConfig
    }
    
    /**
     * Get available operations for a tool type
     */
    fun getAvailableOperations(toolTypeId: String): List<String> {
        return toolTypeRegistry[toolTypeId]?.availableOperations ?: emptyList()
    }
    
    /**
     * Get complete tool type information
     */
    fun getToolTypeInfo(toolTypeId: String): ToolTypeInfo? {
        return toolTypeRegistry[toolTypeId]
    }
    
    /**
     * Check if a tool type exists
     */
    fun hasToolType(toolTypeId: String): Boolean {
        return toolTypeRegistry.containsKey(toolTypeId)
    }
    
    /**
     * Get all discovered tool type IDs
     */
    fun getAvailableToolTypes(): List<String> {
        return toolTypeRegistry.keys.toList()
    }
    
    /**
     * Force reload - useful for development
     */
    fun refresh(context: Context) {
        initialized = false
        initialize(context)
    }
    
    private fun discoverToolTypes(): Map<String, ToolTypeInfo> {
        val discovered = mutableMapOf<String, ToolTypeInfo>()
        
        // TODO: Implement automatic discovery
        // - Scan com.assistant.tools.* packages using reflection
        // - Find classes that extend ToolType
        // - Call their static companion methods for metadata
        // - Register them automatically without hardcoded imports
        
        // MOCK: Manual registration until automatic discovery is implemented
        discovered["tracking"] = ToolTypeInfo(
            toolTypeId = "tracking",
            displayName = "Suivi",
            defaultConfig = """
                {
                    "name": "",
                    "type": "numeric",
                    "show_value": true,
                    "item_mode": "free",
                    "save_new_items": false,
                    "default_unit": "",
                    "min_value": null,
                    "max_value": null,
                    "groups": [
                        {
                            "name": "Default",
                            "items": []
                        }
                    ]
                }
            """.trimIndent(),
            availableOperations = listOf("add_entry", "get_entries", "update_entry", "delete_entry")
        )
        
        return discovered
    }
}