package com.assistant.core.fields.settings

import com.assistant.core.fields.FieldType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the schema of a field definition, generated from FieldTypeSettings: what a config's
 * extra_fields are checked against, and what the AI reads before defining a field. The prompt's
 * own examples are read against it too, since the model copies them.
 */
class FieldTypeSchemasTest {

    private val mapper = ObjectMapper()
    private val text: (String) -> String = { it }

    private val anyDefinition: JsonSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
        .getSchema(SettingsSchemaGenerator.generate(FieldTypeSettings.definitionNodes(text), text).toString())

    private fun accepts(definition: String) = anyDefinition.validate(mapper.readTree(definition)).isEmpty()

    @Test
    fun aDefinitionIsHeldToItsTypesSettings() {
        assertTrue(accepts("""{ "display_name": "Mood", "type": "SCALE", "config": { "min": 1, "max": 5, "min_label": "Bad" } }"""))
        assertFalse("a scale without its bounds", accepts("""{ "display_name": "Mood", "type": "SCALE" }"""))
        assertFalse("another type's setting", accepts("""{ "display_name": "Mood", "type": "TEXT", "config": { "min": 1 } }"""))
        assertFalse("an unknown type", accepts("""{ "display_name": "Mood", "type": "COLOR" }"""))
    }

    /** A choice's options are groups: a value, and what describes it. */
    @Test
    fun aChoicesOptionsAreGroups() {
        assertTrue(accepts("""{ "display_name": "Context", "type": "CHOICE",
            "config": { "options": [{ "value": "work", "color": "BLUE" }, { "value": "home" }], "open": true } }"""))
        assertFalse("options as strings", accepts("""{ "display_name": "Context", "type": "CHOICE",
            "config": { "options": ["work", "home"] } }"""))
        assertFalse("a color outside the palette", accepts("""{ "display_name": "Context", "type": "CHOICE",
            "config": { "options": [{ "value": "work", "color": "BEIGE" }, { "value": "home" }] } }"""))
        assertFalse("a single option", accepts("""{ "display_name": "Context", "type": "CHOICE",
            "config": { "options": [{ "value": "work" }] } }"""))
    }

    /** The schema of one type holds that type only. */
    @Test
    fun theSchemaOfOneTypeHoldsThatTypeOnly() {
        val booleanOnly = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(
            SettingsSchemaGenerator.generate(FieldTypeSettings.definitionNodes(text, listOf(FieldType.BOOLEAN)), text).toString()
        )
        assertTrue(booleanOnly.validate(mapper.readTree("""{ "display_name": "Done", "type": "BOOLEAN" }""")).isEmpty())
        assertFalse(booleanOnly.validate(mapper.readTree("""{ "display_name": "Done", "type": "TEXT" }""")).isEmpty())
    }

    /** Every field definition the prompt shows the model is one the app accepts. */
    @Test
    fun thePromptsFieldDefinitionsAreAccepted() {
        val prompt = File("src/main/java/com/assistant/core/strings/sources/ai_prompt_chunks.xml").readText()
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("\\'", "'")
        val types = FieldType.entries.map { it.name }.toSet()
        val definitions = Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(prompt)
            .mapNotNull { runCatching { mapper.readTree(it.groupValues[1].trim().let { b -> if (b.startsWith("\"")) "{$b}" else b }) }.getOrNull() }
            .flatMap { definitionsIn(it, types) }
            .toList()

        val rejected = definitions.filterNot { anyDefinition.validate(it).isEmpty() }
        assertTrue("the prompt shows no field definition", definitions.isNotEmpty())
        assertEquals("definitions the app refuses: $rejected", emptyList<JsonNode>(), rejected)
    }

    /**
     * The objects of [node] that define a field: a label and a field type. A communication
     * module's fields are the AI's own, named and required or not: CommunicationModulesTest
     * reads them.
     */
    private fun definitionsIn(node: JsonNode, types: Set<String>): Sequence<JsonNode> = sequence {
        if (node.isObject && node.has("display_name") && node.path("type").asText() in types) yield(node)
        node.fields().forEach { (key, child) -> if (key != "communication_module") yieldAll(definitionsIn(child, types)) }
        if (node.isArray) node.elements().forEach { yieldAll(definitionsIn(it, types)) }
    }
}
