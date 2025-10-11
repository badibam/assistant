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
 * - Use period_start and period_end params with format "offset_TYPE"
 * - Example: period_start: "-7_DAY", period_end: "0_DAY" (last 7 days)
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
          "required": ["preText"],
          "properties": {
            "preText": {
              "type": "string",
              "minLength": 1,
              "description": "${s.shared("ai_schema_field_pretext_desc")}"
            },
            "validationRequest": {
              "type": "boolean",
              "description": "${s.shared("ai_schema_field_validation_request_desc")}"
            },
            "dataCommands": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["type", "params"],
                "properties": {
                  "type": {
                    "type": "string",
                    "enum": ["TOOL_DATA", "TOOL_CONFIG", "TOOL_INSTANCES", "ZONE_CONFIG", "ZONES", "APP_STATE", "SCHEMA"]
                  },
                  "params": {
                    "type": "object",
                    "description": "Command parameters. For temporal filtering, use period_start and period_end with format: offset_TYPE (e.g., -7_DAY for 7 days ago, 0_WEEK for current week). Available types: HOUR, DAY, WEEK, MONTH, YEAR. The system automatically applies user's dayStartHour and weekStartDay configuration."
                  }
                },
                "additionalProperties": false
              },
              "minItems": 1,
              "description": "${s.shared("ai_schema_field_data_commands_desc")}"
            },
            "actionCommands": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["type", "params"],
                "properties": {
                  "type": {
                    "type": "string",
                    "enum": ["CREATE_DATA", "UPDATE_DATA", "DELETE_DATA", "CREATE_TOOL", "UPDATE_TOOL", "DELETE_TOOL", "CREATE_ZONE", "UPDATE_ZONE", "DELETE_ZONE"]
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
            "postText": {
              "type": "string",
              "minLength": 1,
              "description": "${s.shared("ai_schema_field_posttext_desc")}"
            },
            "keepControl": {
              "type": "boolean",
              "description": "${s.shared("ai_schema_field_keep_control_desc")}"
            },
            "communicationModule": {
              "type": "object",
              "required": ["type", "data"],
              "properties": {
                "type": {
                  "type": "string",
                  "enum": ["MultipleChoice", "Validation"]
                },
                "data": {
                  "type": "object"
                }
              },
              "additionalProperties": false,
              "description": "${s.shared("ai_schema_field_communication_module_desc")}"
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()
    }
}
