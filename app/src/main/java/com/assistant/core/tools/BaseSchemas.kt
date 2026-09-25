package com.assistant.core.tools

import com.assistant.core.utils.JsonUtils
import android.content.Context
import com.assistant.core.utils.LogManager
import com.assistant.core.strings.Strings
import com.assistant.core.validation.ValidationException
import com.assistant.core.validation.FieldLimits
import com.assistant.core.fields.FieldTypeSchemaProvider
import com.assistant.core.fields.EntrySchemaGenerator
import com.assistant.core.fields.toFieldDefinitions

/**
 * Base JSON Schemas for all ToolTypes
 * Provides common fields to reduce token usage in AI prompts
 * Specific ToolTypes extend these base schemas with their own fields
 */
object BaseSchemas {
    
    /**
     * Base configuration schema for all tool types
     * Common fields: name, description, management, display_mode, icon_name
     *
     * System-managed fields (automatically set by system, never provided by AI/user):
     * - schema_id: Determined by tooltype and variant
     * - data_schema_id: Determined by tooltype and variant
     */
    fun getBaseConfigSchema(context: Context): String {
        val s = Strings.`for`(context = context)

        // Get custom fields items schema from FieldTypeSchemaProvider (single source of truth)
        val customFieldsItemsSchema = FieldTypeSchemaProvider.getCustomFieldsItemsSchema(context)

        val schemaTemplate = """
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
                "validate_config": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("tools_base_schema_config_validate_config")}"
                },
                "validate_data": {
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
                "always_send": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("tools_base_schema_config_always_send")}"
                },
                "group": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.shared("tools_base_schema_config_group")}"
                },
                "extra_fields": {
                    "type": "array",
                    "description": "${s.shared("tools_base_schema_config_custom_fields")}",
                    "items": {{CUSTOM_FIELDS_ITEMS_SCHEMA}}
                }
            },
            "required": ["name", "management", "display_mode"],
            "additionalProperties": false
        }
        """.trimIndent()

        // Replace placeholder with actual schema from FieldTypeSchemaProvider
        return schemaTemplate.replace("{{CUSTOM_FIELDS_ITEMS_SCHEMA}}", customFieldsItemsSchema)
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
     * The data schema of a tool's entries, generated from what its tool type declares for
     * [config] and from the user's fields the config lists in extra_fields.
     *
     * The one schema an entry is held to: the service checks every write against it, the AI
     * reads it, and screens check against it before writing.
     *
     * @throws com.assistant.core.fields.ValidationException if a user field cannot be read
     */
    fun getEntrySchema(toolType: ToolTypeContract, config: org.json.JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        return EntrySchemaGenerator.generate(toolType.getEntryFields(config, context), extra, s::shared)
    }

    /**
     * The data schema of a tool type's entries as its getSchema hands it out: for the tool
     * instance [toolInstanceId] when given, read from its stored config, and for a new tool of
     * that type otherwise, from the type's default config.
     *
     * @throws IllegalStateException if the tool's config cannot be loaded or its fields read
     */
    fun getEntrySchema(toolType: ToolTypeContract, toolInstanceId: String?, context: Context): String {
        val config = if (toolInstanceId == null) org.json.JSONObject(toolType.getDefaultConfig())
                     else loadToolConfig(toolInstanceId, context)
        return getEntrySchemaOrThrow(toolType, config, toolInstanceId, context)
    }

    /**
     * [getEntrySchema] for a config the caller already holds, with its failure named after the
     * tool, so that a schema built from the wrong config never passes for the right one.
     *
     * @throws IllegalStateException if the config's fields cannot be read
     */
    fun getEntrySchemaOrThrow(toolType: ToolTypeContract, config: org.json.JSONObject, toolInstanceId: String?, context: Context): String =
        try {
            getEntrySchema(toolType, config, context)
        } catch (e: Exception) {
            throw IllegalStateException("Cannot read the fields of tool ${toolInstanceId ?: "(new)"}: ${e.message}", e)
        }

    /**
     * The stored config of the tool instance [toolInstanceId].
     *
     * @throws IllegalStateException if there is no such tool
     */
    fun loadToolConfig(toolInstanceId: String, context: Context): org.json.JSONObject {
        val tool = kotlinx.coroutines.runBlocking {
            com.assistant.core.database.AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId)
        } ?: throw IllegalStateException("Cannot load the config of tool $toolInstanceId for its entry schema")
        return org.json.JSONObject(tool.config_json)
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
            "validate_config" -> s.shared("tools_config_label_validate_config")
            "validate_data" -> s.shared("tools_config_label_validate_data")
            "schema_id" -> s.shared("tools_config_label_schema_id")
            "data_schema_id" -> s.shared("tools_config_label_data_schema_id")
            else -> null
        }
    }

}