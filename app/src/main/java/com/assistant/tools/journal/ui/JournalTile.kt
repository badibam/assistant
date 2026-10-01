package com.assistant.tools.journal.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.strings.Strings
import com.assistant.core.tools.EntryToOpen
import com.assistant.core.tools.ToolTile
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.TileLine
import com.assistant.core.ui.components.TileGrid
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.FormatUtils
import com.assistant.core.utils.LogManager
import com.assistant.tools.journal.utils.DateFormatUtils

/** How many entries a FULL tile shows. */
private const val FULL_ENTRIES = 10

/** An entry as the tile shows it. */
private data class TileEntry(val id: String, val title: String, val timestamp: Long)

/**
 * A journal's tile. The summary is a button that writes an entry (created, then opened to be
 * written) and the date of the last one; the body the most recent entries, one per line, their
 * title and how long ago, ten in FULL. Touching an entry opens it.
 */
@Composable
fun rememberJournalTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "journal", context = context) }
    var entries by remember { mutableStateOf<List<TileEntry>?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "limit" to FULL_ENTRIES, "fields" to listOf("id", "name", "timestamp")))
        if (!result.isSuccess) {
            LogManager.ui("Journal tile ${tool.id}: entries not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        entries = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            TileEntry(it["id"] as String, (it["name"] as? String).orEmpty().ifBlank { s.tool("placeholder_untitled") }, (it["timestamp"] as Number).toLong())
        }
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++
        }
    }

    return remember(tool.id) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = entries ?: return
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    UI.Button(type = ButtonType.PRIMARY, size = Size.S, onClick = { open(EntryToOpen.New) }) {
                        UI.Text(s.tool("tile_write"), TextType.LABEL, maxLines = 1)
                    }
                    UI.Text(
                        loaded.firstOrNull()?.let { DateFormatUtils.formatJournalDate(it.timestamp, context) } ?: s.tool("no_entries"),
                        TextType.CAPTION, maxLines = 1
                    )
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val loaded = entries ?: return
                TileGrid(rows, loaded, columns = 1) { entry ->
                    TileLine(
                        entry.title,
                        modifier = Modifier.clickable { open(EntryToOpen.Existing(entry.id)) },
                        trailing = { UI.Text(FormatUtils.formatRelativeTimePast(entry.timestamp, context), TextType.CAPTION, maxLines = 1) }
                    )
                }
            }
        }
    }
}
