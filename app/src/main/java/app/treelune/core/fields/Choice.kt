package app.treelune.core.fields

import app.treelune.core.themes.TagColor

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
        /**
         * Each option is stored as a group: its value, and the label and color that describe it,
         * kept together ({"value": "work", "color": "BLUE"}).
         */
        fun fromConfig(config: Map<String, Any>?): ChoiceSettings {
            val multiple = config?.get("multiple") as? Boolean ?: false
            val ordered = config?.get("ordered") as? Boolean ?: false
            val stored = (config?.get("options") as? List<*>)?.map { it as Map<*, *> } ?: emptyList()
            return ChoiceSettings(
                options = stored.map { it["value"].toString() },
                shape = when {
                    ordered -> ChoiceShape.ORDERED
                    multiple -> ChoiceShape.MULTIPLE
                    else -> ChoiceShape.SINGLE
                },
                open = config?.get("open") as? Boolean ?: false,
                colors = stored.mapNotNull { option ->
                    option["color"]?.let { option["value"].toString() to TagColor.valueOf(it.toString()) }
                }.toMap(),
                labels = stored.mapNotNull { option ->
                    option["label"]?.let { option["value"].toString() to it.toString() }
                }.toMap()
            )
        }

        /**
         * The stored form of [values], each with its label and color if it has one: what a
         * config's "options" holds. The single place that writes it.
         */
        fun storedOptions(
            values: List<String>,
            labels: Map<String, String> = emptyMap(),
            colors: Map<String, TagColor> = emptyMap()
        ): List<Map<String, Any>> = values.map { value ->
            buildMap {
                put("value", value)
                labels[value]?.let { put("label", it) }
                colors[value]?.let { put("color", it.name) }
            }
        }
    }
}

/**
 * The field with [added] appended to its options, for an open choice that has just been given
 * values it did not know. The field is otherwise unchanged.
 */
fun FieldDefinition.withOptionsAdded(added: List<String>): FieldDefinition {
    if (added.isEmpty()) return this
    val settings = ChoiceSettings.fromConfig(config)
    val options = ChoiceSettings.storedOptions(settings.options + added, settings.labels, settings.colors)
    return copy(config = (config ?: emptyMap()) + ("options" to options))
}
