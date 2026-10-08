package app.treelune.tools.sequence.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateTimeFormatter
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.sequence.PlannedStep
import app.treelune.tools.sequence.SequencePlan
import app.treelune.tools.sequence.SequenceLive
import app.treelune.tools.sequence.SequenceRun
import app.treelune.tools.sequence.SequenceService
import app.treelune.tools.sequence.SequenceToolType
import app.treelune.tools.sequence.StepEnd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** An entry as the screen and the tile list it. */
data class SequenceEntry(
    val id: String,
    val timestamp: Long,
    val status: String,
    val duration: Long?,
    val stepsDone: Int?,
    val stepsSkipped: Int?,
    val stepsNotDone: Int?,
    val run: SequenceRun?
) {
    /** The steps of the session, once it is over: done, skipped and not reached together. */
    val stepsTotal: Int? get() = if (stepsDone != null && stepsSkipped != null && stepsNotDone != null) stepsDone + stepsSkipped + stepsNotDone else null
}

/** The entries of a tool, as tool_data.get gives them. */
internal fun sequenceEntries(result: Map<String, Any?>?): List<SequenceEntry> =
    (result?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { e ->
        val state = e["state"] as? Map<*, *>
        val data = e["data"] as? Map<*, *>
        fun count(key: String) = (data?.get(key) as? Number)?.toInt()
        SequenceEntry(
            id = e["id"] as String,
            timestamp = (e["timestamp"] as Number).toLong(),
            status = state?.get(SequenceToolType.STATUS) as? String ?: "",
            duration = (data?.get(SequenceToolType.DURATION) as? Number)?.toLong(),
            stepsDone = count(SequenceToolType.STEPS_DONE),
            stepsSkipped = count(SequenceToolType.STEPS_SKIPPED),
            stepsNotDone = count(SequenceToolType.STEPS_NOT_DONE),
            run = (state?.get(SequenceToolType.RUN) as? String)?.takeIf { it.isNotEmpty() && state[SequenceToolType.STATUS] == SequenceToolType.Status.RUNNING }
                ?.let { SequenceRun.fromJson(JSONObject(it)) }
        )
    }

/** A clock reading: « 1:05 », « 1:02:03 » past the hour. */
internal fun clockText(ms: Long): String {
    val seconds = (ms + 999) / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val sec = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** What the current step's clock shows: the timer's time left, the overtime after it, or the stopwatch. */
internal fun stepClock(run: SequenceRun, now: Long): String {
    val remaining = run.remaining(now)
    return when {
        run.elapsed(now) < 0 -> clockText(-run.elapsed(now))
        remaining == null -> clockText(run.elapsed(now))
        remaining >= 0 -> clockText(remaining)
        else -> "+" + clockText(-remaining)
    }
}

/** The rounds of the blocks around a step: « Intervals 2 / 8 ». */
internal fun roundsText(step: PlannedStep): String = step.rounds.joinToString(" · ") { "${it.block} ${it.round} / ${it.of}" }

/**
 * A session's screen (docs/design/sequence-tool.md, « L'écran »).
 *
 * Without a session running: « Start » (saying since when a planned one waited) and « Note after
 * the fact », the planned sessions with « Ignore all », the run read one line per step, the blocks
 * indented, then the history. During a session: the step large, its round, its clock, its
 * instruction, the next step small, the gestures. A session recorded as running that no service
 * runs was stopped with the app: resume or stop.
 */
@Composable
fun SequenceScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "sequence", context = context) }
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<JSONObject?>(null) }
    var entries by remember { mutableStateOf<List<SequenceEntry>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val live by SequenceLive.running.collectAsState()
    // A session of the history opened, to read and correct
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (config == null) { errorMessage = tool.error; return@LaunchedEffect }
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        entries = sequenceEntries(result.data)
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) version++
                is DataChangeEvent.ToolsChanged -> version++
                else -> {}
            }
        }
    }
    errorMessage?.let { message -> LaunchedEffect(message) { UI.Toast(context, message, Duration.LONG); errorMessage = null } }

    fun call(operation: String, params: Map<String, Any?> = emptyMap(), onDone: () -> Unit = {}) {
        scope.launch {
            val result = coordinator.processUserAction("sequence.$operation", mapOf("tool_instance_id" to toolInstanceId) + params)
            if (result.isSuccess) { onDone(); version++ } else errorMessage = result.error ?: s.shared("message_error_simple")
        }
    }

    val loadedConfig = config
    val loaded = entries
    if (loadedConfig == null || loaded == null) { UI.LoadingIndicator(); return }
    val settings = ToolConfigSettings.read(SequenceToolType, loadedConfig, context)
    val name = settings.string("name")!!
    val running = loaded.firstOrNull { it.run != null }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = name,
            subtitle = settings.string("description")?.takeIf { it.isNotBlank() },
            icon = settings.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = { if (openId != null) openId = null else onNavigateBack() },
            onRightClick = onConfigureClick
        )
        val open = openId?.let { id -> loaded.find { it.id == id && it.stepsTotal != null } }
        when {
            open != null -> SessionView(open, s) { params -> call(SequenceService.CORRECT, mapOf("id" to open.id) + params) {} }
            running != null && live == running.id -> Running(running.id, running.run!!, s) { op -> call(op, mapOf("id" to running.id)) }
            running != null -> Interrupted(s,
                onResume = { call(SequenceService.RESUME_INTERRUPTED, mapOf("id" to running.id)) },
                onStop = { call(SequenceService.STOP, mapOf("id" to running.id)) })
            else -> Idle(loadedConfig, loaded, s, ::call, onOpen = { openId = it })
        }
    }
}

