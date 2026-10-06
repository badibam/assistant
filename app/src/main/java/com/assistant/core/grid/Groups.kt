package com.assistant.core.grid

import com.assistant.core.strings.StringsContext
import org.json.JSONObject

/**
 * The groups an element holds (docs/design/group-integrity.md): null or one that exists. A zone
 * holds one of the home screen's groups; a tool, an automation or a variable one of its zone's
 * tool groups. Every service writing a group checks it here.
 */
object Groups {

    /** The group [json] holds at [key]: null when absent, JSON null or empty. */
    fun held(json: JSONObject, key: String = "group"): String? =
        if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }

    /** Why [group] cannot be held among [groups], naming them; null when it can (none, or one of them). */
    fun refusal(group: String?, groups: List<String>, s: StringsContext): String? =
        if (group.isNullOrEmpty() || group in groups) null
        else s.shared("service_error_group_unknown").format(group, groups.joinToString(", ").ifEmpty { "-" })
}
