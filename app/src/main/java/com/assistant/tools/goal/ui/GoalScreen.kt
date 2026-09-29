package com.assistant.tools.goal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.formatValue
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.DateUtils
import com.assistant.core.utils.JsonUtils
import com.assistant.tools.goal.CriterionKind
import com.assistant.tools.goal.GoalDefinition
import com.assistant.tools.goal.GoalToolType
import com.assistant.tools.goal.Met
import kotlinx.coroutines.launch
import org.json.JSONObject

/** An attempt as the screen lists it. */
data class AttemptRow(val id: String, val start: Long, val status: String, val periodEnd: Long?, val validatedBy: String?)

/**
 * A goal's screen (docs/design/missing-tools.md, « Objectif »): the attempt running on top, its
 * criteria by sub-goal, each value against its condition, entered where it is entered, the count
 * against what is asked, the time left and « Valider »; below, the attempts waiting to be
 * validated, then the history, each opened read-only with « Rouvrir ».
 */
@Composable
fun GoalScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "goal", context = context) }
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<JSONObject?>(null) }
    var attempts by remember { mutableStateOf<List<AttemptRow>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (config == null) { errorMessage = tool.error; return@LaunchedEffect }
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId, "fields" to listOf("id", "timestamp", "state")))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        attempts = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { e ->
            val state = e["state"] as? Map<*, *> ?: emptyMap<String, Any>()
            AttemptRow(e["id"] as String, (e["timestamp"] as Number).toLong(), state[GoalToolType.STATUS] as? String ?: "",
                (state[GoalToolType.PERIOD_END] as? Number)?.toLong(), state[GoalToolType.VALIDATED_BY] as? String)
        }
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) version++
                is DataChangeEvent.ToolsChanged, DataChangeEvent.VariablesChanged -> version++
                else -> {}
            }
        }
    }
    errorMessage?.let { message -> LaunchedEffect(message) { UI.Toast(context, message, Duration.LONG); errorMessage = null } }

    fun operation(name: String, id: String) {
        scope.launch {
            val result = coordinator.processUserAction("goal.$name", mapOf("tool_instance_id" to toolInstanceId, "id" to id))
            if (!result.isSuccess) errorMessage = result.error else version++
        }
    }

    val loadedConfig = config
    val loaded = attempts
    if (loadedConfig == null || loaded == null) { UI.LoadingIndicator(); return }
    val settings = ToolConfigSettings.read(GoalToolType, loadedConfig, context)
    val current = loaded.filter { it.status == GoalToolType.Status.ACTIVE }.maxByOrNull { it.start }
    val toValidate = loaded.filter { it.status == GoalToolType.Status.TO_VALIDATE }.sortedByDescending { it.start }
    val history = loaded.filter { it.status in GoalToolType.Status.LOCKED }.sortedByDescending { it.start }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.PageHeader(
            title = settings.string("name")!!,
            subtitle = settings.string("description")?.takeIf { it.isNotBlank() },
            icon = settings.string("icon_name"),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = { if (openId != null) openId = null else onNavigateBack() },
            onRightClick = onConfigureClick
        )

        val open = openId?.let { id -> loaded.find { it.id == id } }
        if (open != null) {
            AttemptCard(open, loadedConfig, toolInstanceId, version, s, onError = { errorMessage = it },
                onValidate = { operation("validate", open.id) }, onReopen = { operation("reopen", open.id) })
            return@Column
        }

        if (current != null) AttemptCard(current, loadedConfig, toolInstanceId, version, s, onError = { errorMessage = it },
            onValidate = { operation("validate", current.id) }, onReopen = {})
        else UI.Text(s.tool("no_attempt"), TextType.CAPTION)

        if (toValidate.isNotEmpty()) {
            UI.Text(s.tool("tab_to_validate").format(toValidate.size), TextType.SUBTITLE)
            toValidate.forEach { attempt -> AttemptLine(attempt, s) { openId = attempt.id } }
        }
        if (history.isNotEmpty()) {
            UI.Text(s.tool("history"), TextType.SUBTITLE)
            UI.Text(history.take(30).joinToString(" ") { dot(it.status) }, TextType.BODY)
            history.forEach { attempt -> AttemptLine(attempt, s) { openId = attempt.id } }
        }
    }
}

/** A verdict's dot in the history's strip. */
fun dot(status: String): String = when (status) {
    GoalToolType.Status.SUCCEEDED -> "●"
    GoalToolType.Status.FAILED -> "○"
    else -> "·"
}

@Composable
private fun AttemptLine(attempt: AttemptRow, s: StringsContext, onOpen: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.Text("${dot(attempt.status)} ${DateUtils.formatFullDateTime(attempt.start)}", TextType.BODY)
        UI.Text(s.tool("status_${attempt.status}"), TextType.CAPTION)
    }
}

/**
 * One attempt: each criterion's line — its value against its condition, entered in place while
 * the attempt is open — by sub-goal, the count against what is asked, the time left, and what can
 * be done with it.
 */
