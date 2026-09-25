package com.assistant.core.fields

import com.assistant.core.themes.TagColor

/**
 * What a CHOICE value is: one option, a set of options, or the options in a chosen order.
 *
 * SINGLE is a string; MULTIPLE and ORDERED are both lists of distinct options, but only ORDERED
 * gives the order a meaning. Moving a field from one to another changes what its stored values
 * say, so it takes them all (FieldChange.ChoiceShapeChanged).
 */
enum class ChoiceShape {
    SINGLE,
    MULTIPLE,
    ORDERED;

    val isList: Boolean get() = this != SINGLE
}

/**
 * The settings of a CHOICE field, read from its config.
 *
 * @property options The options, in the order the config lists them
 * @property shape Whether a value is one option, several, or a ranking
 * @property open Whether a value outside the options is added to them rather than refused
 * @property colors The color of each option that has one; a colored choice shows as tags
 * @property labels The text shown for each option that has one. A field a tool type declares
 *           stores technical options ("pending") and shows them translated; a user's field
 *           shows its options as they are.
 */
data class ChoiceSettings(
    val options: List<String>,
    val shape: ChoiceShape,
    val open: Boolean,
    val colors: Map<String, TagColor>,
    val labels: Map<String, String>
) {
    /** The text shown for [option]. */
    fun labelOf(option: String): String = labels[option] ?: option

    /**
     * The values of [value] that are not options yet, in the order they appear.
     *
     * Only an open choice has any to add: a closed one leaves them for the schema to refuse.
     */
    fun newOptionsIn(value: Any?): List<String> {
        if (!open || value == null) return emptyList()
        val given = when (value) {
            is List<*> -> value.map { it.toString() }
            else -> listOf(value.toString())
        }
        return given.filter { it !in options }.distinct()
    }

    companion object {
        fun fromConfig(config: Map<String, Any>?): ChoiceSettings {
            val multiple = config?.get("multiple") as? Boolean ?: false
            val ordered = config?.get("ordered") as? Boolean ?: false
            return ChoiceSettings(
                options = (config?.get("options") as? List<*>)?.map { it.toString() } ?: emptyList(),
                shape = when {
                    ordered -> ChoiceShape.ORDERED
                    multiple -> ChoiceShape.MULTIPLE
                    else -> ChoiceShape.SINGLE
                },
                open = config?.get("open") as? Boolean ?: false,
                colors = (config?.get("option_colors") as? Map<*, *>)
                    ?.map { (option, color) -> option.toString() to TagColor.valueOf(color.toString()) }
                    ?.toMap()
                    ?: emptyMap(),
                labels = (config?.get("option_labels") as? Map<*, *>)
                    ?.map { (option, label) -> option.toString() to label.toString() }
                    ?.toMap()
                    ?: emptyMap()
            )
        }
    }
}

/**
 * The field with [added] appended to its options, for an open choice that has just been given
 * values it did not know. The field is otherwise unchanged.
 */
fun FieldDefinition.withOptionsAdded(added: List<String>): FieldDefinition {
    if (added.isEmpty()) return this
    val options = ChoiceSettings.fromConfig(config).options
    return copy(config = (config ?: emptyMap()) + ("options" to options + added))
}
