package com.assistant.tools.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.utils.NumberFormatting
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.strings.Strings
import com.assistant.tools.tracking.handlers.TrackingTypeFactory
import com.assistant.tools.tracking.TrackingSchemas
import com.assistant.tools.tracking.ui.TrackingConfigScreen
import com.assistant.tools.tracking.ui.TrackingScreen
import org.json.JSONObject

/**
 * Tracking Tool Type implementation
 * Provides static metadata for tracking tool instances
 */
object TrackingToolType : ToolTypeContract {
    
    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return s.tool("display_name")
    }
    
    override fun getDefaultConfig(): String {
        val trackingSpecificConfig = """
        {
            "type": "numeric",
            "items": [],
            "name": "",
            "description": "",
            "icon_name": "activity"
        }
        """.trimIndent()
        
        return BaseSchemas.mergeDefaultConfigs(
            BaseSchemas.getBaseDefaultConfig(),
            trackingSpecificConfig
        )
    }
    
    override fun getConfigSchema(context: Context): String {
        // Combine base schema with tracking-specific schema from external object
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            TrackingSchemas.getConfigSchema(context)
        )
    }
    
    override fun getDataSchema(context: Context): String? {
        // Combine base schema with tracking-specific schema from external object
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            TrackingSchemas.getDataSchema(context)
        )
    }
    
    
    override fun getAvailableOperations(): List<String> {
        return listOf(
            "add_entry", "get_entries", "update_entry", "delete_entry", "delete_all_entries",
            "start_activity", "stop_activity", "stop_all"
        )
    }
    
    override fun getDefaultIconName(): String {
        return "activity"
    }
    
    override fun getSuggestedIcons(): List<String> {
        return listOf("activity", "trending-up")
    }
    
    @Composable
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?
    ) {
        TrackingConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            onCancel = onCancel,
            existingToolId = existingToolId,
            onDelete = onDelete
        )
    }
    
    override fun getService(context: Context): ExecutableService {
        return com.assistant.core.services.ToolDataService(context)
    }
    
    override fun getDao(context: Context): Any {
        val database = com.assistant.core.database.AppDatabase.getDatabase(context)
        val baseDao = database.toolDataDao()
        
        // Uses generic implementation which is sufficient for standard tracking
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "tracking")
    }
    
    override fun getDatabaseEntities(): List<Class<*>> {
        return listOf(ToolDataEntity::class.java)
    }
    
    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit
    ) {
        TrackingScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }
    
    override fun getDatabaseMigrations(): List<Migration> {
        return emptyList() // Migrations handled by core now
    }
    
    override fun migrateConfig(fromVersion: Int, configJson: String): String {
        return when (fromVersion) {
            1 -> {
                // Example: Config migration v1 → v2
                // val config = JSONObject(configJson)
                // config.put("new_field", "default_value")
                // config.toString()
                configJson // No migration needed for now
            }
            else -> configJson // No known migration
        }
    }
    
    // Example migrations (for future reference)
    private object Migrations {
        // Example database migration (for future reference)
        /*
        val TRACKING_MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example: Add a column
                database.execSQL("ALTER TABLE tracking_data ADD COLUMN category TEXT DEFAULT ''")
                
                // Example: Migrate data
                database.execSQL("UPDATE tracking_data SET category = 'health' WHERE tool_instance_name LIKE '%health%'")
            }
        }
        */
    }
    
    // ═══ Data Migration Capabilities ═══
    override fun getCurrentDataVersion(): Int = 2
    
    override fun upgradeDataIfNeeded(rawData: String, fromVersion: Int): String {
        return when (fromVersion) {
            1 -> upgradeV1ToV2(rawData)
            else -> rawData // No migration needed
        }
    }
    
    /**
     * Migration v1 → v2 : Add type field if missing
     */
    private fun upgradeV1ToV2(v1Data: String): String {
        return try {
            val dataJson = JSONObject(v1Data)
            // Add type field if absent (v2 requirement)
            if (!dataJson.has("type")) {
                // Infer type from existing data
                val inferredType = when {
                    dataJson.has("quantity") -> "numeric"
                    dataJson.has("items") -> "choice"
                    dataJson.has("duration") -> "timer"
                    dataJson.has("checked") -> "boolean"
                    dataJson.has("level") -> "scale"
                    else -> "text"
                }
                dataJson.put("type", inferredType)
            }
            dataJson.toString()
        } catch (e: Exception) {
            // In case of error, return original data
            v1Data
        }
    }
    
    /**
     * Get user-friendly field name for display
     * @param fieldName The technical field name (e.g., "quantity", "name")
     * @param context Android context for string resource access
     * @return User-friendly field name for display (e.g., "Quantity", "Name")
     */
    override fun getFormFieldName(fieldName: String, context: Context?): String {
        // Context should always be provided by ValidationErrorProcessor
        if (context == null) throw IllegalArgumentException("Context required for internationalized field names")
        
        val s = Strings.`for`(tool = "tracking", context = context)
        
        // Try common fields for all tooltypes first
        val commonFieldName = BaseSchemas.getCommonFieldName(fieldName, context)
        if (commonFieldName != null) return commonFieldName
        
        // Then tracking-specific fields
        return when(fieldName) {
            "default_quantity" -> s.tool("field_default_quantity")
            "quantity" -> s.tool("field_quantity")
            "unit" -> s.tool("field_unit")
            "text" -> s.tool("field_text")
            "rating" -> s.tool("field_rating")
            "min_value" -> s.tool("field_min_value")
            "max_value" -> s.tool("field_max_value")
            "min_label" -> s.tool("field_min_label")
            "max_label" -> s.tool("field_max_label")
            "state" -> s.tool("field_state")
            "true_label" -> s.tool("field_true_label")
            "false_label" -> s.tool("field_false_label")
            "selected_option" -> s.tool("field_selected_option")
            "available_options" -> s.tool("field_available_options")
            "increment" -> s.tool("field_increment")
            "activity" -> s.tool("field_activity")
            "duration_seconds" -> s.tool("field_duration_seconds")
            "type" -> s.tool("field_type")
            "raw" -> s.tool("field_raw")
            else -> s.tool("field_unknown")
        }
    }

    /**
     * Override to implement tracking-specific schema resolution
     * ARCHITECTURE CORRECTE: Seul le ToolType connaît sa logique de résolution
     */
    override fun getResolvedDataSchema(configJson: String, context: Context): String? {
        // TODO: Implement tracking-specific schema resolution
        // 1. Parse configJson to extract tracking configuration
        //    val config = parseConfig(configJson) // {"value_type": "numeric", "unit": "kg", ...}
        //
        // 2. Create data skeleton adapted to this config
        //    val dataSkeleton = when(config.value_type) {
        //        "numeric" -> mapOf("type" to "numeric", "quantity" to 0.0, "unit" to config.unit)
        //        "scale" -> mapOf("type" to "scale", "rating" to config.default_rating)
        //        "choice" -> mapOf("type" to "choice", "selected_option" to "", "available_options" to config.predefined_items)
        //        "timer" -> mapOf("type" to "timer", "duration_seconds" to 0, "activity" to "")
        //        "boolean" -> mapOf("type" to "boolean", "state" to false)
        //        "text" -> mapOf("type" to "text", "text" to "")
        //        else -> mapOf("type" to "text", "text" to "")
        //    }
        //
        // 3. Use SchemaResolver to resolve conditional schema
        //    val baseSchema = getDataSchema(context) ?: return null
        //    return SchemaResolver.resolve(baseSchema, dataSkeleton)

        // For now, return base schema as fallback
        return getDataSchema(context)
    }
}