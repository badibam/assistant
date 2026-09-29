package com.assistant.tools.structured

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.services.ExecutableService
import com.assistant.core.strings.Strings
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.ui.DisplayMode
import com.assistant.tools.structured.ui.StructuredScreen
import com.assistant.tools.structured.ui.StructuredTile
import org.json.JSONObject

/**
 * Structured data: sheets of what the user declares (foods and their calories, books, contacts),
 * each found by its name (docs/design/missing-tools.md, « Données structurées »).
 *
 * Its columns are the user's fields (extra), declared in the instance's config; the tool type
 * declares none, and an entry has no date. The name is required and unique in the instance, the
 * case and the spaces around not counted: it is what the AI, a REFERENCE and an import find a
 * sheet by. What makes it a tool is its screen: a table, a sheet per entry, a shared filter header.
 */
object StructuredToolType : ToolTypeContract {

    /** How many of the user's fields the table shows as columns, after the name. */
    const val TABLE_COLUMNS = "table_columns"

    override fun getDisplayName(context: Context): String =
        Strings.`for`(tool = "structured", context = context).tool("display_name")

    override fun getDescription(context: Context): String =
        Strings.`for`(tool = "structured", context = context).tool("description")

    override fun getDefaultDisplayMode(): String = "LINE"

    override fun getDefaultShowFieldLabels(): Boolean = true

    override fun getFormFieldName(fieldName: String, context: Context): String = when (fieldName) {
        TABLE_COLUMNS -> Strings.`for`(tool = "structured", context = context).tool("field_table_columns")
        else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
    }

    override fun getConfigSettings(context: Context): List<com.assistant.core.fields.settings.SettingNode> {
        val s = Strings.`for`(tool = "structured", context = context)
        return listOf(com.assistant.core.fields.settings.SettingNode.Field(
            FieldDefinition(TABLE_COLUMNS, s.tool("field_table_columns"), s.tool("schema_config_table_columns"),
                FieldType.NUMERIC, false, mapOf("min" to 0, "decimals" to 0)),
            required = true,
            default = 2
        ))
    }

    /** A sheet: its name, required and unique; no date; nothing of the tool type in data. */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields = EntryFields(
        name = CoreFieldUsage.REQUIRED,
        nameUnique = true,
        timestamp = CoreFieldUsage.ABSENT
    )

    override fun getDefaultIconName(): String = "table"

    override fun getSuggestedIcons(): List<String> = listOf("table", "database", "book-open", "apple", "contact")

    override fun getService(context: Context): ExecutableService = com.assistant.core.services.ToolDataService(context)

    override fun getDao(context: Context): Any {
        val baseDao = com.assistant.core.database.AppDatabase.getDatabase(context).toolDataDao()
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "structured")
    }

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit
    ) {
        StructuredScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick)
    }

    @Composable
    override fun TileContent(tool: ToolInstance, displayMode: DisplayMode) {
        StructuredTile(tool = tool, displayMode = displayMode)
    }
}
