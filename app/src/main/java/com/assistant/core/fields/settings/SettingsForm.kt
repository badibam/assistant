package com.assistant.core.fields.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.toFieldConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.assistant.core.fields.FieldValue
import com.assistant.core.strings.Strings
import com.assistant.core.utils.JsonUtils
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.CardType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import org.json.JSONArray
import org.json.JSONObject

/**
 * A part of a form drawn by its owner rather than by SettingsForm: an icon picker, a zone's tool
 * groups, a schedule editor with its summary. It is attached by the name of the setting it edits,
 * so the declaration stays free of screens.
 */
interface SettingEditor {
    /**
     * @param value The stored value: a value, a JSONObject for a group, a JSONArray for a list;
     *   null when absent
     * @param onChange The new value, or null to remove the setting
     */
    @Composable
    fun Edit(value: Any?, onChange: (Any?) -> Unit)
}

/**
 * The fields of the rows a config describes, by where a setting stands in it: what a setting
 * marked SettingNode.Field.rowField chooses among, and a condition put on each row
 * (SettingNode.Condition.onRow) compares. A chart's columns differ from one layer to another, so
 * the form's owner, who knows what a row is, answers for each place.
 */
interface RowFields {
    /**
     * @param root The whole config being edited
     * @param path The keys and positions leading from [root] to the object holding the setting
     * @return The fields by name; null while they are read
     */
    @Composable
    fun at(root: JSONObject, path: List<Any>): Map<String, com.assistant.core.fields.FieldDefinition>?
}

/** Where a form stands in the config it edits, for the settings that depend on it (RowFields). */
private data class Place(val root: JSONObject, val path: List<Any>, val rows: RowFields?) {
    fun into(vararg steps: Any) = copy(path = path + steps)
}

private val LocalPlace = androidx.compose.runtime.compositionLocalOf<Place?> { null }

/** [content] drawn one step further into the config: a group's name, a list's name and a position. */
@Composable
private fun Into(vararg steps: Any, content: @Composable () -> Unit) {
    val place = LocalPlace.current
    if (place == null) content()
    else androidx.compose.runtime.CompositionLocalProvider(LocalPlace provides place.into(*steps)) { content() }
}

/** The fields of the rows where the form stands; a declaration that asks for them without an owner giving them is a mistake. */
@Composable
private fun rowFields(): Map<String, com.assistant.core.fields.FieldDefinition>? {
    val place = LocalPlace.current
    val rows = place?.rows ?: error("A setting chooses among the fields of rows, and the form was given none (RowFields)")
    return rows.at(place.root, place.path)
}

/**
 * One step from a page of the form to a page it opens: a group by its name, or an element of a
 * list by the list's name and its position.
 */
private sealed class Step {
    data class Group(val name: String) : Step()
    data class Element(val list: String, val index: Int) : Step()

    /** The keys and positions it goes through in the stored config. */
    val keys: List<Any> get() = when (this) {
        is Group -> listOf(name)
        is Element -> listOf(list, index)
    }
}

/** The pages open, from the root, kept across a recreation of the screen. */
private val TrailSaver: androidx.compose.runtime.saveable.Saver<List<Step>, String> = androidx.compose.runtime.saveable.Saver(
    save = { trail ->
        JSONArray(trail.map { step ->
            when (step) {
                is Step.Group -> JSONObject().put("group", step.name)
                is Step.Element -> JSONObject().put("list", step.list).put("index", step.index)
            }
        }).toString()
    },
    restore = { saved ->
        val array = JSONArray(saved)
        (0 until array.length()).map { i ->
            val step = array.getJSONObject(i)
            if (step.has("group")) Step.Group(step.getString("group")) else Step.Element(step.getString("list"), step.getInt("index"))
        }
    }
)

/** The page being drawn, [trail] from the root, and how a line on it opens the page under it. */
private class Pages(val trail: List<Step>, private val go: (List<Step>) -> Unit) {
    fun open(step: Step) = go(trail + step)
}

private val LocalPages = androidx.compose.runtime.compositionLocalOf<Pages?> { null }

/**
 * Whether settings hold more than values — a group, a list, or a brick drawn by its selector —
 * which makes the object holding them a page of its own (docs/design/settings-pages.md). A
 * variant counts by all its options, so that an object does not turn from a page into a card
 * when another one is chosen.
 */
