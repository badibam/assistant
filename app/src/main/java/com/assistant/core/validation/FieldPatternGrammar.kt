package com.assistant.core.validation

/**
 * Single source for the grammar of the field paths a TOOL_DATA query asks for.
 *
 * A query names its fields one by one, with no wildcard. Three shapes exist:
 * - a root field, with no dot: "id", "timestamp", "name", "created_at"
 * - a data field: "data.value", "data.text"
 * - a custom field: "custom_fields.notes", "custom_fields.mood"
 *
 * "data" and "extra" on their own are refused: both are containers, and asking for
 * the container instead of a field inside it is the mistake this grammar exists to name.
 * Any other path carrying a dot is refused too, since nothing would know where to read it.
 * So is a path one level deeper, like "custom_fields.sleep.start": the filter keeps whole keys
 * and would look for a key named "sleep.start", find none and leave the field out in silence.
 * A value holding an object or a list is asked for whole ("custom_fields.sleep").
 *
 * Root fields are not listed here on purpose. The grammar says where a path points, not
 * whether that field exists on the entry; a root field the entry does not carry is simply
 * absent from the result. A new root field therefore needs no change here.
 *
 * Both ends read the paths through this object: the validation that answers the AI
 * (AICommandProcessor) and the filtering that builds the result (ToolDataService).
 * They used to hold one half of the grammar each, which is how a path could pass
 * validation and then be dropped in silence by the filter.
 */
object FieldPatternGrammar {

    const val DATA_PREFIX = "data."
    const val CUSTOM_FIELDS_PREFIX = "extra."

    private const val DATA_CONTAINER = "data"
    private const val CUSTOM_FIELDS_CONTAINER = "extra"

    /**
     * Requested paths sorted by where they point, plus the ones that point nowhere.
     *
     * The names in [data] and [custom] have lost their prefix: they are keys to read inside
     * the corresponding JSON object.
     */
    data class ParsedFields(
        val root: List<String>,
        val data: List<String>,
        val custom: List<String>,
        val invalid: List<String>
    )

    fun parse(paths: List<String>): ParsedFields {
        val root = mutableListOf<String>()
        val data = mutableListOf<String>()
        val custom = mutableListOf<String>()
        val invalid = mutableListOf<String>()

        for (path in paths) {
            when {
                path.isBlank() -> invalid.add(path)
                path == DATA_CONTAINER || path == CUSTOM_FIELDS_CONTAINER -> invalid.add(path)
                path.startsWith(DATA_PREFIX) -> {
                    val name = path.removePrefix(DATA_PREFIX)
                    if (isFieldName(name)) data.add(name) else invalid.add(path)
                }
                path.startsWith(CUSTOM_FIELDS_PREFIX) -> {
                    val name = path.removePrefix(CUSTOM_FIELDS_PREFIX)
                    if (isFieldName(name)) custom.add(name) else invalid.add(path)
                }
                path.contains(".") -> invalid.add(path)
                else -> root.add(path)
            }
        }

        return ParsedFields(root = root, data = data, custom = custom, invalid = invalid)
    }

    /** A key inside data or custom_fields: not blank, and one level only. */
    private fun isFieldName(name: String): Boolean = name.isNotBlank() && !name.contains(".")
}
