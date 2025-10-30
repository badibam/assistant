package com.assistant.core.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON Utilities - Convert Kotlin types to JSON types
 *
 * Problem solved:
 * - ExecutableCommand.params contains Map<String, Any> with nested structures
 * - When passed to services via coordinator, they expect JSONObject with JSONArray/JSONObject
 * - Simple JSONObject().put(key, value) doesn't convert nested Map/List recursively
 * - Services use optJSONArray/optJSONObject which return null if types don't match
 *
 * Solution:
 * - Recursive conversion of all Kotlin types to JSON equivalents
 * - Map<String, Any> → JSONObject
 * - List<Any> → JSONArray
 * - Primitives → kept as-is (String, Int, Long, Double, Boolean, null)
 *
 * Usage:
 * - Call toJSONObject() on Map<String, Any> before passing to service
 * - Handles arbitrary nesting depth
 * - Preserves all data, only changes types
 */
object JsonUtils {

    /**
     * Convert a Kotlin Map to JSONObject with recursive conversion of nested structures
     *
     * @param map Map potentially containing nested Maps and Lists
     * @return JSONObject with all Kotlin types converted to JSON equivalents
     */
    fun toJSONObject(map: Map<String, Any?>): JSONObject {
        val jsonObject = JSONObject()
        map.forEach { (key, value) ->
            jsonObject.put(key, toJSONValue(value))
        }
        return jsonObject
    }

    /**
     * Recursively convert a value to JSON-compatible type
     * Handles Map, List, primitives, null, and nested structures
     *
     * @param value Any value that may be a Kotlin type or already JSON type
     * @return Value with Kotlin types converted to JSON (or null if value is null)
     */
    private fun toJSONValue(value: Any?): Any? {
        return when {
            value == null -> JSONObject.NULL

            // Already JSON types - keep as-is
            value is JSONObject || value is JSONArray -> value

            // Kotlin types - convert to JSON
            value is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                toJSONObject(value as Map<String, Any?>)
            }

            value is List<*> -> {
                val jsonArray = JSONArray()
                value.forEach { item ->
                    jsonArray.put(toJSONValue(item))
                }
                jsonArray
            }

            // Primitives - keep as-is (JSONObject.put handles these natively)
            value is String || value is Int || value is Long || value is Double || value is Boolean -> value

            // Numbers - ensure proper type
            value is Number -> when {
                value.toString().contains(".") -> value.toDouble()
                else -> value.toLong()
            }

            // Unknown type - log warning and convert to string as fallback
            else -> {
                android.util.Log.w(
                    "JsonUtils",
                    "Unknown value type during JSON conversion: ${value.javaClass.name} - converting to string"
                )
                value.toString()
            }
        }
    }
}
