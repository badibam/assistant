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
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.LogManager

/** The stopwatches running on the screen shown, which the tiles mark; none where no screen provides them. */
val LocalRunning = androidx.compose.runtime.staticCompositionLocalOf { Running() }

/** The tools with a stopwatch running on one of their entries, and the zones holding one (tools.running). */
data class Running(val tools: Set<String> = emptySet(), val zones: Set<String> = emptySet()) {
    fun tool(id: String): Boolean = id in tools
    fun zone(id: String): Boolean = id in zones
}

/**
 * The stopwatches running in every tool, read again whenever tools or their entries change;
 * none while they are read, and none when the read fails, which is logged.
 */
@Composable
fun rememberRunning(): Running {
    val context = LocalContext.current
    var running by remember { mutableStateOf(Running()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged || event is DataChangeEvent.ToolsChanged) version++
        }
    }
    LaunchedEffect(version) {
        val result = Coordinator(context).processUserAction("tools.running", emptyMap())
        if (!result.isSuccess) {
            LogManager.ui("rememberRunning: not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        fun ids(key: String) = (result.data?.get(key) as? List<*>).orEmpty().map { it.toString() }.toSet()
        running = Running(ids("tools"), ids("zones"))
    }
    return running
}
