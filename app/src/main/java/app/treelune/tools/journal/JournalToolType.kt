package app.treelune.tools.journal

import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.tools.ToolTypeContract
import app.treelune.core.tools.BaseSchemas
import app.treelune.core.services.ExecutableService
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import app.treelune.core.validation.FieldLimits
import app.treelune.tools.journal.ui.JournalScreen
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.TextLength
import org.json.JSONObject

/**
 * Journal Tool Type implementation
 * Provides static metadata for journal tool instances
 *
 * Journal entries are timestamped entries with title and content,
 * sorted chronologically.
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

    override fun getDefaultDisplayMode(): String = "EXTENDED"


    override fun getDefaultShowFieldLabels(): Boolean = true

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(tool = "journal", context = context)
        return when (fieldName) {
            "content" -> s.tool("field_content")
            "sort_order" -> s.tool("field_sort_order")
            else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
        }
    }

    /** A journal's own setting: in which order its entries are shown. */
    override fun getConfigSettings(context: Context): List<app.treelune.core.fields.settings.SettingNode> {
        val s = Strings.`for`(tool = "journal", context = context)
        val orders = listOf("ascending", "descending")
        return listOf(app.treelune.core.fields.settings.SettingNode.Field(
            app.treelune.core.fields.FieldDefinition("sort_order", s.tool("field_sort_order"), s.tool("schema_config_sort_order"),
                app.treelune.core.fields.FieldType.CHOICE, false,
                mapOf("options" to app.treelune.core.fields.ChoiceSettings.storedOptions(orders, orders.associateWith { s.tool("sort_order_$it") }))),
            required = true,
            default = "descending"
        ))
    }

    /**
     * A journal entry: a titled, dated text. Its date is required, since the journal is read in
     * date order.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "journal", context = context)
        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.REQUIRED,
            data = listOf(
                FixedField(
                    FieldDefinition(
                        name = "content",
                        displayName = s.tool("field_content"),
                        description = s.tool("schema_data_content"),
                        type = FieldType.TEXT,
                        alwaysVisible = false,
                        config = mapOf("length" to TextLength.UNLIMITED.name)
                    )
                )
            )
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

    override fun getService(context: Context): ExecutableService {
        return app.treelune.core.services.ToolDataService(context)
    }

    override fun getDao(context: Context): Any {
        val database = app.treelune.core.database.AppDatabase.getDatabase(context)
        val baseDao = database.toolDataDao()

        // Uses generic implementation for standard journal entries
        return app.treelune.core.database.dao.DefaultExtendedToolDataDao(baseDao, "journal")
    }

    override fun getDatabaseEntities(): List<Class<*>> {
        return listOf(ToolDataEntity::class.java)
    }

    @Composable
    override fun rememberTile(tool: app.treelune.core.database.entities.ToolInstance, open: (app.treelune.core.tools.EntryToOpen) -> Unit): app.treelune.core.tools.ToolTile =
        app.treelune.tools.journal.ui.rememberJournalTile(tool, open)

    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openEntry: app.treelune.core.tools.EntryToOpen?
    ) {
        JournalScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick,
            openEntry = openEntry
        )
    }

}
