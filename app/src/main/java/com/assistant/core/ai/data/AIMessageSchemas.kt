package com.assistant.core.ai.data

import android.content.Context
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory

/**
 * AI Message Response Schema - validation for AI structured responses
 *
 * Defines the JSON Schema for validating AIMessage structure returned by AI.
 * This ensures AI responses are properly formatted with required fields and
 * mutual exclusivity between dataCommands, actionCommands and communicationModule.
 *
 * TEMPORAL PARAMETERS FOR DATA COMMANDS:
 * All AI dataCommands support temporal filtering via relative periods:
 * - A period is a filter on timestamp, whose value can be "offset_TYPE"
 * - Example: {"field": "timestamp", "op": "between", "value": ["-7_DAY", "0_DAY"]} (last 7 days)
 * - Available types: HOUR, DAY, WEEK, MONTH, YEAR
 * - Negative offset = past, 0 = current period, positive = future
 * - System automatically resolves using user's dayStartHour and weekStartDay config
 * - AI never needs to calculate timestamps, timezones, or handle calendar logic
 */
object AIMessageSchemas {

    /**
     * Get ai_message_response schema
     *
     * @param context Android context for i18n
     * @return Schema object for AI message validation
     */
    fun getAIMessageResponseSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "ai_message_response",
            displayName = s.shared("ai_schema_message_response_name"),
            description = s.shared("ai_schema_message_response_desc"),
            category = SchemaCategory.AI_PROVIDER,
            content = getAIMessageResponseSchemaContent(context)
        )
    }

    /**
     * Schema content for AIMessage response structure
     *
     * Enforces:
     * - preText is always required
     * - Mutual exclusivity between dataCommands, actionCommands, communicationModule
     * - validationRequest only valid with actionCommands
     * - postText only valid with actionCommands
     */
    private fun getAIMessageResponseSchemaContent(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
          "type": "object",
          "required": ["pre_text"],
          "properties": {
            "pre_text": {
              "type": "string",
              "minLength": 1,
              "description": "${s.shared("ai_schema_field_pretext_desc")}"
            },
            "validation_request": {
              "type": "boolean",
              "description": "${s.shared("ai_schema_field_validation_request_desc")}"
            },
            "data_commands": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["type", "params"],
                "properties": {
                  "type": {
                    "type": "string",
                    "enum": ["TOOL_DATA", "TOOL_CONFIG", "TOOL_INSTANCES", "ZONE_CONFIG", "ZONES", "APP_STATE", "CURRENT_DATETIME", "SCHEMA", "ICONS", "VARIABLES", "READING", "FILE", "IMPORT_PLAN"]
                  },
                  "params": {
                    "type": "object",
                    "description": "Command parameters. A relative date is {\"relative\": {\"unit\": \"DAY\", \"offset\": -1, \"edge\": \"START\"}} or {\"relative\": \"NOW\"}; units HOUR, DAY, WEEK, MONTH, YEAR, following the user's start of day and week."
                  }
                },
                "additionalProperties": false
              },
              "minItems": 1,
              "description": "${s.shared("ai_schema_field_data_commands_desc")}"
            },
            "action_commands": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["type", "params"],
                "properties": {
                  "type": {
                    "type": "string",
                    "enum": ["CREATE_DATA", "UPDATE_DATA", "DELETE_DATA", "START_DURATION", "STOP_DURATION", "TOOL_OPERATION", "CREATE_TOOL", "UPDATE_TOOL", "DELETE_TOOL", "CREATE_ZONE", "UPDATE_ZONE", "DELETE_ZONE", "CREATE_VARIABLE", "UPDATE_VARIABLE", "DELETE_VARIABLE", "IMPORT_DATA"]
                  },
                  "params": {
                    "type": "object"
                  }
                },
                "additionalProperties": false
              },
              "minItems": 1,
              "description": "${s.shared("ai_schema_field_action_commands_desc")}"
            },
            "post_text": {
              "type": "string",
              "minLength": 1,
              "description": "${s.shared("ai_schema_field_posttext_desc")}"
            },
            "keep_control": {
              "type": "boolean",
              "description": "${s.shared("ai_schema_field_keep_control_desc")}"
            },
            "communication_module": {
              "type": "object",
              "required": ["fields"],
              "description": "${s.shared("ai_schema_field_communication_module_desc")}"
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()
    }
}
