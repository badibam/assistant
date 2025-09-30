package com.assistant.tools.notes

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.services.ExecutableService
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.FieldLimits
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

    override fun getDescription(context: Context): String {
        val s = Strings.`for`(tool = "notes", context = context)
        return s.tool("description")
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

        return notesSpecificConfig
    }

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return when (schemaId) {
            "notes_config" -> createNotesConfigSchema(context)
            "notes_data" -> createNotesDataSchema(context)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf("notes_config", "notes_data")
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(tool = "notes", context = context)
        return when (fieldName) {
            "content" -> s.tool("field_content")
            "position" -> s.tool("field_position")
            else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
        }
    }

    private fun createNotesConfigSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "notes", context = context)

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            """
            {
                "properties": {},
                "required": []
            }
            """.trimIndent()
        )

        return Schema(
            id = "notes_config",
            displayName = s.tool("schema_config_display_name"),
            description = s.tool("schema_config_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createNotesDataSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "notes", context = context)

        val specificSchema = """
        {
            "properties": {
                "name": {
                    "type": "string",
                    "const": "Note",
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
                            "minLength": 1,
                            "maxLength": ${FieldLimits.LONG_LENGTH},
                            "description": "${s.tool("schema_data_content")}"
                        },
                        "position": {
                            "type": "integer",
                            "minimum": 0,
                            "description": "${s.tool("schema_data_position")}"
                        }
                    },
                    "required": ["content"],
                    "additionalProperties": false
                }
            },
            "required": ["name", "timestamp", "data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "notes_data",
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

}