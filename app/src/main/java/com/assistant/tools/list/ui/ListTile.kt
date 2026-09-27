package com.assistant.tools.list.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.tools.list.ListToolType
import org.json.JSONObject
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.ui.DisplayMode
import com.assistant.core.ui.Duration
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.tools.list.ListItem
import com.assistant.tools.list.ListItems
import kotlinx.coroutines.launch

/**
 * How many items a CONDENSED or EXTENDED tile shows before "+ n more unchecked": both are
 * 128dp high in the default theme, which leaves room under the header for about two rows.
 */
private const val MEDIUM_TILE_ROWS = 2

/**
 * A list's tile on a zone, its boxes checked without opening the tool: a LINE tile counts the
 * items left; a CONDENSED or EXTENDED one shows the first of them and how many more; a SQUARE
 * one all of them, scrolling inside it; a FULL one every item, the checked ones greyed below.
 */
@Composable
fun ListTile(tool: ToolInstance, displayMode: DisplayMode) {
    val toolInstanceId = tool.id
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "list", context = context) }
    val scope = rememberCoroutineScope()
    // The tile is drawn anew with the tool when its config is saved, so it reads the setting here
    val removeWhenChecked = remember(tool.config_json) {
        ToolConfigSettings.read(ListToolType, JSONObject(tool.config_json), context).boolean(ListToolType.REMOVE_WHEN_CHECKED)
    }

    var items by remember { mutableStateOf<List<ListItem>?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(toolInstanceId, version) {
        ListItems.load(coordinator, toolInstanceId)?.let { items = it }
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == toolInstanceId) version++
        }
    }

    // Nothing to show until loaded: the tile keeps its header alone meanwhile
    val loaded = items ?: return
    val shown = ListItems.shown(loaded)
    val left = shown.filterNot { it.isChecked }

    // A refusal is said, as on the list's screen: a box that does not stay checked needs a reason
    fun check(item: ListItem, checked: Boolean) {
        scope.launch {
            val result = ListItems.setChecked(coordinator, item, checked, removeWhenChecked)
            if (!result.isSuccess) UI.Toast(context, result.error ?: s.shared("message_error_simple"), Duration.LONG)
        }
    }

    when (displayMode) {
        DisplayMode.ICON, DisplayMode.MINIMAL -> Unit

        DisplayMode.LINE -> UI.Text(
            text = when {
                loaded.isEmpty() -> s.tool("tile_empty")
                left.isEmpty() -> s.tool("tile_all_checked")
                left.size == 1 -> s.tool("tile_unchecked_one")
                else -> s.tool("tile_unchecked_many").format(left.size.toString())
            },
            type = TextType.BODY,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )

        DisplayMode.CONDENSED, DisplayMode.EXTENDED -> Column {
            left.take(MEDIUM_TILE_ROWS).forEach { TileRow(it) { checked -> check(it, checked) } }
            val more = left.size - MEDIUM_TILE_ROWS
            when {
                more == 1 -> UI.Text(s.tool("tile_more_unchecked_one"), TextType.CAPTION)
                more > 1 -> UI.Text(s.tool("tile_more_unchecked_many").format(more.toString()), TextType.CAPTION)
            }
            if (left.isEmpty()) UI.Text(s.tool(if (loaded.isEmpty()) "tile_empty" else "tile_all_checked"), TextType.CAPTION)
        }

        DisplayMode.SQUARE -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            left.forEach { TileRow(it) { checked -> check(it, checked) } }
            if (left.isEmpty()) UI.Text(s.tool(if (loaded.isEmpty()) "tile_empty" else "tile_all_checked"), TextType.CAPTION)
        }

        DisplayMode.FULL -> Column {
            shown.forEach { TileRow(it) { checked -> check(it, checked) } }
            if (shown.isEmpty()) UI.Text(s.tool("tile_empty"), TextType.CAPTION)
        }
    }
}

/** One item in a tile: its box and its name, greyed once checked. */
@Composable
private fun TileRow(item: ListItem, onCheck: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        UI.Checkbox(checked = item.isChecked, onCheckedChange = onCheck)
        UI.Text(item.name, if (item.isChecked) TextType.CAPTION else TextType.BODY)
    }
}
