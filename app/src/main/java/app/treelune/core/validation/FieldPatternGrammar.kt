package app.treelune.core.validation

/**
 * Single source for the grammar of the field paths a TOOL_DATA query asks for.
 *
 * A query names its fields one by one, with no wildcard. Four shapes exist:
 * - a root field, with no dot: "id", "timestamp", "name", "created_at"
 * - a field of the tool type: "data.value", "data.unit"
 * - a field of the user: "extra.notes", "extra.mood"
 * - a key of the entry's state: "state.read", "state.running"
 *
 * "data", "extra" and "state" on their own are refused: they are containers, and asking for
 * the container instead of a field inside it is the mistake this grammar exists to name.
 * Any other path carrying a dot is refused too, since nothing would know where to read it.
 * So is a path one level deeper, like "extra.sleep.start": the filter keeps whole keys
 * and would look for a key named "sleep.start", find none and leave the field out in silence.
 * A value holding an object or a list is asked for whole ("extra.sleep", "state.running").
 *
 * Root fields are not listed here on purpose. The grammar says where a path points, not
 * whether that field exists on the entry; a root field the entry does not carry is simply
 * absent from the result. A new root field therefore needs no change here.
 *
 * Both ends read the paths through this object: the validation that answers the AI
 * (AICommandProcessor) and the filtering that builds the result (ToolDataService), so a path
 * one accepts is one the other can serve.
 */
object FieldPatternGrammar {

    /** The objects of an entry a path can point inside, by their key. */
    val CONTAINERS = listOf("data", "extra", "state")

    /**
     * Requested paths sorted by where they point, plus the ones that point nowhere.
     *
     * [inside] maps each container to the keys asked inside it, without their prefix; a
     * container nothing was asked in is absent.
     */
    data class ParsedFields(
        val root: List<String>,
        val inside: Map<String, List<String>>,
        val invalid: List<String>
    )

    fun parse(paths: List<String>): ParsedFields {
        val root = mutableListOf<String>()
        val inside = mutableMapOf<String, MutableList<String>>()
        val invalid = mutableListOf<String>()

        for (path in paths) {
            val container = CONTAINERS.firstOrNull { path.startsWith("$it.") }
            when {
                path.isBlank() || path in CONTAINERS -> invalid.add(path)
                container != null -> {
                    val name = path.removePrefix("$container.")
                    if (isFieldName(name)) inside.getOrPut(container) { mutableListOf() }.add(name) else invalid.add(path)
                }
                path.contains(".") -> invalid.add(path)
                else -> root.add(path)
            }
        }

        return ParsedFields(root = root, inside = inside, invalid = invalid)
    }

    /** A key inside a container: not blank, and one level only. */
    private fun isFieldName(name: String): Boolean = name.isNotBlank() && !name.contains(".")
}
