package com.assistant.tools.goal

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FixedField
import com.assistant.core.fields.StateField
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.ScheduleSettings
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.services.ExecutableService
import com.assistant.core.strings.Strings
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.tools.ToolOperation
import com.assistant.core.tools.ToolScheduler
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.ui.DisplayMode
import com.assistant.tools.goal.ui.GoalScreen
import com.assistant.tools.goal.ui.GoalTile
import org.json.JSONObject

/**
 * A goal (docs/design/missing-tools.md, « Objectif »): an entry is an attempt over a period, opened
 * active, filled in along the period, validated as succeeded or failed — or expired, left
 * unvalidated. A one-off goal has one attempt; a recurring one an attempt per period, opened by
 * its schedule.
 *
 * The definition lives in the config (criteria, sub-goals, at least N), and each attempt keeps a
 * copy: changing the goal does not change how past attempts were judged. An entered criterion is
 * a field of the attempt, written as any field; a read one is read over the attempt's period. A
 * validated or expired attempt is locked: only reopening it changes it.
 */
object GoalToolType : ToolTypeContract {

    /** An attempt's life. */
    object Status {
        const val ACTIVE = "active"
        const val TO_VALIDATE = "to_validate"
        const val SUCCEEDED = "succeeded"
        const val FAILED = "failed"
        const val EXPIRED = "expired"
        val ALL = listOf(ACTIVE, TO_VALIDATE, SUCCEEDED, FAILED, EXPIRED)
        val LOCKED = setOf(SUCCEEDED, FAILED, EXPIRED)
    }

    // State keys of an attempt
    const val STATUS = "status"
    const val PERIOD_END = "period_end"
    const val JUDGEMENT = "judgement"
    const val VALIDATED_BY = "validated_by"
    const val VALIDATED_AT = "validated_at"
    const val REOPENED_AT = "reopened_at"
    const val NOTIFIED = "notified"

    /** The data key of the definition's copy, a JSON text. */
    const val DEFINITION = "definition"

    /** Seven days, the default delay before an attempt left to validate expires. */
    const val DEFAULT_EXPIRY = 7L * 86_400_000

    private fun s(context: Context) = Strings.`for`(tool = "goal", context = context)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getDescription(context: Context): String = s(context).tool("description")
    override fun getDefaultDisplayMode(): String = "LINE"
    override fun getDefaultShowFieldLabels(): Boolean = true
    override fun getDefaultIconName(): String = "target"
    override fun getSuggestedIcons(): List<String> = listOf("target", "trophy", "flag", "medal")

    /** The settings and fields a validation error may name, by their key. */
    private val LABELS = setOf(
        "criteria", "sub_goals", "at_least", "start", "deadline", "schedule", "duration", "enabled", "expiry_delay", "notify",
        "essential", "kind", "variable", "tool", "field", "reduction", "op", "target", "target_unit", "unit", "instructions",
        DEFINITION, STATUS, PERIOD_END, JUDGEMENT, VALIDATED_BY, VALIDATED_AT, REOPENED_AT, NOTIFIED
    )

    override fun getFormFieldName(fieldName: String, context: Context): String = when (fieldName) {
        in LABELS -> s(context).tool("field_$fieldName")
        else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
    }

