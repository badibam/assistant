package com.assistant.core.validation

import android.content.Context
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.json.JSONObject
import com.assistant.core.utils.LogManager

/**
 * Unified JSON Schema validator with recursive validation support
 * Clean API for all validation scenarios
 */
object SchemaValidator {
    private const val TAG = "SchemaValidator"
    
    // Cache for compiled schemas - enabled for production performance
    private val schemaCache: MutableMap<String, JsonSchema> = mutableMapOf()
    private val cacheEnabled = true
    
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    
    /**
     * Main validation function using direct schema objects
     * @param schema Complete Schema object with content and metadata
     * @param data Data to validate as key-value map
     * @param context Android context for string resource access
     * @param partialValidation If true, ignores 'required' fields that are missing (for partial updates)
     * @return ValidationResult with success/error status and user-friendly messages
     */
    fun validate(
        schema: Schema,
        data: Map<String, Any>,
        context: Context,
        partialValidation: Boolean = false
    ): ValidationResult {
        LogManager.schema("Schema validation start for schema: ${schema.id}, partialValidation=$partialValidation")

        return try {
            val cleanData = filterEmptyValues(convertJsonObjectsToMaps(data))

            // For partial validation, modify schema to make 'required' fields optional
            // This allows updates to only specify the fields they want to change
            val schemaContent = if (partialValidation) {
                removeRequiredConstraint(schema.content)
            } else {
                schema.content
            }

            LogManager.schema("=== Schema being validated ===")
            LogManager.schema("Schema ID: ${schema.id}")
            LogManager.schema("Schema content (partial=$partialValidation): $schemaContent")
            LogManager.schema("=== End schema ===")

            val jsonSchema = getOrCompileSchema(schemaContent)
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val dataNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(cleanData)

            LogManager.schema("Data map: $cleanData")
            LogManager.schema("NetworkNT datanode: $dataNode")

            val errors = jsonSchema.validate(dataNode)
            LogManager.schema("NetworkNT errors count: ${errors.size}")
            errors.forEach { error ->
                LogManager.schema("Error Path='${error.path}' Message='${error.message}' SchemaPath='${error.schemaPath}'")
            }

            val result = if (errors.isEmpty()) {
                LogManager.schema("Validation success")
                ValidationResult.success()
            } else {
                val errorMessage = ValidationErrorProcessor.filterErrors(errors, schema.content, context)
                if (errorMessage.isEmpty()) {
                    LogManager.schema("Validation success (errors filtered out)")
                    ValidationResult.success()
                } else {
                    LogManager.schema("Validation failed: $errorMessage", "ERROR")
                    ValidationResult.error(errorMessage)
                }
            }

            result

        } catch (e: Exception) {
            LogManager.schema("Exception during validation: ${e.message}", "ERROR", e)
            ValidationResult.error("Erreur de validation: ${e.message}")
        }
    }
    
    
    /**
     * Filters out empty values from data before validation
     * Recursively handles nested structures to prevent type mismatch errors
     *
     * IMPORTANT: This filters out null values (removes them from the Map)
     * This is appropriate for CREATE operations where null = "don't specify this field"
     * For UPDATE operations with partial validation, nulls are already handled differently
     */
    private fun filterEmptyValues(data: Map<String, Any?>): Map<String, Any> {
        return data.mapNotNull { (key, value) ->
            val filteredValue = filterEmptyValue(value)
            if (filteredValue != null) {
                key to filteredValue
            } else {
                null
            }
        }.toMap()
    }
    
