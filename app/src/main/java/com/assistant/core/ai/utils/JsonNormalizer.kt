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
 */
object JsonNormalizer {

    /**
     * Normalize a params map by converting all JSON native types to Kotlin types
     *
     * @param params Map potentially containing JSONObject/JSONArray
     * @return Map with all JSON types converted to Kotlin equivalents
     */
    fun normalizeParams(params: Map<String, Any>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        params.forEach { (key, value) ->
            val normalized = normalizeValue(value)
            if (normalized != null) {
                result[key] = normalized
            }
        }
        return result
    }

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

            // JSON types - convert to Kotlin
            // Handle JSONObject and its anonymous subclasses (JSONObject$1, etc.)
            // Don't cast directly - use reflection-based approach
            value.javaClass.name.startsWith("org.json.JSONObject") -> {
                // Reconstruct a new JSONObject from the anonymous subclass
                // This avoids ClassCastException with JSONObject$1
                val sourceObj = JSONObject(value.toString())
                val map = mutableMapOf<String, Any>()
                sourceObj.keys().forEach { key ->
                    val normalized = normalizeValue(sourceObj.get(key))
                    if (normalized != null) {
                        map[key] = normalized
                    }
                }
                map
            }

            value.javaClass.name.startsWith("org.json.JSONArray") -> {
                // Reconstruct a new JSONArray from the anonymous subclass
                val sourceArray = JSONArray(value.toString())
                (0 until sourceArray.length()).mapNotNull { i ->
                    normalizeValue(sourceArray.get(i))
                }
            }

            // Already Kotlin types - recurse in case of nested structures
            value is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val typedMap = value as Map<String, Any>
                val result = mutableMapOf<String, Any>()
                typedMap.forEach { (k, v) ->
                    val normalized = normalizeValue(v)
                    if (normalized != null) {
                        result[k] = normalized
                    }
                }
                result
            }

            value is List<*> -> {
                value.mapNotNull { item ->
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
