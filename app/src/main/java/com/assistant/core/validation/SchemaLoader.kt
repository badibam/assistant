package com.assistant.core.validation

import android.content.Context
import com.assistant.core.tools.BaseSchemas

/**
 * Central schema loader with template engine for external JSON schema files
 * Supports variable substitution for system constants like field length limits
 */
object SchemaLoader {
    private val schemaCache = mutableMapOf<String, String>()
    
    /**
     * Loads a JSON schema from assets with template variable substitution
     * @param context Android context for asset access
     * @param schemaPath Path relative to assets/schemas/ (e.g., "ui/zone_config_schema.json")
     * @return Processed JSON schema with variables replaced
     */
    fun loadSchema(context: Context, schemaPath: String): String {
        val cacheKey = "$schemaPath-${getTemplateVersion()}"
        
        return schemaCache.getOrPut(cacheKey) {
            try {
                val rawSchema = context.assets.open("schemas/$schemaPath").use { 
                    it.bufferedReader().readText()
                }
                
                applyTemplateVariables(rawSchema)
            } catch (e: Exception) {
                safeLog("Failed to load schema $schemaPath: ${e.message}")
                throw IllegalArgumentException("Schema file not found: $schemaPath", e)
            }
        }
    }
    
    /**
     * Applies template variable substitution to schema content
     * Replaces {{VARIABLE_NAME}} with actual values from BaseSchemas
     */
    private fun applyTemplateVariables(schema: String): String {
        var processedSchema = schema
        
        // Field limits variables from BaseSchemas
        processedSchema = processedSchema
            .replace("{{SHORT_LENGTH}}", BaseSchemas.FieldLimits.SHORT_LENGTH.toString())
            .replace("{{MEDIUM_LENGTH}}", BaseSchemas.FieldLimits.MEDIUM_LENGTH.toString())
            .replace("{{LONG_LENGTH}}", BaseSchemas.FieldLimits.LONG_LENGTH.toString())
            .replace("{{UNLIMITED_LENGTH}}", BaseSchemas.FieldLimits.UNLIMITED_LENGTH.toString())
        
        // Future: other system variables can be added here
        // processedSchema = processedSchema.replace("{{MAX_ITEMS}}", Constants.MAX_ITEMS.toString())
        
        safeLog("Applied template variables to schema (${schema.length} -> ${processedSchema.length} chars)")
        return processedSchema
    }
    
    /**
     * Generates cache key version based on template constants
     * Cache is invalidated when constants change
     */
    private fun getTemplateVersion(): String {
        return "${BaseSchemas.FieldLimits.SHORT_LENGTH}-${BaseSchemas.FieldLimits.MEDIUM_LENGTH}-${BaseSchemas.FieldLimits.LONG_LENGTH}"
    }
    
    /**
     * Clears schema cache - useful for development/testing
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
            android.util.Log.d("SchemaLoader", message)
        } catch (e: RuntimeException) {
            println("SchemaLoader: $message")
        }
    }
}