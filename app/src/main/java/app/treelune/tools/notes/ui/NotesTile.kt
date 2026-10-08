package app.treelune.tools.notes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import app.treelune.core.strings.Strings
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.LogManager

/** A note as the tile shows it. */
private data class TileNote(val id: String, val title: String?, val content: String, val position: Int, val timestamp: Long)

/**
 * A notes tool's tile. The summary is a button that opens the tool on a new note, and how many
 * notes there are; the body the notes in their manual order, two per row side by side, each a
 * card showing the start of its text, under its title when the notes take titles, on as many
 * lines as its slot holds; all of them in FULL. Touching a note opens it.
 */
@Composable
fun rememberNotesTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    var notes by remember { mutableStateOf<List<TileNote>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val titles = remember(tool.config_json) { app.treelune.tools.notes.NotesToolType.hasTitles(org.json.JSONObject(tool.config_json), context) }

    LaunchedEffect(tool.id, version, titles) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "fields" to listOf("id", "name", "timestamp", "data.content", "state.position")))
        if (!result.isSuccess) {
            LogManager.ui("Notes tile ${tool.id}: notes not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        notes = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            TileNote(
                id = it["id"] as String,
                title = (it["name"] as? String)?.takeIf { name -> titles && name.isNotBlank() },
                content = (it["data"] as? Map<*, *>)?.get("content") as? String ?: "",
                position = ((it["state"] as? Map<*, *>)?.get("position") as? Number)?.toInt() ?: 0,
                timestamp = (it["timestamp"] as Number).toLong()
            )
        }.sortedWith(compareBy<TileNote> { it.position }.thenBy { it.timestamp })
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
                val loaded = notes ?: return
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    UI.Button(type = ButtonType.PRIMARY, size = Size.S, onClick = { open(EntryToOpen.New) }) {
                        UI.Text(s.tool("tile_new"), TextType.LABEL, maxLines = 1)
                    }
                    UI.Text(
                        when (loaded.size) {
                            0 -> s.tool("tile_empty")
                            1 -> s.tool("tile_count_one")
                            else -> s.tool("tile_count_many").format(loaded.size.toString())
                        },
                        TextType.CAPTION, maxLines = 1
                    )
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val loaded = notes ?: return
                TileGrid(rows, loaded, columns = 2, perRow = 1) { note ->
                    Box(modifier = Modifier.fillMaxSize().padding(vertical = UI.Space.XS).clickable { open(EntryToOpen.Existing(note.id)) }) {
                        UI.Card(type = CardType.DEFAULT) {
                            Box(modifier = Modifier.fillMaxSize().padding(UI.Space.XS)) {
                                val text = note.content.ifBlank { s.tool("content_empty") }
                                // No count of lines: the slot's height bounds the text, its last line cut by an ellipsis
                                if (note.title != null) Column {
                                    UI.Text(note.title, TextType.LABEL, maxLines = 1)
                                    UI.Text(text, TextType.CAPTION)
                                } else UI.Text(text, TextType.CAPTION)
                            }
                        }
                    }
                }
            }
        }
    }
}
