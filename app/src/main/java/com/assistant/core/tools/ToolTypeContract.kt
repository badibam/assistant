package com.assistant.core.tools

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.validation.SchemaProvider

/**
 * Contract for tool type implementations
 * Defines the mandatory static metadata that each tool type must provide
 * Extends SchemaProvider for unified form validation across the app
 * Includes data migration capabilities for autonomous data upgrades
 */
interface ToolTypeContract : SchemaProvider {
    
    /**
     * Human-readable display name for this tool type
     * @param context Android context for string resource access
     */
    fun getDisplayName(context: Context): String
    
    /**
     * Default configuration JSON for new instances of this tool type
     */
    fun getDefaultConfig(): String
    
    // ═══ Schema Provider Implementation ═══

    /**
     * Override getSchema to delegate to specific methods for ToolTypes
     */
    override fun getSchema(schemaType: String, context: Context): String? = when(schemaType) {
        "config" -> getConfigSchema(context)
        "data" -> getDataSchema(context)
        else -> null
    }
    
    /**
     * Configuration schema for this tool type (REQUIRED)
     * @param context Android context for string resource access
     * @return JSON Schema string for configuration validation
     */
    fun getConfigSchema(context: Context): String
    
    /**
     * Data schema for this tool type (OPTIONAL)
     * @param context Android context for string resource access
     * @return JSON Schema string for data validation, or null if no data schema
     */
    fun getDataSchema(context: Context): String? = null
    
    
    /**
     * List of operations this tool type supports
     */
    fun getAvailableOperations(): List<String>
    
    /**
     * Default icon name for this tool type (corresponds to SVG file name in themes)
     */
    fun getDefaultIconName(): String
    
    /**
     * Suggested icon names for this tool type (shown first in icon selector)
     * @return List of icon IDs that make sense for this tool type
     */
    fun getSuggestedIcons(): List<String> = emptyList()
    
    /**
     * Configuration screen for this tool type
     * @param zoneId ID of the zone where the tool will be created
     * @param onSave Called when configuration is saved with the config JSON
     * @param onCancel Called when configuration is cancelled
     * @param existingToolId Optional existing tool ID for editing mode
     * @param onDelete Optional delete callback for editing mode
     */
    @Composable
    fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?
    )
    
    /**
     * Create service instance for this tool type
     * Returns null if this tool type doesn't have an associated service
     * @param context Android context for service creation
     */
    fun getService(context: Context): ExecutableService?
    
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
    
    /**
     * Usage screen for this tool type
     * @param toolInstanceId ID of the tool instance
     * @param configJson Configuration JSON of the tool instance
     * @param onNavigateBack Called when user wants to navigate back
     * @param onLongClick Called when user long-clicks for configuration access
     */
    @Composable
    fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit
    )
    
    /**
     * Get database migrations for this tool type
     * Returns empty list if no migrations are needed
     * Migrations should be ordered by version (startVersion ascending)
     * @return List of Room Migration objects for this tool type
     */
    fun getDatabaseMigrations(): List<Migration>
    
    /**
     * Migrate configuration JSON from old version to new version
     * Used when tool type configuration schema changes
     * @param fromVersion Version of the configuration to migrate from
     * @param configJson Current configuration JSON string
     * @return Migrated configuration JSON string, or original if no migration needed
     */
    fun migrateConfig(fromVersion: Int, configJson: String): String
    
    // ═══ Data Migration Capabilities (from ToolTypeDataContract) ═══
    
    /**
     * Current data version for this tooltype
     * @return Numeric version (e.g., 1, 2, 3...)
     */
    fun getCurrentDataVersion(): Int = 1
    
    /**
     * Upgrades data if necessary
     * Called at startup for each obsolete data entry
     * 
     * @param rawData Existing JSON data 
     * @param fromVersion Current data version
     * @return JSON data upgraded to getCurrentDataVersion()
     */
    fun upgradeDataIfNeeded(rawData: String, fromVersion: Int): String = rawData
}