@Composable
private fun AttemptCard(
    attempt: AttemptRow,
    config: JSONObject,
    toolInstanceId: String,
    version: Int,
    s: StringsContext,
    onError: (String?) -> Unit,
    onValidate: () -> Unit,
    onReopen: () -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    var judgement by remember(attempt.id) { mutableStateOf<Map<*, *>?>(null) }
    var local by remember(attempt.id) { mutableIntStateOf(0) }
    LaunchedEffect(attempt.id, version, local) {
        val result = coordinator.processUserAction("goal.evaluate", mapOf("tool_instance_id" to toolInstanceId, "id" to attempt.id))
        if (result.isSuccess) judgement = result.data else onError(result.error)
    }
    val locked = attempt.status in GoalToolType.Status.LOCKED
    val definition = GoalDefinition.of(config)
    val loaded = judgement

    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(DateUtils.formatFullDateTime(attempt.start) + (attempt.periodEnd?.let { " → " + DateUtils.formatFullDateTime(it) } ?: ""), TextType.CAPTION)
            UI.Text(s.tool("status_${attempt.status}") + (attempt.validatedBy?.let { " · " + s.tool("validated_by_$it") } ?: ""), TextType.LABEL)
            if (loaded == null) { UI.LoadingIndicator(); return@Column }
            val criteria = (loaded["criteria"] as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
            UI.Text(s.tool("count").format(loaded["met"], loaded["required"]) + " · " + s.tool("verdict_${(loaded["verdict"] as? String ?: "UNKNOWN").lowercase()}"), TextType.SUBTITLE)
            attempt.periodEnd?.takeIf { attempt.status == GoalToolType.Status.ACTIVE }?.let { end ->
                val left = end - System.currentTimeMillis()
                if (left > 0) UI.Text(s.tool("time_left").format(com.assistant.core.fields.Durations.format(left, mapOf("precision" to "MINUTE"), Strings.`for`(context = context))), TextType.CAPTION)
            }
            criteria.forEach { row ->
                val key = row["key"] as String
                val criterion = definition.allCriteria.find { it.key == key }
                val met = Met.valueOf(row["met"] as? String ?: "UNKNOWN")
                val entered = criterion?.enteredField()
                if (!locked && entered != null) {
                    FieldInput(entered, row["value"], { value ->
                        scope.launch {
                            val result = coordinator.processUserAction("tool_data.update", mapOf("id" to attempt.id, "data" to mapOf(key to value)))
                            if (!result.isSuccess) onError(result.error) else local++
                        }
                    }, context, required = false)
                    UI.Text(condition(criterion, s) + " · " + s.tool("met_${met.name.lowercase()}"), TextType.CAPTION)
                } else {
                    val shown = row["failure"] as? String ?: row["value"]?.let { value ->
                        (entered ?: FieldDefinition(key, key, null, FieldType.NUMERIC, false, mapOf("decimals" to 2))).formatValue(value, context)
                    } ?: s.shared("label_no_value")
                    UI.Text("${row["name"]} : $shown" + (criterion?.let { " / " + condition(it, s) } ?: "") + " · " + s.tool("met_${met.name.lowercase()}"), TextType.BODY)
                    val value = (row["value"] as? Number)?.toDouble()
                    val target = (criterion?.target as? Number)?.toDouble()
                    if (value != null && target != null && target > 0 && criterion.kind != CriterionKind.ENTERED_BOOLEAN) {
                        val scale = if (criterion.kind == CriterionKind.ENTERED_DURATION) 1.0 else criterion.targetUnit.millis
                        UI.Gauge(fraction = (value / (target * scale)).toFloat().coerceIn(0f, 1f))
                    }
                }
            }
            (loaded["sub_goals"] as? Map<*, *>)?.forEach { (name, subMet) ->
                UI.Text("$name · " + s.tool("met_${(subMet as String).lowercase()}"), TextType.CAPTION)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!locked) UI.Button(type = ButtonType.PRIMARY, onClick = onValidate) { UI.Text(s.tool("action_validate"), TextType.LABEL) }
                else UI.Button(type = ButtonType.SECONDARY, onClick = onReopen) { UI.Text(s.tool("action_reopen"), TextType.LABEL) }
            }
        }
    }
}

/** A criterion's condition in words: "≥ 7 h", "= oui". */
private fun condition(criterion: com.assistant.tools.goal.Criterion, s: StringsContext): String {
    if (criterion.kind == CriterionKind.ENTERED_BOOLEAN) return "= " + s.shared(if (criterion.target == false) "label_no" else "label_yes")
    val target = (criterion.target as? Number)?.let {
        if (criterion.kind == CriterionKind.ENTERED_DURATION) java.time.Duration.ofMillis(it.toLong()).toMinutes().toString() + " min"
        else (if (it.toDouble() % 1.0 == 0.0) it.toLong().toString() else it.toString()) +
            (if (criterion.targetUnit != com.assistant.tools.goal.TargetUnit.NUMBER) " " + s.tool("target_unit_${criterion.targetUnit.name.lowercase()}") else "") +
            (criterion.unit?.let { u -> " $u" } ?: "")
    } ?: "?"
    return "${criterion.op} $target"
}
