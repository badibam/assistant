package com.assistant.core.ai.data

import android.content.Context
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema

/**
 * Communication Module Schemas - validation for AI communication modules
 *
 * Provides JSON Schema validation for different communication module types:
 * - MultipleChoice: question + list of options
 * - Validation: confirmation message
 *
 * Note: No SchemaProvider interface needed - these are static internal schemas,
 * not dynamically discovered like tool types.
 */
object CommunicationModuleSchemas {

    /**
     * Get schema for a communication module type
     *
     * @param type Module type (MultipleChoice, Validation, etc.)
     * @param context Android context (for future i18n if needed)
     * @return Schema object or null if type unknown
     */
    fun getSchema(type: String, context: Context): Schema? {
        val s = Strings.`for`(context = context)
        val schemaId = "communication_module_${type.lowercase()}"
        return when (type) {
            "MultipleChoice" -> Schema(
                id = schemaId,
                displayName = s.shared("ai_schema_communication_multiple_choice_name"),
                description = s.shared("ai_schema_communication_multiple_choice_desc"),
                category = com.assistant.core.validation.SchemaCategory.AI_PROVIDER,
                content = getMultipleChoiceSchema()
            )
            "Validation" -> Schema(
                id = schemaId,
                displayName = s.shared("ai_schema_communication_validation_name"),
                description = s.shared("ai_schema_communication_validation_desc"),
                category = com.assistant.core.validation.SchemaCategory.AI_PROVIDER,
                content = getValidationSchema()
            )
            else -> null
        }
    }

    /**
     * Schema for MultipleChoice module
     * Required: question (string), options (array of strings, min 2 items)
     */
    private fun getMultipleChoiceSchema(): String = """
        {
          "type": "object",
          "required": ["question", "options"],
          "properties": {
            "question": {
              "type": "string",
              "minLength": 1,
              "description": "The question to ask the user"
            },
            "options": {
              "type": "array",
              "items": {
                "type": "string",
                "minLength": 1
              },
              "minItems": 2,
              "description": "List of options for the user to choose from (minimum 2)"
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    /**
     * Schema for Validation module
     * Required: message (string)
     */
    private fun getValidationSchema(): String = """
        {
          "type": "object",
          "required": ["message"],
          "properties": {
            "message": {
              "type": "string",
              "minLength": 1,
              "description": "The validation message/question to show the user"
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()
}
