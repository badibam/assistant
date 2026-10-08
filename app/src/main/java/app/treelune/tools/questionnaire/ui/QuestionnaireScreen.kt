package app.treelune.tools.questionnaire.ui

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
import app.treelune.core.ai.data.EnrichmentType
import app.treelune.core.ai.data.MessageSegment
import app.treelune.core.ai.enrichments.PointerConfig
import app.treelune.core.ai.orchestration.ChatRequests
import app.treelune.core.commands.CommandResult
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.CustomFieldsDisplay
import app.treelune.core.fields.CustomFieldsInput
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldsLayout
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.questionnaire.QuestionnaireToolType
import kotlinx.coroutines.launch
import org.json.JSONObject

/** An entry as the screen lists it: its answers and where it stands. */
data class QuestionnaireEntry(val id: String, val timestamp: Long, val status: String, val answers: Map<String, Any?>)

/**
 * A questionnaire's screen (docs/design/missing-tools.md, « Questionnaire »): « Fill in now » and
 * « With the AI » on top, the entries to fill with « Ignore all », then the history, newest first,
 * each entry with its title and state; touching one opens it, its answers changed there. An
 * entry's title is the questionnaire's name and the moment it is about, relative, never stored.
 */
@Composable
fun QuestionnaireScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit, openEntryId: String? = null) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "questionnaire", context = context) }
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<JSONObject?>(null) }
    var entries by remember { mutableStateOf<List<QuestionnaireEntry>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // The passing under way: "" on demand, an entry's id when planned, the one to fill opened
    // from the tile; null when none
    var passing by rememberSaveable { mutableStateOf(openEntryId) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (config == null) { errorMessage = tool.error; return@LaunchedEffect }
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        entries = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { e ->
            QuestionnaireEntry(
                id = e["id"] as String,
                timestamp = (e["timestamp"] as Number).toLong(),
                status = (e["state"] as? Map<*, *>)?.get(QuestionnaireToolType.STATUS) as? String ?: QuestionnaireToolType.Status.FILLED,
                answers = (e["extra"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
            )
        }
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) version++
                is DataChangeEvent.ToolsChanged -> version++
                else -> {}
            }
        }
    }
    errorMessage?.let { message -> LaunchedEffect(message) { UI.Toast(context, message, Duration.LONG); errorMessage = null } }

    fun write(action: suspend () -> CommandResult, onDone: () -> Unit = {}) {
        scope.launch {
            val result = action()
            if (result.isSuccess) { onDone(); version++ } else errorMessage = result.error ?: s.shared("message_error_simple")
        }
    }

    val loadedConfig = config
    val loaded = entries
    if (loadedConfig == null || loaded == null) { UI.LoadingIndicator(); return }
    val settings = ToolConfigSettings.read(QuestionnaireToolType, loadedConfig, context)
    val name = settings.string("name")!!
    val questions: List<FieldDefinition> = loadedConfig.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
    fun title(entry: QuestionnaireEntry) = "$name · ${FormatUtils.formatRelativeTimePast(entry.timestamp, context)}"

    /** With the AI: a chat opened with the questionnaire's message and a pointer to what to fill. */
    fun withAi(entry: QuestionnaireEntry?) {
        fun pointer(kind: ReferenceKind, id: String) = MessageSegment.EnrichmentBlock(EnrichmentType.POINTER,
            PointerConfig(EntrySelection(Reference(kind, id))).toJson().toString())
        ChatRequests.open(listOfNotNull(
            MessageSegment.Text(settings.string(QuestionnaireToolType.AI_MESSAGE) ?: ""),
            pointer(ReferenceKind.TOOL_INSTANCE, toolInstanceId),
            entry?.let { pointer(ReferenceKind.ENTRY, it.id) }
        ))
    }

    val current = passing
    if (current != null) {
        val entry = loaded.find { it.id == current }
        Passing(questions, entry, s, onCancel = { passing = null }, onAnswer = { key, value ->
            if (entry != null) write({ coordinator.processUserAction("tool_data.update", mapOf("id" to entry.id, "extra" to mapOf(key to value))) })
        }, onFinish = { answers ->
            if (entry != null) write({ coordinator.processUserAction("questionnaire.complete", mapOf("tool_instance_id" to toolInstanceId, "id" to entry.id)) }) { passing = null }
            else write({
                val now = System.currentTimeMillis()
                coordinator.processUserAction("tool_data.create", mapOf(
                    "tool_instance_id" to toolInstanceId,
                    "timestamp" to now,
                    "data" to emptyMap<String, Any>(),
                    "extra" to answers.filterValues { it != null },
                    "state" to mapOf(QuestionnaireToolType.STATUS to QuestionnaireToolType.Status.FILLED, QuestionnaireToolType.FILLED_AT to now)
                ))
            }) { passing = null }
        })
        return
    }

    val toFill = loaded.filter { it.status == QuestionnaireToolType.Status.TO_FILL }.sortedByDescending { it.timestamp }
    val history = loaded.filter { it.status != QuestionnaireToolType.Status.TO_FILL }.sortedByDescending { it.timestamp }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = name,
            subtitle = settings.string("description")?.takeIf { it.isNotBlank() },
            icon = settings.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = { if (openId != null) openId = null else onNavigateBack() },
            onRightClick = onConfigureClick
        )

        val open = openId?.let { id -> loaded.find { it.id == id } }
        if (open != null) {
            EntryView(open, title(open), loadedConfig, questions, s) { answers ->
                write({ coordinator.processUserAction("tool_data.update", mapOf("id" to open.id, "extra" to answers)) })
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Button(type = ButtonType.PRIMARY, onClick = { passing = "" }) { UI.Text(s.tool("action_fill_now"), TextType.LABEL) }
            UI.Button(type = ButtonType.SECONDARY, onClick = { withAi(null) }) { UI.Text(s.tool("action_with_ai"), TextType.LABEL) }
        }

        if (toFill.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.tool("tab_to_fill").format(toFill.size), TextType.SUBTITLE)
                UI.Button(type = ButtonType.DEFAULT, onClick = {
                    write({ coordinator.processUserAction("questionnaire.ignore_all", mapOf("tool_instance_id" to toolInstanceId)) })
                }) { UI.Text(s.tool("action_ignore_all"), TextType.LABEL) }
            }
            toFill.forEach { entry ->
                UI.Card(type = CardType.DEFAULT) {
                    Column(modifier = Modifier.padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                        UI.Text(title(entry), TextType.BODY)
                        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                            UI.Button(type = ButtonType.PRIMARY, onClick = { passing = entry.id }) { UI.Text(s.tool("action_fill"), TextType.LABEL) }
                            UI.Button(type = ButtonType.SECONDARY, onClick = { withAi(entry) }) { UI.Text(s.tool("action_with_ai"), TextType.LABEL) }
                            UI.Button(type = ButtonType.DEFAULT, onClick = {
                                write({ coordinator.processUserAction("questionnaire.ignore", mapOf("tool_instance_id" to toolInstanceId, "id" to entry.id)) })
                            }) { UI.Text(s.tool("action_ignore"), TextType.LABEL) }
                        }
                    }
                }
            }
        }

        UI.Text(s.tool("history"), TextType.SUBTITLE)
        if (history.isEmpty()) UI.Text(s.tool("history_empty"), TextType.CAPTION)
        history.forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth().clickable { openId = entry.id }.padding(vertical = UI.Space.XS), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(title(entry), TextType.BODY)
                UI.Text(s.tool("status_${entry.status}"), TextType.CAPTION)
            }
        }
    }
}