private fun List<SettingNode>.isPage(): Boolean = any { node ->
    when (node) {
        is SettingNode.Group, is SettingNode.ListOf, is SettingNode.Term,
        is SettingNode.Selection, is SettingNode.Condition -> true
        is SettingNode.Section -> node.nodes.isPage()
        is SettingNode.Variant -> node.cases.values.any { it.isPage() }
        is SettingNode.Field, is SettingNode.Period -> false
    }
}

/**
 * The nodes stored in the object these nodes describe, as the form shows them over [config]: a
 * section's, and the case of a variant that [config] chooses.
 */
private fun List<SettingNode>.shown(config: JSONObject): List<SettingNode> = flatMap { node ->
    when (node) {
        is SettingNode.Section -> node.nodes.shown(config)
        is SettingNode.Variant -> {
            val chosen = config.optString(node.selector.definition.name).ifEmpty { node.selector.default?.toString() }
            listOf(node) + (node.cases[chosen] ?: emptyList()).shown(config)
        }
        else -> listOf(node)
    }
}

/** A page reached by a trail: its settings, the object they are stored in, its name in the path. */
private data class Page(val nodes: List<SettingNode>, val config: JSONObject, val label: String)

/**
 * The pages [trail] goes through from [nodes] over [config], as far as they still exist: an
 * element removed, a group whose variant was switched away, end it there.
 */
private fun pagesOf(nodes: List<SettingNode>, config: JSONObject, trail: List<Step>): List<Page> {
    val pages = mutableListOf<Page>()
    var level = nodes
    var current = config
    for (step in trail) {
        val page = when (step) {
            is Step.Group -> level.shown(current).filterIsInstance<SettingNode.Group>().firstOrNull { it.name == step.name }
                ?.let { Page(it.nodes, current.optJSONObject(it.name) ?: JSONObject(), it.label) }
            is Step.Element -> level.shown(current).filterIsInstance<SettingNode.ListOf>().firstOrNull { it.name == step.list }?.let { list ->
                val element = current.optJSONArray(list.name)?.opt(step.index) as? JSONObject
                val shape = list.item as? SettingNode.Item.Of
                if (element == null || shape == null) null
                else Page(shape.nodes, element, summaryText(list, element, step.index))
            }
        } ?: break
        pages += page
        level = page.nodes
        current = page.config
    }
    return pages
}

/** [container] with [value] put where [keys] lead, every object and list on the way copied. */
private fun replaced(container: Any, keys: List<Any>, value: Any): Any {
    if (keys.isEmpty()) return value
    val key = keys.first()
    return when (container) {
        is JSONObject -> JSONObject(container.toString()).apply {
            put(key as String, replaced(container.opt(key)?.takeIf { it != JSONObject.NULL } ?: JSONObject(), keys.drop(1), value))
        }
        is JSONArray -> JSONArray(container.toString()).apply { put(key as Int, replaced(container.get(key), keys.drop(1), value)) }
        else -> error("Nothing to step into at '$key'")
    }
}

/**
 * The form of any settings declaration (docs/DATA.md): a field by the
 * input of its field type, a group as a card, a list with add, remove and reorder, a variant with
 * the settings of the option chosen, a section as a titled card over settings stored beside it.
 * A setting the app writes itself (SettingNode.Field.systemWritten) is not shown; a secret one
 * is entered masked; a schedule (ScheduleSettings.group) opens its own editor.
 *
 * A group, or an element of a list, holding more than values is a page of its own
 * (docs/design/settings-pages.md): one line in its parent's page, which opens it full width under
 * the path from the root; the phone's back goes up one page. Every page edits the same [config].
 *
 * Stateless: [config] is the object being edited, and every change hands a new one to [onChange];
 * the form keeps only which page is open.
 *
 * @param editors Parts drawn by their owner, by setting name (SettingEditor), at the top level of
 *   the declaration only: a name inside a group or a list element may mean something else there
 * @param rows The fields of the rows [config] describes, for the settings that choose among them;
 *   given where [config] is the whole config, never inside it
 * @param scroll The scrolling of the screen around the form, brought back to its top when
 *   another page opens
 * @param root What its owner shows with the settings of the root page and no other, above them:
 *   what is not a setting of [config] (a tool's zone)
 */
