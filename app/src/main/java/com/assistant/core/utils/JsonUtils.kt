package com.assistant.core.utils

import com.assistant.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON Utilities - Bidirectional conversion between Kotlin and JSON types
 *
 * Problem solved:
 * - ExecutableCommand.params contains Map<String, Any> with nested structures
 * - When passed to services via coordinator, they expect JSONObject with JSONArray/JSONObject
 * - Simple JSONObject().put(key, value) doesn't convert nested Map/List recursively
 * - Services use optJSONArray/optJSONObject which return null if types don't match
 * - Service responses may return JSONObject/String that need conversion back to Map
 *
 * Solution:
 * - Recursive conversion Kotlin → JSON: Map → JSONObject, List → JSONArray
 * - Recursive conversion JSON → Kotlin: JSONObject → Map, JSONArray → List
 * - String JSON → Map parsing with error handling
 * - Primitives preserved bidirectionally (String, Int, Long, Double, Boolean, null)
 *
 * Usage:
 * - toJSONObject(): Convert Map to JSONObject before passing to service
 * - toMap(): Convert JSONObject or String JSON to Map after receiving from service
 * - Handles arbitrary nesting depth in both directions
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
            // Preserve JSONObject.NULL explicitly (for field removal in migrations)
            value == JSONObject.NULL -> JSONObject.NULL

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
                LogManager.service(
                    "Unknown value type during JSON conversion: ${value.javaClass.name} - converting to string",
                    "WARN"
                )
                value.toString()
            }
        }
    }

    /**
     * Convert JSONObject or String JSON to Kotlin Map with recursive conversion of nested structures
     *
     * Handles three input types:
     * - JSONObject: Direct conversion to Map
     * - String: Parse as JSON then convert to Map
     * - Map: Return as-is (already converted)
     *
     * @param value JSONObject, String JSON, or Map
     * @return Mutable Map with all JSON types converted to Kotlin equivalents
     * @throws org.json.JSONException if String cannot be parsed as valid JSON
     */
    fun toMap(value: Any?): MutableMap<String, Any?> {
        return when (value) {
            is Map<*, *> -> {
                // Already a Map, convert recursively to ensure nested structures are also converted
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, Any?>).mapValues { (_, v) -> fromJSONValue(v) }.toMutableMap()
            }
            is String -> {
                // Parse String JSON and convert
                val json = JSONObject(value)
                jsonObjectToMap(json)
            }
            is JSONObject -> {
                // Convert JSONObject directly
                jsonObjectToMap(value)
            }
            null -> mutableMapOf()
            else -> {
                LogManager.service(
                    "Unexpected type in toMap: ${value.javaClass.name} - returning empty map",
                    "WARN"
                )
                mutableMapOf()
            }
        }
    }

    /**
     * Convert JSONObject to mutable Map recursively
     */
    private fun jsonObjectToMap(jsonObject: JSONObject): MutableMap<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        jsonObject.keys().forEach { key ->
            map[key] = fromJSONValue(jsonObject.get(key))
        }
        return map
    }

    /**
     * Recursively convert a JSON value to Kotlin type
     * Handles JSONObject, JSONArray, primitives, null, and nested structures
     *
     * @param value Any JSON value (JSONObject, JSONArray, primitive, or null)
     * @return Value with JSON types converted to Kotlin (or null if JSONObject.NULL)
     */
    private fun fromJSONValue(value: Any?): Any? {
        return when {
            value == null || value == JSONObject.NULL -> null

            // JSON types - convert to Kotlin
            value is JSONObject -> jsonObjectToMap(value)

            value is JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    list.add(fromJSONValue(value.get(i)))
                }
                list
            }

            // Primitives and already-Kotlin types - keep as-is
            value is String || value is Int || value is Long || value is Double || value is Boolean -> value

            // Numbers - ensure proper type
            value is Number -> when {
                value.toString().contains(".") -> value.toDouble()
                else -> value.toLong()
            }

            // Already converted Map/List - keep as-is
            value is Map<*, *> || value is List<*> -> value

            // Unknown type - log warning and convert to string as fallback
            else -> {
                LogManager.service(
                    "Unknown value type during fromJSON conversion: ${value.javaClass.name} - converting to string",
                    "WARN"
                )
                value.toString()
            }
        }
    }
}
