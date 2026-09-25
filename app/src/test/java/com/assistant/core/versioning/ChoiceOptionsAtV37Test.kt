package com.assistant.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers the v37 rewrite of CHOICE options into groups, run once on the device's database and on
 * every older backup imported: nothing it misses can be fixed afterwards.
 */
class ChoiceOptionsAtV37Test {

    /** The options as maps: a JSONObject does not keep its keys in order. */
    private fun groups(options: org.json.JSONArray): List<Map<String, Any>> =
        (0 until options.length()).map { i -> options.getJSONObject(i).let { o -> o.keys().asSequence().associateWith { o.get(it) } } }

    /** A user's CHOICE field: each option a group, its color folded in, the table gone. */
    @Test
    fun aUsersChoiceFieldTakesItsColorsIntoItsOptions() {
        val config = JSONObject("""
            { "extra_fields": [
                { "name": "context", "type": "CHOICE",
                  "config": { "options": ["work", "home"], "option_colors": { "work": "BLUE" }, "open": true } },
                { "name": "note", "type": "TEXT", "config": { "length": "SHORT" } } ] }
        """)

        val choice = ChoiceOptionsAtV37.config("journal", config)
            .getJSONArray("extra_fields").getJSONObject(0).getJSONObject("config")

        assertEquals(listOf(mapOf("value" to "work", "color" to "BLUE"), mapOf("value" to "home")), groups(choice.getJSONArray("options")))
        assertFalse(choice.has("option_colors"))
        assertEquals(true, choice.getBoolean("open"))
    }

    /** A tracking tool of type choice keeps its options in its value settings. */
    @Test
    fun aChoiceTrackingToolsValueSettingsAreRewritten() {
        val config = JSONObject("""{ "type": "choice", "value": { "options": ["low", "high"] } }""")

        val value = ChoiceOptionsAtV37.config("tracking", config).getJSONObject("value")

        assertEquals("""[{"value":"low"},{"value":"high"}]""", value.getJSONArray("options").toString())
    }

    /** Options already in groups are left as they are: the rewrite can meet a newer config. */
    @Test
    fun optionsAlreadyInGroupsAreLeftAsTheyAre() {
        val config = JSONObject("""
            { "extra_fields": [ { "name": "c", "type": "CHOICE", "config": { "options": [{ "value": "a", "color": "RED" }] } } ] }
        """)

        val options = ChoiceOptionsAtV37.config("notes", config)
            .getJSONArray("extra_fields").getJSONObject(0).getJSONObject("config").getJSONArray("options")

        assertEquals(listOf(mapOf("value" to "a", "color" to "RED")), groups(options))
    }

    /** A backup's tool configs are rewritten in place. */
    @Test
    fun aBackupsConfigsAreRewritten() {
        val data = JSONObject().put("tool_instances", org.json.JSONArray().put(
            JSONObject().put("tooltype", "tracking").put("config_json", """{ "type": "choice", "value": { "options": ["x", "y"] } }""")
        ))

        ChoiceOptionsAtV37.backup(data)

        val config = JSONObject(data.getJSONArray("tool_instances").getJSONObject(0).getString("config_json"))
        assertEquals("""[{"value":"x"},{"value":"y"}]""", config.getJSONObject("value").getJSONArray("options").toString())
    }
}
