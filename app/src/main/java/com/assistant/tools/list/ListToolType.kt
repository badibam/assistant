package com.assistant.tools.list

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.StateField
import com.assistant.core.services.ExecutableService
import com.assistant.core.strings.Strings
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.tools.ManualOrder
import com.assistant.core.tools.ToolTypeContract
import com.assistant.tools.list.ui.ListScreen
import org.json.JSONObject

/**
 * A list: the present state of what is left to do — shopping, tasks, a checklist — without
 * a history of what was done, which is a tracking's role (docs/design/missing-tools.md, "Liste").
 *
 * An item is a name, the list's own fields, and two things in its state: its place in the
 * manual order, kept by the service (ManualOrder), and when it was checked. Checked is having a
 * date; unchecking removes it.
 */
object ListToolType : ToolTypeContract {

    /** The state key of the instant an item was checked, absent while it is not. */
    const val CHECKED_AT = "checked_at"

    override fun getDisplayName(context: Context): String =
        Strings.`for`(tool = "list", context = context).tool("display_name")

    override fun getDescription(context: Context): String =
        Strings.`for`(tool = "list", context = context).tool("description")

    override fun getDefaultDisplayMode(): String = "EXTENDED"


    override fun getDefaultShowFieldLabels(): Boolean = false

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(tool = "list", context = context)
        return when (fieldName) {
            // An item's name is what it says: its content, on screen and in an error
            "name" -> s.tool("field_content")
            ManualOrder.POSITION -> s.tool("field_position")
            CHECKED_AT -> s.tool("field_checked_at")
            else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
        }
    }

    /** The setting under which checking an item deletes it, for a list whose done items have nothing left to say. */
    const val REMOVE_WHEN_CHECKED = "remove_when_checked"

    /**
     * Whether checking an item deletes it at once (shopping: what is bought goes). What an item
     * carries beyond its name is the user's fields, not a setting.
     */
    override fun getConfigSettings(context: Context): List<com.assistant.core.fields.settings.SettingNode> {
        val s = Strings.`for`(tool = "list", context = context)
        return listOf(com.assistant.core.fields.settings.SettingNode.Field(
            FieldDefinition(REMOVE_WHEN_CHECKED, s.tool("field_remove_when_checked"), s.tool("schema_config_remove_when_checked"),
                FieldType.BOOLEAN, false, null),
            required = true,
            default = false
        ))
    }

    /**
     * An item: its name, required; the moment it was added, as any entry; nothing of the tool
     * type in data. Its state holds its position, which is no filter, and when it was checked,
     * which is one: "present" reads the checked items, "absent" the ones left.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "list", context = context)
        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.OPTIONAL,
            state = listOf(
                StateField(
                    FieldDefinition(
                        name = ManualOrder.POSITION,
                        displayName = s.tool("field_position"),
                        description = s.tool("schema_data_position"),
                        type = FieldType.NUMERIC,
                        alwaysVisible = false,
                        config = mapOf("min" to 0, "decimals" to 0)
                    ),
                    filterable = false
                ),
                StateField(
                    FieldDefinition(
                        name = CHECKED_AT,
                        displayName = s.tool("field_checked_at"),
                        description = s.tool("schema_data_checked_at"),
                        type = FieldType.DATETIME,
                        alwaysVisible = false,
                        config = null
                    ),
                    filterable = true
                )
            )
        )
    }

    override fun getDefaultIconName(): String = "list-checks"

    override fun getSuggestedIcons(): List<String> =
        listOf("list-checks", "list-todo", "shopping-cart", "clipboard-list")

    override fun getService(context: Context): ExecutableService =
        com.assistant.core.services.ToolDataService(context)

    override fun settleEntries(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> =
        ManualOrder.settle(entries, writtenId)

    override fun getDao(context: Context): Any {
        val baseDao = com.assistant.core.database.AppDatabase.getDatabase(context).toolDataDao()
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "list")
    }

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openEntry: com.assistant.core.tools.EntryToOpen?
    ) {
        ListScreen(
            toolInstanceId = toolInstanceId,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (com.assistant.core.tools.EntryToOpen) -> Unit): com.assistant.core.tools.ToolTile =
        com.assistant.tools.list.ui.rememberListTile(tool)
}
