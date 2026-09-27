package com.assistant.core.validation

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.settings.FieldTypeSettings
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.SettingsSchemaGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what a value refused by a variant is told: the errors of the branch its selector
 * points to, not that no branch holds.
 */
class VariantErrorsTest {

    private val text: (String) -> String = { it }
    private val mapper = ObjectMapper()

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, name, null, type, false, config)

    /** A config shaped like a tool's: shared settings, a list of field definitions, a variant on "type". */
    private val schema = SettingsSchemaGenerator.generate(listOf(
        SettingNode.Field(field("name", FieldType.TEXT), required = true),
        SettingNode.ListOf("extra_fields", "Fields", SettingNode.Item.Of(FieldTypeSettings.definitionNodes(text)), fieldDefinitions = true,
                summary = listOf("display_name")),
        SettingNode.Variant(
            selector = SettingNode.Field(field("type", FieldType.CHOICE,
                mapOf("options" to listOf(mapOf("value" to "numeric"), mapOf("value" to "text"))))),
            cases = mapOf(
                "numeric" to listOf(SettingNode.Field(field("unit", FieldType.TEXT), required = true)),
                "text" to emptyList()
            )
        )
    ), text)

    private fun errors(json: String): List<String> {
        val data = mapper.readTree(json)
        val found = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(mapper.readTree(schema.toString())).validate(data)
        val (others, explained) = VariantErrors.sort(found, schema, data)
        return others.map { it.message } + explained
    }

    @Test
    fun theChosenBranchSaysWhatIsMissing() {
        val found = errors("""{ "name": "Weight", "type": "numeric" }""")
        assertEquals(1, found.size)
        assertTrue(found.toString(), found.single().contains("unit"))
    }

    @Test
    fun aSettingOfAnotherOptionIsNamed() {
        val found = errors("""{ "name": "Mood", "type": "text", "unit": "kg" }""")
        assertTrue(found.toString(), found.any { it.contains("unit") })
    }

    @Test
    fun aMissingOrUnknownSelectorIsToldItsOptions() {
        assertTrue(errors("""{ "name": "Weight" }""").single().contains("numeric|text"))
        assertTrue(errors("""{ "name": "Weight", "type": "colour" }""").single().contains("numeric|text"))
    }

    /** A field definition in a list is a variant on its own type, explained at its own path. */
    @Test
    fun aVariantInsideAListIsExplainedAtItsPath() {
        val found = errors("""{ "name": "Weight", "type": "text",
            "extra_fields": [{ "display_name": "Mood", "type": "SCALE", "config": { "min": 1 } }] }""")
        assertTrue(found.toString(), found.any { it.startsWith("$.extra_fields[0]") && it.contains("max") })
    }

    @Test
    fun aValidValueHasNothingToExplain() {
        assertEquals(emptyList<String>(), errors("""{ "name": "Weight", "type": "numeric", "unit": "kg" }"""))
    }
}
