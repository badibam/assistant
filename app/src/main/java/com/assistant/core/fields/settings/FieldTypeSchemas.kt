package com.assistant.core.fields.settings

import android.content.Context
import com.assistant.core.fields.FieldType
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.SchemaProvider

/**
 * The schema of a field definition of one type ("field_type_TEXT"…), generated from
 * FieldTypeSettings: what the AI asks for before defining a field, and what a field definition
 * is checked against.
 */
object FieldTypeSchemas : SchemaProvider {

    private const val PREFIX = "field_type_"

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        val type = FieldType.entries.firstOrNull { PREFIX + it.name == schemaId } ?: return null
        val s = Strings.`for`(context = context)
        return Schema(
            id = schemaId,
            displayName = s.shared("field_type_${type.name.lowercase()}_display_name"),
            description = s.shared("field_type_${type.name.lowercase()}_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = SettingsSchemaGenerator.generate(FieldTypeSettings.definitionNodes(s::shared, listOf(type)), s::shared).toString()
        )
    }

    override fun getAllSchemaIds(): List<String> = FieldType.entries.map { PREFIX + it.name }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return FieldTypeSettings.definitionNodes(s::shared).labelOf(fieldName) ?: s.shared("label_field_generic")
    }

    /** The schema of any field definition, whatever its type: an element of a config's extra_fields. */
    fun anyDefinition(context: Context): String {
        val s = Strings.`for`(context = context)
        return SettingsSchemaGenerator.generate(FieldTypeSettings.definitionNodes(s::shared), s::shared).toString()
    }
}
