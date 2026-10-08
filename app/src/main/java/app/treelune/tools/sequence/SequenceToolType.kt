package app.treelune.tools.sequence

import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.conditions.Conditions
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.tools.BaseSchemas
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolOperation
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.tools.ToolTile
import app.treelune.core.tools.ToolTypeContract
import app.treelune.tools.sequence.ui.SequenceScreen
import app.treelune.tools.sequence.ui.rememberSequenceTile
import org.json.JSONObject

/**
 * A session (docs/design/sequence-tool.md): a run of steps prepared ahead, then done following
 * the screen, a timer or « Done » per step, a signal at each change. It guides; it measures
 * nothing. An entry says that a session took place: when, how long, how far.
 *
 * The config holds the steps (SequencePlan), the signals and an optional schedule. An entry runs
 * while the session does: its state keeps the run (SequenceRun), and its data what is left of it
 * once over, written by the session's operations alone.
 */
object SequenceToolType : ToolTypeContract {

    object Status {
        const val PLANNED = "planned"
        const val RUNNING = "running"
        const val DONE = "done"
        const val STOPPED = "stopped"
        const val IGNORED = "ignored"
        val ALL = listOf(PLANNED, RUNNING, DONE, STOPPED, IGNORED)
    }

    /** The signal at each change of step. */
    object StepSignal {
        const val SOUND_AND_VIBRATION = "sound_and_vibration"
        const val SOUND = "sound"
        const val VIBRATION = "vibration"
        const val NONE = "none"
        val ALL = listOf(SOUND_AND_VIBRATION, SOUND, VIBRATION, NONE)
    }

    // Config
    const val STEP_SIGNAL = "step_signal"
    const val COUNTDOWN = "countdown"
    const val VOICE = "voice"
    const val ENABLED = "enabled"

    // Entry: state
    const val STATUS = "status"
    const val STARTED_AT = "started_at"
    const val ENDED_AT = "ended_at"
    const val RUN = "run"

    // Entry: data
    const val DURATION = "duration"
    const val PAUSED = "paused"
    const val STEPS_DONE = "steps_done"
    const val STEPS_SKIPPED = "steps_skipped"
    const val STEPS_NOT_DONE = "steps_not_done"

    private fun s(context: Context) = Strings.`for`(tool = "sequence", context = context)

