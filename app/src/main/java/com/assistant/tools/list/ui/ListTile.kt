package com.assistant.tools.list.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.strings.Strings
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.core.tools.ToolTile
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.Duration
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.TileGrid
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.tools.list.DueNotice
import com.assistant.tools.list.ListItem
import com.assistant.tools.list.ListItems
import com.assistant.tools.list.ListToolType
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A list's tile, its boxes checked and its items added without opening the tool. The summary
 * counts the items left unchecked ("3 restants"), or the late ones in a list with due dates
 * ("2 en retard"), beside a button adding one; the body shows the unchecked items, the late
 * ones first and the others in their order, two per line on two columns, each with its box:
 * four per row of cells, all of them in FULL. The checked ones stay in the tool.
 */
@Composable
fun rememberListTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "list", context = context) }
    val scope = rememberCoroutineScope()
    // The tile is drawn anew with the tool when its config is saved, so it reads the setting here
    val removeWhenChecked = remember(tool.config_json) {
        ToolConfigSettings.read(ListToolType, JSONObject(tool.config_json), context).boolean(ListToolType.REMOVE_WHEN_CHECKED)
    }
    val dueDates = remember(tool.config_json) { ListToolType.hasDueDates(JSONObject(tool.config_json)) }

    val fields = remember(tool.config_json) { JSONObject(tool.config_json).optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList() }

    var items by remember { mutableStateOf<List<ListItem>?>(null) }
    var adding by androidx.compose.runtime.saveable.rememberSaveable(tool.id) { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        ListItems.load(coordinator, tool.id)?.let { items = it }
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++
        }
    }

    // A refusal is said, as on the list's screen: a box that does not stay checked needs a reason
    fun check(item: ListItem, checked: Boolean) {
        scope.launch {
            val result = ListItems.setChecked(coordinator, item.id, checked, removeWhenChecked)
            if (!result.isSuccess) UI.Toast(context, result.error ?: s.shared("message_error_simple"), Duration.LONG)
        }
    }

    if (adding) {
        ListAddDialog(
            fields = fields,
            dueDates = dueDates,
            onAdd = { name, extra, dueAt ->
                scope.launch {
                    val result = ListItems.add(coordinator, tool.id, name, extra, dueAt.takeIf { dueDates })
                    if (result.isSuccess) adding = false
                    else UI.Toast(context, result.error ?: s.shared("message_error_simple"), Duration.LONG)
                }
            },
            onCancel = { adding = false }
        )
    }

    return remember(tool.id, removeWhenChecked, dueDates) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                // Nothing to show until loaded: the tile keeps its header alone meanwhile
                val loaded = items ?: return
                val left = loaded.count { !it.isChecked }
                val now = System.currentTimeMillis()
                val late = if (dueDates) loaded.count { DueNotice.isLate(it, now) } else 0
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        UI.Text(
                            text = when {
                                loaded.isEmpty() -> s.tool("tile_empty")
                                late == 1 -> s.tool("tile_late_one")
                                late > 1 -> s.tool("tile_late_many").format(late.toString())
                                left == 0 -> s.tool("tile_all_checked")
                                left == 1 -> s.tool("tile_left_one")
                                else -> s.tool("tile_left_many").format(left.toString())
                            },
                            type = TextType.BODY,
                            fillMaxWidth = true,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                    UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.ICON, size = Size.S, onClick = { adding = true })
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val loaded = items ?: return
                val now = System.currentTimeMillis()
                // The late ones first, each part keeping the list's order: a stable sort
                val left = ListItems.shown(loaded).filterNot { it.isChecked }.sortedBy { if (DueNotice.isLate(it, now)) 0 else 1 }
                TileGrid(rows, left, columns = 2) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                        UI.Checkbox(checked = item.isChecked, onCheckedChange = { checked -> check(item, checked) })
                        UI.Text(item.name, TextType.BODY, maxLines = 1)
                    }
                }
            }
        }
    }
}
