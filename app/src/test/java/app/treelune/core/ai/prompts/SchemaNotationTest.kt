package app.treelune.core.ai.prompts

import app.treelune.core.ai.data.CommunicationModules
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.EntrySchemaGenerator
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import app.treelune.core.fields.settings.FieldTypeSettings
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Covers what the model reads of a schema (SchemaNotation): every keyword the app's generators
 * write has a notation, nothing a schema says is lost, and a variant's shared settings are
 * written once.
 */
class SchemaNotationTest {

    private val text: (String) -> String = { it }

    private fun modelView(schema: JSONObject) = SchemaModelView.forModel(schema, ZoneId.of("Europe/Paris"))

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, "label of $name", "description of $name", type, false, config)

    /** A field of every type, with the settings that give its schema its keywords. */
    private val everyType = listOf(
        field("a_text", FieldType.TEXT, mapOf("length" to "SHORT")),
        field("a_number", FieldType.NUMERIC, mapOf("min" to 0, "max" to 10, "decimals" to 1, "unit" to "kg")),
        field("a_scale", FieldType.SCALE, mapOf("min" to 1, "max" to 5, "step" to 1, "min_label" to "bad", "max_label" to "good")),
        field("a_choice", FieldType.CHOICE, mapOf("options" to listOf(mapOf("value" to "a"), mapOf("value" to "b")), "open" to true)),
        field("some_choices", FieldType.CHOICE, mapOf("options" to listOf(mapOf("value" to "a"), mapOf("value" to "b")), "multiple" to true)),
        field("a_boolean", FieldType.BOOLEAN, mapOf("true_label" to "yes")),
        field("a_range", FieldType.RANGE, mapOf("min" to 0, "max" to 10, "decimals" to 0)),
        field("a_date", FieldType.DATE),
        field("a_time", FieldType.TIME),
        field("a_datetime", FieldType.DATETIME),
        field("a_duration", FieldType.DURATION)
    )

    /** The schemas the app generates, in the form the model reads. */
    private val generated: Map<String, JSONObject> by lazy {
        val entries = EntrySchemaGenerator.generate(
            EntryFields(
                name = CoreFieldUsage.REQUIRED,
                timestamp = CoreFieldUsage.OPTIONAL,
                data = everyType.map { FixedField(it, required = it.type == FieldType.TEXT) } +
                    FixedField(field("sent_copy", FieldType.TEXT), systemWritten = true),
                state = listOf(StateField(field("read", FieldType.BOOLEAN), filterable = true))
            ),
            everyType,
            text
        )
        val settings = listOf(
            SettingNode.Field(field("api_key", FieldType.TEXT), required = true, secret = true),
            SettingNode.Field(field("window", FieldType.DURATION), default = 3_600_000L),
            SettingNode.ListOf("extra_fields", "Fields", SettingNode.Item.Of(FieldTypeSettings.definitionNodes(text)), fieldDefinitions = true,
                summary = listOf("display_name")),
            SettingNode.Group("schedule", "Schedule", ScheduleSettings.nodes(text))
        ) + FieldType.entries.flatMap { FieldTypeSettings.configNodes(it, text) }.distinctBy { (it as? SettingNode.Field)?.definition?.name ?: it.hashCode().toString() }
        mapOf(
            "entries" to JSONObject(entries),
            "field definition" to SettingsSchemaGenerator.generate(FieldTypeSettings.definitionNodes(text), text),
            "communication module" to SettingsSchemaGenerator.generate(CommunicationModules.declarationNodes(text), text),
            "settings" to SettingsSchemaGenerator.generate(settings, text)
        ).mapValues { modelView(it.value) }
    }

    @Test
    fun everyGeneratedSchemaHasANotation() {
        generated.forEach { (name, schema) ->
            val notation = SchemaNotation.render(schema)
            assertTrue("$name renders nothing", notation.isNotBlank())
        }
    }

    /**
     * Every value a schema names, and every label and description it holds, is in what the model
     * reads, except inside a list of field definitions, which the prompt spells out once.
     */
    @Test
    fun nothingIsLost() {
        generated.forEach { (name, schema) ->
            val notation = SchemaNotation.render(schema)
            val missing = said(schema).filterNot { it in notation }
            assertEquals("$name loses: $missing", emptyList<String>(), missing)
        }
    }

    @Test
    fun theVariantsSharedSettingsAreWrittenOnce() {
        val nodes = listOf(
            SettingNode.Field(field("name", FieldType.TEXT), required = true),
            SettingNode.Variant(
                selector = SettingNode.Field(field("type", FieldType.CHOICE,
                    mapOf("options" to listOf(mapOf("value" to "one"), mapOf("value" to "two"))))),
                cases = mapOf(
                    "one" to listOf(SettingNode.Field(field("only_one", FieldType.BOOLEAN))),
                    "two" to emptyList()
                )
            )
        )
        val notation = SchemaNotation.render(modelView(SettingsSchemaGenerator.generate(nodes, text)))

        assertEquals(1, Regex("description of name").findAll(notation).count())
        assertTrue(notation, notation.contains("type*: one|two"))
        assertTrue(notation, notation.contains("if type = one:"))
        assertTrue(notation, notation.contains("only_one"))
        assertTrue(notation, notation.contains("if type = two: nothing more"))
    }

    @Test
    fun aListOfFieldDefinitionsPointsToThePrompt() {
        val notation = SchemaNotation.render(generated.getValue("settings"))
        assertTrue(notation, notation.contains("extra_fields: list of field definitions (see Fields)"))
        assertTrue("the definition is not spelled out", "display_name" !in notation.substringAfter("extra_fields").substringBefore("schedule"))
    }

    @Test
    fun whatTheModelMustNotWriteIsSaid() {
        val entries = SchemaNotation.render(generated.getValue("entries"))
        assertTrue(entries, Regex("sent_copy: .*written by the app").containsMatchIn(entries))
        val settings = SchemaNotation.render(generated.getValue("settings"))
        assertTrue(settings, Regex("api_key\\*: .*secret").containsMatchIn(settings))
        assertTrue(settings, Regex("window: ISO 8601 duration \\[default \"PT1H\"]").containsMatchIn(settings))
        // A required value's default is not what its absence means: its absence is refused
        assertTrue(settings, Regex("decimals\\*: .*suggested 0").containsMatchIn(settings))
    }

    @Test(expected = SchemaNotation.UnknownKeyword::class)
    fun anUnknownKeywordFails() {
        SchemaNotation.render(JSONObject("""{"type":"object","properties":{"x":{"type":"string","dependentRequired":{}}}}"""))
    }

    /** The property names, labels and descriptions of [schema], outside lists of field definitions. */
    private fun said(schema: Any?): Set<String> = when (schema) {
        is JSONObject -> if (schema.optBoolean(SettingsSchemaGenerator.FIELD_DEFINITIONS)) emptySet() else buildSet {
            // A property may be named "description": the names are read from "properties" only
            schema.optJSONObject("properties")?.let { properties ->
                properties.keys().forEach { add(it); addAll(said(properties.get(it))) }
            }
            listOf("title", "description").forEach { key -> (schema.opt(key) as? String)?.takeIf { it.isNotBlank() }?.let { add(it) } }
            listOf("items", "oneOf").forEach { key -> schema.opt(key)?.let { addAll(said(it)) } }
        }
        is JSONArray -> (0 until schema.length()).flatMap { said(schema.get(it)) }.toSet()
        else -> emptySet()
    }
}
