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
 *
 * With due dates on (docs/design/list-due-dates.md), an item may also carry a due date in its
 * data; the scheduler notifies it once when it comes and marks it in the state, and an item so
 * marked and still unchecked waits for the user.
 */
object ListToolType : ToolTypeContract {

    /** The state key of the instant an item was checked, absent while it is not. */
    const val CHECKED_AT = "checked_at"

    /** The setting under which items may carry a due date, notified when it comes. */
    const val DUE_DATES = "due_dates"

    /** The data key of an item's due date, declared only while the list has due dates. */
    const val DUE_AT = "due_at"

    /** The state key of the due date whose notification went out, written by the scheduler alone. */
    const val DUE_NOTIFIED = "due_notified"

    /**
     * Whether [config] gives its items due dates. An absent setting reads as its declared
     * default, off, which is what a list stored before the setting existed means.
     */
    fun hasDueDates(config: JSONObject): Boolean = config.optBoolean(DUE_DATES, false)

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
            DUE_AT -> s.tool("field_due_at")
            DUE_NOTIFIED -> s.tool("field_due_notified")
            DUE_DATES -> s.tool("field_due_dates")
            else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
        }
    }

    /** The setting under which checking an item deletes it, for a list whose done items have nothing left to say. */
    const val REMOVE_WHEN_CHECKED = "remove_when_checked"

    /**
     * Whether checking an item deletes it at once (shopping: what is bought goes), and whether
     * items may carry a due date. What an item carries beyond its name and its due date is the
     * user's fields, not a setting.
     */
    override fun getConfigSettings(context: Context): List<com.assistant.core.fields.settings.SettingNode> {
        val s = Strings.`for`(tool = "list", context = context)
        fun switch(name: String) = com.assistant.core.fields.settings.SettingNode.Field(
            FieldDefinition(name, s.tool("field_$name"), s.tool("schema_config_$name"), FieldType.BOOLEAN, false, null),
            required = true,
            default = false
        )
        return listOf(switch(REMOVE_WHEN_CHECKED), switch(DUE_DATES))
    }

    /**
     * An item: its name, required; the moment it was added, as any entry; in data its due date,
     * optional, when the list has due dates, and nothing otherwise. Its state holds its position,
     * which is no filter, when it was checked, which is one: "present" reads the checked items,
     * "absent" the ones left, and the due date notified, which the waiting reads.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "list", context = context)
        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.OPTIONAL,
            data = if (hasDueDates(config)) listOf(com.assistant.core.fields.FixedField(dueAtField(context))) else emptyList(),
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
                ),
                // Declared whatever the setting: a list whose due dates are turned off keeps the
                // marks already written, which nothing reads any more and the next write clears
                StateField(
                    FieldDefinition(
                        name = DUE_NOTIFIED,
                        displayName = s.tool("field_due_notified"),
                        description = s.tool("schema_data_due_notified"),
                        type = FieldType.DATETIME,
                        alwaysVisible = false,
                        config = null
                    ),
                    filterable = true
                )
            )
        )
    }

    /** An item's due date, as its data declares it and its form enters it. */
    fun dueAtField(context: Context) = FieldDefinition(
        name = DUE_AT,
        displayName = Strings.`for`(tool = "list", context = context).tool("field_due_at"),
        description = Strings.`for`(tool = "list", context = context).tool("schema_data_due_at"),
        type = FieldType.DATETIME,
        alwaysVisible = false,
        config = null
    )

    /** An item whose due date was notified and that is still not checked waits. */
    override fun getWaiting(config: JSONObject): List<JSONObject> =
        if (!hasDueDates(config)) emptyList()
        else listOf(
            com.assistant.core.conditions.Conditions.onField("state.$DUE_NOTIFIED", "present", null),
            com.assistant.core.conditions.Conditions.onField("state.$CHECKED_AT", "absent", null)
        )

    override fun getDefaultIconName(): String = "list-checks"

    override fun getSuggestedIcons(): List<String> =
        listOf("list-checks", "list-todo", "shopping-cart", "clipboard-list")

    override fun getService(context: Context): ExecutableService = ListService(context)

    override fun getOperations(context: Context): List<com.assistant.core.tools.ToolOperation> {
        val s = Strings.`for`(tool = "list", context = context)
        val id = com.assistant.core.fields.settings.SettingNode.Field(FieldDefinition("id", s.tool("field_item"), null, FieldType.TEXT, false,
            mapOf("length" to com.assistant.core.fields.TextLength.SHORT.name)), required = true)
        return listOf(
            com.assistant.core.tools.ToolOperation("check", s.tool("operation_check"), listOf(id)),
            com.assistant.core.tools.ToolOperation("uncheck", s.tool("operation_uncheck"), listOf(id))
        )
    }

    /** The manual order, then the due notice of the item written (DueNotice). */
    override suspend fun settleEntries(entries: suspend () -> List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> {
        val all = entries()
        val ordered = ManualOrder.settle(all, writtenId).associateBy { it.id }
        val written = writtenId?.let { id -> ordered[id] ?: all.find { it.id == id } } ?: return ordered.values.toList()
        val cleared = DueNotice.settle(written) ?: return ordered.values.toList()
        return (ordered + (cleared.id to cleared)).values.toList()
    }

    override fun getScheduler(): com.assistant.core.tools.ToolScheduler = ListDueScheduler

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
