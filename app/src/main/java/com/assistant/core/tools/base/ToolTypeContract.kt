package com.assistant.core.tools.base

import android.content.Context
import androidx.compose.runtime.Composable

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
    
    /**
     * Configuration screen for this tool type
     * @param zoneId ID of the zone where the tool will be created
     * @param onSave Called when configuration is saved with the config JSON
     * @param onCancel Called when configuration is cancelled
     * @param existingConfig Optional existing configuration JSON for editing
     */
    @Composable
    fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingConfig: String?
    )
    
    /**
     * Create service instance for this tool type
     * Returns null if this tool type doesn't have an associated service
     * @param context Android context for service creation
     */
    fun getService(context: Context): Any?
    
    /**
     * Create DAO instance for this tool type
     * Returns null if this tool type doesn't have an associated DAO
     * @param context Android context for DAO creation
     */
    fun getDao(context: Context): Any?
    
    /**
     * Get database entities for this tool type
     * Used for Room database setup via discovery
     * @return List of entity classes for this tool type
     */
    fun getDatabaseEntities(): List<Class<*>>
}