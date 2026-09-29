package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the stored filters to their v52 form, conditions (Conditions): `{"field", "op", "value"}`
 * becomes `{"left": {"field"}, "op", "right": {"constant": value}}`, a `between`'s two values a pair
 * of constants, and no `right` where there was no value (`absent`, `present`). A filter already in
 * the new form is left as it is.
 *
 * Where filters are stored: the selection of a message's POINTER (a chat's, an automation's start
 * message), and the selection of a variable's reading terms.
 *
 * Shared by the database migration and the backup import.
 */
object ConditionsAtV52 {

    /** [old] as a condition, or null when it is one already. */
    fun filter(old: JSONObject): JSONObject? {
        if (!old.has("field") || old.has("left")) return null
        val next = JSONObject().put("left", JSONObject().put("field", old.getString("field"))).put("op", old.getString("op"))
        val value = old.opt("value")?.takeIf { it != JSONObject.NULL } ?: return next
        return if (old.getString("op") == "between" && value is JSONArray && value.length() == 2) {
            next.put("right", JSONArray().put(JSONObject().put("constant", value.get(0))).put(JSONObject().put("constant", value.get(1))))
        } else {
            next.put("right", JSONObject().put("constant", value))
        }
    }

    /** Rewrites the filters of [selection] in place; true when one changed. */
    private fun selection(selection: JSONObject?): Boolean {
        val filters = selection?.optJSONArray("filters") ?: return false
        var changed = false
        for (i in 0 until filters.length()) {
            val next = filter(filters.getJSONObject(i)) ?: continue
            filters.put(i, next)
            changed = true
        }
        return changed
    }

    /** A message's rich content with the filters of its pointers rewritten, or null when none changed. */
    fun richContent(json: String): String? {
        val content = JSONObject(json)
        val segments = content.optJSONArray("segments") ?: return null
        var changed = false
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONObject(i)
            if (segment.optString("type") != "enrichment" || segment.optString("enrichment_type") != "POINTER") continue
            val config = JSONObject(segment.getString("config"))
            if (!selection(config.optJSONObject("selection"))) continue
            segment.put("config", config.toString())
            changed = true
        }
        return if (changed) content.toString() else null
    }

    /** A variable's definition with the filters of its reading terms rewritten, or null when none changed. */
    fun definition(json: String): String? {
        val definition = JSONObject(json)
        val terms = definition.optJSONObject("terms") ?: return null
        var changed = false
        for (name in terms.keys()) {
            if (selection(terms.optJSONObject(name)?.optJSONObject("reading")?.optJSONObject("selection"))) changed = true
        }
        return if (changed) definition.toString() else null
    }

    /** Rewrites the backup document's messages and variables in place. */
    fun backup(data: JSONObject) {
        data.optJSONArray("session_messages")?.let { messages ->
            for (i in 0 until messages.length()) {
                val message = messages.getJSONObject(i)
                if (message.isNull("rich_content_json")) continue
                richContent(message.getString("rich_content_json"))?.let { message.put("rich_content_json", it) }
            }
        }
        data.optJSONArray("variables")?.let { variables ->
            for (i in 0 until variables.length()) {
                val variable = variables.getJSONObject(i)
                definition(variable.getString("definition_json"))?.let { variable.put("definition_json", it) }
            }
        }
    }
}
