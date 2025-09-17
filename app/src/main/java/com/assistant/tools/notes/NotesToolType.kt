package com.assistant.tools.notes

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.services.ExecutableService
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.strings.Strings
import com.assistant.tools.notes.ui.NotesConfigScreen
import com.assistant.tools.notes.ui.NotesScreen
import org.json.JSONObject

/**
 * Notes Tool Type implementation
 * Provides static metadata for notes tool instances
 */
object NotesToolType : ToolTypeContract {

    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "notes", context = context)
        return s.tool("display_name")
    }

    override fun getDefaultConfig(): String {
        val notesSpecificConfig = """
        {
            "name": "",
            "description": "",
            "icon_name": "note",
            "display_mode": "EXTENDED",
            "management": "manual",
            "config_validation": "disabled",
            "data_validation": "disabled"
        }
        """.trimIndent()

        return BaseSchemas.mergeDefaultConfigs(
            BaseSchemas.getBaseDefaultConfig(),
            notesSpecificConfig
        )
    }

    override fun getConfigSchema(context: Context): String {
        // Combine base schema with notes-specific schema from external object
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            NotesSchemas.getConfigSchema(context)
        )
    }

    override fun getDataSchema(context: Context): String? {
        // Combine base schema with notes-specific schema from external object
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            NotesSchemas.getDataSchema(context)
        )
    }

    override fun getAvailableOperations(): List<String> {
        return listOf(
            "add_entry",
            "get_entries",
            "update_entry",
            "delete_entry"
        )
    }

    override fun getDefaultIconName(): String {
        return "note"
    }

    override fun getSuggestedIcons(): List<String> {
        return emptyList() // Use standard icon selection
    }

    @Composable
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?
    ) {
        NotesConfigScreen(
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

        // Uses generic implementation which is sufficient for standard notes
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "notes")
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
        NotesScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }

    override fun getDatabaseMigrations(): List<Migration> {
        return emptyList() // Migrations handled by core
    }

    override fun migrateConfig(fromVersion: Int, configJson: String): String {
        return when (fromVersion) {
            1 -> {
                // Example: Config migration v1 → v2 (if needed in future)
                configJson // No migration needed for now
            }
            else -> configJson // No known migration
        }
    }

    // ═══ Data Migration Capabilities ═══
    override fun getCurrentDataVersion(): Int = 1

    override fun upgradeDataIfNeeded(rawData: String, fromVersion: Int): String {
        return when (fromVersion) {
            // No upgrades needed for v1
            else -> rawData // No migration needed
        }
    }

    /**
     * Get user-friendly field name for display
     * @param fieldName The technical field name (e.g., "content")
     * @param context Android context for string resource access
     * @return User-friendly field name for display (e.g., "Contenu")
     */
    override fun getFormFieldName(fieldName: String, context: Context?): String {
        // Context should always be provided by ValidationErrorProcessor
        if (context == null) throw IllegalArgumentException("Context required for internationalized field names")

        val s = Strings.`for`(tool = "notes", context = context)

        // Try common fields for all tooltypes first
        val commonFieldName = BaseSchemas.getCommonFieldName(fieldName, context)
        if (commonFieldName != null) return commonFieldName

        // Then notes-specific fields
        return when(fieldName) {
            "content" -> s.tool("field_content")
            "position" -> s.tool("field_position")
            else -> s.tool("field_unknown")
        }
    }
}