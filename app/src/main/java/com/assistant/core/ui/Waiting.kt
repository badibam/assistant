package com.assistant.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.LogManager

/** What waits on the screen shown, which the tiles read; nothing where no screen provides it. */
val LocalWaiting = androidx.compose.runtime.staticCompositionLocalOf { Waiting() }

/** How many entries wait for the user, by tool and by zone (tools.waiting). */
data class Waiting(val tools: Map<String, Int> = emptyMap(), val zones: Map<String, Int> = emptyMap()) {
    fun tool(id: String): Boolean = (tools[id] ?: 0) > 0
    fun zone(id: String): Boolean = (zones[id] ?: 0) > 0
}

/**
 * What waits in the tools of [zoneId], or of every zone when null, read again whenever tools or
 * their entries change; nothing while it is read, and nothing when the read fails, which is logged.
 */
@Composable
fun rememberWaiting(zoneId: String?): Waiting {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf(Waiting()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged || event is DataChangeEvent.ToolsChanged) version++
        }
    }
    LaunchedEffect(zoneId, version) {
        val result = Coordinator(context).processUserAction("tools.waiting", zoneId?.let { mapOf("zone_id" to it) } ?: emptyMap())
        if (!result.isSuccess) {
            LogManager.ui("rememberWaiting: not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        fun counts(key: String) = (result.data?.get(key) as? Map<*, *>)?.entries?.associate { it.key.toString() to (it.value as Number).toInt() } ?: emptyMap()
        waiting = Waiting(counts("tools"), counts("zones"))
    }
    return waiting
}