@Composable
fun SettingsForm(
    nodes: List<SettingNode>,
    config: JSONObject,
    onChange: (JSONObject) -> Unit,
    context: Context,
    editors: Map<String, SettingEditor> = emptyMap(),
    rows: RowFields? = null,
    scroll: androidx.compose.foundation.ScrollState? = null,
    root: (@Composable () -> Unit)? = null
) {
    var trail by rememberSaveable(stateSaver = TrailSaver) { mutableStateOf(emptyList<Step>()) }
    val pages = pagesOf(nodes, config, trail)
    // A page that no longer exists (its element removed by the AI, its variant switched) gives
    // way to the deepest one still there
    if (pages.size < trail.size) androidx.compose.runtime.SideEffect { trail = trail.take(pages.size) }
    val shown = trail.take(pages.size)
    androidx.activity.compose.BackHandler(enabled = shown.isNotEmpty()) { trail = shown.dropLast(1) }

    // A page opened, or left for another, shows from the top of the screen; not when it opens
    var drawn by remember { mutableStateOf<List<Step>?>(null) }
    androidx.compose.runtime.LaunchedEffect(shown) {
        if (drawn != null && drawn != shown) scroll?.scrollTo(0)
        drawn = shown
    }

    val keys = shown.flatMap { it.keys }
    val page = pages.lastOrNull()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPages provides Pages(shown) { trail = it },
        LocalPlace provides rows?.let { Place(config, keys, it) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            if (page == null) {
                root?.invoke()
                NodesForm(nodes, nodes, config, onChange, context, editors)
            } else {
                PageHeader(pages.map { it.label }, context) { depth -> trail = shown.take(depth) }
                // Owners attach editors at the top level only
                NodesForm(page.nodes, page.nodes, page.config, { changed -> onChange(replaced(config, keys, changed) as JSONObject) }, context, emptyMap())
            }
        }
    }
}

/**
 * The head of a page under the root: a button up one page, the page's name, and under it the
 * path it was reached by, each step going back to its page.
 *
 * @param labels The pages from the root's first to the one open
 * @param onGo The number of pages to keep open, 0 for the root
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageHeader(labels: List<String>, context: Context, onGo: (Int) -> Unit) {
    val s = remember { Strings.`for`(context = context) }
    UI.Card(type = CardType.DEFAULT, highlight = true) {
        Row(modifier = Modifier.padding(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
            UI.ActionButton(action = ButtonAction.BACK, display = ButtonDisplay.ICON, size = Size.M, onClick = { onGo(labels.size - 1) })
            Column(modifier = Modifier.weight(1f).padding(start = UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                UI.Text(labels.last(), TextType.SUBTITLE)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                    (listOf(s.shared("settings_path_root")) + labels.dropLast(1)).forEachIndexed { depth, label ->
                        if (depth > 0) UI.Text(s.shared("settings_path_separator"), TextType.CAPTION)
                        Box(modifier = Modifier.clickable { onGo(depth) }) { UI.Text(label, TextType.CAPTION) }
                    }
                }
            }
        }
    }
}

/**
 * The line of a page in its parent's: its [title], [summary] under it, then what the page holds
 * where [nodes] describe [config] — the phrase of each brick, the count of each list; touched, it
 * opens the page. [trailing] keeps its own gestures.
 */
@Composable
private fun PageLine(
    title: (@Composable () -> Unit)?,
    summary: @Composable () -> Unit,
    nodes: List<SettingNode>,
    config: JSONObject,
    context: Context,
    onOpen: () -> Unit,
    trailing: @Composable () -> Unit
) {
    val counts = counts(nodes, config, context)
    val phrases = brickPhrases(nodes, config, context)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(start = UI.Space.M, top = UI.Space.S, bottom = UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                title?.invoke()
                summary()
                phrases.forEach { UI.Text(it, TextType.CAPTION, maxLines = 2) }
                if (counts.isNotEmpty()) UI.Text(counts.joinToString(" ${Strings.`for`(context = context).shared("list_item_summary_separator")} "), TextType.CAPTION, maxLines = 2)
            }
            UI.Icon("chevron-right", size = 20.dp)
        }
        trailing()
    }
}

