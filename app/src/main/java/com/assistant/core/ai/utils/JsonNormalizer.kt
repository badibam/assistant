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
        return when (value) {
            null -> null

            // JSON types - convert to Kotlin
            is JSONObject -> {
                val map = mutableMapOf<String, Any>()
                value.keys().forEach { key ->
                    val normalized = normalizeValue(value.get(key))
                    if (normalized != null) {
                        map[key] = normalized
                    }
                }
                map
            }

            is JSONArray -> {
                (0 until value.length()).mapNotNull { i ->
                    normalizeValue(value.get(i))
                }
            }

            // Already Kotlin types - recurse in case of nested structures
            is Map<*, *> -> {
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

            is List<*> -> {
                value.mapNotNull { item ->
                    normalizeValue(item)
                }
            }

            // Primitives - keep as-is
            is String, is Int, is Long, is Double, is Boolean -> value

            // Numbers might come as different types from JSON
            is Number -> when {
                value.toString().contains(".") -> value.toDouble()
                else -> value.toLong()
            }

            // Unknown type - keep as-is with warning
            else -> {
                android.util.Log.w(
                    "JsonNormalizer",
                    "Unknown value type during normalization: ${value::class.java.simpleName}"
                )
                value
            }
        }
    }
}
