package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** Covers the v43 rewrite: a numeric tracking tool's units live in "units" alone, its entries keep theirs. */
class TrackingUnitAtV43Test {

    @Test
    fun theValueUnitHeadsTheUnits() {
        val config = JSONObject("""{ "type": "numeric", "value": { "decimals": 1, "unit": "kg" }, "units": ["g"] }""")

        val next = TrackingUnitAtV43.config("tracking", config)

        assertFalse(next.getJSONObject("value").has("unit"))
        assertEquals(1, next.getJSONObject("value").getInt("decimals"))
        assertEquals(listOf("kg", "g"), next.getJSONArray("units").toList())
    }

    @Test
    fun aUnitAlreadyListedIsNotRepeated() {
        val config = JSONObject("""{ "type": "numeric", "value": { "decimals": 2, "unit": "kg" }, "units": ["g", "kg"] }""")

        assertEquals(listOf("g", "kg"), TrackingUnitAtV43.config("tracking", config).getJSONArray("units").toList())
    }

    @Test
    fun aToolWithoutUnitsGetsTheList() {
        val config = JSONObject("""{ "type": "numeric", "value": { "decimals": 2, "unit": "km" } }""")

        assertEquals(listOf("km"), TrackingUnitAtV43.config("tracking", config).getJSONArray("units").toList())
    }

    @Test
    fun otherToolsAreLeftAlone() {
        val journal = JSONObject("""{ "extra_fields": [ { "name": "walk", "type": "NUMERIC", "config": { "unit": "km", "decimals": 1 } } ] }""")

        assertSame("a user's NUMERIC field keeps its unit", journal, TrackingUnitAtV43.config("journal", journal))
    }

    @Test
    fun anEntryWithoutUnitTakesTheValueUnitAndOneWithAUnitKeepsIt() {
        val backup = JSONObject("""{
            "tool_instances": [
                { "id": "t1", "tooltype": "tracking", "config_json": "{\"type\":\"numeric\",\"value\":{\"decimals\":1,\"unit\":\"kg\"}}" },
                { "id": "t2", "tooltype": "tracking", "config_json": "{\"type\":\"numeric\",\"value\":{\"decimals\":1},\"units\":[\"cm\"]}" } ],
            "tool_data": [
                { "id": "e1", "tool_instance_id": "t1", "data": "{\"value\":72.5}" },
                { "id": "e2", "tool_instance_id": "t1", "data": "{\"value\":500,\"unit\":\"g\"}" },
                { "id": "e3", "tool_instance_id": "t2", "data": "{\"value\":180}" } ] }""")

        TrackingUnitAtV43.backup(backup)

        val entries = backup.getJSONArray("tool_data")
        fun data(i: Int) = JSONObject(entries.getJSONObject(i).getString("data"))
        assertEquals("kg", data(0).getString("unit"))
        assertEquals("g", data(1).getString("unit"))
        assertFalse("a tool whose value set no unit leaves its entries as they are", data(2).has("unit"))
        val config = JSONObject(backup.getJSONArray("tool_instances").getJSONObject(0).getString("config_json"))
        assertEquals(listOf("kg"), config.getJSONArray("units").toList())
    }

    private fun JSONArray.toList() = (0 until length()).map { getString(it) }
}