    /**
     * Filters a single value recursively
     *
     * CRITICAL: Empty strings are NOT filtered (they are valid values to intentionally clear a field)
     * Only null values are filtered for partial updates
     * Empty Maps/Lists are filtered only if all their contents were filtered out
     */
    private fun filterEmptyValue(value: Any?): Any? {
        return when (value) {
            null -> null
            // DO NOT filter empty strings - they represent intentional clearing of a field
            // This is different from null/absent field (partial update = keep existing value)
            is String -> value
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val originalMap = value as Map<String, Any?>
                val filteredMap = filterEmptyValues(originalMap)
                // Keep the map even if empty - it may be required by schema
                // The schema validation will catch if it shouldn't be empty
                filteredMap
            }
            is List<*> -> {
                val filteredList = value.mapNotNull { filterEmptyValue(it) }
                if (filteredList.isEmpty()) null else filteredList
            }
            else -> value
        }
    }
    
    /**
     * Converts Android JSONObjects to Maps for Jackson compatibility
     * Recursively handles nested structures
     *
     * IMPORTANT: Preserves null values - schemas may explicitly allow null for certain fields
     */
    private fun convertJsonObjectsToMaps(data: Map<String, Any?>): Map<String, Any?> {
        return data.mapValues { (_, value) ->
            convertAnyJsonObjectToMap(value)  // Returns null for null values, no fallback to ""
        }
    }
    
    /**
     * Recursively converts any JSONObject, JSONArray, or nested structures
     *
     * IMPORTANT: Preserves null values - schemas may explicitly allow null for certain fields
     */
    private fun convertAnyJsonObjectToMap(value: Any?): Any? {
        return when (value) {
            // Explicit null handling - preserve null values
            null -> null

            // Convert JSONObject.NULL to Kotlin null
            org.json.JSONObject.NULL -> null

            is org.json.JSONObject -> {
                val map = mutableMapOf<String, Any?>()
                value.keys().forEach { key ->
                    val rawValue = value.get(key)
                    // Recursively convert, preserving null values
                    val convertedValue = if (rawValue == org.json.JSONObject.NULL) {
                        null
                    } else {
                        convertAnyJsonObjectToMap(rawValue)
                    }
                    map[key] = convertedValue  // Add even if null - schema may allow it
                }
                map
            }
            is org.json.JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    val rawValue = value.get(i)
                    val convertedValue = if (rawValue == org.json.JSONObject.NULL) {
                        null
                    } else {
                        convertAnyJsonObjectToMap(rawValue)
                    }
                    if (convertedValue != null) {
                        list.add(convertedValue)
                    }
                }
                list
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val originalMap = value as Map<String, Any?>
                // Preserve null values in nested Maps - no fallback to ""
                originalMap.mapValues { (_, v) -> convertAnyJsonObjectToMap(v) }
            }
            is List<*> -> {
                value.map { convertAnyJsonObjectToMap(it) }
            }
            else -> value
        }
    }
    
    /**
     * Remove 'required' constraints from schema for partial validation
     * Recursively processes nested objects to remove all 'required' arrays
     *
     * This allows partial updates where only modified fields are provided
     * while still validating the types/formats of fields that ARE provided
     */
    private fun removeRequiredConstraint(schemaJson: String): String {
        return try {
            val schemaObject = JSONObject(schemaJson)
            removeRequiredFromObject(schemaObject)
            schemaObject.toString()
        } catch (e: Exception) {
            LogManager.schema("Failed to remove required constraint: ${e.message}", "WARN", e)
            schemaJson // Return original on error
        }
    }

    /**
     * Recursively remove 'required' arrays from a JSONObject and its nested objects
     */
    private fun removeRequiredFromObject(obj: JSONObject) {
        // Remove 'required' array at this level
        if (obj.has("required")) {
            obj.remove("required")
        }

        // Recursively process nested objects
        obj.keys().forEach { key ->
            val value = obj.opt(key)
            when (value) {
                is JSONObject -> removeRequiredFromObject(value)
                is org.json.JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        if (item is JSONObject) {
                            removeRequiredFromObject(item)
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets or compiles schema with optional caching
     */
    private fun getOrCompileSchema(schemaJson: String): JsonSchema {
        val cacheKey = schemaJson.hashCode().toString()
        
        return if (cacheEnabled) {
            schemaCache.getOrPut(cacheKey) {
                LogManager.schema("Compiling and caching schema")
                compileSchema(schemaJson)
            }
        } else {
            LogManager.schema("Compiling schema (no cache)")
            compileSchema(schemaJson)
        }
    }
    
    /**
     * Compiles JSON schema string into JsonSchema object
     */
    private fun compileSchema(schemaJson: String): JsonSchema {
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        val schemaNode = objectMapper.readTree(schemaJson)
        return schemaFactory.getSchema(schemaNode)
    }
    
    /**
     * Clears schema cache - useful for tests or development
     */
    fun clearCache() {
        schemaCache.clear()
        LogManager.schema("Schema cache cleared")
    }
    
}