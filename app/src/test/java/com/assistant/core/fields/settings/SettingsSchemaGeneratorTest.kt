package com.assistant.core.fields.settings

import com.assistant.core.ai.prompts.ModelValues
import com.assistant.core.ai.prompts.SchemaModelView
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Covers the schema a settings declaration generates: each shape turned into the JSON Schema
 * construct it stands for, and a variant followed on the way to the model and back.
 */
class SettingsSchemaGeneratorTest {

    private val mapper = ObjectMapper()
    private val text: (String) -> String = { it }

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null, required: Boolean = false, default: Any? = null) =
        SettingNode.Field(FieldDefinition(name, name, null, type, false, config), required = required, default = default)

    /** A tracking-like config: a type choosing between a numeric shape and a duration shape. */
    private val declaration = listOf(
        field("name", FieldType.TEXT, required = true),
        SettingNode.Section("display", listOf(field("icon", FieldType.TEXT))),
        SettingNode.Variant(
            selector = field("kind", FieldType.CHOICE, mapOf("options" to ChoiceSettings.storedOptions(listOf("numeric", "timer")))),
            cases = mapOf(
                "numeric" to listOf(SettingNode.ListOf("units", "units", SettingNode.Item.Value(
                    FieldDefinition("unit", "unit", null, FieldType.TEXT, false, null)), minItems = 1, distinct = true)),
                "timer" to listOf(field("window", FieldType.DURATION, default = 3_600_000L))
            )
        )
    )

    private val schema = SettingsSchemaGenerator.generate(declaration, text)
    private fun accepts(config: String) =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schema.toString()).validate(mapper.readTree(config)).isEmpty()

    @Test
    fun eachOptionOfAVariantHoldsItsOwnSettings() {
        assertTrue(accepts("""{ "name": "Weight", "kind": "numeric", "units": ["kg"] }"""))
        assertTrue(accepts("""{ "name": "Work", "kind": "timer", "window": 60000 }"""))
        assertFalse("another option's setting", accepts("""{ "name": "Work", "kind": "timer", "units": ["kg"] }"""))
        assertFalse("no option chosen", accepts("""{ "name": "Work" }"""))
    }

    /** A section stores nothing: its settings sit in the object that holds it. */
    @Test
    fun aSectionsSettingsAreStoredBesideTheOthers() {
        assertTrue(accepts("""{ "name": "Weight", "icon": "scale", "kind": "numeric", "units": ["kg"] }"""))
    }

    @Test
    fun aListIsHeldToItsMinimumAndToDistinctElements() {
        assertFalse("empty", accepts("""{ "name": "Weight", "kind": "numeric", "units": [] }"""))
        assertFalse("twice the same", accepts("""{ "name": "Weight", "kind": "numeric", "units": ["kg", "kg"] }"""))
    }

    /** Two settings of one name in one object: the one declared last would replace the other. */
    @Test
    fun aNameDeclaredTwiceIsRefused() {
        assertThrows(IllegalStateException::class.java) {
            SettingsSchemaGenerator.generate(listOf(field("name", FieldType.TEXT), field("name", FieldType.NUMERIC)), text)
        }
    }

    /** A duration inside a variant reaches the model in ISO 8601, its default too, and comes back. */
    @Test
    fun aVariantIsFollowedOnTheWayToTheModelAndBack() {
        val paris = ZoneId.of("Europe/Paris")
        val stored = JSONObject("""{ "name": "Work", "kind": "timer", "window": 5400000 }""")

        val out = ModelValues.toModel(stored, schema, paris) as JSONObject
        assertEquals("PT1H30M", out.getString("window"))
        assertEquals(5_400_000L, (ModelValues.fromModel(out, schema, paris) as JSONObject).get("window"))

        val timer = SchemaModelView.forModel(schema, paris).getJSONArray("oneOf").getJSONObject(1)
        assertEquals("PT1H", timer.getJSONObject("properties").getJSONObject("window").getString("default"))
    }

    /** A new config holds the declared defaults, a variant's own under its default option. */
    @Test
    fun theDefaultsMakeTheNewConfig() {
        val withDefault = listOf(
            field("name", FieldType.TEXT, required = true),
            SettingNode.Variant(
                selector = field("kind", FieldType.CHOICE, default = "timer"),
                cases = mapOf("numeric" to emptyList(), "timer" to listOf(field("window", FieldType.DURATION, default = 3_600_000L)))
            )
        )

        val config = SettingDefaults.of(withDefault)

        assertEquals("timer", config.getString("kind"))
        assertEquals(3_600_000L, config.getLong("window"))
        assertFalse("no default, no value", config.has("name"))
    }
}
