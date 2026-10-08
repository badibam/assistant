package app.treelune.core.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * The keys set to null taken out of an object written whole, where null means "not given": an
 * entry created, a config or settings saved. What is checked and what is stored are then the same
 * object — the validator checks what it is handed, nulls included.
 *
 * The objects inside are cleared the same way, those inside a list included. A list's own
 * elements are left as sent: an element is not a field left out, and taking one out would move
 * the others (a condition's bounds, `[null, 5]`).
 *
 * An update does not go through here: there a key set to null clears the field, which the merge
 * applies.
 */
object JsonNulls {

    fun withoutNullKeys(json: JSONObject): JSONObject = JSONObject().also { cleared ->
        json.keys().forEach { key ->
            val value = json.opt(key)
            if (value != null && value != JSONObject.NULL) cleared.put(key, cleared(value))
        }
    }

    fun withoutNullKeys(map: Map<String, Any?>): Map<String, Any?> =
        map.filterValues { it != null && it != JSONObject.NULL }.mapValues { (_, value) -> cleared(value) }

    private fun cleared(value: Any?): Any? = when (value) {
        is JSONObject -> withoutNullKeys(value)
        is JSONArray -> JSONArray().also { array -> for (i in 0 until value.length()) array.put(cleared(value.opt(i))) }
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            withoutNullKeys(value as Map<String, Any?>)
        }
        is List<*> -> value.map { cleared(it) }
        else -> value
    }
}
