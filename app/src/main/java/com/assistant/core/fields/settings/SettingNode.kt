package com.assistant.core.fields.settings

import com.assistant.core.fields.FieldDefinition

/**
 * One node of a settings declaration: what a config holds, described so that its schema, its
 * screen and its conversion for the AI all come from one place (docs/DATA.md).
 *
 * Two levels and nothing else. A value is always a field type ([Field]); what assembles values
 * is this fixed set of shapes, which does not grow case by case. What a field type cannot say
 * is not said.
 */
sealed class SettingNode {

    /**
     * One value, stored under the field's name.
     *
     * @property required The config is refused without it
     * @property default The value the form prefills and the absence of the setting means, in its
     *   stored form (milliseconds for a DURATION); null when absence means "nothing"
     * @property secret Entered masked, never sent to the AI, never logged (an API key)
     * @property systemWritten Written by the app and sent back unchanged, never entered: the
     *   form does not show it (a field definition's name, made from its label)
     */
    data class Field(
        val definition: FieldDefinition,
        val required: Boolean = false,
        val default: Any? = null,
        val secret: Boolean = false,
        val systemWritten: Boolean = false
    ) : SettingNode()

    /** Settings stored together as one object under [name]. */
    data class Group(
        val name: String,
        val label: String,
        val nodes: List<SettingNode>,
        val required: Boolean = false
    ) : SettingNode()

    /**
     * A list stored under [name], each element a group of settings or a single value.
     *
     * @property minItems The fewest elements the list may hold
     * @property distinct Whether two elements may be equal
     * @property fieldDefinitions Whether its elements are field definitions, which the model
     *   reads once in its prompt rather than in every schema holding such a list
     */
    data class ListOf(
        val name: String,
        val label: String,
        val item: Item,
        val required: Boolean = false,
        val minItems: Int = 0,
        val distinct: Boolean = false,
        val fieldDefinitions: Boolean = false
    ) : SettingNode()

    /** What one element of a [ListOf] is. */
    sealed class Item {
        /** An object of settings. */
        data class Of(val nodes: List<SettingNode>) : Item()
        /** A single value; the field's name is not stored, only its value. */
        data class Value(val definition: FieldDefinition) : Item()
    }

    /**
     * Settings that depend on a choice: [selector] is a CHOICE field, and each of its options
     * brings its own nodes. They are stored flat, beside the selector, in the object that holds it.
     */
    data class Variant(
        val selector: Field,
        val cases: Map<String, List<SettingNode>>
    ) : SettingNode()

    /** Settings shown together on the screen; nothing of it is stored. */
    data class Section(
        val label: String,
        val nodes: List<SettingNode>
    ) : SettingNode()
}

/**
 * The label of the setting named [name] anywhere in these nodes, for a validation error to name
 * it as the screen does; null when no setting has that name.
 */
fun List<SettingNode>.labelOf(name: String): String? {
    for (node in this) {
        val found = when (node) {
            is SettingNode.Field -> node.definition.displayName.takeIf { node.definition.name == name }
            is SettingNode.Group -> node.label.takeIf { node.name == name } ?: node.nodes.labelOf(name)
            is SettingNode.ListOf -> node.label.takeIf { node.name == name }
                ?: (node.item as? SettingNode.Item.Of)?.nodes?.labelOf(name)
            is SettingNode.Variant -> listOf(node.selector).labelOf(name)
                ?: node.cases.values.firstNotNullOfOrNull { it.labelOf(name) }
            is SettingNode.Section -> node.nodes.labelOf(name)
        }
        if (found != null) return found
    }
    return null
}