/**
 * What each term and selection of entries [config] holds where [nodes] describe it says, in one
 * line: "Meals › Kcal · Sum · Filters: 2", a variable's name, a constant. The names are read, so
 * the lines come once read; a read that fails says so in its line.
 */
@Composable
private fun brickPhrases(nodes: List<SettingNode>, config: JSONObject, context: Context): List<String> {
    val bricks = nodes.shown(config).mapNotNull { node ->
        when (node) {
            is SettingNode.Term -> config.optJSONObject(node.name)?.let { node.label to it }
            is SettingNode.Selection -> config.optJSONObject(node.name)?.let { node.label to JSONObject().put("selection", it) }
            else -> null
        }
    }
    val key = bricks.joinToString { it.second.toString() }
    val phrases by androidx.compose.runtime.produceState(emptyList<String>(), key) {
        val s = Strings.`for`(context = context)
        value = bricks.map { (label, brick) ->
            s.shared("settings_count").format(label, try { brickPhrase(brick, context) } catch (e: IllegalStateException) { e.message ?: "" })
        }
    }
    return phrases
}

/**
 * The phrase of one brick as stored: `{"constant"}`, `{"variable"}`, `{"reading"}`, or a selection
 * wrapped as `{"selection"}`.
 *
 * @throws IllegalStateException when a name cannot be read
 */
private suspend fun brickPhrase(brick: JSONObject, context: Context): String {
    val s = Strings.`for`(context = context)
    val separator = " ${s.shared("list_item_summary_separator")} "
    suspend fun name(kind: com.assistant.core.selection.ReferenceKind, id: String): String =
        when (val named = com.assistant.core.fields.loadReferenceNames(listOf(com.assistant.core.selection.Reference(kind, id)), context).values.firstOrNull()) {
            is com.assistant.core.fields.ReferenceName.Named -> named.name
            else -> s.shared("pointer_target_deleted")
        }
    // The tool a selection reads, its field by its label, and the number of its filters
    suspend fun selection(selection: JSONObject, field: String?): List<String> {
        val target = com.assistant.core.fields.ReferenceTarget.referenceOf(selection.opt("target")) ?: return emptyList()
        val toolId = target.id ?: return emptyList()
        val tool = name(target.kind, toolId)
        val fieldLabel = field?.let { path -> com.assistant.core.fields.ToolFields.filterable(toolId, context, s)[path]?.displayName ?: path }
        val filters = selection.optJSONArray("filters")?.length() ?: 0
        return listOfNotNull(
            listOfNotNull(tool, fieldLabel).joinToString(" ${s.shared("settings_path_separator")} "),
            s.shared("settings_phrase_filters").format(filters).takeIf { filters > 0 }
        )
    }
    return when {
        brick.has("constant") -> JsonUtils.toValue(brick.opt("constant").takeIf { it != JSONObject.NULL })?.toString() ?: ""
        brick.has("variable") -> brick.optString("variable").takeIf { it.isNotEmpty() }
            ?.let { name(com.assistant.core.selection.ReferenceKind.VARIABLE, it) } ?: ""
        brick.has("reading") -> {
            val reading = brick.getJSONObject("reading")
            val parts = selection(reading.optJSONObject("selection") ?: JSONObject(), reading.optString("field").takeIf { it.isNotEmpty() }).toMutableList()
            reading.optString("reduction").takeIf { it.isNotEmpty() }?.let { parts.add(minOf(1, parts.size), s.shared("reduction_${it.lowercase()}")) }
            parts.joinToString(separator)
        }
        else -> selection(brick.getJSONObject("selection"), null).joinToString(separator)
    }
}

/** The lists [config] holds where [nodes] describe it, those not empty, each with its number of elements. */
private fun counts(nodes: List<SettingNode>, config: JSONObject, context: Context): List<String> {
    val s = Strings.`for`(context = context)
    return nodes.shown(config).filterIsInstance<SettingNode.ListOf>().mapNotNull { list ->
        config.optJSONArray(list.name)?.length()?.takeIf { it > 0 }?.let { s.shared("settings_count").format(list.label, it) }
    }
}

/**
 * [nodes] drawn over [config], the object stored under [level]: the whole object, which a section
 * shares with its parent and a variant needs to switch.
 */
