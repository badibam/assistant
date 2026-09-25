package com.assistant.core.utils

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
 * - String JSON → Map parsing
 * - A value with no JSON form fails, rather than being stored as its text or dropped
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

            // Any collection is an array: a Set would otherwise fall through to its text "[a, b]"
            value is Collection<*> -> {
                val jsonArray = JSONArray()
                value.forEach { item ->
                    jsonArray.put(toJSONValue(item))
                }
                jsonArray
            }

            value is Array<*> -> toJSONValue(value.toList())

            // An enum value is stored under its name
            value is Enum<*> -> value.name

            // Primitives - keep as-is (JSONObject.put handles these natively)
            value is String || value is Int || value is Long || value is Double || value is Boolean -> value

            // Numbers - ensure proper type
            value is Number -> when {
                value.toString().contains(".") -> value.toDouble()
                else -> value.toLong()
            }

            // Its text would be stored in place of the value, with nothing to tell it apart
            else -> throw IllegalArgumentException("No JSON form for a ${value.javaClass.name}")
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
     * @throws IllegalArgumentException for any other type
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
            // An empty map would read as an object holding nothing, and the value would be lost
            else -> throw IllegalArgumentException("Not a JSON object: a ${value.javaClass.name}")
        }
    }

    /**
     * Convert a Kotlin List to a JSONArray, for the two edges that still speak in strings:
     * a database column and an entity rebuilt from a service result.
     */
    fun toJSONArray(list: List<Any?>): JSONArray {
        return JSONArray().apply {
            list.forEach { put(toJSONValue(it)) }
        }
    }

    /**
     * Convert a JSON array, in any of the forms it arrives in, to a Kotlin List.
     *
     * Mirrors toMap for values that are arrays rather than objects: a column holds the string
     * form, callers get the list. Null and blank both give an empty list, since a column that
     * was never written and one holding nothing mean the same thing to a caller.
     */
    fun toList(value: Any?): List<Any?> {
        return when (value) {
            null -> emptyList()
            is List<*> -> value.map { fromJSONValue(it) }
            is JSONArray -> (0 until value.length()).map { fromJSONValue(value.get(it)) }
            is String -> if (value.isBlank()) emptyList() else toList(JSONArray(value))
            else -> throw IllegalArgumentException("Not a JSON array: a ${value.javaClass.name}")
        }
    }

    /**
     * Convert one JSON value, of any kind, to its Kotlin form: a JSONArray to a List, a
     * JSONObject to a Map, all the way down; JSONObject.NULL to null.
     */
    fun toValue(value: Any?): Any? = fromJSONValue(value)

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

            else -> throw IllegalArgumentException("Not a JSON value: a ${value.javaClass.name}")
        }
    }
}