    private fun field(name: String, label: String, type: FieldType, description: String? = null, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    private fun choice(values: List<String>, labels: (String) -> String): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, values.associateWith(labels), emptyMap()))

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getDescription(context: Context): String = s(context).tool("description")
    override fun getDefaultDisplayMode(): String = "LINE"
    override fun getDefaultShowFieldLabels(): Boolean = true
    override fun getDefaultIconName(): String = "timer"
    override fun getSuggestedIcons(): List<String> = listOf("timer", "dumbbell", "flower-2", "list-ordered", "hourglass", "chef-hat", "music")

    override fun getFormFieldName(fieldName: String, context: Context): String = when (fieldName) {
        SequencePlan.STEPS, STEP_SIGNAL, COUNTDOWN, VOICE, ENABLED, "schedule",
        STATUS, STARTED_AT, ENDED_AT, RUN, DURATION, PAUSED, STEPS_DONE, STEPS_SKIPPED, STEPS_NOT_DONE -> s(context).tool("field_$fieldName")
        else -> BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName
    }

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        return listOf(
            SettingNode.Section(s.tool("section_steps"), listOf(steps(s::tool))),
            SettingNode.Section(s.tool("section_signals"), listOf(
                SettingNode.Field(field(STEP_SIGNAL, s.tool("field_step_signal"), FieldType.CHOICE, s.tool("schema_step_signal"),
                    choice(StepSignal.ALL) { s.tool("step_signal_$it") }), required = true, default = StepSignal.SOUND_AND_VIBRATION),
                SettingNode.Field(field(COUNTDOWN, s.tool("field_countdown"), FieldType.BOOLEAN, s.tool("schema_countdown")), required = true, default = true),
                SettingNode.Field(field(VOICE, s.tool("field_voice"), FieldType.BOOLEAN, s.tool("schema_voice")), required = true, default = false)
            )),
            SettingNode.Section(s.tool("section_schedule"), listOf(
                ScheduleSettings.group(s.tool("field_schedule"), shared::shared),
                SettingNode.Field(field(ENABLED, s.tool("field_enabled"), FieldType.BOOLEAN, s.tool("schema_enabled")), required = true, default = true)
            ))
        )
    }

    /** The steps of a session, as its config holds them under "steps"; [text] gives the tool's strings. */
    fun steps(text: (String) -> String): SettingNode.ListOf = stepList(text, depth = 0)

    /**
     * The list of elements at [depth]: steps or blocks, the blocks of a block holding steps only,
     * stored without their kind (SequencePlan).
     */
    private fun stepList(text: (String) -> String, depth: Int): SettingNode.ListOf {
        val item = if (depth == SequencePlan.MAX_DEPTH) stepNodes(text, inBlock = true) else listOf(
            SettingNode.Variant(
                selector = SettingNode.Field(field(SequencePlan.KIND, text("field_kind"), FieldType.CHOICE, text("schema_kind"),
                    choice(listOf(SequencePlan.KIND_STEP, SequencePlan.KIND_BLOCK)) { text("kind_$it") }), required = true, default = SequencePlan.KIND_STEP),
                cases = mapOf(
                    SequencePlan.KIND_STEP to stepNodes(text, inBlock = depth > 0),
                    SequencePlan.KIND_BLOCK to listOf(
                        SettingNode.Field(field(SequencePlan.NAME, text("field_block_name"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), required = true),
                        SettingNode.Field(field(SequencePlan.REPEAT, text("field_repeat"), FieldType.NUMERIC, text("schema_repeat"), mapOf("min" to 1, "decimals" to 0)), required = true, default = 2),
                        stepList(text, depth + 1)
                    )
                )
            )
        )
        return SettingNode.ListOf(SequencePlan.STEPS, text("field_steps"), SettingNode.Item.Of(item), required = true, minItems = 1, summary = listOf(SequencePlan.NAME))
    }

    /** A step: its name, its instruction, how it ends; in a block, whether its last round leaves it out. */
    private fun stepNodes(text: (String) -> String, inBlock: Boolean): List<SettingNode> {
        val duration = listOf(SettingNode.Field(field(SequencePlan.DURATION, text("field_step_duration"), FieldType.DURATION), required = true))
        return listOfNotNull(
            SettingNode.Field(field(SequencePlan.NAME, text("field_step_name"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), required = true),
            SettingNode.Field(field(SequencePlan.INSTRUCTION, text("field_instruction"), FieldType.TEXT, text("schema_instruction"), mapOf("length" to TextLength.MEDIUM.name))),
            SettingNode.Variant(
                selector = SettingNode.Field(field(SequencePlan.END, text("field_end"), FieldType.CHOICE, text("schema_end"),
                    choice(StepEnd.KEYS) { text("end_$it") }), required = true, default = StepEnd.MANUAL.key),
                cases = mapOf(StepEnd.TIMED.key to duration, StepEnd.TIMED_THEN_MANUAL.key to duration, StepEnd.MANUAL.key to emptyList())
            ),
            if (inBlock) SettingNode.Field(field(SequencePlan.SKIP_LAST_ROUND, text("field_skip_last_round"), FieldType.BOOLEAN, text("schema_skip_last_round")), default = false) else null
        )
    }

    /**
     * An entry: no name, the moment the session started or was planned for; in data what is left
     * of it once over, in state its life and, while it runs, the run itself. All are written by
     * the session's operations alone.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = s(context)
        fun data(name: String, type: FieldType, config: Map<String, Any>? = null) =
            FixedField(field(name, s.tool("field_$name"), type, s.tool("schema_$name"), config), systemWritten = true)
        val count = mapOf("min" to 0, "decimals" to 0)
        return EntryFields(
            name = CoreFieldUsage.ABSENT,
            timestamp = CoreFieldUsage.REQUIRED,
            data = listOf(
                data(DURATION, FieldType.DURATION),
                data(PAUSED, FieldType.DURATION),
                data(STEPS_DONE, FieldType.NUMERIC, count),
                data(STEPS_SKIPPED, FieldType.NUMERIC, count),
                data(STEPS_NOT_DONE, FieldType.NUMERIC, count)
            ),
            state = listOf(
                StateField(field(STATUS, s.tool("field_status"), FieldType.CHOICE, s.tool("schema_status"), choice(Status.ALL) { s.tool("status_$it") }), filterable = true),
                StateField(field(STARTED_AT, s.tool("field_started_at"), FieldType.DATETIME), filterable = false),
                StateField(field(ENDED_AT, s.tool("field_ended_at"), FieldType.DATETIME), filterable = false),
                StateField(field(RUN, s.tool("field_run"), FieldType.TEXT, s.tool("schema_run"), mapOf("length" to TextLength.UNLIMITED.name)), filterable = false)
            ),
            // A session is planned, started or noted by the tool's own operations
            start = app.treelune.core.fields.EntryStart(emptyList(), refusal = s.tool("start_refused"))
        )
    }

    /**
     * What the AI may do: mark a session done, a planned one or one that was not, correct one
     * over, ignore a planned one. Starting and every gesture of a session running belong to
     * whoever does it: the screen and the notification call them, the AI is never offered them
     * (SequenceService).
     */
    override fun getOperations(context: Context): List<ToolOperation> {
        val s = s(context)
        val id = SettingNode.Field(field("id", s.tool("field_entry"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), required = true)
        val count = mapOf("min" to 0, "decimals" to 0)
        return listOf(
            ToolOperation(SequenceService.COMPLETE, s.tool("operation_complete"), listOf(
                SettingNode.Field(field("id", s.tool("field_entry"), FieldType.TEXT, s.tool("schema_complete_id"), mapOf("length" to TextLength.SHORT.name))),
                SettingNode.Field(field("timestamp", s.tool("field_when"), FieldType.DATETIME, s.tool("schema_complete_timestamp"))),
                SettingNode.Field(field(DURATION, s.tool("field_duration"), FieldType.DURATION, s.tool("schema_complete_duration")))
            )),
            ToolOperation(SequenceService.CORRECT, s.tool("operation_correct"), listOf(
                id,
                SettingNode.Field(field("timestamp", s.tool("field_when"), FieldType.DATETIME)),
                SettingNode.Field(field(DURATION, s.tool("field_duration"), FieldType.DURATION)),
                SettingNode.Field(field(STEPS_DONE, s.tool("field_steps_done"), FieldType.NUMERIC, config = count)),
                SettingNode.Field(field(STEPS_SKIPPED, s.tool("field_steps_skipped"), FieldType.NUMERIC, config = count)),
                SettingNode.Field(field(STEPS_NOT_DONE, s.tool("field_steps_not_done"), FieldType.NUMERIC, config = count))
            )),
            ToolOperation(SequenceService.IGNORE, s.tool("operation_ignore"), listOf(id)),
            ToolOperation(SequenceService.IGNORE_ALL, s.tool("operation_ignore_all"), emptyList())
        )
    }

    /** A planned session not done yet waits. */
    override fun getWaiting(config: JSONObject): List<JSONObject> =
        listOf(Conditions.onField("state.$STATUS", "in", listOf(Status.PLANNED)))

    override fun getService(context: Context): ExecutableService = SequenceService(context)
    override fun getScheduler(): ToolScheduler = SequenceScheduler

    override fun getDao(context: Context): Any {
        val baseDao = app.treelune.core.database.AppDatabase.getDatabase(context).toolDataDao()
        return app.treelune.core.database.dao.DefaultExtendedToolDataDao(baseDao, "sequence")
    }

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openEntry: EntryToOpen?) {
        SequenceScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick)
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile = rememberSequenceTile(tool)
}