@Composable
private fun NodesForm(
    nodes: List<SettingNode>,
    level: List<SettingNode>,
    config: JSONObject,
    onChange: (JSONObject) -> Unit,
    context: Context,
    editors: Map<String, SettingEditor>
) {
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        nodes.forEach { node -> NodeForm(node, level, config, onChange, context, editors) }
    }
}

@Composable
private fun NodeForm(
    node: SettingNode,
    level: List<SettingNode>,
    config: JSONObject,
    onChange: (JSONObject) -> Unit,
    context: Context,
    editors: Map<String, SettingEditor>
) {
    fun set(name: String, value: Any?) = onChange(JSONObject(config.toString()).apply {
        if (value == null || value == JSONObject.NULL || (value is JSONObject && value.length() == 0)) remove(name) else put(name, value)
    })

    when (node) {
        is SettingNode.Field -> {
            if (node.systemWritten) return
            val name = node.definition.name
            val stored = config.opt(name)?.takeIf { it != JSONObject.NULL }
            val editor = editors[name]
            when {
                editor != null -> editor.Edit(stored) { set(name, it) }
                // A field of the rows where the setting stands, chosen among them
                node.rowField -> RowFieldChoice(node.definition.displayName, stored as? String, node.required) { set(name, it) }
                // A field of the tool the setting beside it designates, chosen among its fields
                node.fieldOf != null -> ToolFieldChoice(node, config.opt(node.fieldOf), stored as? String, context) { set(name, it) }
                // A value of the field this object defines, entered as that field: its type, and
                // the options or bounds set above it, read from the object as it is being edited
                node.valueOfDefined -> {
                    val defined = node.definition.copy(
                        description = null,
                        config = config.optJSONObject("config")?.toFieldConfig()
                    )
                    // A choice without options yet has nothing to offer
                    val hasValues = defined.type != com.assistant.core.fields.FieldType.CHOICE ||
                        com.assistant.core.fields.ChoiceSettings.fromConfig(defined.config).options.isNotEmpty()
                    if (hasValues) FieldInput(defined, JsonUtils.toValue(stored), { set(name, it) }, context, required = false)
                }
                // A secret is entered masked, whatever its field type says of its length
                node.secret -> UI.FormField(
                    label = node.definition.displayName,
                    value = stored?.toString() ?: "",
                    onChange = { set(name, it.ifEmpty { null }) },
                    fieldType = com.assistant.core.ui.FieldType.PASSWORD,
                    required = node.required
                )
                // A typed setting (a number, a text) with a default shows it until the user types in
                // it; from then on it shows what is typed, empty included, the label saying what
                // empty means. Shown again once emptied, the default would come back at once, and
                // the input could never be emptied to type another value.
                node.default != null &&
                    (node.definition.type == com.assistant.core.fields.FieldType.NUMERIC || node.definition.type == com.assistant.core.fields.FieldType.TEXT) -> {
                    var typed by rememberSaveable { mutableStateOf(false) }
                    val shown = if (stored == null && !typed) node.default else JsonUtils.toValue(stored)
                    val definition = if (stored == null && typed) {
                        val shownDefault = (node.default as? Number)?.let { java.math.BigDecimal(it.toString()).stripTrailingZeros().toPlainString() } ?: node.default.toString()
                        node.definition.copy(displayName = Strings.`for`(context = context).shared("label_with_default").format(node.definition.displayName, shownDefault))
                    } else node.definition
                    FieldInput(definition, shown, { typed = true; set(name, it) }, context, required = node.required)
                }
                // An absent setting shows the value its absence means. The input takes the Kotlin
                // form of a value: a list of options, not the JSONArray they are stored as
                else -> FieldInput(node.definition, JsonUtils.toValue(stored) ?: node.default, { set(name, it) }, context, required = node.required)
            }
        }

        is SettingNode.Group -> {
            // A schedule is drawn by its editor on every screen, without its owner attaching it
            val editor = editors[node.name]
                ?: if (node.name == ScheduleSettings.NAME) ScheduleSettingEditor(node.label, Strings.`for`(context = context)) else null
            // An optional group set can be removed whole: left out, it is none
            val onRemove = if (!node.required && config.has(node.name)) {{ set(node.name, null) }} else null
            val pages = LocalPages.current
            if (editor != null) editor.Edit(config.optJSONObject(node.name)) { set(node.name, it) }
            else if (pages != null && node.nodes.isPage()) UI.Card(type = CardType.DEFAULT) {
                PageLine(
                    title = { UI.Text(node.label, TextType.SUBTITLE) },
                    summary = {},
                    nodes = node.nodes,
                    config = config.optJSONObject(node.name) ?: JSONObject(),
                    context = context,
                    onOpen = { pages.open(Step.Group(node.name)) }
                ) { onRemove?.let { UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = it) } }
            }
            else Titled(node.label, onRemove = onRemove) {
                Into(node.name) {
                    NodesForm(node.nodes, node.nodes, config.optJSONObject(node.name) ?: JSONObject(), { set(node.name, it) }, context, emptyMap())
                }
            }
        }

        is SettingNode.ListOf -> {
            val editor = editors[node.name]
            if (editor != null) editor.Edit(config.optJSONArray(node.name)) { set(node.name, it) }
            else Titled(node.label) {
                Into(node.name) {
                    ListForm(node, config.optJSONArray(node.name) ?: JSONArray(), { set(node.name, it.takeIf { a -> a.length() > 0 }) }, context)
                }
            }
        }

        is SettingNode.Variant -> {
            val selector = node.selector.definition.name
            val chosen = config.optString(selector).ifEmpty { node.selector.default?.toString() }
            FieldInput(node.selector.definition, chosen, { option ->
                if (option != null && option != chosen) onChange(SettingVariants.switched(config, level, node, option.toString()))
            }, context, required = true)
            node.cases[chosen]?.let { NodesForm(it, level, config, onChange, context, editors) }
        }

        is SettingNode.Section -> Titled(node.label) {
            NodesForm(node.nodes, level, config, onChange, context, editors)
        }

        // Put on each row: its sides are the row's fields or written values
        is SettingNode.Condition -> if (node.onRow) {
            val fields = rowFields()
            if (fields == null) UI.LoadingIndicator()
            else com.assistant.core.ui.selectors.RowConditionSetting(
                label = node.label,
                condition = config.optJSONObject(node.name),
                fields = fields,
                onChange = { set(node.name, it) },
                s = Strings.`for`(context = context)
            )
        } else {
            // Its left side is the value entered beside it once that value has a type
            val entered = node.enteredField?.let { config.optJSONObject(it) }?.let { declared ->
                com.assistant.core.fields.FieldType.entries.firstOrNull { it.name == declared.optString("type") }?.let { type ->
                    com.assistant.core.fields.FieldDefinition(node.name, node.label, null, type, false, declared.optJSONObject("config")?.toFieldConfig())
                }
            }
            com.assistant.core.ui.selectors.ConditionSetting(
                label = node.label,
                condition = config.optJSONObject(node.name),
                entered = entered,
                onChange = { set(node.name, it) },
                s = Strings.`for`(context = context),
                where = com.assistant.core.ui.selectors.ReadingContext(node.reference, node.emptyPeriod, perEntry = false)
            )
        }

        is SettingNode.Term -> {
            val s = Strings.`for`(context = context)
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(text = node.label, type = TextType.SUBTITLE)
                com.assistant.core.ui.selectors.TermPicker(
                    term = config.optJSONObject(node.name) ?: JSONObject(),
                    // A constant here has no other side to take its type from: a number
                    constantField = com.assistant.core.fields.FieldDefinition(node.name, node.label, null, com.assistant.core.fields.FieldType.NUMERIC, false, null),
                    onChange = { set(node.name, it) },
                    s = s,
                    where = com.assistant.core.ui.selectors.ReadingContext(node.reference, node.emptyPeriod, perEntry = false),
                    kinds = node.kinds
                )
            }
        }

        is SettingNode.Selection -> com.assistant.core.ui.selectors.SelectionSetting(
            label = node.label,
            selection = config.optJSONObject(node.name),
            reference = node.reference,
            onChange = { set(node.name, it) }
        )

        is SettingNode.Period -> {
            val s = Strings.`for`(context = context)
            val period = config.optJSONObject(node.name)?.let { com.assistant.core.selection.EntryPeriod.fromJson(it) { key -> s.shared(key) } }
                ?: com.assistant.core.selection.EntryPeriod()
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.FieldLabel(node.label, node.required)
                com.assistant.core.ui.components.PeriodPicker(period, { set(node.name, it.toJson()) },
                    com.assistant.core.fields.FieldType.DATETIME, node.reference)
            }
        }
    }
}

