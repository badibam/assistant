package app.treelune.tools.tracking.ui

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
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.formatValue
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileLine
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.LogManager
import app.treelune.tools.tracking.TrackingKind
import app.treelune.tools.tracking.TrackingShortcut
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
 * The body is the shortcuts in the config's order, then a choice's options, two per line on two
 * columns: a shortcut with its buttons as on the tool's screen, an option with the button that
 * enters it at once. Four per row of cells, all of them in FULL.
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
                            Line {
                                // The time takes the free width, keeping the button at the end of a wide tile
                                Box(modifier = Modifier.weight(1f)) { ElapsedText(current, actions.valueField?.config, s) }
                                QuickButton(ButtonAction.STOP, !actions.isSaving) { actions.stop(current) }
                            }
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
                val s = remember { Strings.`for`(tool = "tracking", context = context) }
                // A shortcut, or an option of a choice
                val items: List<Any> = actions.shortcuts + actions.choice?.options.orEmpty()
                app.treelune.core.ui.components.TileGrid(rows, items, columns = 2) { item ->
                    when (item) {
                        is TrackingShortcut -> TileLine(
                            shortcutLabel(item, actions.kind) { app.treelune.core.utils.NumberFormatting.formatForDisplay(it.toDouble(), context = context) },
                            // A running stopwatch's time under its name, which keeps the width beside the button
                            secondary = running.firstOrNull { it.name == item.name }?.let { elapsedText(it, actions.valueField?.config, s) },
                            trailing = { ShortcutButtons(actions, item, running, null, withElapsed = false) }
                        )
                        is String -> TileLine(trailing = { QuickButton(ButtonAction.ADD, !actions.isSaving) { actions.quickSaveOption(item) } }) {
                            OptionLabel(actions.choice!!, item)
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically, content = content)

/** The same entry as [last], now: a timer starts again, a counter adds its step again. */
private fun again(actions: TrackingActions, last: LastEntry) = when (actions.kind) {
    TrackingKind.TIMER -> actions.start(last.name)
    TrackingKind.OCCURRENCE -> actions.quickSave(last.name, null, null)
    else -> actions.quickSave(last.name, last.value, last.unit)
}
