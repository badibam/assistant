package com.assistant.core.fields.settings

import com.assistant.core.fields.FieldDefinition

/**
 * One node of a settings declaration: what a config holds, described so that its schema, its
 * screen and its conversion for the AI all come from one place (docs/DATA.md).
 *
 * Two levels and nothing else. A value is always a field type ([Field]); what assembles values
 * is this fixed set of shapes, which does not grow case by case. What a field type cannot say
 * is not said. A brick a config holds whole (docs/BRICKS.md: a condition, a term, a selection of
 * entries, a period) is a node of its own, stored in the brick's one form and drawn by its selector.
 */
sealed class SettingNode {

    /**
     * One value, stored under the field's name.
     *
     * @property required The config is refused without it
     * @property default The value the form prefills and the absence of the setting means, in its
     *   stored form (milliseconds for a DURATION); null when absence means "nothing"
     * @property secret Entered masked, never sent to the AI, never logged (an API key)
     * @property address A web address, entered without the capital and the correction of a text
     * @property systemWritten Written by the app and sent back unchanged, never entered: the
     *   form does not show it (a field definition's name, made from its label)
     * @property valueOfDefined A value of the field the object holding it defines (a field
     *   definition's default value): [definition] gives its type, and the object its config —
     *   options, bounds — which the form reads to enter it and the service to check it. Its
     *   schema is its type's alone, the config being set beside it
     * @property fieldOf The name of the setting beside it that designates a tool instance (a
     *   REFERENCE): this one is the path of one of that tool's fields ("data.kcal"), which the form
     *   offers to choose among rather than to type
     * @property rowField A field of the rows the config describes where the setting stands (a
     *   column of a chart's layer), by name: the form offers those its owner gives (RowFields)
     */
    data class Field(
        val definition: FieldDefinition,
        val required: Boolean = false,
        val default: Any? = null,
        val secret: Boolean = false,
        val address: Boolean = false,
        val systemWritten: Boolean = false,
        val valueOfDefined: Boolean = false,
        val fieldOf: String? = null,
        val rowField: Boolean = false
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
     *   stored in the element itself: a value, or a list of values, shown joined.
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
                    require(item.nodes.storedField(key) != null || item.nodes.storedValues(key) != null) {
                        "The summary of the list '$name' names '$key', which its elements do not store"
                    }
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
        /**
         * A single value; the field's name is not stored, only its value. With [rowField], the
         * name of a field of the rows, as SettingNode.Field.rowField.
         */
        data class Value(val definition: FieldDefinition, val rowField: Boolean = false) : Item()
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
     * @property onRow Put on each row the config describes where it stands, not judged once: its
     *   sides are fields of the row (RowFields) or written values, `{"field": "kcal"}` and
     *   `{"constant": 2100}`; [reference] and [emptyPeriod] then say nothing
     */
    data class Condition(
        val name: String,
        val label: String,
        val reference: String,
        val emptyPeriod: String?,
        val required: Boolean = false,
        val enteredField: String? = null,
        val onRow: Boolean = false
    ) : SettingNode()

    /**
     * A term stored under [name] (the Terme brick, docs/BRICKS.md): `{"constant"}`, `{"variable"}`
     * or `{"reading"}`, among [kinds]; drawn by TermPicker, read by the service that uses it.
     *
     * @property reference The name of the instant it is read at, which its relative dates resolve against
     * @property emptyPeriod What a reading without a period of its own reads there
     */
    data class Term(
        val name: String,
        val label: String,
        val kinds: Set<com.assistant.core.terms.Term.Kind>,
        val reference: String,
        val emptyPeriod: String?,
        val required: Boolean = false
    ) : SettingNode()

    /**
     * Entries of one tool stored under [name] (the Sélection d'entrées brick, docs/BRICKS.md):
     * `{"target", "filters", "fields"}`, without a period, which the config gives elsewhere (a
     * chart's displayed period); drawn by SelectionSetting.
     *
     * @property reference The name of the instant its filters' relative dates resolve against
     */
    data class Selection(
        val name: String,
        val label: String,
        val reference: String,
        val required: Boolean = false
    ) : SettingNode()

    /**
     * A period stored under [name] (the Période brick, docs/BRICKS.md): `{"start", "end"}`, each
     * bound an instant (TimePoint), absent for no limit; drawn by PeriodPicker.
     *
     * @property reference The name of the instant its relative bounds resolve against
     */
    data class Period(
        val name: String,
        val label: String,
        val reference: String,
        val required: Boolean = false
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
            is SettingNode.Group, is SettingNode.ListOf, is SettingNode.Condition,
            is SettingNode.Term, is SettingNode.Selection, is SettingNode.Period -> null
        }
        if (found != null) return found
    }
    return null
}

/**
 * The list of single values stored under [name] in the object these nodes describe, as
 * storedField finds a value: its elements' field, null when no such list is there.
 */
fun List<SettingNode>.storedValues(name: String): FieldDefinition? {
    for (node in this) {
        val found = when (node) {
            is SettingNode.ListOf -> (node.item as? SettingNode.Item.Value)?.definition?.takeIf { node.name == name }
            is SettingNode.Variant -> node.cases.values.firstNotNullOfOrNull { it.storedValues(name) }
            is SettingNode.Section -> node.nodes.storedValues(name)
            else -> null
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
            is SettingNode.Term -> node.label.takeIf { node.name == name }
            is SettingNode.Selection -> node.label.takeIf { node.name == name }
            is SettingNode.Period -> node.label.takeIf { node.name == name }
        }
        if (found != null) return found
    }
    return null
}
