package com.assistant.core.tools

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.validation.SchemaProvider
import org.json.JSONObject

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
    
    // Schema Provider Implementation
    // SchemaProvider methods are inherited from SchemaProvider interface
    
    
    
    /**
     * List of operations this tool type supports
     */
    fun getAvailableOperations(): List<String>
    
    /**
     * Default icon for this tool type, a Lucide name: given to a tool created without one.
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
     * @param initialGroup Optional pre-selected group for new tool creation
     */
    @Composable
    fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?,
        initialGroup: String?
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
     * The fields of this tool type's entries, for a tool instance whose config is [config]:
     * how it uses name and timestamp, its fixed fields in data, and the fields of their state.
     * The user's fields come from the config's extra_fields and are not declared here.
     *
     * The data schema of the entries is generated from it (BaseSchemas.getEntrySchema), so a
     * tool type never writes that schema by hand.
     */
    fun getEntryFields(config: JSONObject, context: Context): com.assistant.core.fields.EntryFields

    /**
     * [config] once [added] have joined the options of the CHOICE field [field] this tool type
     * declares in data, for a field whose vocabulary is open. Only a tool type that declares an
     * open CHOICE in data keeps its options in its config and answers; the service asks no other.
     */
    fun configWithOptionsAdded(config: JSONObject, field: String, added: List<String>): JSONObject =
        throw IllegalStateException("${this::class.simpleName} declares no open choice in data")

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
     * Keep a rule that spans the entries of one tool instance, such as a manual order.
     *
     * ToolDataService calls it on every create, update and delete of an entry of this tool type,
     * whoever the caller — screen or AI — and stores its answer in the same transaction as the write.
     *
     * @param entries Every entry of the tool instance as it will stand once the write is done
     * @param writtenId The entry just created or updated; null after a delete
     * @return The entries whose content must change, the written one included if it must;
     *         empty when the rule already holds
     */
    fun settleEntries(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> = emptyList()

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
     * Returns the list of config fields that are relevant for interpreting data entries
     *
     * Used by CommandExecutor to build config_extract section in TOOL_DATA responses.
     * The config_extract provides context for AI to interpret data values correctly
     * (e.g., scale min/max, choice options, custom_fields definitions).
     *
     * Default implementation returns only custom_fields (relevant for all tooltypes).
     * Override to include tooltypes-specific fields needed to interpret data.
     *
     * Example (Tracking):
     * - type: to know if it's scale, numeric, choice, etc.
     * - min/max: to interpret scale values
     * - items: to know available numeric items
     * - options: to interpret choice values
     * - custom_fields: always included (field definitions)
     *
     * Note: This extract is included in EVERY TOOL_DATA response (no deduplication)
     * since config can change between queries.
     *
     * @return List of config field names to include in config_extract
     */
    fun getRelevantConfigFieldsForData(): List<String> {
        return listOf("extra_fields")  // Default: only custom_fields
    }
}