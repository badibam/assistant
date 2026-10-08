package app.treelune.tools.sequence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.ui.components.TileLine
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateTimeFormatter
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.StoredSchedule
import app.treelune.tools.sequence.SequenceToolType
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * A session's tile (docs/design/sequence-tool.md, « L'écran »). During a session, the summary is
 * its step and its clock; otherwise the planned session waiting and since when, or the next one
 * planned, or the last one done. The body lists the last sessions, one per line: when, state,
 * length, how far.
 */
@Composable
fun rememberSequenceTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "sequence", context = context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }

    var entries by remember { mutableStateOf<List<SequenceEntry>?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id))
        if (!result.isSuccess) {
            LogManager.ui("Session tile ${tool.id}: entries not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        entries = sequenceEntries(result.data)
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event -> if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++ }
    }

    return remember(tool.id, config.toString()) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = entries ?: return
                val running = loaded.firstOrNull { it.run != null }?.run
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                if (running != null) LaunchedEffect(running) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
                val planned = loaded.filter { it.status == SequenceToolType.Status.PLANNED }.maxByOrNull { it.timestamp }
                val last = loaded.filter { it.status == SequenceToolType.Status.DONE || it.status == SequenceToolType.Status.STOPPED }.maxByOrNull { it.timestamp }
                val (first, second) = when {
                    running != null -> running.current.name to stepClock(running, now)
                    planned != null -> s.tool("tile_planned") to FormatUtils.formatRelativeTimePast(planned.timestamp, context)
                    else -> {
                        val next = (StoredSchedule.of(config) as? StoredSchedule.Readable)
                            ?.takeIf { config.optBoolean(SequenceToolType.ENABLED, true) }
                            ?.let { ScheduleCalculator.calculateNextExecution(it.schedule.pattern, System.currentTimeMillis()) }
                        when {
                            next != null -> s.tool("tile_next") to DateTimeFormatter.formatForDisplay(next, context)
                            last != null -> s.tool("tile_last") to FormatUtils.formatRelativeTimePast(last.timestamp, context)
                            else -> s.tool("history_empty") to ""
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    UI.Text(first, TextType.BODY, maxLines = 1)
                    if (second.isNotEmpty()) UI.Text(second, TextType.CAPTION, maxLines = 1)
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val past = entries?.filter { it.status != SequenceToolType.Status.PLANNED && it.run == null }?.sortedByDescending { it.timestamp } ?: return
                TileGrid(rows, past, columns = 1) { entry -> TileLine(historyLine(entry, s, context), maxLines = 1) }
            }
        }
    }
}
