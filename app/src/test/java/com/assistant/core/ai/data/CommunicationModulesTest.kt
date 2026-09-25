package com.assistant.core.ai.data

import com.assistant.core.fields.settings.SettingsSchemaGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers a communication module as the AI declares it (docs/AI.md): a list of fields, each a field definition the AI names, and the answer checked against
 * the schema generated from them.
 */
class CommunicationModulesTest {

    private val mapper = ObjectMapper()
    private val text: (String) -> String = { it }
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    private val moduleSchema = factory.getSchema(
        SettingsSchemaGenerator.generate(CommunicationModules.declarationNodes(text), text).toString()
    )

    private fun accepts(module: String) = moduleSchema.validate(mapper.readTree(module)).isEmpty()

    private val zoneQuestion = """{ "fields": [
        { "name": "zone", "display_name": "Zone to delete", "type": "CHOICE", "required": true,
          "config": { "options": [{ "value": "health" }, { "value": "work" }] } },
        { "name": "why", "display_name": "Why", "type": "TEXT", "config": { "length": "LONG" } }
    ] }"""

    @Test
    fun aModuleIsFieldsTheAiNames() {
        assertTrue(accepts(zoneQuestion))
        assertTrue("a confirmation has no field", accepts("""{ "fields": [] }"""))
        assertFalse("a field without its key", accepts("""{ "fields": [{ "display_name": "Why", "type": "TEXT" }] }"""))
        assertFalse("a type's settings are those of any field",
            accepts("""{ "fields": [{ "name": "mood", "display_name": "Mood", "type": "SCALE" }] }"""))
        assertFalse("the former shape", accepts("""{ "type": "MultipleChoice", "data": { "options": ["a", "b"] } }"""))
    }

    /** The answer is an object of values under the fields' names, those a field needs included. */
    @Test
    fun theAnswerIsHeldToTheFields() {
        val module = CommunicationModule(JSONObject(zoneQuestion))
        val answerSchema = factory.getSchema(CommunicationModules.answerSchema(module, text).toString())
        fun answers(answer: String) = answerSchema.validate(mapper.readTree(answer)).isEmpty()

        assertTrue(answers("""{ "zone": "work", "why": "Done with it" }"""))
        assertTrue("an optional field left empty", answers("""{ "zone": "work" }"""))
        assertFalse("a required field left empty", answers("""{ "why": "Done with it" }"""))
        assertFalse("a value outside a closed choice", answers("""{ "zone": "leisure" }"""))
        assertFalse("a key the module did not ask", answers("""{ "zone": "work", "other": 1 }"""))
    }

    @Test
    fun aFieldSaysWhetherItIsRequired() {
        val fields = CommunicationModules.fieldsOf(JSONObject(zoneQuestion))
        assertEquals(listOf(true, false), fields.map { it.required })
        assertEquals(listOf("zone", "why"), fields.map { it.definition.name })
    }

    /** Every module the prompt shows the model is one the app accepts. */
    @Test
    fun thePromptsModulesAreAccepted() {
        val prompt = File("src/main/java/com/assistant/core/strings/sources/ai_prompt_chunks.xml").readText()
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("\\'", "'")
        val modules = Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(prompt)
            .mapNotNull { runCatching { mapper.readTree(it.groupValues[1].trim()) }.getOrNull() }
            .flatMap { modulesIn(it) }
            .toList()

        val rejected = modules.filterNot { moduleSchema.validate(it).isEmpty() }
        assertTrue("the prompt shows no communication module", modules.isNotEmpty())
        assertEquals("modules the app refuses: $rejected", emptyList<JsonNode>(), rejected)
    }

    private fun modulesIn(node: JsonNode): Sequence<JsonNode> = sequence {
        node.get("communication_module")?.let { yield(it) }
        node.elements().forEach { yieldAll(modulesIn(it)) }
    }
}