/** A session running: its step large, the next small, the gestures; the screen kept on while shown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Running(id: String, run: SequenceRun, s: StringsContext, gesture: (String) -> Unit) {
    val view = LocalView.current
    DisposableEffect(id) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(id) {
        while (true) { now = System.currentTimeMillis(); delay(TICK) }
    }
    val step = run.current
    val leadIn = run.elapsed(now) < 0

    UI.Text(s.tool("running_position").format(run.position + 1, run.steps.size), TextType.CAPTION)
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            if (leadIn) UI.Text(s.tool("running_get_ready"), TextType.CAPTION)
            UI.Text(step.name, TextType.TITLE)
            if (step.rounds.isNotEmpty()) UI.Text(roundsText(step), TextType.SUBTITLE)
            UI.Text(stepClock(run, now), TextType.TITLE)
            if (run.isPaused) UI.Text(s.tool("running_paused"), TextType.WARNING)
            else if (run.held) UI.Text(s.tool("running_held"), TextType.CAPTION)
            step.instruction?.let { UI.Text(it, TextType.BODY) }
        }
    }
    run.next?.let { UI.Text(s.tool("running_next").format(it.name), TextType.CAPTION) }

    UI.Button(type = ButtonType.PRIMARY, onClick = { gesture(SequenceService.DONE) }) { UI.Text(s.tool("action_done"), TextType.LABEL) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        if (run.isPaused) UI.Button(type = ButtonType.SECONDARY, onClick = { gesture(SequenceService.RESUME) }) { UI.Text(s.tool("action_resume"), TextType.LABEL) }
        else UI.Button(type = ButtonType.SECONDARY, onClick = { gesture(SequenceService.PAUSE) }) { UI.Text(s.tool("action_pause"), TextType.LABEL) }
        UI.Button(type = ButtonType.DEFAULT, onClick = { gesture(SequenceService.SKIP) }) { UI.Text(s.tool("action_skip"), TextType.LABEL) }
        if (run.position > 0) UI.Button(type = ButtonType.DEFAULT, onClick = { gesture(SequenceService.BACK) }) { UI.Text(s.tool("action_back"), TextType.LABEL) }
        UI.Button(type = ButtonType.DEFAULT, onClick = { gesture(SequenceService.RESTART) }) { UI.Text(s.tool("action_restart"), TextType.LABEL) }
        if (step.end.timed) UI.Button(type = ButtonType.DEFAULT, onClick = { gesture(SequenceService.EXTEND) }) { UI.Text(s.tool("action_extend"), TextType.LABEL) }
    }
    UI.ActionButton(action = ButtonAction.STOP, requireConfirmation = true, confirmMessage = s.tool("confirm_stop")) { gesture(SequenceService.STOP) }
}

/** A session the app stopped mid-way: resumed, its step starting again, or stopped. */
@Composable
private fun Interrupted(s: StringsContext, onResume: () -> Unit, onStop: () -> Unit) {
    UI.Text(s.tool("interrupted"), TextType.BODY)
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        UI.Button(type = ButtonType.PRIMARY, onClick = onResume) { UI.Text(s.tool("action_resume_interrupted"), TextType.LABEL) }
        UI.Button(type = ButtonType.SECONDARY, onClick = onStop) { UI.Text(s.tool("action_stop"), TextType.LABEL) }
    }
}