/**
 * The choice of a field of the rows where the form stands (SettingNode.Field.rowField), by
 * FieldPicker; a name no longer among them shows as it is, for the service to name it.
 */
@Composable
private fun RowFieldChoice(label: String, stored: String?, required: Boolean, onChange: (String?) -> Unit) {
    val fields = rowFields() ?: return UI.LoadingIndicator()
    com.assistant.core.ui.selectors.FieldPicker(
        label = label,
        fields = fields,
        selected = stored?.let { com.assistant.core.ui.selectors.FieldPick.Path(it) },
        onSelect = { pick -> onChange((pick as com.assistant.core.ui.selectors.FieldPick.Path).path) },
        required = required
    )
}

@Composable
private fun ListForm(
    list: SettingNode.ListOf,
    items: JSONArray,
    onChange: (JSONArray) -> Unit,
    context: Context
) {
    val values = (0 until items.length()).map { items.get(it) }
    fun publish(next: List<Any>) = onChange(JSONArray(next))

    // The elements open, by position: every one starts closed, a new one opens, and several may
    // be open at once. Screen state only, never stored.
    var open by rememberSaveable { mutableStateOf(intArrayOf()) }

    // Positions, not elements, key the column: an edited element is a new object, and keying the
    // items by it would rebuild the field being typed in at every keystroke. Each position comes
    // with its element, so a move taken into account is a list of other content
    UI.ReorderableColumn(
        items = values.withIndex().toList(),
        key = { it.index },
        onMove = { from, to ->
            // An element keeps its open state where it lands
            val order = values.indices.toMutableList().apply { add(to, removeAt(from)) }
            open = order.indices.filter { order[it] in open }.toIntArray()
            publish(order.map { values[it] })
        },
        spacing = UI.Space.S
    ) { _, (index, item) ->
        fun remove() {
            open = open.filter { it != index }.map { if (it > index) it - 1 else it }.toIntArray()
            publish(values.toMutableList().also { it.removeAt(index) })
        }
        when (val shape = list.item) {
            is SettingNode.Item.Value -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    if (shape.rowField) RowFieldChoice(shape.definition.displayName, item.takeIf { it != JSONObject.NULL } as? String, required = true) { value ->
                        publish(values.toMutableList().also { it[index] = value ?: JSONObject.NULL })
                    }
                    else FieldInput(shape.definition, item.takeIf { it != JSONObject.NULL }, { value ->
                        publish(values.toMutableList().also { it[index] = value ?: JSONObject.NULL })
                    }, context)
                }
                DragHandle()
                UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { remove() })
            }
            is SettingNode.Item.Of -> UI.Card(type = CardType.DEFAULT) {
                val element = item as? JSONObject ?: JSONObject()
                val pages = LocalPages.current
                // An element holding more than values is a line opening its page
                if (pages != null && shape.nodes.isPage()) {
                    PageLine(
                        title = null,
                        summary = { Summary(list.summary, shape.nodes, element, context) },
                        nodes = shape.nodes,
                        config = element,
                        context = context,
                        onOpen = { pages.open(Step.Element(list.name, index)) }
                    ) {
                        DragHandle()
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { remove() })
                    }
                    return@Card
                }
                val isOpen = index in open
                Column {
                    // The summary line opens and closes the element; the handle and the bin keep
                    // their own gestures
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { open = if (isOpen) open.filter { it != index }.toIntArray() else open + index }
                                .padding(start = UI.Space.M, top = UI.Space.S, bottom = UI.Space.S),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) { Summary(list.summary, shape.nodes, element, context) }
                            UI.Icon(if (isOpen) "chevron-up" else "chevron-down", size = 20.dp)
                        }
                        DragHandle()
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { remove() })
                    }
                    if (isOpen) {
                        Column(modifier = Modifier.padding(start = UI.Space.M, end = UI.Space.M, bottom = UI.Space.M)) {
                            Into(index) {
                                NodesForm(shape.nodes, shape.nodes, element, { changed ->
                                    publish(values.toMutableList().also { it[index] = changed })
                                }, context, emptyMap())
                            }
                        }
                    }
                }
            }
        }
    }
    val pages = LocalPages.current
    UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.ICON, size = Size.S, onClick = {
        // A new element starts from its defaults, open to be filled in — on its page when it has
        // one; a value starts empty
        val shape = list.item
        val fresh: Any = when (shape) {
            is SettingNode.Item.Of -> SettingDefaults.of(shape.nodes)
            is SettingNode.Item.Value -> JSONObject.NULL
        }
        publish(values + fresh)
        if (pages != null && shape is SettingNode.Item.Of && shape.nodes.isPage()) pages.open(Step.Element(list.name, values.size))
        else open = open + values.size
    })
}

