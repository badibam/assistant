package com.assistant.tools.notes

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
import com.assistant.tools.notes.ui.NotesConfigScreen
import com.assistant.tools.notes.ui.NotesScreen
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FixedField
import com.assistant.core.fields.StateField
import com.assistant.core.fields.TextLength
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
            "icon_name": "sticky-note",
            "display_mode": "EXTENDED",
            "management": "manual",
            "validate_config": false,
            "validate_data": false,
            "always_send": false
        }
        """.trimIndent()

        return notesSpecificConfig
    }

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        return when (schemaId) {
            "notes_config" -> com.assistant.core.tools.ToolConfigSettings.schema(this, schemaId, context)
            "notes_data" -> createNotesDataSchema(context, toolInstanceId)
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

    /** Notes have no setting of their own. */
    override fun getConfigSettings(context: Context): List<com.assistant.core.fields.settings.SettingNode> = emptyList()

    /**
     * The data schema of notes, generated from their declared fields.
     */
    private fun createNotesDataSchema(context: Context, toolInstanceId: String?): Schema {
        val s = Strings.`for`(tool = "notes", context = context)
        return Schema(
            id = "notes_data",
            displayName = s.tool("schema_data_display_name"),
            description = s.tool("schema_data_description"),
            category = SchemaCategory.TOOL_DATA,
            content = BaseSchemas.getEntrySchema(this, toolInstanceId, context)
        )
    }

    /**
     * A note: a text, without a name (none would say anything the text does not), kept in a
     * manual order. Its position is state: the app writes it when a note is moved, and the
     * service keeps the order (NoteOrder).
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "notes", context = context)
        return EntryFields(
            name = CoreFieldUsage.ABSENT,
            timestamp = CoreFieldUsage.OPTIONAL,
            data = listOf(
                FixedField(
                    FieldDefinition(
                        name = "content",
                        displayName = s.tool("field_content"),
                        description = s.tool("schema_data_content"),
                        type = FieldType.TEXT,
                        alwaysVisible = false,
                        config = mapOf("length" to TextLength.LONG.name)
                    ),
                    required = true
                )
            ),
            state = listOf(
                StateField(
                    FieldDefinition(
                        name = "position",
                        displayName = s.tool("field_position"),
                        description = s.tool("schema_data_position"),
                        type = FieldType.NUMERIC,
                        alwaysVisible = false,
                        config = mapOf("min" to 0, "decimals" to 0)
                    ),
                    filterable = false
                )
            )
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
        return "sticky-note"
    }

    override fun getSuggestedIcons(): List<String> {
        return listOf("sticky-note", "notepad-text", "notebook-pen")
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
        NotesConfigScreen(
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

    override fun settleEntries(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> =
        NoteOrder.settle(entries, writtenId)

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