/**
 * The passing, one question per screen: « Next » moves on, a question passed stays without an
 * answer. Planned, each answer is saved as it is given ([onAnswer]) and the passing starts at the
 * first question without one; on demand, the answers stay here until the end ([onFinish]).
 */
@Composable
private fun Passing(
    questions: List<FieldDefinition>,
    entry: QuestionnaireEntry?,
    s: StringsContext,
    onCancel: () -> Unit,
    onAnswer: (String, Any?) -> Unit,
    onFinish: (Map<String, Any?>) -> Unit
) {
    val context = LocalContext.current
    var answers by rememberSaveable(stateSaver = app.treelune.core.ui.FieldValuesSaver) { mutableStateOf(entry?.answers ?: emptyMap()) }
    var index by rememberSaveable { mutableIntStateOf(questions.indexOfFirst { entry?.answers?.get(it.name) == null }.coerceAtLeast(0)) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
        UI.PageHeader(title = s.tool("passing_title").format(index + 1, questions.size), leftButton = ButtonAction.BACK, onLeftClick = onCancel)
        if (questions.isEmpty()) { UI.Text(s.tool("no_question"), TextType.CAPTION); return@Column }
        val question = questions[index]
        question.description?.let { UI.Text(it, TextType.CAPTION) }
        FieldInput(question, answers[question.name], { answers = answers + (question.name to it) }, context, required = false)
        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            if (index > 0) UI.Button(type = ButtonType.SECONDARY, onClick = { index-- }) { UI.Text(s.tool("action_previous"), TextType.LABEL) }
            val last = index == questions.size - 1
            UI.Button(type = ButtonType.PRIMARY, onClick = {
                onAnswer(question.name, answers[question.name])
                if (last) onFinish(answers) else index++
            }) { UI.Text(s.tool(if (last) "action_finish" else "action_next"), TextType.LABEL) }
        }
    }
}

/** An entry read, every answer in full, and changed in place. */
@Composable
private fun EntryView(entry: QuestionnaireEntry, title: String, config: JSONObject, questions: List<FieldDefinition>, s: StringsContext, onSave: (Map<String, Any?>) -> Unit) {
    val context = LocalContext.current
    var editing by rememberSaveable(entry.id) { mutableStateOf(false) }
    var draft by rememberSaveable(entry.id, stateSaver = app.treelune.core.ui.FieldValuesSaver) { mutableStateOf(entry.answers) }
    UI.Text(title, TextType.SUBTITLE)
    UI.Text(s.tool("status_${entry.status}"), TextType.CAPTION)
    if (!editing) {
        CustomFieldsDisplay(QuestionnaireToolType, config, entry.answers, FieldsLayout.EXPANDED, context)
        UI.Button(type = ButtonType.PRIMARY, onClick = { draft = entry.answers; editing = true }) { UI.Text(s.tool("action_edit"), TextType.LABEL) }
    } else {
        CustomFieldsInput(customFieldsMetadata = questions, values = draft, onValuesChange = { draft = it }, context = context)
        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Button(type = ButtonType.PRIMARY, onClick = { onSave(questions.associate { it.name to draft[it.name] }); editing = false }) { UI.Text(s.shared("action_save"), TextType.LABEL) }
            UI.Button(type = ButtonType.SECONDARY, onClick = { editing = false }) { UI.Text(s.shared("action_cancel"), TextType.LABEL) }
        }
    }
}
