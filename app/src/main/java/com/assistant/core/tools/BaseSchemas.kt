package com.assistant.core.tools

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * Base JSON Schemas for all ToolTypes
 * Provides common fields to reduce token usage in AI prompts
 * Specific ToolTypes extend these base schemas with their own fields
 */
object BaseSchemas {
    
    /**
     * Base configuration schema for all tool types
     * Common fields: name, description, management, display_mode, icon_name
     */
    fun getBaseConfigSchema(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "${s.shared("tools_base_schema_config_name")}"
                },
                "description": {
                    "type": "string",
                    "maxLength": 250,
                    "description": "${s.shared("tools_base_schema_config_description")}"
                },
                "management": {
                    "type": "string",
                    "enum": ["manual", "ai"],
                    "description": "${s.shared("tools_base_schema_config_management")}"
                },
                "display_mode": {
                    "type": "string",
                    "enum": ["ICON", "MINIMAL", "LINE", "CONDENSED", "EXTENDED", "SQUARE", "FULL"],
                    "description": "${s.shared("tools_base_schema_config_display_mode")}"
                },
                "icon_name": {
                    "type": "string",
                    "maxLength": 60,
                    "default": "activity",
                    "description": "${s.shared("tools_base_schema_config_icon_name")}"
                },
                "config_validation": {
                    "type": "string",
                    "enum": ["enabled", "disabled"],
                    "description": "${s.shared("tools_base_schema_config_config_validation")}"
                },
                "data_validation": {
                    "type": "string", 
                    "enum": ["enabled", "disabled"],
                    "description": "${s.shared("tools_base_schema_config_data_validation")}"
                }
            },
            "required": ["name", "management", "display_mode", "config_validation", "data_validation"],
            "additionalProperties": false
        }
        """.trimIndent()
    }
    
    /**
     * Base data schema for all tool types
     * Common fields: id, tool_instance_id, tooltype, name, timestamp, created_at, updated_at
     */
    fun getBaseDataSchema(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_data_id")}"
                },
                "tool_instance_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_data_tool_instance_id")}"
                },
                "tooltype": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_data_tooltype")}"
                },
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "${s.shared("tools_base_schema_data_name")}"
                },
                "timestamp": {
                    "type": "number",
                    "minimum": 0,
                    "description": "${s.shared("tools_base_schema_data_timestamp")}"
                },
                "created_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "${s.shared("tools_base_schema_data_created_at")}"
                },
                "updated_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "${s.shared("tools_base_schema_data_updated_at")}"
                }
            },
            "required": ["tool_instance_id", "tooltype", "name", "timestamp"]
        }
        """.trimIndent()
    }
    
    /**
     * Utility function to merge base schema with specific schema
     * Flattens schemas to avoid nested allOf structures and simplify validation
     * Handles properties, required, allOf, and metadata fields (x-*, $schema, etc.)
     */
    fun createExtendedSchema(baseSchema: String, specificSchema: String): String {
        return try {
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val baseNode = objectMapper.readTree(baseSchema)
            val specificNode = objectMapper.readTree(specificSchema)
            
            val result = objectMapper.createObjectNode()
            
            // 1. Merge all metadata fields (type, $schema, title, description, x-*, etc.)
            mergeMetadataFields(result, baseNode, specificNode)
            
            // 2. Merge properties (specific overrides base)
            mergeProperties(result, baseNode, specificNode, objectMapper)
            
            // 3. Merge required arrays (union of both)
            mergeRequired(result, baseNode, specificNode, objectMapper)
            
            // 4. Extract and flatten allOf conditions to root level
            mergeAllOfConditions(result, baseNode, specificNode, objectMapper)
            
            // 5. Set additionalProperties (specific overrides base, default false)
            setAdditionalProperties(result, baseNode, specificNode)
            
            objectMapper.writeValueAsString(result)
            
        } catch (e: Exception) {
            // Fallback to old behavior if JSON parsing fails
            """
            {
                "allOf": [
                    $baseSchema,
                    $specificSchema
                ]
            }
            """.trimIndent()
        }
    }
    
    /**
     * Merges metadata fields (type, $schema, title, description, x-*, etc.)
     * Specific schema overrides base schema for conflicting fields
     */
    private fun mergeMetadataFields(
        result: com.fasterxml.jackson.databind.node.ObjectNode,
        baseNode: com.fasterxml.jackson.databind.JsonNode,
        specificNode: com.fasterxml.jackson.databind.JsonNode
    ) {
        val metadataFields = setOf("type", "\$schema", "title", "description")
        val skipFields = setOf("properties", "required", "allOf", "additionalProperties")
        
        // Add base metadata
        baseNode.fields().forEach { (key, value) ->
            if (key !in skipFields && (key in metadataFields || key.startsWith("x-"))) {
                result.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
        }
        
        // Add specific metadata (overrides base)
        specificNode.fields().forEach { (key, value) ->
            if (key !in skipFields && (key in metadataFields || key.startsWith("x-"))) {
                result.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
        }
        
        // Ensure we have a type if neither schema specified it
        if (!result.has("type")) {
            result.put("type", "object")
        }
    }
    
    /**
     * Merges properties from both schemas (specific overrides base)
     */
    private fun mergeProperties(
        result: com.fasterxml.jackson.databind.node.ObjectNode,
        baseNode: com.fasterxml.jackson.databind.JsonNode,
        specificNode: com.fasterxml.jackson.databind.JsonNode,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ) {
        val mergedProperties = objectMapper.createObjectNode()
        
        // Add base properties
        baseNode.get("properties")?.fields()?.forEach { (key, value) ->
            mergedProperties.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
        }
        
        // Add specific properties (overrides base)
        specificNode.get("properties")?.fields()?.forEach { (key, value) ->
            mergedProperties.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
        }
        
        if (mergedProperties.size() > 0) {
            result.set<com.fasterxml.jackson.databind.JsonNode>("properties", mergedProperties)
        }
    }
    
    /**
     * Merges required arrays from both schemas (union)
     */
    private fun mergeRequired(
        result: com.fasterxml.jackson.databind.node.ObjectNode,
        baseNode: com.fasterxml.jackson.databind.JsonNode,
        specificNode: com.fasterxml.jackson.databind.JsonNode,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ) {
        val requiredSet = mutableSetOf<String>()
        
        // Add base required
        baseNode.get("required")?.forEach { item ->
            requiredSet.add(item.asText())
        }
        
        // Add specific required
        specificNode.get("required")?.forEach { item ->
            requiredSet.add(item.asText())
        }
        
        if (requiredSet.isNotEmpty()) {
            val requiredArray = objectMapper.createArrayNode()
            requiredSet.sorted().forEach { requiredArray.add(it) }
            result.set<com.fasterxml.jackson.databind.JsonNode>("required", requiredArray)
        }
    }
    
    /**
     * Extracts and flattens allOf conditions from both schemas to root level
     */
    private fun mergeAllOfConditions(
        result: com.fasterxml.jackson.databind.node.ObjectNode,
        baseNode: com.fasterxml.jackson.databind.JsonNode,
        specificNode: com.fasterxml.jackson.databind.JsonNode,
        objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    ) {
        val allConditions = mutableListOf<com.fasterxml.jackson.databind.JsonNode>()
        
        // Extract from base schema
        baseNode.get("allOf")?.forEach { condition ->
            allConditions.add(condition)
        }
        
        // Extract from specific schema
        specificNode.get("allOf")?.forEach { condition ->
            allConditions.add(condition)
        }
        
        if (allConditions.isNotEmpty()) {
            val allOfArray = objectMapper.createArrayNode()
            allConditions.forEach { allOfArray.add(it) }
            result.set<com.fasterxml.jackson.databind.JsonNode>("allOf", allOfArray)
        }
    }
    
    /**
     * Sets additionalProperties (specific overrides base, default false)
     */
    private fun setAdditionalProperties(
        result: com.fasterxml.jackson.databind.node.ObjectNode,
        baseNode: com.fasterxml.jackson.databind.JsonNode,
        specificNode: com.fasterxml.jackson.databind.JsonNode
    ) {
        when {
            specificNode.has("additionalProperties") -> 
                result.set<com.fasterxml.jackson.databind.JsonNode>("additionalProperties", 
                    specificNode.get("additionalProperties"))
            baseNode.has("additionalProperties") -> 
                result.set<com.fasterxml.jackson.databind.JsonNode>("additionalProperties", 
                    baseNode.get("additionalProperties"))
            else -> 
                result.put("additionalProperties", false)
        }
    }
    
    /**
     * Get base default configuration JSON for all ToolTypes
     * Contains common fields with sensible defaults
     * @return JSON string with base configuration
     */
    fun getBaseDefaultConfig(): String {
        return """
        {
            "management": "manual",
            "config_validation": "disabled",
            "data_validation": "disabled", 
            "display_mode": "LINE"
        }
        """.trimIndent()
    }
    
    /**
     * Merge base default config with specific ToolType config
     * @param baseDefaults Base configuration JSON
     * @param specificDefaults ToolType-specific configuration JSON  
     * @return Merged configuration JSON
     */
    fun mergeDefaultConfigs(baseDefaults: String, specificDefaults: String): String {
        return try {
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val baseNode = objectMapper.readTree(baseDefaults)
            val specificNode = objectMapper.readTree(specificDefaults)
            
            val result = objectMapper.createObjectNode()
            
            // Add base fields
            baseNode.fields().forEach { (key, value) ->
                result.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
            
            // Override with specific fields
            specificNode.fields().forEach { (key, value) ->
                result.set<com.fasterxml.jackson.databind.JsonNode>(key, value)
            }
            
            objectMapper.writeValueAsString(result)
            
        } catch (e: Exception) {
            // Fallback: just return specific config if merge fails
            specificDefaults
        }
    }
    
    /**
     * Get human-readable name for common ToolType fields
     * Used for validation error messages
     * @param fieldName Technical field name (e.g., "management", "display_mode")
     * @param context Android context for string resource access
     * @return Translated field name or null if not a common field
     */
    fun getCommonFieldName(fieldName: String, context: Context): String? {
        val s = Strings.`for`(context = context)
        return when(fieldName) {
            "name" -> s.shared("tools_config_label_name")
            "description" -> s.shared("tools_config_label_description") 
            "management" -> s.shared("tools_config_label_management")
            "display_mode" -> s.shared("tools_config_label_display_mode")
            "icon_name" -> s.shared("tools_config_label_icon")
            "config_validation" -> s.shared("tools_config_label_config_validation")
            "data_validation" -> s.shared("tools_config_label_data_validation")
            else -> null
        }
    }
    
    /**
     * Generic length constants for text fields across all tool types
     * Maps directly to FieldType text variants
     */
    object FieldLimits {
        const val SHORT_LENGTH = 60        // FieldType.TEXT - identifiers, names, labels
        const val MEDIUM_LENGTH = 250      // FieldType.TEXT_MEDIUM - descriptions, text values
        const val LONG_LENGTH = 1500       // FieldType.TEXT_LONG - long content
        const val UNLIMITED_LENGTH = Int.MAX_VALUE  // FieldType.TEXT_UNLIMITED - no limits
    }
}