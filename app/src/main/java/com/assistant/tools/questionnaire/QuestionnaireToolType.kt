package com.assistant.tools.questionnaire

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.StateField
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.ScheduleSettings
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.services.ExecutableService
import com.assistant.core.strings.Strings
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.tools.ToolOperation
import com.assistant.core.tools.ToolScheduler
import com.assistant.core.tools.ToolTypeContract
import com.assistant.tools.questionnaire.ui.QuestionnaireScreen
import org.json.JSONObject

/**
 * A questionnaire (docs/design/missing-tools.md, « Questionnaire »): its questions are the user's
 * fields of a dated entry. What makes it a tool: the passing, one question per screen; the planned
 * invitation; the passing by the AI in a chat, which speaks the same field types.
 *
 * On demand, nothing is written before the end of the passing. Planned, at the time due the app
 * creates the entry « to fill », dated from that time, and notifies; each answer is saved as it is
 * given, and the passing resumes at the first question without one. An entry is to fill, filled or
 * ignored; ignored is a gap chosen, kept in the history.
 */
object QuestionnaireToolType : ToolTypeContract {

    object Status {
        const val TO_FILL = "to_fill"
        const val FILLED = "filled"
        const val IGNORED = "ignored"
        val ALL = listOf(TO_FILL, FILLED, IGNORED)
    }

    const val STATUS = "status"
    const val FILLED_AT = "filled_at"
    const val AI_MESSAGE = "ai_message"

    private fun s(context: Context) = Strings.`for`(tool = "questionnaire", context = context)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getDescription(context: Context): String = s(context).tool("description")
    override fun getDefaultDisplayMode(): String = "LINE"
    override fun getDefaultShowFieldLabels(): Boolean = true
    override fun getDefaultIconName(): String = "clipboard-list"
    override fun getSuggestedIcons(): List<String> = listOf("clipboard-list", "list-checks", "message-circle-question-mark", "notebook-pen")

    override fun getFormFieldName(fieldName: String, context: Context): String = when (fieldName) {
        "schedule", "enabled", AI_MESSAGE, STATUS, FILLED_AT -> s(context).tool("field_$fieldName")
        else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
    }

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        return listOf(
            ScheduleSettings.group(s.tool("field_schedule"), shared::shared),
            SettingNode.Field(FieldDefinition("enabled", s.tool("field_enabled"), s.tool("schema_enabled"), FieldType.BOOLEAN, false, null), required = true, default = true),
            SettingNode.Field(FieldDefinition(AI_MESSAGE, s.tool("field_ai_message"), s.tool("schema_ai_message"), FieldType.TEXT, false,
                mapOf("length" to TextLength.LONG.name)), required = true, default = s.tool("ai_message_default"))
        )
    }

    /** An entry: no name, the moment it is about; the user's fields are the questions; its state. */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = s(context)
        return EntryFields(
            name = CoreFieldUsage.ABSENT,
            timestamp = CoreFieldUsage.REQUIRED,
            state = listOf(
                StateField(FieldDefinition(STATUS, s.tool("field_status"), s.tool("schema_status"), FieldType.CHOICE, false,
                    mapOf("options" to ChoiceSettings.storedOptions(Status.ALL, Status.ALL.associateWith { s.tool("status_$it") }, emptyMap()))), filterable = true),
                StateField(FieldDefinition(FILLED_AT, s.tool("field_filled_at"), s.tool("schema_filled_at"), FieldType.DATETIME, false, null), filterable = false)
            )
        )
    }

    /** Filling and ignoring an entry, for the screen and the AI alike. */
    override fun getOperations(context: Context): List<ToolOperation> {
        val s = s(context)
        val id = SettingNode.Field(FieldDefinition("id", s.tool("field_entry"), null, FieldType.TEXT, false, mapOf("length" to TextLength.SHORT.name)), required = true)
        return listOf(
            ToolOperation("complete", s.tool("operation_complete"), listOf(id)),
            ToolOperation("ignore", s.tool("operation_ignore"), listOf(id)),
            ToolOperation("ignore_all", s.tool("operation_ignore_all"), emptyList())
        )
    }

    /** An invitation not filled yet waits. */
    override fun getWaiting(config: JSONObject): List<JSONObject> =
        listOf(com.assistant.core.conditions.Conditions.onField("state.$STATUS", "in", listOf(Status.TO_FILL)))

    override fun getService(context: Context): ExecutableService = QuestionnaireService(context)
    override fun getScheduler(): ToolScheduler = QuestionnaireScheduler

    override fun getDao(context: Context): Any {
        val baseDao = com.assistant.core.database.AppDatabase.getDatabase(context).toolDataDao()
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "questionnaire")
    }

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openEntry: com.assistant.core.tools.EntryToOpen?) {
        QuestionnaireScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick, openEntryId = (openEntry as? com.assistant.core.tools.EntryToOpen.Existing)?.id)
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (com.assistant.core.tools.EntryToOpen) -> Unit): com.assistant.core.tools.ToolTile =
        com.assistant.tools.questionnaire.ui.rememberQuestionnaireTile(tool)
}
