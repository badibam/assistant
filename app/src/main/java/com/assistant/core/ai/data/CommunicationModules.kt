package com.assistant.core.ai.data

import android.content.Context
import com.assistant.core.ai.prompts.ModelValues
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.fields.FieldConfigValidator
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.FieldTypeSettings
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.SettingsSchemaGenerator
import com.assistant.core.fields.toFieldDefinition
import com.assistant.core.strings.Strings
import com.assistant.core.utils.JsonUtils
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import org.json.JSONObject

/**
 * What a communication module is made of, declared with the fields: the list of fields the AI
 * writes, and the answer the user gives to them.
 *
 * A field of a module is a field definition, as in a config's extra_fields, with two
 * differences: the AI names it, since the answer comes back under that name, and it says
 * whether an answer needs it. Its type's settings are those of any field.
 */
object CommunicationModules {

    /** The name the AI asks the schema of a module by. */
    const val SCHEMA_ID = "communication_module"

    /** The declaration of one field of a module. */
    private fun fieldNodes(text: (String) -> String): List<SettingNode> =
        // A question's texts are read once, in a card as wide as the screen: longer than a column's
        FieldTypeSettings.definitionNodes(text, labels = TextLength.MEDIUM).mapNotNull { node ->
            val name = (node as? SettingNode.Field)?.definition?.name
            when (name) {
                // The AI's own key for the answer, not one the app makes from the label
                "name" -> node.copy(
                    required = true,
                    systemWritten = false,
                    definition = node.definition.copy(description = text("ai_module_field_name_description"))
                )
                // Whether an empty field shows in a tool's history: nothing to say in a question
                "always_visible" -> null
                else -> node
            }
        } + SettingNode.Field(
            FieldDefinition(
                name = "required",
                displayName = text("ai_module_field_required"),
                description = text("ai_module_field_required_description"),
                type = FieldType.BOOLEAN,
                alwaysVisible = false,
                config = null
            ),
            default = false
        )

    /**
     * The declaration of a module: its fields, none for a confirmation. Not required: an empty
     * list and no list both ask for a confirmation, and the validator drops an empty list before
     * checking, so a required one would refuse the confirmation it was written for.
     */
    fun declarationNodes(text: (String) -> String): List<SettingNode> = listOf(
        SettingNode.ListOf(
            name = "fields",
            label = text("ai_module_fields"),
            item = SettingNode.Item.Of(fieldNodes(text)),
            fieldDefinitions = true,
            summary = listOf("display_name", "type")
        )
    )

    /** The schema of a module, as the AI reads it and as its module is checked against. */
    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = SCHEMA_ID,
            displayName = s.shared("ai_schema_communication_module_name"),
            description = s.shared("ai_schema_communication_module_desc"),
            category = SchemaCategory.AI_PROVIDER,
            content = SettingsSchemaGenerator.generate(declarationNodes(s::shared), s::shared).toString()
        )
    }

    /**
     * What is wrong with a module the AI wrote, or null when nothing is: its schema, then what a
     * schema cannot say of a field (a key in snake_case, two fields under one key, a type's
     * settings that contradict each other).
     */
    fun check(declaration: JSONObject, context: Context): String? {
        val result = SchemaValidator.validate(schema(context), JsonUtils.toMap(storedForm(declaration)), context)
        if (!result.isValid) return result.errorMessage

        val definitions = fieldsOf(declaration).map { it.definition }
        definitions.forEachIndexed { index, definition ->
            val others = definitions.filterIndexed { i, _ -> i < index }
            val fieldResult = FieldConfigValidator.validate(definition, others, context)
            if (!fieldResult.isValid) return "${definition.name}: ${fieldResult.errorMessage}"
        }
        return null
    }

    /** The fields of a declaration, which [check] has found valid. */
    fun fieldsOf(declaration: JSONObject): List<SettingNode.Field> {
        val list = storedForm(declaration).optJSONArray("fields") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val field = list.getJSONObject(i)
            SettingNode.Field(field.toFieldDefinition(), required = field.optBoolean("required", false))
        }
    }

    /**
     * [declaration] with its values in the form the app stores: a field's default value that is
     * a duration, which the AI writes in ISO 8601 like any duration, in milliseconds. One that
     * does not read is left as written, for [check] to refuse.
     */
    private fun storedForm(declaration: JSONObject): JSONObject {
        val stored = JSONObject(declaration.toString())
        val list = stored.optJSONArray("fields") ?: return stored
        for (i in 0 until list.length()) {
            val field = list.optJSONObject(i) ?: continue
            val default = field.opt("default_value")
            if (field.optString("type") == FieldType.DURATION.name && default is String) {
                runCatching { java.time.Duration.parse(default).toMillis() }.onSuccess { field.put("default_value", it) }
            }
        }
        return stored
    }

    /** The schema of an answer to [module]: its fields, generated like any declaration's. */
    fun answerSchema(module: CommunicationModule, text: (String) -> String): JSONObject =
        SettingsSchemaGenerator.generate(module.fields, text)

    /** Whether [answer] answers [module]: every field it needs, each value one its field takes. */
    fun checkAnswer(module: CommunicationModule, answer: JSONObject, context: Context): ValidationResult {
        val s = Strings.`for`(context = context)
        val schema = Schema(
            id = "${SCHEMA_ID}_answer",
            displayName = s.shared("ai_schema_communication_module_name"),
            description = s.shared("ai_schema_communication_module_desc"),
            category = SchemaCategory.AI_PROVIDER,
            content = answerSchema(module, s::shared).toString()
        )
        return SchemaValidator.validate(schema, JsonUtils.toMap(answer), context)
    }

    /** [answer] in the form the AI reads: its dates and durations in ISO 8601. */
    fun answerForModel(module: CommunicationModule, answer: JSONObject, context: Context): JSONObject {
        val s = Strings.`for`(context = context)
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        return ModelValues.toModel(answer, answerSchema(module, s::shared), zone) as JSONObject
    }

    /** An answer as the AI wrote it read back into stored values, to show it by its fields. */
    fun answerFromModel(module: CommunicationModule, answer: JSONObject, context: Context): JSONObject {
        val s = Strings.`for`(context = context)
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        return ModelValues.fromModel(answer, answerSchema(module, s::shared), zone) as JSONObject
    }
}
