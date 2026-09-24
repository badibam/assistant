package com.assistant.core.ai.utils

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON Normalizer - Convert JSON native types to Kotlin types
 *
 * Problem solved:
 * - AIMessage.parseParams() uses JSONObject.get() which returns JSON native types
 * - JSONArray is not compatible with List<*> in Kotlin (cast returns null)
 * - JSONObject is not compatible with Map<*, *> in Kotlin (cast returns null)
 * - All AI commands (data + action) may contain complex nested structures
 *
 * Solution:
 * - Recursive normalization of all JSON types to Kotlin equivalents
 * - JSONObject → Map<String, Any>
 * - JSONArray → List<Any>
 * - Primitives → kept as-is (String, Int, Long, Double, Boolean)
 * - null → kept as-is
 *
 * Usage:
 * - Call normalizeParams() on Map<String, Any> from parseParams()
 * - Handles arbitrary nesting depth
 * - Preserves all data, only changes types
 *
 * A null is kept at every level, the top one included: it is how a command asks for a field
 * to be emptied, and leaving the key out would read as a command that never mentioned it.
 * Coordinator turns it into JSON null at the service boundary.
 */
object JsonNormalizer {

    /**
     * Normalize a params map by converting all JSON native types to Kotlin types
     *
     * @param params Map potentially containing JSONObject/JSONArray
     * @return Map with all JSON types converted to Kotlin equivalents, JSON null as null
     */
    fun normalizeParams(params: Map<String, Any>): Map<String, Any?> =
        params.mapValues { (_, value) -> normalizeValue(value) }

    /**
     * Recursively normalize a value
     * Handles JSONObject, JSONArray, primitives, null, and nested structures
     *
     * @param value Any value that may be a JSON type or Kotlin type
     * @return Normalized value with JSON types converted to Kotlin
     */
    private fun normalizeValue(value: Any?): Any? {
        return when {
            value == null -> null

            // JSONObject.NULL is a special singleton representing null in JSON
            // It's not actually null but equals JSONObject.NULL
            value == JSONObject.NULL -> null

            // JSON types - convert to Kotlin
            // Handle JSONObject and its anonymous subclasses (JSONObject$1, etc.)
            // Don't cast directly - use reflection-based approach
            value.javaClass.name.startsWith("org.json.JSONObject") -> {
                // Check if the stringified value is just "null" (can happen with nested nulls)
                val stringified = value.toString()
                if (stringified == "null") {
                    return null
                }

                // Reconstruct a new JSONObject from the anonymous subclass
                // This avoids ClassCastException with JSONObject$1
                val sourceObj = JSONObject(stringified)
                val map = mutableMapOf<String, Any?>()
                sourceObj.keys().forEach { key ->
                    val normalized = normalizeValue(sourceObj.get(key))
                    // Keep null values in the map (don't filter them out)
                    // Filtering nulls was causing fields to disappear from batch operations
                    map[key] = normalized
                }
                map
            }

            value.javaClass.name.startsWith("org.json.JSONArray") -> {
                // Reconstruct a new JSONArray from the anonymous subclass
                val sourceArray = JSONArray(value.toString())
                // Keep nulls, like the Kotlin list branch below: dropping them shortened the
                // list and moved everything after the hole down a place, so the same data
                // came out differently depending only on which form it arrived in.
                (0 until sourceArray.length()).map { i ->
                    normalizeValue(sourceArray.get(i))
                }
            }

            // Already Kotlin types - recurse in case of nested structures
            value is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val typedMap = value as Map<String, Any?>
                val result = mutableMapOf<String, Any?>()
                typedMap.forEach { (k, v) ->
                    val normalized = normalizeValue(v)
                    // Keep null values in the map (don't filter them out)
                    // Filtering nulls was causing fields to disappear from batch operations
                    result[k] = normalized
                }
                result
            }

            value is List<*> -> {
                // Keep nulls in lists (use map instead of mapNotNull)
                value.map { item ->
                    normalizeValue(item)
                }
            }

            // Primitives - keep as-is
            value is String || value is Int || value is Long || value is Double || value is Boolean -> value

            // Numbers might come as different types from JSON
            value is Number -> when {
                value.toString().contains(".") -> value.toDouble()
                else -> value.toLong()
            }

            // Unknown type - log with full class name and keep as-is
            else -> {
                android.util.Log.w(
                    "JsonNormalizer",
                    "Unknown value type during normalization: ${value.javaClass.name} (${value.javaClass.simpleName})"
                )
                value
            }
        }
    }
}
