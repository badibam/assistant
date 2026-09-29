package com.assistant.tools.chart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.charts.ChartDetails
import com.assistant.core.charts.ChartSpec
import com.assistant.core.charts.ChartTable
import com.assistant.core.charts.ChartView
import com.assistant.core.charts.Hit
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.selection.TimeResolver
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import com.assistant.tools.chart.ChartSources
import com.assistant.tools.chart.ChartToolType
import org.json.JSONObject

/** What the screen draws once read: the chart, its tables, its period at the moment read. */
private data class Drawn(val spec: ChartSpec, val tables: List<ChartTable>, val period: Pair<Long?, Long?>, val now: Long)

/**
 * A chart's screen (docs/design/missing-tools.md, « Graphique »): the chart, its legend under it,
 * and under that what a touch found — every column of the row, or why a value is missing and the
 * entries to correct. No period is set here: a chart is made for one need, its period is its
 * config's, relative to the moment it is shown.
 *
 * Read again when its config changes, and when any tool's entries do: what it reads is in them.
 */
@Composable
fun ChartScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "chart", context = context) }

    var config by remember { mutableStateOf<JSONObject?>(null) }
    var drawn by remember { mutableStateOf<Drawn?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    // What a touch found: a passing look, gone with a rotation like any tooltip, never produced by the user
    var touched by remember { mutableStateOf<Hit?>(null) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        val loaded = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (loaded == null) { problem = tool.error ?: s.shared("tools_loading_config"); return@LaunchedEffect }
        config = loaded
        val now = System.currentTimeMillis()
        drawn = try {
            val spec = ChartSpec.of(loaded) { s.shared(it) }
            Drawn(spec, ChartSources(context).tables(spec, now), spec.period.instants(TimeResolver.at(now)), now).also { problem = null }
        } catch (e: IllegalArgumentException) {
            problem = e.message; null
        } catch (e: IllegalStateException) {
            LogManager.ui("ChartScreen: chart $toolInstanceId not read: ${e.message}", "WARN")
            problem = e.message; null
        }
        touched = null
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged, is DataChangeEvent.ToolsChanged -> version++
                else -> {}
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val loaded = config
        val settings = loaded?.let { ToolConfigSettings.read(ChartToolType, it, context) }
        UI.PageHeader(
            title = settings?.string("name") ?: ChartToolType.getDisplayName(context),
            subtitle = settings?.string("description")?.takeIf { it.isNotBlank() },
            icon = settings?.string("icon_name"),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onNavigateBack,
            onRightClick = onConfigureClick
        )
        val chart = drawn
        when {
            problem != null -> UI.Text(s.tool("screen_problem").format(problem), TextType.ERROR)
            chart == null -> UI.LoadingIndicator()
            chart.tables.all { it.rows.isEmpty() } -> UI.Text(s.tool("screen_empty"), TextType.CAPTION)
            else -> {
                ChartView(chart.spec, chart.tables, chart.period, chart.now, onTap = { touched = it })
                touched?.let { ChartDetails(it) } ?: UI.Text(s.tool("screen_touch"), TextType.CAPTION)
            }
        }
    }
}
