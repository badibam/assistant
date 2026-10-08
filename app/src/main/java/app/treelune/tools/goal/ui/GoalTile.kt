package app.treelune.tools.goal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.formatValue
import app.treelune.core.fields.toFieldDefinition
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileLine
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateUtils
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.StoredSchedule
import app.treelune.tools.goal.GoalDefinition
import app.treelune.tools.goal.GoalToolType
import app.treelune.tools.goal.Met
import org.json.JSONObject

/** An attempt as the tile reads it. */
private data class TileAttempt(val id: String, val start: Long, val status: String, val periodEnd: Long?)

/** A line of the body: a criterion judged, or the name of the sub-goal the next ones belong to. */
private sealed interface GoalLine {
    data class Criterion(val name: String, val value: String, val condition: String?, val met: Met) : GoalLine
    data class SubGoal(val name: String) : GoalLine
}

/**
 * A goal's tile.
 *
 * The summary is the attempt running, counted ("4 / 5": the first-level elements met against
 * those asked, goal.evaluate) and the time left, or "To validate" while it waits. Without one:
 * between two attempts and when the next opens; stopped by its switch and the last verdict; a
 * one-off goal done, its verdict and its date.
 *
 * The body is the criteria of that attempt (the last one without a running one), one per line in
 * the config's order: its name, its value against its condition, and whether it is met. FULL
 * shows them all by sub-goal. Read only: an attempt is validated in the tool.
 */
@Composable
fun rememberGoalTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "goal", context = context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }

    var attempts by remember { mutableStateOf<List<TileAttempt>?>(null) }
    var judgement by remember { mutableStateOf<Map<*, *>?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id))
        if (!result.isSuccess) {
            LogManager.ui("Goal tile ${tool.id}: attempts not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        val read = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            val state = it["state"] as? Map<*, *>
            TileAttempt(it["id"] as String, (it["timestamp"] as Number).toLong(), state?.get(GoalToolType.STATUS) as? String ?: "",
                (state?.get(GoalToolType.PERIOD_END) as? Number)?.toLong())
        }.sortedByDescending { it.start }
        // The attempt the body judges: the one running, else the last
        val judged = read.firstOrNull { it.status !in GoalToolType.Status.LOCKED } ?: read.firstOrNull()
        judgement = judged?.let { attempt ->
            val evaluated = coordinator.processUserAction("goal.evaluate", mapOf("tool_instance_id" to tool.id, "id" to attempt.id))
            if (evaluated.isSuccess) evaluated.data else null.also { LogManager.ui("Goal tile ${tool.id}: attempt ${attempt.id} not judged: ${evaluated.error}", "ERROR") }
        }
        attempts = read
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if ((event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) || event is DataChangeEvent.VariablesChanged) version++
        }
    }

    /** The two lines of the summary. */
    fun summary(loaded: List<TileAttempt>): Pair<String, String> {
        val current = loaded.firstOrNull { it.status !in GoalToolType.Status.LOCKED }
        val last = loaded.firstOrNull()
        return when {
            current != null -> {
                val count = judgement?.let { s.tool("tile_count").format(it["met"], it["required"]) } ?: ""
                val second = when {
                    current.status == GoalToolType.Status.TO_VALIDATE -> s.tool("status_to_validate")
                    current.periodEnd != null -> s.tool("tile_ends").format(FormatUtils.formatRelativeTime(current.periodEnd, context))
                    else -> ""
                }
                count to second
            }
            !config.optBoolean("enabled", true) ->
                s.tool("tile_stopped") to (last?.let { s.tool("status_${it.status}") } ?: "")
            StoredSchedule.of(config) is StoredSchedule.None && last != null ->
                s.tool("status_${last.status}") to DateUtils.formatDateForDisplay(last.start)
            else -> {
                val next = when (val stored = StoredSchedule.of(config)) {
                    is StoredSchedule.Readable -> ScheduleCalculator.calculateNextExecution(stored.schedule.pattern, System.currentTimeMillis())
                    StoredSchedule.None -> (config.opt("start") as? Number)?.toLong()
                    is StoredSchedule.Unreadable -> null
                }
                s.tool("tile_between") to (next?.let { s.tool("tile_next").format(FormatUtils.formatRelativeTime(it, context)) } ?: "")
            }
        }
    }

    /** The body's lines: the criteria in the config's order, by sub-goal when [bySubGoal]. */
    fun lines(bySubGoal: Boolean): List<GoalLine> {
        val loaded = judgement ?: return emptyList()
        val definition = GoalDefinition.of(config)
        val rows = (loaded["criteria"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().associateBy { it["key"] as String }
        fun line(key: String): GoalLine? {
            val row = rows[key] ?: return null
            val criterion = definition.allCriteria.find { it.key == key }
            val field = criterion?.enteredField() ?: (row["field"] as? Map<*, *>)?.let { JsonUtils.toJSONObject(it.entries.associate { e -> e.key.toString() to e.value }).toFieldDefinition() }
            val value = row["failure"] as? String ?: row["value"]?.let { v -> field?.formatValue(v, context) ?: v.toString() } ?: s.shared("label_no_value")
            return GoalLine.Criterion(row["name"] as? String ?: "", value, condition(row["op"] as? String ?: "", row["compared"] as? List<*> ?: emptyList<Any?>(), field, context, s),
                Met.valueOf(row["met"] as? String ?: Met.UNKNOWN.name))
        }
        val own = definition.criteria.mapNotNull { line(it.key) }
        val subs = definition.subGoals.flatMap { sub -> (if (bySubGoal) listOf(GoalLine.SubGoal(sub.name)) else emptyList()) + sub.criteria.mapNotNull { line(it.key) } }
        return own + subs
    }

    return remember(tool.id, config.toString()) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = attempts ?: return
                val (first, second) = summary(loaded)
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    UI.Text(first, TextType.BODY, maxLines = 1)
                    UI.Text(second, TextType.CAPTION, maxLines = 1)
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                if (attempts == null) return
                TileGrid(rows, lines(bySubGoal = rows == null), columns = 1) { line ->
                    when (line) {
                        is GoalLine.SubGoal -> TileLine(line.name, type = TextType.LABEL)
                        is GoalLine.Criterion -> TileLine(line.name, trailing = {
                            UI.Text(line.value + (line.condition?.let { " / $it" } ?: ""), TextType.CAPTION, maxLines = 1)
                            UI.Icon(iconName = when (line.met) { Met.YES -> "check"; Met.NO -> "x"; Met.UNKNOWN -> "circle-dashed" }, size = 16.dp,
                                contentDescription = s.tool("met_${line.met.name.lowercase()}"))
                        })
                    }
                }
            }
        }
    }
}
