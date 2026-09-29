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
     * @property valueOfDefined A value of the field the object holding it defines (a field
     *   definition's default value): [definition] gives its type, and the object its config —
     *   options, bounds — which the form reads to enter it and the service to check it. Its
     *   schema is its type's alone, the config being set beside it
     * @property fieldOf The name of the setting beside it that designates a tool instance (a
     *   REFERENCE): this one is the path of one of that tool's fields ("data.kcal"), which the form
     *   offers to choose among rather than to type
     */
    data class Field(
        val definition: FieldDefinition,
        val required: Boolean = false,
        val default: Any? = null,
        val secret: Boolean = false,
        val systemWritten: Boolean = false,
        val valueOfDefined: Boolean = false,
        val fieldOf: String? = null
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
     * @property summary For a list of groups, the settings of an element that stand for it when
     *   it is closed, in the order shown: a form shows every element closed, one line of these
     *   values, and opens it on demand. Required for such a list, and each name is a setting
     *   stored in the element itself.
     */
    data class ListOf(
        val name: String,
        val label: String,
        val item: Item,
        val required: Boolean = false,
        val minItems: Int = 0,
        val distinct: Boolean = false,
        val fieldDefinitions: Boolean = false,
        val summary: List<String> = emptyList()
    ) : SettingNode() {
        init {
            if (item is Item.Of) {
                require(summary.isNotEmpty()) { "The list '$name' has groups for elements, and no summary" }
                summary.forEach { key ->
                    requireNotNull(item.nodes.storedField(key)) { "The summary of the list '$name' names '$key', which its elements do not store" }
                }
            } else {
                require(summary.isEmpty()) { "The list '$name' has single values for elements: they are their own summary" }
            }
        }
    }

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

    /**
     * A condition stored under [name] (the Condition brick, docs/BRICKS.md): `{"left", "op", "right"}`,
     * judged once, each side a term; drawn by ConditionSetting, its sides read by the service
     * that judges it.
     *
     * @property reference The name of the instant it is judged at, which its relative dates resolve against
     * @property emptyPeriod What a reading without a period of its own reads there
     * @property enteredField The name of the setting beside it that declares a value entered in
     *   each entry (FieldTypeSettings.valueNodes): when it is set, the condition is put on that
     *   entry, its left side the entry's field, which whoever owns the key writes
     */
    data class Condition(
        val name: String,
        val label: String,
        val reference: String,
        val emptyPeriod: String?,
        val required: Boolean = false,
        val enteredField: String? = null
    ) : SettingNode()

    /** Settings shown together on the screen; nothing of it is stored. */
    data class Section(
        val label: String,
        val nodes: List<SettingNode>
    ) : SettingNode()
}

/**
 * The field stored under [name] in the object these nodes describe: a field of its own, a
 * variant's selector or one of its cases, a field of a section — not one inside a group or a list,
 * which is stored in an object of its own.
 */
fun List<SettingNode>.storedField(name: String): FieldDefinition? {
    for (node in this) {
        val found = when (node) {
            is SettingNode.Field -> node.definition.takeIf { it.name == name }
            is SettingNode.Variant -> node.selector.definition.takeIf { it.name == name }
                ?: node.cases.values.firstNotNullOfOrNull { it.storedField(name) }
            is SettingNode.Section -> node.nodes.storedField(name)
            is SettingNode.Group, is SettingNode.ListOf, is SettingNode.Condition -> null
        }
        if (found != null) return found
    }
    return null
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
            is SettingNode.Condition -> node.label.takeIf { node.name == name }
        }
        if (found != null) return found
    }
    return null
}
