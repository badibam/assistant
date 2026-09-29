package com.assistant.tools.messages.ui

import androidx.compose.foundation.clickable
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
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.strings.Strings
import com.assistant.core.tools.EntryToOpen
import com.assistant.core.tools.ToolTile
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.TileGrid
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DateTimeFormatter
import com.assistant.core.utils.FormatUtils
import com.assistant.core.utils.LogManager

/** How many messages a FULL tile shows, unread and read together. */
private const val FULL_MESSAGES = 10

/** What the tile shows: the messages received, unread first, and the next send. */
private data class Inbox(val unread: List<Occurrence>, val read: List<Occurrence>, val nextSend: Long?)

/**
 * A messages tool's tile. The summary counts the unread messages and names the oldest, which a
 * touch on the tile opens; with none, it says all are read and when the next one goes out. The
 * body lists the unread messages from the oldest, then the last read ones, one per line, their
 * title (the unread ones stronger) and how long ago, ten in FULL. Opening a message reads it.
 */
@Composable
fun rememberMessagesTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    var inbox by remember { mutableStateOf<Inbox?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val onError: (String) -> Unit = { LogManager.ui("Messages tile ${tool.id}: $it", "ERROR") }
        val sent = loadByStatus(context, coordinator, tool.id, "sent", onError) ?: return@LaunchedEffect
        val pending = loadByStatus(context, coordinator, tool.id, "pending", onError) ?: return@LaunchedEffect
        val received = sent.filterNot { it.archived }
        inbox = Inbox(
            unread = received.filterNot { it.read }.sortedBy { it.dueAt },
            read = received.filter { it.read }.sortedByDescending { it.dueAt },
            nextSend = pending.minOfOrNull { it.dueAt }
        )
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
                val loaded = inbox ?: return
                val oldest = loaded.unread.firstOrNull()
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    if (oldest != null) {
                        UI.Text(s.tool("tile_unread").format(loaded.unread.size.toString()), TextType.BODY, maxLines = 1)
                        UI.Text(oldest.displayTitle, TextType.CAPTION, maxLines = 1)
                    } else {
                        UI.Text(s.tool("tile_all_read"), TextType.BODY, maxLines = 1)
                        UI.Text(
                            loaded.nextSend?.let { s.tool("tile_next").format(DateTimeFormatter.formatForDisplay(it, context)) } ?: s.tool("tile_no_send"),
                            TextType.CAPTION, maxLines = 1
                        )
                    }
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val loaded = inbox ?: return
                val shown = (loaded.unread + loaded.read).let { if (rows == null) it.take(FULL_MESSAGES) else it }
                TileGrid(rows, shown, columns = 1) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { open(EntryToOpen.Existing(message.id)) },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            UI.Text(message.displayTitle, if (message.read) TextType.CAPTION else TextType.LABEL, maxLines = 1)
                        }
                        UI.Text(FormatUtils.formatRelativeTimePast(message.dueAt, context), TextType.CAPTION, maxLines = 1)
                    }
                }
            }
        }
    }
}