/**
 * No session running: start one, the planned ones (done without the app, or ignored), the run,
 * the history, a session of which opens to be corrected.
 */
@Composable
private fun Idle(config: JSONObject, entries: List<SequenceEntry>, s: StringsContext, call: (String, Map<String, Any?>, () -> Unit) -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val planned = entries.filter { it.status == SequenceToolType.Status.PLANNED }.sortedByDescending { it.timestamp }
    val history = entries.filter { it.status != SequenceToolType.Status.PLANNED }.sortedByDescending { it.timestamp }

    planned.firstOrNull()?.let { UI.Text(s.tool("planned_waiting").format(FormatUtils.formatRelativeTimePast(it.timestamp, context)), TextType.CAPTION) }
    UI.Button(type = ButtonType.PRIMARY, onClick = { call(SequenceService.START, emptyMap()) {} }) { UI.Text(s.tool("action_start"), TextType.LABEL) }

    if (planned.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.tool("planned_count").format(planned.size), TextType.SUBTITLE)
            UI.Button(type = ButtonType.DEFAULT, onClick = { call(SequenceService.IGNORE_ALL, emptyMap()) {} }) { UI.Text(s.tool("action_ignore_all"), TextType.LABEL) }
        }
        planned.forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(DateTimeFormatter.formatForDisplay(entry.timestamp, context), TextType.BODY)
                UI.Button(type = ButtonType.SECONDARY, onClick = { call(SequenceService.COMPLETE, mapOf("id" to entry.id)) {} }) { UI.Text(s.tool("action_mark_done"), TextType.LABEL) }
                UI.Button(type = ButtonType.DEFAULT, onClick = { call(SequenceService.IGNORE, mapOf("id" to entry.id)) {} }) { UI.Text(s.tool("action_ignore"), TextType.LABEL) }
            }
        }
    }

    UI.Text(s.tool("field_steps"), TextType.SUBTITLE)
    StepLines(config.optJSONArray(SequencePlan.STEPS), depth = 0, s)

    UI.Text(s.tool("history"), TextType.SUBTITLE)
    if (history.isEmpty()) UI.Text(s.tool("history_empty"), TextType.CAPTION)
    history.forEach { entry ->
        val opens = entry.stepsTotal != null
        Box(modifier = if (opens) Modifier.fillMaxWidth().clickable { onOpen(entry.id) }.padding(vertical = UI.Space.XS) else Modifier.padding(vertical = UI.Space.XS)) {
            UI.Text(historyLine(entry, s, context), TextType.BODY)
        }
    }
}

/** One history line: when, its state, how long, how far. */
internal fun historyLine(entry: SequenceEntry, s: StringsContext, context: Context): String = listOfNotNull(
    DateTimeFormatter.formatForDisplay(entry.timestamp, context),
    s.tool("status_${entry.status}"),
    entry.duration?.let { clockText(it) },
    if (entry.stepsDone != null && entry.stepsTotal != null) s.tool("steps_of").format(entry.stepsDone, entry.stepsTotal) else null
).joinToString(" · ")

