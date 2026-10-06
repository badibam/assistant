package com.assistant.core.grid

import com.assistant.core.strings.StringsContext
import org.json.JSONObject

/**
 * The groups an element holds: null or one that exists. A zone
 * holds one of the home screen's groups; a tool, an automation or a variable one of its zone's
 * tool groups. Every service writing a group checks it here.
 */
object Groups {

    /** The group [json] holds at [key]: null when absent, JSON null or empty. */
    fun held(json: JSONObject, key: String = "group"): String? =
        if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }

    /**
     * A list of groups going from [before] to [after], the elements whose name changed given by
     * [renames] (former name → new name). Only the editor of the list knows a rename from a
     * removal followed by an addition: it hands its renames with the list.
     */
    class Change(val before: List<String>, val after: List<String>, val renames: Map<String, String>) {

        /** Whether each rename goes from a group of [before] to one of [after]. */
        val isValid: Boolean get() = renames.all { (from, to) -> from in before && to in after }

        /** The group an element holding [group] holds after the change. */
        fun held(group: String?): String? = group?.let { renames[it] ?: it }

        /** Whether an element holding [group] would hold a group the list no longer has. */
        fun loses(group: String?): Boolean = held(group)?.let { it !in after } ?: false

        /** [before] under the names the change gives them, to place the elements already renamed. */
        val beforeRenamed: List<String> get() = before.map { renames[it] ?: it }
    }

    /** The renames of the list [list] in a write's params: `renames: {list: {former: new}}`. */
    fun renames(params: JSONObject, list: String): Map<String, String> {
        val given = params.optJSONObject("renames")?.optJSONObject(list) ?: return emptyMap()
        return given.keys().asSequence().associateWith { given.getString(it) }
    }

    /** Why [group] cannot be held among [groups], naming them; null when it can (none, or one of them). */
    fun refusal(group: String?, groups: List<String>, s: StringsContext): String? =
        if (group.isNullOrEmpty() || group in groups) null
        else s.shared("service_error_group_unknown").format(group, groups.joinToString(", ").ifEmpty { "-" })

    /**
     * Why [change] cannot be written: renames that do not go from the list before to the list
     * after, or groups it removes that [members] still hold (member's name → group held), each
     * named with its members. Null when it can.
     */
    fun changeRefusal(change: Change, members: List<Pair<String, String?>>, s: StringsContext): String? {
        if (!change.isValid) return s.shared("service_error_group_renames")
        val lost = members.filter { (_, group) -> change.loses(group) }.groupBy({ it.second!! }, { it.first })
        if (lost.isEmpty()) return null
        return lost.entries.joinToString("\n") { (group, names) -> s.shared("service_error_group_in_use").format(group, names.joinToString(", ")) }
    }
}