    private fun field(name: String, label: String, type: FieldType, description: String? = null, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    private fun choice(values: List<String>, labels: (String) -> String): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, values.associateWith(labels), emptyMap()))

    /** The settings of one criterion: its name, whether it is essential, its value and condition. */
    private fun criterionNodes(context: Context): List<SettingNode> {
        val s = s(context)
        val ops = listOf("<", "<=", "=", ">=", ">")
        fun op() = SettingNode.Field(field("op", s.tool("field_op"), FieldType.CHOICE, config = choice(ops) { it }), required = true, default = ">=")
        fun target() = SettingNode.Field(field("target", s.tool("field_target"), FieldType.NUMERIC, config = mapOf("decimals" to 2)), required = true)
        fun targetUnit() = SettingNode.Field(field("target_unit", s.tool("field_target_unit"), FieldType.CHOICE, s.tool("schema_target_unit"),
            choice(TargetUnit.entries.map { it.name }) { s.tool("target_unit_${it.lowercase()}") }), default = TargetUnit.NUMBER.name)
        fun instructions() = SettingNode.Field(field("instructions", s.tool("field_instructions"), FieldType.TEXT, s.tool("schema_instructions"), mapOf("length" to TextLength.MEDIUM.name)))
        return listOf(
            // Written by the app when the criterion is created (completeConfig), sent back unchanged
            SettingNode.Field(field("key", "key", FieldType.TEXT, s.tool("schema_key"), mapOf("length" to TextLength.SHORT.name)), systemWritten = true),
            SettingNode.Field(field("name", s.shared("label_name"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), required = true),
            SettingNode.Field(field("essential", s.tool("field_essential"), FieldType.BOOLEAN, s.tool("schema_essential")), default = false),
            SettingNode.Variant(
                selector = SettingNode.Field(field("kind", s.tool("field_kind"), FieldType.CHOICE, s.tool("schema_kind"),
                    choice(CriterionKind.entries.map { it.name }) { s.tool("kind_${it.lowercase()}") }), required = true),
                cases = mapOf(
                    CriterionKind.VARIABLE.name to listOf(
                        SettingNode.Field(field("variable", s.tool("field_variable"), FieldType.REFERENCE, s.tool("schema_variable"),
                            mapOf("target" to mapOf("kinds" to listOf(ReferenceKind.VARIABLE.name)))), required = true),
                        op(), target(), targetUnit()
                    ),
                    CriterionKind.FIELD.name to listOf(
                        SettingNode.Field(field("tool", s.tool("field_tool"), FieldType.REFERENCE, s.tool("schema_tool"),
                            mapOf("target" to mapOf("kinds" to listOf(ReferenceKind.TOOL_INSTANCE.name)))), required = true),
                        SettingNode.Field(field("field", s.tool("field_field"), FieldType.TEXT, s.tool("schema_field"), mapOf("length" to TextLength.SHORT.name)), required = true, fieldOf = "tool"),
                        SettingNode.Field(field("reduction", s.tool("field_reduction"), FieldType.CHOICE,
                            config = choice(Reduction.entries.map { it.name }) { s.shared("reduction_${it.lowercase()}") }), required = true, default = Reduction.LAST.name),
                        op(), target(), targetUnit()
                    ),
                    CriterionKind.ENTERED_BOOLEAN.name to listOf(
                        SettingNode.Field(field("target", s.tool("field_expected"), FieldType.BOOLEAN), required = true, default = true),
                        instructions()
                    ),
                    CriterionKind.ENTERED_NUMBER.name to listOf(
                        SettingNode.Field(field("unit", s.shared("field_config_unit"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name))),
                        op(), target(), instructions()
                    ),
                    CriterionKind.ENTERED_DURATION.name to listOf(
                        op(), SettingNode.Field(field("target", s.tool("field_target"), FieldType.DURATION), required = true), instructions()
                    ),
                    CriterionKind.ENTERED_SCALE.name to listOf(op(), target(), instructions())
                )
            )
        )
    }

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        val criteria = SettingNode.ListOf("criteria", s.tool("field_criteria"), SettingNode.Item.Of(criterionNodes(context)), summary = listOf("name", "kind"))
        return listOf(
            SettingNode.Section(s.tool("section_definition"), listOf(
                criteria,
                SettingNode.ListOf("sub_goals", s.tool("field_sub_goals"), SettingNode.Item.Of(listOf(
                    SettingNode.Field(field("name", shared.shared("label_name"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), required = true),
                    SettingNode.Field(field("at_least", s.tool("field_at_least"), FieldType.NUMERIC, s.tool("schema_at_least"), mapOf("min" to 0, "decimals" to 0))),
                    SettingNode.Field(field("essential", s.tool("field_essential"), FieldType.BOOLEAN, s.tool("schema_essential")), default = false),
                    criteria
                )), summary = listOf("name")),
                SettingNode.Field(field("at_least", s.tool("field_at_least"), FieldType.NUMERIC, s.tool("schema_at_least"), mapOf("min" to 0, "decimals" to 0)))
            )),
            SettingNode.Section(s.tool("section_time"), listOf(
                SettingNode.Field(field("start", s.tool("field_start"), FieldType.DATETIME, s.tool("schema_start"))),
                SettingNode.Field(field("deadline", s.tool("field_deadline"), FieldType.DATETIME, s.tool("schema_deadline"))),
                ScheduleSettings.group(s.tool("field_schedule"), shared::shared),
                SettingNode.Field(field("duration", s.tool("field_duration"), FieldType.DURATION, s.tool("schema_duration"))),
                SettingNode.Field(field("enabled", s.tool("field_enabled"), FieldType.BOOLEAN, s.tool("schema_enabled")), required = true, default = true),
                SettingNode.Field(field("expiry_delay", s.tool("field_expiry_delay"), FieldType.DURATION, s.tool("schema_expiry_delay")), required = true, default = DEFAULT_EXPIRY),
                SettingNode.Field(field("notify", s.tool("field_notify"), FieldType.BOOLEAN, s.tool("schema_notify")), required = true, default = true)
            ))
        )
    }

    /**
     * An attempt: the goal's name, the start of its period; in data the definition's copy and a
     * field per entered criterion of the current definition; in state its life, the end of its
     * period, and once validated its judgement, frozen.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = s(context)
        val entered = GoalDefinition.of(config).allCriteria.mapNotNull { it.enteredField() }
        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.REQUIRED,
            data = listOf(FixedField(field(DEFINITION, s.tool("field_definition"), FieldType.TEXT, s.tool("schema_definition"),
                mapOf("length" to TextLength.UNLIMITED.name)), systemWritten = true)) + entered.map { FixedField(it) },
            state = listOf(
                StateField(field(STATUS, s.tool("field_status"), FieldType.CHOICE, s.tool("schema_status"),
                    choice(Status.ALL) { s.tool("status_$it") }), filterable = true),
                StateField(field(PERIOD_END, s.tool("field_period_end"), FieldType.DATETIME, s.tool("schema_period_end")), filterable = true),
                StateField(field(JUDGEMENT, s.tool("field_judgement"), FieldType.TEXT, s.tool("schema_judgement"), mapOf("length" to TextLength.UNLIMITED.name)), filterable = false),
                StateField(field(VALIDATED_BY, s.tool("field_validated_by"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), filterable = false),
                StateField(field(VALIDATED_AT, s.tool("field_validated_at"), FieldType.DATETIME), filterable = false),
                StateField(field(REOPENED_AT, s.tool("field_reopened_at"), FieldType.DATETIME), filterable = false),
                StateField(field(NOTIFIED, s.tool("field_notified"), FieldType.BOOLEAN), filterable = false)
            )
        )
    }

    /**
     * Validating and reopening an attempt, for the screen and the AI alike. Who validated is the
     * call's origin (currentOrigin): a person or the AI, never a scheduler.
     */
    override fun getOperations(context: Context): List<ToolOperation> {
        val s = s(context)
        val id = SettingNode.Field(field("id", s.tool("field_attempt"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), required = true)
        return listOf(
            ToolOperation("validate", s.tool("operation_validate"), listOf(id)),
            ToolOperation("reopen", s.tool("operation_reopen"), listOf(id))
        )
    }

    /**
     * Each criterion without a key gets one, from its name and apart from the keys already taken;
     * a key given is kept, so renaming a criterion keeps its values.
     */
    override fun completeConfig(config: JSONObject, previous: JSONObject?): JSONObject {
        val completed = JSONObject(config.toString())
        val criteria = listOfNotNull(completed.optJSONArray("criteria")) +
            (completed.optJSONArray("sub_goals")?.let { subs -> (0 until subs.length()).mapNotNull { subs.getJSONObject(it).optJSONArray("criteria") } } ?: emptyList())
        val all = criteria.flatMap { array -> (0 until array.length()).map { array.getJSONObject(it) } }
        val taken = all.mapNotNull { it.optString("key").takeIf { k -> k.isNotEmpty() } }.toMutableSet()
        all.filter { it.optString("key").isEmpty() }.forEach { criterion ->
            val base = Criterion.keyOf(criterion.optString("name"))
            var key = base
            var n = 2
            while (key in taken) key = "${base}_${n++}"
            taken.add(key)
            criterion.put("key", key)
        }
        return completed
    }

    /** A validated or expired attempt is locked: only its own operation, reopening it, changes it. */
    override fun refuseChange(entry: ToolDataEntity, context: Context): String? =
        s(context).tool("error_locked").takeIf { statusOf(entry) in Status.LOCKED }

    fun statusOf(entry: ToolDataEntity): String? = entry.state?.takeIf { it.isNotBlank() }?.let { JSONObject(it).optString(STATUS) }

    override fun getService(context: Context): ExecutableService = GoalService(context)

    override fun getScheduler(): ToolScheduler = GoalScheduler

    override fun getDao(context: Context): Any {
        val baseDao = com.assistant.core.database.AppDatabase.getDatabase(context).toolDataDao()
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "goal")
    }

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit) {
        GoalScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick)
    }

    @Composable
    override fun TileContent(tool: ToolInstance, displayMode: DisplayMode) {
        GoalTile(tool = tool, displayMode = displayMode)
    }
}
