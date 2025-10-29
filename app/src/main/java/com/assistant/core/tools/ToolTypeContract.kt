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
     * Description of this tool type explaining its purpose and usage
     * @param context Android context for string resource access
     */
    fun getDescription(context: Context): String

    /**
     * Default configuration JSON for new instances of this tool type
     */
    fun getDefaultConfig(): String
    
    // ═══ Schema Provider Implementation ═══
    // SchemaProvider methods are inherited from SchemaProvider interface
    
    
    
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
     * Enrich data before storage by calculating derived/auto-generated fields
     * This method is called by ToolDataService before inserting data into database
     *
     * Common use cases:
     * - Calculate display-friendly "raw" field from structured data
     * - Add computed fields based on configuration
     * - Normalize data format
     *
     * Important: Any field marked as auto-generated should be:
     * - Removed if explicitly provided in dataJson (ignored)
     * - Recalculated by this method
     * - Documented as auto-generated in schema descriptions
     * - NOT included in validation schemas (not user-provided)
     *
     * @param dataJson The data JSON to enrich
     * @param name The entry name (optional, may be used in calculations)
     * @param configJson The tool instance configuration JSON (optional, may be needed for calculations)
     * @return Enriched data JSON with auto-generated fields added
     * @throws Exception if enrichment fails (will cause the create operation to fail)
     */
    fun enrichData(dataJson: String, name: String?, configJson: String?): String = dataJson

    /**
     * Get scheduler instance for this tool type.
     *
     * Discovery pattern: CoreScheduler discovers schedulers via ToolTypeManager,
     * tools with scheduling needs return a ToolScheduler instance.
     *
     * @return ToolScheduler instance if this tool requires periodic scheduling, null otherwise
     */
    fun getScheduler(): ToolScheduler? = null

    /**
     * Indicates if this tool type supports execution history tracking
     *
     * Tools with executions maintain history in tool_executions table:
     * - Messages: scheduled message deliveries
     * - Goals: periodic goal evaluations
     * - Alerts: threshold checks and notifications
     * - Questionnaires: scheduled form submissions
     *
     * Used by ZoneScopeSelector to show EXECUTIONS context option.
     *
     * @return true if this tool type stores execution history, false otherwise (default)
     */
    fun supportsExecutions(): Boolean = false
}