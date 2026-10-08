package app.treelune.core.fields.settings

import org.json.JSONArray

/**
 * The name each element of a list of values had when the screen read it, followed as the list is
 * edited: the only place a rename can be told from a removal followed by an addition
 * (Groups.Change). An element moved keeps its own, one removed takes its away,
 * one added has none, and one whose text is edited keeps the name it came with. Screen state,
 * never stored; the screen hands [renames] to the service with the list.
 */
class ListOrigins {

    private val origins = mutableMapOf<String, List<String?>>()

    /** Starts following [list] from its [values] as read, once: later calls leave it as followed. */
    fun start(list: String, values: List<Any?>) {
        if (list !in origins) origins[list] = values.map { it as? String }
    }

    /** The elements of [list] put in [order], the former position of each. */
    fun moved(list: String, order: List<Int>) {
        origins[list]?.let { held -> origins[list] = order.map { held.getOrNull(it) } }
    }

    fun removed(list: String, index: Int) {
        origins[list]?.let { held -> origins[list] = held.toMutableList().also { if (index in it.indices) it.removeAt(index) } }
    }

    fun added(list: String) {
        origins[list]?.let { held -> origins[list] = held + null }
    }

    /**
     * The renames of [list] standing at [values]: former name → new name, for each element whose
     * name changed. None when the list was not followed, or changed elsewhere than in its editor.
     */
    fun renames(list: String, values: JSONArray?): Map<String, String> {
        val held = origins[list] ?: return emptyMap()
        val current = values?.let { array -> (0 until array.length()).map { array.opt(it) as? String } } ?: emptyList()
        if (held.size != current.size) return emptyMap()
        return held.zip(current).mapNotNull { (from, to) -> if (from != null && to != null && from != to) from to to else null }.toMap()
    }
}
