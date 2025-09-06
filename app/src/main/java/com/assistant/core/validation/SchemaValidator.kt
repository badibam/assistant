package com.assistant.core.validation

import android.content.Context
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.json.JSONObject
import android.util.Log

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
     * Main validation function - handles all validation scenarios
     * Uses NetworkNT with Jackson for comprehensive validation of nested structures
     * @param schemaProvider Provider that supplies schema and field translations
     * @param data Data to validate as key-value map
     * @param context Android context for string resource access
     * @param useDataSchema true to use data schema, false to use config schema
     * @return ValidationResult with success/error status and user-friendly messages
     */
    fun validate(schemaProvider: SchemaProvider, data: Map<String, Any>, context: Context, useDataSchema: Boolean = false): ValidationResult {
        val schema = if (useDataSchema) {
            schemaProvider.getDataSchema() ?: throw IllegalArgumentException("SchemaProvider has no data schema")
        } else {
            schemaProvider.getConfigSchema()
        }
        safeLog("SCHEMADEBUG: SCHEMA VALIDATION START")
        
        return try {
            // Convert any JSONObject to Map for Jackson compatibility and filter empty values
            val cleanData = filterEmptyValues(convertJsonObjectsToMaps(data))
            
            // DEBUG: Log schema being used
            safeLog("SCHEMADEBUG: === SCHEMA BEING VALIDATED ===")
            safeLog("SCHEMADEBUG: $schema")
            safeLog("SCHEMADEBUG: === END SCHEMA ===")
            
            // Schema is flattened by createExtendedSchema, but still need SchemaResolver 
            // to resolve if/then conditions before passing to NetworkNT
            val resolvedSchema = SchemaResolver.resolve(schema, cleanData)
            
            // Let NetworkNT validate against resolved schema
            val jsonSchema = getOrCompileSchema(resolvedSchema)
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val dataNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(cleanData)
            
            safeLog("SCHEMADEBUG: DATA MAP: $cleanData")
            safeLog("SCHEMADEBUG: NETWORKNT DATANODE: $dataNode")
            
            // DEBUG: Check conditional validation
            if (schema.contains("\"if\"") && schema.contains("\"then\"")) {
                safeLog("SCHEMADEBUG: CONDITIONAL SCHEMA DETECTED")
                val valueObject = cleanData["value"] as? Map<*, *>
                if (valueObject != null) {
                    val valueType = valueObject["type"]
                    safeLog("SCHEMADEBUG: VALUE TYPE in data: '$valueType'")
                }
            }
            
            // NetworkNT validates against the full schema including allOf/if/then conditions
            val errors = jsonSchema.validate(dataNode)
            safeLog("SCHEMADEBUG: NETWORKNT ERRORS COUNT: ${errors.size}")
            errors.forEach { error ->
                safeLog("SCHEMADEBUG: ERROR Path='${error.path}' Message='${error.message}' SchemaPath='${error.schemaPath}'")
            }
            
            val result = if (errors.isEmpty()) {
                safeLog("SCHEMADEBUG: VALIDATION SUCCESS")
                ValidationResult.success()
            } else {
                val errorMessage = ValidationErrorProcessor.filterErrors(errors, schema, context, schemaProvider)
                if (errorMessage.isEmpty()) {
                    safeLog("SCHEMADEBUG: VALIDATION SUCCESS (errors filtered out)")
                    ValidationResult.success()
                } else {
                    safeLog("SCHEMADEBUG: VALIDATION FAILED: $errorMessage")
                    ValidationResult.error(errorMessage)
                }
            }
            
            result
            
        } catch (e: Exception) {
            safeLog("SCHEMADEBUG: Exception during validation: ${e.message}")
            ValidationResult.error("Erreur de validation: ${e.message}")
        }
    }
    
    
    /**
     * Filters out empty values (empty strings, null values) from data before validation
     * Recursively handles nested structures to prevent type mismatch errors
     */
    private fun filterEmptyValues(data: Map<String, Any>): Map<String, Any> {
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
     */
    private fun filterEmptyValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is String -> if (value.trim().isEmpty()) null else value
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val originalMap = value as Map<String, Any>
                val filteredMap = filterEmptyValues(originalMap)
                if (filteredMap.isEmpty()) null else filteredMap
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
     */
    private fun convertJsonObjectsToMaps(data: Map<String, Any>): Map<String, Any> {
        return data.mapValues { (_, value) ->
            convertAnyJsonObjectToMap(value) ?: ""
        }
    }
    
    /**
     * Recursively converts any JSONObject, JSONArray, or nested structures
     */
    private fun convertAnyJsonObjectToMap(value: Any?): Any? {
        return when (value) {
            is org.json.JSONObject -> {
                val map = mutableMapOf<String, Any>()
                value.keys().forEach { key ->
                    val convertedValue = convertAnyJsonObjectToMap(value.get(key))
                    if (convertedValue != null) {
                        map[key] = convertedValue
                    }
                }
                map
            }
            is org.json.JSONArray -> {
                val list = mutableListOf<Any>()
                for (i in 0 until value.length()) {
                    val convertedValue = convertAnyJsonObjectToMap(value.get(i))
                    if (convertedValue != null) {
                        list.add(convertedValue)
                    }
                }
                list
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val originalMap = value as Map<String, Any>
                originalMap.mapValues { (_, v) -> convertAnyJsonObjectToMap(v) ?: "" }
            }
            is List<*> -> {
                value.mapNotNull { convertAnyJsonObjectToMap(it) }
            }
            else -> value
        }
    }
    
    /**
     * Gets or compiles schema with optional caching
     */
    private fun getOrCompileSchema(schemaJson: String): JsonSchema {
        val cacheKey = schemaJson.hashCode().toString()
        
        return if (cacheEnabled) {
            schemaCache.getOrPut(cacheKey) {
                safeLog("SCHEMADEBUG: Compiling and caching schema")
                compileSchema(schemaJson)
            }
        } else {
            safeLog("SCHEMADEBUG: Compiling schema (no cache)")
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
        safeLog("SCHEMADEBUG: Schema cache cleared")
    }
    
    /**
     * Safe logging that works in both Android and unit test environments
     */
    private fun safeLog(message: String) {
        try {
            android.util.Log.d("SCHEMADEBUG", message)
        } catch (e: RuntimeException) {
            println("SCHEMADEBUG: $message")
        }
    }
}