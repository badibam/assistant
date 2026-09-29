package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.fields.formatValue
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTile
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.FormatUtils
import com.assistant.core.utils.LogManager
import com.assistant.tools.tracking.TrackingKind
import org.json.JSONObject

/** The most recent entry of a tracking tool, as its tile shows it. */
private data class LastEntry(val name: String, val timestamp: Long, val value: Any?, val unit: String?)

/**
 * A tracking tool's tile.
 *
 * The summary is its last entry: the one whose stopwatch runs, its name, its time going and a
 * stop button; otherwise the most recent, its value and the time since, with two buttons: the
 * same entry again now (same name, value and unit; a counter's same step, an occurrence alone,
 * a timer started again; the user's fields at their default values) and the entry dialog
 * prefilled with its name and value.
 *
 * The body is the shortcuts in the config's order, two per line on two columns, each with its
 * buttons as on the tool's screen: four per row of cells, all of them in FULL.
 */
@Composable
fun rememberTrackingTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }
    val actions = rememberTrackingActions(tool.id, config)

    var running by remember { mutableStateOf<List<RunningEntry>>(emptyList()) }
    var latest by remember { mutableStateOf<LastEntry?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "limit" to 1))
        if (!result.isSuccess) {
            LogManager.tracking("Tile of ${tool.id}: last entry not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        latest = (result.data?.get("entries") as? List<*>)?.firstOrNull()?.let { it as? Map<*, *> }?.let { entry ->
            val data = entry["data"] as? Map<*, *>
            LastEntry(entry["name"] as? String ?: "", (entry["timestamp"] as Number).toLong(), data?.get("value"), data?.get("unit") as? String)
        }
        running = if (actions.kind == TrackingKind.TIMER) actions.loadRunning() ?: return@LaunchedEffect else emptyList()
        loaded = true
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++
        }
    }

    return remember(actions) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                // Nothing to show until loaded: the tile keeps its header alone meanwhile
                if (!loaded) return
                actions.Dialog()
                val s = remember { Strings.`for`(tool = "tracking", context = context) }
                val current = running.firstOrNull()
                val last = latest
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    when {
                        current != null -> {
                            UI.Text(current.name, TextType.BODY, maxLines = 1)
                            Line { ElapsedText(current, actions.valueField?.config, s); QuickButton(ButtonAction.STOP, !actions.isSaving) { actions.stop(current) } }
                        }
                        last != null -> {
                            val shown = listOfNotNull(last.name, last.value?.let { actions.valueField?.formatValue(it, context) }, last.unit)
                            UI.Text(shown.joinToString(" "), TextType.BODY, maxLines = 1)
                            Line {
                                Box(modifier = Modifier.weight(1f)) {
                                    UI.Text(FormatUtils.formatRelativeTimePast(last.timestamp, context), TextType.CAPTION, maxLines = 1)
                                }
                                QuickButton(ButtonAction.REPEAT, !actions.isSaving) { again(actions, last) }
                                QuickButton(ButtonAction.EDIT, !actions.isSaving) {
                                    actions.openDialog(last.name, last.value, last.unit, nameEditable = true, offerShortcut = false)
                                }
                            }
                        }
                        else -> {
                            UI.Text(s.tool("tile_no_entry"), TextType.CAPTION, maxLines = 1)
                            Line {
                                Box(modifier = Modifier.weight(1f))
                                QuickButton(ButtonAction.ADD, !actions.isSaving) {
                                    actions.openDialog("", null, null, nameEditable = true, offerShortcut = true)
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                if (!loaded) return
                val shown = rows?.let { actions.shortcuts.take(it * 4) } ?: actions.shortcuts
                val lines = shown.chunked(2)
                // Given rows, each of their lines takes its share of the height; FULL takes what it shows
                val lineCount = rows?.let { it * 2 }
                Column(modifier = if (rows != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                    for (index in 0 until (lineCount ?: lines.size)) {
                        val pair = lines.getOrNull(index) ?: emptyList()
                        Row(
                            modifier = (if (lineCount != null) Modifier.weight(1f) else Modifier).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (column in 0 until 2) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    val shortcut = pair.getOrNull(column) ?: return@Row
                                    Box(modifier = Modifier.weight(1f)) {
                                        UI.Text(
                                            shortcutLabel(shortcut, actions.kind) { com.assistant.core.utils.NumberFormatting.formatForDisplay(it.toDouble(), context = context) },
                                            TextType.BODY, maxLines = 1
                                        )
                                    }
                                    ShortcutButtons(actions, shortcut, running, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One line of the summary: its parts side by side, centered on the line. */
@Composable
private fun Line(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = content)

/** The same entry as [last], now: a timer starts again, a counter adds its step again. */
private fun again(actions: TrackingActions, last: LastEntry) = when (actions.kind) {
    TrackingKind.TIMER -> actions.start(last.name)
    TrackingKind.OCCURRENCE -> actions.quickSave(last.name, null, null)
    else -> actions.quickSave(last.name, last.value, last.unit)
}