/**
 * The line that stands for a closed element: the values of its [summary] settings, each shown by
 * its field type and parted by a separator, those without a value left out; an element with none of them says it is
 * untitled.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(summary: List<String>, nodes: List<SettingNode>, element: JSONObject, context: Context) {
    val shown = summary.mapNotNull { key ->
        val value = JsonUtils.toValue(element.opt(key)?.takeIf { it != JSONObject.NULL })
            ?.takeIf { it.toString().isNotEmpty() && it != emptyList<Any>() } ?: return@mapNotNull null
        // A list of values shows them one after the other, each as its field shows it
        Triple(nodes.storedField(key) ?: requireNotNull(nodes.storedValues(key)), value, nodes.storedField(key) == null)
    }
    if (shown.isEmpty()) {
        UI.Text(Strings.`for`(context = context).shared("list_item_untitled"), TextType.CAPTION)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
    ) {
        shown.forEachIndexed { i, (definition, value, isList) ->
            // Values side by side would read as one phrase ("Weighed on Date and time")
            if (i > 0) UI.Text(Strings.`for`(context = context).shared("list_item_summary_separator"), TextType.BODY)
            if (isList && value is List<*>) value.forEach { FieldValue(definition, it, context) }
            else FieldValue(definition, value, context)
        }
    }
}

/**
 * The name of an element in the path: its [SettingNode.ListOf.summary] values as text, or the
 * list's label and its position when they are empty.
 */
