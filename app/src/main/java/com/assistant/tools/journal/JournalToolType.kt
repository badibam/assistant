package com.assistant.tools.journal

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.services.ExecutableService
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.FieldLimits
import com.assistant.tools.journal.ui.JournalConfigScreen
import com.assistant.tools.journal.ui.JournalScreen
import org.json.JSONObject

/**
 * Journal Tool Type implementation
 * Provides static metadata for journal tool instances
 *
 * Journal entries are timestamped entries with title and content,
 * sorted chronologically with audio transcription support.
 */
object JournalToolType : ToolTypeContract {

    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "journal", context = context)
        return s.tool("display_name")
    }

    override fun getDescription(context: Context): String {
        val s = Strings.`for`(tool = "journal", context = context)
        return s.tool("description")
    }

    override fun getDefaultConfig(): String {
        return """
        {
            "name": "",
            "description": "",
            "icon_name": "book-open",
            "display_mode": "EXTENDED",
            "management": "manual",
            "validateConfig": false,
            "validateData": false,
            "always_send": false,
            "sort_order": "descending"
        }
        """.trimIndent()
    }

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        return when (schemaId) {
            "journal_config" -> createJournalConfigSchema(context)
            "journal_data" -> createJournalDataSchema(context, toolInstanceId)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf("journal_config", "journal_data")
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(tool = "journal", context = context)
        return when (fieldName) {
            "content" -> s.tool("field_content")
            "sort_order" -> s.tool("field_sort_order")
            else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
        }
    }

    /**
     * Creates journal configuration schema
     * Extends base config with sort_order field
     */
    private fun createJournalConfigSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "journal", context = context)

        val specificSchema = """
        {
            "properties": {
                "sort_order": {
                    "type": "string",
                    "enum": ["ascending", "descending"],
                    "default": "descending",
                    "description": "${s.tool("schema_config_sort_order")}"
                }
            },
            "required": ["sort_order"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "journal_config",
            displayName = s.tool("schema_config_display_name"),
            description = s.tool("schema_config_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    /**
     * Creates journal data schema
     * - name: Entry title (required via BaseSchemas)
     * - timestamp: Entry date/time (modifiable, required)
     * - data.content: Text content without length limit (optional - can be filled via transcription)
     * - custom_fields: Custom fields defined in tool instance config (if toolInstanceId provided)
     */
    private fun createJournalDataSchema(context: Context, toolInstanceId: String?): Schema {
        val s = Strings.`for`(tool = "journal", context = context)

        val specificSchema = """
        {
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.tool("schema_data_name")}"
                },
                "timestamp": {
                    "type": "number",
                    "description": "${s.tool("schema_data_timestamp")}"
                },
                "data": {
                    "type": "object",
                    "description": "${s.tool("schema_data_data")}",
                    "properties": {
                        "content": {
                            "type": "string",
                            "description": "${s.tool("schema_data_content")}"
                        }
                    },
                    "required": [],
                    "additionalProperties": false
                }
            },
            "required": ["name", "timestamp", "data"]
        }
        """.trimIndent()

        // Use createExtendedDataSchema to enrich with custom fields if toolInstanceId provided
        val content = if (toolInstanceId != null) {
            BaseSchemas.createExtendedDataSchema(
                BaseSchemas.getBaseDataSchema(context),
                specificSchema,
                toolInstanceId,
                context
            )
        } else {
            BaseSchemas.createExtendedSchema(
                BaseSchemas.getBaseDataSchema(context),
                specificSchema
            )
        }

        return Schema(
            id = "journal_data",
            displayName = s.tool("schema_data_display_name"),
            description = s.tool("schema_data_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
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
        return "book-open"
    }

    override fun getSuggestedIcons(): List<String> {
        return listOf(
            "book-open",
            "notebook",
            "pen-line",
            "calendar-days",
            "heart",
            "sparkles",
            "moon"
        )
    }

    @Composable
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?,
        initialGroup: String?
    ) {
        JournalConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            onCancel = onCancel,
            existingToolId = existingToolId,
            onDelete = onDelete,
            initialGroup = initialGroup
        )
    }

    override fun getService(context: Context): ExecutableService {
        return com.assistant.core.services.ToolDataService(context)
    }

    override fun getDao(context: Context): Any {
        val database = com.assistant.core.database.AppDatabase.getDatabase(context)
        val baseDao = database.toolDataDao()

        // Uses generic implementation for standard journal entries
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "journal")
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
        JournalScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }

}