/** The run as the config writes it: one line per step, its end; a block « × N », its steps indented. */
@Composable
private fun StepLines(elements: JSONArray?, depth: Int, s: StringsContext) {
    if (elements == null) return
    for (i in 0 until elements.length()) {
        val element = elements.getJSONObject(i)
        val indent = Modifier.padding(start = UI.Space.L * depth)
        val isBlock = depth < SequencePlan.MAX_DEPTH && element.optString(SequencePlan.KIND) == SequencePlan.KIND_BLOCK
        if (isBlock) {
            Box(modifier = indent) { UI.Text("${element.optString(SequencePlan.NAME)} × ${element.optInt(SequencePlan.REPEAT, 1)}", TextType.STRONG) }
            StepLines(element.optJSONArray(SequencePlan.STEPS), depth + 1, s)
        } else {
            val end = runCatching { StepEnd.of(element.optString(SequencePlan.END)) }.getOrNull()
            val how = when (end) {
                StepEnd.MANUAL -> s.tool("end_manual")
                null -> ""
                else -> clockText(element.optLong(SequencePlan.DURATION)) + if (end == StepEnd.TIMED_THEN_MANUAL) " · " + s.tool("end_then_manual_short") else ""
            }
            Box(modifier = indent) { UI.Text(listOf(element.optString(SequencePlan.NAME), how).filter { it.isNotEmpty() }.joinToString(" · "), TextType.BODY) }
        }
    }
}

/**
 * A session over, read and corrected: its time, its length, its steps done, skipped and not
 * reached, its state following the counts as the service sets it (SequenceCorrection).
 */
@Composable
private fun SessionView(entry: SequenceEntry, s: StringsContext, onSave: (Map<String, Any?>) -> Unit) {
    val context = LocalContext.current
    var at by rememberSaveable(entry.id) { mutableStateOf<Any?>(entry.timestamp) }
    var duration by rememberSaveable(entry.id) { mutableStateOf<Any?>(entry.duration) }
    var done by rememberSaveable(entry.id) { mutableStateOf<Any?>(entry.stepsDone) }
    var skipped by rememberSaveable(entry.id) { mutableStateOf<Any?>(entry.stepsSkipped) }
    var notDone by rememberSaveable(entry.id) { mutableStateOf<Any?>(entry.stepsNotDone) }
    val count = mapOf("min" to 0, "decimals" to 0)
    fun field(name: String, type: FieldType, config: Map<String, Any>? = null) = FieldDefinition(name, s.tool("field_$name"), null, type, false, config)
    val notReached = (notDone as? Number)?.toInt() ?: 0
    UI.Text(s.tool("status_${if (notReached == 0) SequenceToolType.Status.DONE else SequenceToolType.Status.STOPPED}"), TextType.SUBTITLE)
    FieldInput(field("when", FieldType.DATETIME), at, { at = it }, context, required = true)
    FieldInput(field(SequenceToolType.DURATION, FieldType.DURATION), duration, { duration = it }, context)
    FieldInput(field(SequenceToolType.STEPS_DONE, FieldType.NUMERIC, count), done, { done = it }, context, required = true)
    FieldInput(field(SequenceToolType.STEPS_SKIPPED, FieldType.NUMERIC, count), skipped, { skipped = it }, context, required = true)
    FieldInput(field(SequenceToolType.STEPS_NOT_DONE, FieldType.NUMERIC, count), notDone, { notDone = it }, context, required = true)
    UI.Text(s.tool("correct_total").format(entry.stepsTotal ?: 0), TextType.CAPTION)
    UI.Button(type = ButtonType.PRIMARY, onClick = {
        onSave(mapOf(
            "timestamp" to at,
            SequenceToolType.DURATION to duration,
            SequenceToolType.STEPS_DONE to done,
            SequenceToolType.STEPS_SKIPPED to skipped,
            SequenceToolType.STEPS_NOT_DONE to notDone
        ))
    }) { UI.Text(s.shared("action_save"), TextType.LABEL) }
}

/** How often the clock on screen is redrawn. */
private const val TICK = 250L
