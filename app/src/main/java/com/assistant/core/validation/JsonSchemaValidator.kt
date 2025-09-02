package com.assistant.core.validation

import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import com.assistant.core.tools.ToolTypeContract
import org.json.JSONObject
import android.util.Log

/**
 * Centralized JSON Schema validator for tool configurations and data
 * Replaces manual validation with schema-based validation for consistency
 */
object JsonSchemaValidator {
    private const val TAG = "JsonSchemaValidator"
    
    // Cache for compiled schemas - disabled during development
    private val schemaCache: MutableMap<String, JsonSchema> = mutableMapOf()
    private val cacheEnabled = false // TODO: Enable in production with cacheEnabled = true
    
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    
    /**
     * Validates tool configuration against its schema
     * @param toolType The tool type contract providing the schema
     * @param configJson The configuration JSON string to validate
     * @return ValidationResult with success/error status and messages
     */
    fun validateConfig(toolType: ToolTypeContract, configJson: String): ValidationResult {
        safeLog("validateConfig called for tool type: ${toolType::class.simpleName}")
        
        return try {
            val schema = getOrCompileSchema(toolType.getConfigSchema(), "${toolType::class.simpleName}_config")
            val configJsonNode = com.fasterxml.jackson.databind.ObjectMapper().readTree(configJson)
            
            val errors = schema.validate(configJsonNode)
            
            if (errors.isEmpty()) {
                safeLog("Config validation successful")
                ValidationResult.success()
            } else {
                val errorMessage = formatValidationErrors(errors)
                safeLog("Config validation failed: $errorMessage")
                ValidationResult.error(errorMessage)
            }
        } catch (e: Exception) {
            safeLog("Exception during config validation: ${e.message}")
            ValidationResult.error("Schema validation error: ${e.message}")
        }
    }
    
    /**
     * Validates tool data against its schema
     * @param toolType The tool type contract providing the schema  
     * @param dataJson The data JSON string to validate
     * @return ValidationResult with success/error status and messages
     */
    fun validateData(toolType: ToolTypeContract, dataJson: String): ValidationResult {
        safeLog("validateData called for tool type: ${toolType::class.simpleName}")
        
        return try {
            val schema = getOrCompileSchema(toolType.getDataSchema(), "${toolType::class.simpleName}_data")
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val dataJsonNode = objectMapper.readTree(dataJson)
            
            // Pre-process: Parse JSON strings in fields where schema expects objects
            val processedData = preprocessJsonStrings(dataJsonNode, schema, objectMapper)
            
            val errors = schema.validate(processedData)
            
            if (errors.isEmpty()) {
                safeLog("Data validation successful")
                ValidationResult.success()
            } else {
                val errorMessage = formatValidationErrors(errors)
                safeLog("Data validation failed: $errorMessage")
                ValidationResult.error(errorMessage)
            }
        } catch (e: Exception) {
            safeLog("Exception during data validation: ${e.message}")
            ValidationResult.error("Schema validation error: ${e.message}")
        }
    }
    
    /**
     * Gets or compiles schema with optional caching
     * Cache is disabled during development to allow schema modifications
     */
    private fun getOrCompileSchema(schemaJson: String, cacheKey: String): JsonSchema {
        return if (cacheEnabled) {
            schemaCache.getOrPut(cacheKey) {
                safeLog("Compiling and caching schema: $cacheKey")
                compileSchema(schemaJson)
            }
        } else {
            // Always recompile during development
            safeLog("Compiling schema (no cache): $cacheKey")
            compileSchema(schemaJson)
        }
    }
    
    /**
     * Compiles JSON schema string into JsonSchema object
     */
    private fun compileSchema(schemaJson: String): JsonSchema {
        val schemaNode = com.fasterxml.jackson.databind.ObjectMapper().readTree(schemaJson)
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
     * Pre-processes JSON data to parse JSON strings where schema expects objects
     * This handles cases like TrackingData.value which contains JSON strings
     * but schemas describe them as objects
     */
    private fun preprocessJsonStrings(
        dataNode: com.fasterxml.jackson.databind.JsonNode, 
        schema: JsonSchema,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ): com.fasterxml.jackson.databind.JsonNode {
        
        // For now, specifically handle the 'value' field for tracking data
        // This can be made more generic in the future if needed
        if (dataNode.isObject && dataNode.has("value")) {
            val valueNode = dataNode.get("value")
            if (valueNode.isTextual) {
                try {
                    // Try to parse the value string as JSON
                    val parsedValue = objectMapper.readTree(valueNode.asText())
                    
                    // Create a mutable copy and replace the value field
                    val mutableData = dataNode.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
                    mutableData.set<com.fasterxml.jackson.databind.JsonNode>("value", parsedValue)
                    
                    safeLog("Preprocessed JSON string in 'value' field")
                    return mutableData
                } catch (e: Exception) {
                    safeLog("Failed to parse 'value' as JSON, keeping as string: ${e.message}")
                    // Keep original if parsing fails
                }
            }
        }
        
        return dataNode
    }
    
    /**
     * Safe logging that works in both Android and unit test environments
     */
    private fun safeLog(message: String) {
        try {
            Log.d(TAG, message)
        } catch (e: RuntimeException) {
            // In unit tests, Android Log is not available, so we use println
            println("$TAG: $message")
        }
    }
}