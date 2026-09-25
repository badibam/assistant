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
 * The form of any settings declaration (docs/design/config-fields.md, decision 3): a field by the
 * input of its field type, a group as a card, a list with add, remove and reorder, a variant with
 * the settings of the option chosen, a section as a titled card over settings stored beside it.
 * A setting the app writes itself (SettingNode.Field.systemWritten) is not shown; a secret one
 * is entered masked.
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
                // A secret is entered masked, whatever its field type says of its length
                node.secret -> UI.FormField(
                    label = node.definition.displayName,
                    value = stored?.toString() ?: "",
                    onChange = { set(name, it.ifEmpty { null }) },
                    fieldType = com.assistant.core.ui.FieldType.PASSWORD,
                    required = node.required
                )
                // An absent setting shows the value its absence means. The input takes the Kotlin
                // form of a value: a list of options, not the JSONArray they are stored as
                else -> FieldInput(node.definition, JsonUtils.toValue(stored) ?: node.default, { set(name, it) }, context, required = node.required)
            }
        }

        is SettingNode.Group -> {
            val editor = editors[node.name]
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

    values.forEachIndexed { index, item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                when (val shape = list.item) {
                    is SettingNode.Item.Value -> FieldInput(shape.definition, item.takeIf { it != JSONObject.NULL }, { value ->
                        publish(values.toMutableList().also { it[index] = value ?: JSONObject.NULL })
                    }, context)
                    is SettingNode.Item.Of -> UI.Card(type = CardType.DEFAULT) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SettingsForm(shape.nodes, item as? JSONObject ?: JSONObject(), { element ->
                                publish(values.toMutableList().also { it[index] = element })
                            }, context)
                        }
                    }
                }
            }
            Column {
                UI.ActionButton(action = ButtonAction.UP, display = ButtonDisplay.ICON, size = Size.S, enabled = index > 0, onClick = {
                    publish(values.toMutableList().also { it.add(index - 1, it.removeAt(index)) })
                })
                UI.ActionButton(action = ButtonAction.DOWN, display = ButtonDisplay.ICON, size = Size.S, enabled = index < values.size - 1, onClick = {
                    publish(values.toMutableList().also { it.add(index + 1, it.removeAt(index)) })
                })
                UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                    publish(values.toMutableList().also { it.removeAt(index) })
                })
            }
        }
    }
    UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.ICON, size = Size.S, onClick = {
        // A new element starts from its defaults; a value starts empty, to be filled in
        val fresh: Any = when (val shape = list.item) {
            is SettingNode.Item.Of -> SettingDefaults.of(shape.nodes)
            is SettingNode.Item.Value -> JSONObject.NULL
        }
        publish(values + fresh)
    })
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
