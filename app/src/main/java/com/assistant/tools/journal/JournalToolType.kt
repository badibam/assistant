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
import com.assistant.tools.journal.ui.JournalScreen
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FixedField
import com.assistant.core.fields.TextLength
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
    override fun getConfigSettings(context: Context): List<com.assistant.core.fields.settings.SettingNode> {
        val s = Strings.`for`(tool = "journal", context = context)
        val orders = listOf("ascending", "descending")
        return listOf(com.assistant.core.fields.settings.SettingNode.Field(
            com.assistant.core.fields.FieldDefinition("sort_order", s.tool("field_sort_order"), s.tool("schema_config_sort_order"),
                com.assistant.core.fields.FieldType.CHOICE, false,
                mapOf("options" to com.assistant.core.fields.ChoiceSettings.storedOptions(orders, orders.associateWith { s.tool("sort_order_$it") }))),
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
        onLongClick: () -> Unit,
        openEntry: com.assistant.core.tools.EntryToOpen?
    ) {
        JournalScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }

}
