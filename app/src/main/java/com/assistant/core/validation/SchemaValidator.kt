package com.assistant.core.validation

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
    
    // Cache for compiled schemas - disabled during development
    private val schemaCache: MutableMap<String, JsonSchema> = mutableMapOf()
    private val cacheEnabled = false // TODO: Enable in production
    
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    
    /**
     * Main validation function - handles all validation scenarios
     * Supports recursive validation of nested objects and arrays
     * @param schema JSON schema string
     * @param data Data to validate as key-value map
     * @return ValidationResult with success/error status and user-friendly messages
     */
    fun validate(schema: String, data: Map<String, Any>): ValidationResult {
        safeLog("Starting validation")
        
        return try {
            // 1. Auto-detect context from data for conditional schemas
            val context = extractContextFromData(data)
            safeLog("Auto-detected context: $context")
            
            // 2. Resolve conditional schemas if context exists
            val resolvedSchema = if (context.isNotEmpty()) {
                SchemaResolver.resolve(schema, context)
            } else {
                schema
            }
            
            // 3. Validate data against resolved schema
            val result = validateAgainstSchema(resolvedSchema, data)
            
            safeLog("Validation completed - isValid: ${result.isValid}")
            result
            
        } catch (e: Exception) {
            safeLog("Exception during validation: ${e.message}")
            ValidationResult.error("Erreur de validation: ${e.message}")
        }
    }
    
    /**
     * Validates data against a specific schema provider
     * Convenience method for tool types and other schema providers
     */
    fun validate(schemaProvider: SchemaProvider, data: Map<String, Any>, useDataSchema: Boolean = false): ValidationResult {
        val schema = if (useDataSchema) {
            schemaProvider.getDataSchema() ?: return ValidationResult.error("Aucun schéma de données disponible")
        } else {
            schemaProvider.getConfigSchema()
        }
        
        return validate(schema, data)
    }
    
    /**
     * Core validation logic against a resolved schema
     */
    private fun validateAgainstSchema(schema: String, data: Map<String, Any>): ValidationResult {
        val jsonSchema = getOrCompileSchema(schema)
        val jsonString = buildJsonFromMap(data)
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        val dataNode = objectMapper.readTree(jsonString)
        
        // Validate against schema
        val errors = jsonSchema.validate(dataNode)
        
        return if (errors.isEmpty()) {
            // Recursively validate nested structures
            validateNestedStructures(schema, data)
        } else {
            val errorMessage = formatValidationErrors(errors)
            ValidationResult.error(errorMessage)
        }
    }
    
    /**
     * Recursively validates nested objects and arrays
     */
    private fun validateNestedStructures(schema: String, data: Map<String, Any>): ValidationResult {
        for ((key, value) in data) {
            when (value) {
                is List<*> -> {
                    // Validate array items
                    val arrayPath = "properties.$key.items"
                    val itemSchema = SchemaExtractor.extractSubSchema(schema, arrayPath)
                    
                    if (itemSchema != null) {
                        for ((index, item) in value.withIndex()) {
                            if (item is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                val itemData = item as Map<String, Any>
                                val itemResult = validate(itemSchema, itemData)
                                
                                if (!itemResult.isValid) {
                                    return ValidationResult.error("Erreur dans l'élément ${index + 1} de '$key': ${itemResult.errorMessage}")
                                }
                            }
                        }
                    }
                }
                
                is Map<*, *> -> {
                    // Validate nested object
                    val objectPath = "properties.$key"
                    val objectSchema = SchemaExtractor.extractSubSchema(schema, objectPath)
                    
                    if (objectSchema != null) {
                        @Suppress("UNCHECKED_CAST")
                        val nestedData = value as Map<String, Any>
                        val nestedResult = validate(objectSchema, nestedData)
                        
                        if (!nestedResult.isValid) {
                            return ValidationResult.error("Erreur dans '$key': ${nestedResult.errorMessage}")
                        }
                    }
                }
            }
        }
        
        return ValidationResult.success()
    }
    
    /**
     * Auto-detects context from data for conditional schema resolution
     */
    private fun extractContextFromData(data: Map<String, Any>): Map<String, Any> {
        val context = mutableMapOf<String, Any>()
        
        // Look for common context keys
        data["type"]?.let { context["type"] = it }
        data["mode"]?.let { context["mode"] = it }
        
        // Check nested value object (common in tracking data)
        (data["value"] as? Map<*, *>)?.let { valueMap ->
            valueMap["type"]?.let { context["type"] = it }
        }
        
        return context
    }
    
    /**
     * Converts a data map to JSON string for validation
     */
    private fun buildJsonFromMap(data: Map<String, Any>): String {
        return try {
            val jsonObject = JSONObject()
            
            for ((key, value) in data) {
                // Skip empty strings to trigger "missing" errors instead of "wrong type"
                if (value is String && value.trim().isEmpty()) {
                    continue
                }
                
                when (value) {
                    is Map<*, *> -> {
                        val nestedJson = JSONObject()
                        @Suppress("UNCHECKED_CAST")
                        val nestedMap = value as Map<String, Any>
                        for ((nestedKey, nestedValue) in nestedMap) {
                            if (nestedValue is String && nestedValue.trim().isEmpty()) {
                                continue
                            }
                            nestedJson.put(nestedKey, nestedValue)
                        }
                        if (nestedJson.length() > 0) {
                            jsonObject.put(key, nestedJson)
                        }
                    }
                    
                    is List<*> -> {
                        val filteredList = value.filter { 
                            !(it is String && it.trim().isEmpty()) 
                        }
                        if (filteredList.isNotEmpty()) {
                            jsonObject.put(key, org.json.JSONArray(filteredList))
                        }
                    }
                    
                    else -> {
                        jsonObject.put(key, value)
                    }
                }
            }
            
            jsonObject.toString()
        } catch (e: Exception) {
            safeLog("Error building JSON from data: ${e.message}")
            "{}"
        }
    }
    
    /**
     * Gets or compiles schema with optional caching
     */
    private fun getOrCompileSchema(schemaJson: String): JsonSchema {
        val cacheKey = schemaJson.hashCode().toString()
        
        return if (cacheEnabled) {
            schemaCache.getOrPut(cacheKey) {
                safeLog("Compiling and caching schema")
                compileSchema(schemaJson)
            }
        } else {
            safeLog("Compiling schema (no cache)")
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
     * Formats validation errors into readable string
     */
    private fun formatValidationErrors(errors: Set<ValidationMessage>): String {
        return errors.joinToString("; ") { error ->
            "Field '${error.path}': ${error.message}"
        }
    }
    
    /**
     * Clears schema cache - useful for tests or development
     */
    fun clearCache() {
        schemaCache.clear()
        safeLog("Schema cache cleared")
    }
    
    /**
     * Safe logging that works in both Android and unit test environments
     */
    private fun safeLog(message: String) {
        try {
            Log.d(TAG, message)
        } catch (e: RuntimeException) {
            println("$TAG: $message")
        }
    }
}