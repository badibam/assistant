package com.assistant.core.fields.settings

import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What the config form does when a variant changes option: what only the former option declared
 * leaves, down to the parts of a list's elements, and the new option's defaults come in. On the
 * shape of a tracking tool, whose type decides its value settings, units and shortcuts.
 */
class SettingVariantsTest {

    private fun field(name: String, type: FieldType, default: Any? = null) =
        SettingNode.Field(FieldDefinition(name, name, null, type, false, null), default = default)

    private val item = { withValue: Boolean, withUnit: Boolean ->
        SettingNode.ListOf("items", "items", SettingNode.Item.Of(listOfNotNull(
            field("name", FieldType.TEXT),
            if (withValue) field("value", FieldType.NUMERIC) else null,
            if (withUnit) field("unit", FieldType.TEXT) else null
        )))
    }

    private val variant = SettingNode.Variant(
        selector = SettingNode.Field(FieldDefinition("type", "type", null, FieldType.CHOICE, false,
            mapOf("options" to ChoiceSettings.storedOptions(listOf("numeric", "counter", "event"), emptyMap()))), default = "numeric"),
        cases = mapOf(
            "numeric" to listOf(
                SettingNode.Group("value", "value", listOf(field("decimals", FieldType.NUMERIC, default = 0))),
                SettingNode.ListOf("units", "units", SettingNode.Item.Value(FieldDefinition("unit", "unit", null, FieldType.TEXT, false, null))),
                item(true, true)
            ),
            "counter" to listOf(field("allow_decrement", FieldType.BOOLEAN, default = true), item(true, false)),
            "event" to listOf(item(false, false))
        )
    )
    private val level = listOf(field("name", FieldType.TEXT), variant)

    private val numeric = JSONObject("""
        {"name":"Run","type":"numeric","value":{"decimals":1},"units":["km"],
         "items":[{"name":"Short","value":5,"unit":"km"}]}
    """)

    /** Settings of the former option leave, the shared ones stay, and the new option's defaults come in. */
    @Test
    fun theFormerOptionsSettingsLeaveAndTheNewDefaultsComeIn() {
        val counter = SettingVariants.switched(numeric, level, variant, "counter")

        assertEquals("Run", counter.getString("name"))
        assertFalse(counter.has("value"))
        assertFalse(counter.has("units"))
        assertEquals(true, counter.getBoolean("allow_decrement"))
    }

    /** A list both options have keeps its elements, each without what the new option does not declare. */
    @Test
    fun aSharedListKeepsOnlyWhatTheNewOptionDeclares() {
        val counter = SettingVariants.switched(numeric, level, variant, "counter")
        assertEquals(JSONObject("""{"name":"Short","value":5}""").toString(), counter.getJSONArray("items").getJSONObject(0).toString())

        val event = SettingVariants.switched(counter, level, variant, "event")
        assertEquals(JSONObject("""{"name":"Short"}""").toString(), event.getJSONArray("items").getJSONObject(0).toString())
        assertFalse(event.has("allow_decrement"))
    }
}
