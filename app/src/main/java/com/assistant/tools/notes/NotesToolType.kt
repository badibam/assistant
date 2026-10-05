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

    override fun getDefaultDisplayMode(): String = "EXTENDED"


    override fun getDefaultShowFieldLabels(): Boolean = true

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(tool = "notes", context = context)
        return when (fieldName) {
            "content" -> s.tool("field_content")
            "position" -> s.tool("field_position")
            else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
        }
    }

    /** The config key of whether notes take a title. */
    const val TITLES = "titles"

    /** Whether notes take a title, off unless the user turns it on. */
    override fun getConfigSettings(context: Context): List<com.assistant.core.fields.settings.SettingNode> {
        val s = Strings.`for`(tool = "notes", context = context)
        return listOf(
            com.assistant.core.fields.settings.SettingNode.Field(
                FieldDefinition(TITLES, s.tool("field_titles"), s.tool("schema_config_titles"), FieldType.BOOLEAN, false, null),
                default = false
            )
        )
    }

    /** Whether the notes of a tool whose config is [config] take a title. */
    fun hasTitles(config: JSONObject, context: Context): Boolean =
        com.assistant.core.tools.ToolConfigSettings.read(this, config, context).boolean(TITLES)

    /**
     * A note: a text, kept in a manual order, with a title when the config says so -- the
     * entry's name, optional. Without titles the name is absent: the titles written while they
     * were on stay stored, unseen, and show again when they are turned back on. Its position is
     * state: the app writes it when a note is moved, and the service keeps the order
     * (ManualOrder).
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "notes", context = context)
        return EntryFields(
            name = if (hasTitles(config, context)) CoreFieldUsage.OPTIONAL else CoreFieldUsage.ABSENT,
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

    override fun getDefaultIconName(): String {
        return "sticky-note"
    }

    override fun getSuggestedIcons(): List<String> {
        return listOf("sticky-note", "notepad-text", "notebook-pen")
    }

    override fun getService(context: Context): ExecutableService {
        return com.assistant.core.services.ToolDataService(context)
    }

    override suspend fun settleEntries(entries: suspend () -> List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> =
        com.assistant.core.tools.ManualOrder.settle(entries(), writtenId)

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
    override fun rememberTile(tool: com.assistant.core.database.entities.ToolInstance, open: (com.assistant.core.tools.EntryToOpen) -> Unit): com.assistant.core.tools.ToolTile =
        com.assistant.tools.notes.ui.rememberNotesTile(tool, open)

    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openEntry: com.assistant.core.tools.EntryToOpen?
    ) {
        NotesScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick,
            openEntry = openEntry
        )
    }

}