private fun summaryText(list: SettingNode.ListOf, element: JSONObject, index: Int): String {
    val shown = list.summary.mapNotNull { key ->
        when (val value = JsonUtils.toValue(element.opt(key)?.takeIf { it != JSONObject.NULL })) {
            null -> null
            is List<*> -> value.joinToString(", ").takeIf { it.isNotEmpty() }
            else -> value.toString().takeIf { it.isNotEmpty() }
        }
    }
    return shown.joinToString(" · ").ifEmpty { "${list.label} ${index + 1}" }
}

/** A card with [label] as its title, over [content]; with [onRemove], a button removing what it holds. */
@Composable
private fun Titled(label: String, onRemove: (() -> Unit)? = null, content: @Composable () -> Unit) {
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { UI.Text(label, TextType.SUBTITLE) }
                onRemove?.let { UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = it) }
            }
            content()
        }
    }
}

/**
 * The choice of a field of the tool [tool] designates (SettingNode.Field.fieldOf), by FieldPicker;
 * nothing to choose before the tool is. The fields not read are said, never left out in silence.
 */
@Composable
private fun ToolFieldChoice(node: SettingNode.Field, tool: Any?, stored: String?, context: Context, onChange: (String?) -> Unit) {
    val s = remember { Strings.`for`(context = context) }
    val toolId = com.assistant.core.fields.ReferenceTarget.referenceOf(tool)?.id
    var fields by remember { mutableStateOf<Map<String, com.assistant.core.fields.FieldDefinition>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(toolId) {
        error = null
        fields = if (toolId == null) null else try {
            com.assistant.core.fields.ToolFields.filterable(toolId, context, s)
        } catch (e: IllegalStateException) { error = e.message; null }
    }
    val loaded = fields
    when {
        error != null -> UI.Text(error!!, TextType.ERROR)
        loaded == null -> UI.Text(node.definition.displayName + " — " + s.shared("setting_field_of_tool_first"), TextType.CAPTION)
        else -> com.assistant.core.ui.selectors.FieldPicker(
            label = node.definition.displayName,
            fields = loaded,
            selected = stored?.let { com.assistant.core.ui.selectors.FieldPick.Path(it) },
            onSelect = { pick -> onChange((pick as com.assistant.core.ui.selectors.FieldPick.Path).path) },
            required = node.required
        )
    }
}
