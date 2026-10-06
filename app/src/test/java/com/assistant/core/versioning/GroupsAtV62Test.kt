package com.assistant.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Covers the v62 rewrite: a group held is one that exists, or it is emptied. */
class GroupsAtV62Test {

    private fun backup() = JSONObject("""{
        "app_settings_categories": [ { "category": "main_screen", "settings": "{\"zone_groups\":[\"Body\",\"Work\"]}" } ],
        "zones": [
            { "id": "z1", "group": "Body", "tool_groups": "[\"Food\",\"Sport\"]" },
            { "id": "z2", "group": "Health" },
            { "id": "z3", "group": null }
        ],
        "tool_instances": [
            { "id": "t1", "zone_id": "z1", "config_json": "{\"name\":\"Meals\",\"group\":\"Food\"}" },
            { "id": "t2", "zone_id": "z1", "config_json": "{\"name\":\"Runs\",\"group\":\"Running\"}" },
            { "id": "t3", "zone_id": "z2", "config_json": "{\"name\":\"Notes\",\"group\":\"Food\"}" }
        ],
        "automations": [ { "id": "a1", "zone_id": "z1", "group": "Sport" }, { "id": "a2", "zone_id": "z1", "group": "Gym" } ],
        "variables": [ { "id": "v1", "zone_id": "z1", "group": "Food" }, { "id": "v2", "zone_id": "z2", "group": "Food" } ]
    }""")

    private fun row(data: JSONObject, key: String, index: Int) = data.getJSONArray(key).getJSONObject(index)

    @Test
    fun aZonesGroupIsOneOfTheHomeScreens() {
        val data = backup().also { GroupsAtV62.backup(it) }

        assertEquals("Body", row(data, "zones", 0).getString("group"))
        assertFalse("a group the home screen does not have leaves", row(data, "zones", 1).has("group"))
        assertFalse("a null stays absent", row(data, "zones", 2).has("group"))
    }

    @Test
    fun aToolsGroupIsOneOfItsZones() {
        val data = backup().also { GroupsAtV62.backup(it) }
        fun config(index: Int) = JSONObject(row(data, "tool_instances", index).getString("config_json"))

        assertEquals("Food", config(0).getString("group"))
        assertFalse(config(1).has("group"))
        assertFalse("a zone without tool groups keeps none", config(2).has("group"))
        assertEquals("the rest of the config is kept", "Runs", config(1).getString("name"))
    }

    @Test
    fun anAutomationsAndAVariablesGroupIsOneOfTheirZones() {
        val data = backup().also { GroupsAtV62.backup(it) }

        assertEquals("Sport", row(data, "automations", 0).getString("group"))
        assertFalse(row(data, "automations", 1).has("group"))
        assertEquals("Food", row(data, "variables", 0).getString("group"))
        assertFalse(row(data, "variables", 1).has("group"))
    }

    @Test
    fun withoutHomeScreenSettingsNoZoneGroupExists() {
        val data = backup().apply { remove("app_settings_categories") }.also { GroupsAtV62.backup(it) }

        assertFalse(row(data, "zones", 0).has("group"))
    }
}
