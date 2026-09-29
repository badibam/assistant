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
 * The form of any settings declaration (docs/DATA.md): a field by the
 * input of its field type, a group as a card, a list with add, remove and reorder, a variant with
 * the settings of the option chosen, a section as a titled card over settings stored beside it.
 * A setting the app writes itself (SettingNode.Field.systemWritten) is not shown; a secret one
 * is entered masked; a schedule (ScheduleSettings.group) opens its own editor.
 *
 * Stateless: [config] is the object being edited, and every change hands a new one to [onChange].
 *
 * @param editors Parts drawn by their owner, by setting name (SettingEditor), at the top level of
 *   the declaration only: a name inside a group or a list element may mean something else there
 */
@Composable
fun SettingsForm(
    nodes: List<SettingNode>,
    config: JSONObject,
    onChange: (JSONObject) -> Unit,
    context: Context,
    editors: Map<String, SettingEditor> = emptyMap()
) {
    NodesForm(nodes, nodes, config, onChange, context, editors)
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            if (editor != null) editor.Edit(config.optJSONObject(node.name)) { set(node.name, it) }
            else Titled(node.label) {
                SettingsForm(node.nodes, config.optJSONObject(node.name) ?: JSONObject(), { set(node.name, it) }, context)
            }
        }

        is SettingNode.ListOf -> {
            val editor = editors[node.name]
            if (editor != null) editor.Edit(config.optJSONArray(node.name)) { set(node.name, it) }
            else Titled(node.label) {
                ListForm(node, config.optJSONArray(node.name) ?: JSONArray(), { set(node.name, it.takeIf { a -> a.length() > 0 }) }, context)
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
    }
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
        spacing = 8.dp
    ) { _, (index, item) ->
        fun remove() {
            open = open.filter { it != index }.map { if (it > index) it - 1 else it }.toIntArray()
            publish(values.toMutableList().also { it.removeAt(index) })
        }
        when (val shape = list.item) {
            is SettingNode.Item.Value -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    FieldInput(shape.definition, item.takeIf { it != JSONObject.NULL }, { value ->
                        publish(values.toMutableList().also { it[index] = value ?: JSONObject.NULL })
                    }, context)
                }
                DragHandle()
                UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { remove() })
            }
            is SettingNode.Item.Of -> UI.Card(type = CardType.DEFAULT) {
                val element = item as? JSONObject ?: JSONObject()
                val isOpen = index in open
                Column {
                    // The summary line opens and closes the element; the handle and the bin keep
                    // their own gestures
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { open = if (isOpen) open.filter { it != index }.toIntArray() else open + index }
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) { Summary(list.summary, shape.nodes, element, context) }
                            UI.Icon(if (isOpen) "chevron-up" else "chevron-down", size = 20.dp)
                        }
                        DragHandle()
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { remove() })
                    }
                    if (isOpen) {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            SettingsForm(shape.nodes, element, { changed ->
                                publish(values.toMutableList().also { it[index] = changed })
                            }, context)
                        }
                    }
                }
            }
        }
    }
    UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.ICON, size = Size.S, onClick = {
        // A new element starts from its defaults, open to be filled in; a value starts empty
        val fresh: Any = when (val shape = list.item) {
            is SettingNode.Item.Of -> SettingDefaults.of(shape.nodes)
            is SettingNode.Item.Value -> JSONObject.NULL
        }
        open = open + values.size
        publish(values + fresh)
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
            ?.takeIf { it.toString().isNotEmpty() } ?: return@mapNotNull null
        requireNotNull(nodes.storedField(key)) to value
    }
    if (shown.isEmpty()) {
        UI.Text(Strings.`for`(context = context).shared("list_item_untitled"), TextType.CAPTION)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        shown.forEachIndexed { i, (definition, value) ->
            // Values side by side would read as one phrase ("Weighed on Date and time")
            if (i > 0) UI.Text(Strings.`for`(context = context).shared("list_item_summary_separator"), TextType.BODY)
            FieldValue(definition, value, context)
        }
    }
}

/** A card with [label] as its title, over [content]. */
@Composable
private fun Titled(label: String, content: @Composable () -> Unit) {
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UI.Text(label, TextType.SUBTITLE)
            content()
        }
    }
}

/**
 * The choice of a field of the tool [tool] designates (SettingNode.Field.fieldOf), by its label and
 * path; nothing to choose before the tool is. The fields not read are said, never left out in silence.
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
        else -> {
            val choices = loaded.map { (path, field) -> path to "${field.displayName} ($path)" }
            UI.FormSelection(
                label = node.definition.displayName,
                options = choices.map { it.second },
                selected = choices.firstOrNull { it.first == stored }?.second ?: "",
                onSelect = { label -> onChange(choices.first { it.second == label }.first) },
                required = node.required
            )
        }
    }
}
