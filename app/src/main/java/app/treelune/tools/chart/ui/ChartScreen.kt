package app.treelune.tools.chart.ui

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
import app.treelune.tools.chart.Hit
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.chart.ChartToolType
import org.json.JSONObject

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
    var reading by remember { mutableStateOf<ChartReading?>(null) }
    var version by remember { mutableIntStateOf(0) }
    // What a touch found: a passing look, gone with a rotation like any tooltip, never produced by the user
    var touched by remember { mutableStateOf<Hit?>(null) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        val loaded = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        reading = if (loaded == null) ChartReading.Problem(tool.error ?: s.shared("tools_loading_config"))
            else ChartReading.of(loaded, context).also { config = loaded }
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
        modifier = Modifier.fillMaxSize().padding(UI.Space.L).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        val loaded = config
        val settings = loaded?.let { ToolConfigSettings.read(ChartToolType, it, context) }
        UI.PageHeader(
            title = settings?.string("name") ?: ChartToolType.getDisplayName(context),
            subtitle = settings?.string("description")?.takeIf { it.isNotBlank() },
            icon = settings?.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings?.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onNavigateBack,
            onRightClick = onConfigureClick
        )
        when (val chart = reading) {
            null -> UI.LoadingIndicator()
            is ChartReading.Problem -> UI.Text(s.tool("screen_problem").format(chart.message), TextType.ERROR)
            is ChartReading.Drawn -> if (chart.empty) UI.Text(s.tool("screen_empty"), TextType.CAPTION) else {
                ChartView(chart.spec, chart.tables, chart.period, chart.now, onTap = { touched = it })
                touched?.let { ChartDetails(it) } ?: UI.Text(s.tool("screen_touch"), TextType.CAPTION)
            }
        }
    }
}
