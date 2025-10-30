package com.assistant.core.tools

import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.strings.Strings
import com.assistant.core.validation.ValidationException
import com.assistant.core.validation.FieldLimits

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
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.shared("tools_base_schema_config_name")}"
                },
                "description": {
                    "type": "string",
                    "maxLength": ${FieldLimits.MEDIUM_LENGTH},
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
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "default": "activity",
                    "description": "${s.shared("tools_base_schema_config_icon_name")}"
                },
                "validateConfig": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("tools_base_schema_config_validate_config")}"
                },
                "validateData": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("tools_base_schema_config_validate_data")}"
                },
                "schema_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_config_schema_id")}"
                },
                "data_schema_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_config_data_schema_id")}"
                },
                "execution_schema_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_config_execution_schema_id")}"
                },
                "always_send": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("tools_base_schema_config_always_send")}"
                }
            },
            "required": ["name", "management", "display_mode"],
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
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
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
                },
                "schema_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_data_schema_id")}"
                }
            },
            "required": ["tool_instance_id", "tooltype", "name", "timestamp"]
        }
        """.trimIndent()
    }

    /**
     * Base execution schema for all tool types that support executions
     * Common fields: id, tool_instance_id, tooltype, template_data_id, scheduled_time, execution_time,
     *                status, triggered_by, created_at, updated_at
     *
     * Note: snapshot_data, execution_result, and metadata are tool-specific and defined in each ToolType's schema
     */
    fun getBaseExecutionSchema(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_execution_id")}"
                },
                "tool_instance_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_execution_tool_instance_id")}"
                },
                "tooltype": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_execution_tooltype")}"
                },
                "template_data_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_execution_template_data_id")}"
                },
                "scheduled_time": {
                    "type": ["integer", "null"],
                    "minimum": 0,
                    "description": "${s.shared("tools_base_schema_execution_scheduled_time")}"
                },
                "execution_time": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "${s.shared("tools_base_schema_execution_execution_time")}"
                },
                "status": {
                    "type": "string",
                    "enum": ["pending", "completed", "failed", "cancelled"],
                    "description": "${s.shared("tools_base_schema_execution_status")}"
                },
                "triggered_by": {
                    "type": "string",
                    "enum": ["SCHEDULE", "MANUAL", "EVENT"],
                    "description": "${s.shared("tools_base_schema_execution_triggered_by")}"
                },
                "created_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "${s.shared("tools_base_schema_execution_created_at")}"
                },
                "updated_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "${s.shared("tools_base_schema_execution_updated_at")}"
                },
                "schema_id": {
                    "type": "string",
                    "description": "${s.shared("tools_base_schema_execution_schema_id")}"
                }
            },
            "required": ["tool_instance_id", "tooltype", "template_data_id", "execution_time", "status", "triggered_by"]
        }
        """.trimIndent()
    }
    
    /**
     * Simple utility function to merge base schema with specific schema
     * Merges properties and required fields only
     */
    fun createExtendedSchema(baseSchema: String, specificSchema: String): String {
        return try {
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            val baseNode = objectMapper.readTree(baseSchema)
            val specificNode = objectMapper.readTree(specificSchema)

            val result = objectMapper.createObjectNode()

            // Set basic schema structure
            result.put("type", "object")
            result.put("additionalProperties", false)

            // Merge properties (specific overrides base)
            mergeProperties(result, baseNode, specificNode, objectMapper)

            // Merge required arrays (union of both)
            mergeRequired(result, baseNode, specificNode, objectMapper)

            objectMapper.writeValueAsString(result)

        } catch (e: Exception) {
            LogManager.schema("Schema merge failed in createExtendedSchema: ${e.message}", "ERROR", e)
            throw ValidationException("Failed to create extended schema", e)
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
            "validateConfig" -> s.shared("tools_config_label_validate_config")
            "validateData" -> s.shared("tools_config_label_validate_data")
            "schema_id" -> s.shared("tools_config_label_schema_id")
            "data_schema_id" -> s.shared("tools_config_label_data_schema_id")
            "execution_schema_id" -> s.shared("tools_config_label_execution_schema_id")
            else -> null
        }
    }
    
}