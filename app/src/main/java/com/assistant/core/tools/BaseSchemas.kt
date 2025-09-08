package com.assistant.core.tools

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
    fun getBaseConfigSchema(): String {
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "Display name for this tool instance"
                },
                "description": {
                    "type": "string",
                    "maxLength": 250,
                    "description": "Optional description for this tool instance"
                },
                "management": {
                    "type": "string",
                    "enum": ["manual", "ai"],
                    "description": "Management type: manual input or AI-assisted"
                },
                "display_mode": {
                    "type": "string",
                    "description": "Display mode for this tool instance"
                },
                "icon_name": {
                    "type": "string",
                    "maxLength": 60,
                    "default": "activity",
                    "description": "Icon name for this tool instance"
                },
                "config_validation": {
                    "type": "string",
                    "enum": ["enabled", "disabled"],
                    "description": "Configuration validation mode"
                },
                "data_validation": {
                    "type": "string", 
                    "enum": ["enabled", "disabled"],
                    "description": "Data validation mode"
                }
            },
            "required": ["name", "management"],
            "additionalProperties": false
        }
        """.trimIndent()
    }
    
    /**
     * Base data schema for all tool types
     * Common fields: id, tool_instance_id, tooltype, name, timestamp, created_at, updated_at
     */
    fun getBaseDataSchema(): String {
        return """
        {
            "type": "object",
            "properties": {
                "id": {
                    "type": "string",
                    "description": "Unique identifier for this data entry"
                },
                "tool_instance_id": {
                    "type": "string",
                    "description": "ID of the tool instance this data belongs to"
                },
                "tooltype": {
                    "type": "string",
                    "description": "Type of the tool (tracking, goal, etc.)"
                },
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "Name/identifier for this specific data entry"
                },
                "timestamp": {
                    "type": "number",
                    "minimum": 0,
                    "description": "Timestamp when this data was recorded (user-specified)"
                },
                "created_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "Timestamp when this entry was created in the system"
                },
                "updated_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "Timestamp when this entry was last updated"
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