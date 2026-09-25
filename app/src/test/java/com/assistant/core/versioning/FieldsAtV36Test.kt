package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what v36 does to the tools and entries it receives, from the database or a backup:
 * the user's fields are renamed, and nothing is lost on the way.
 */
class FieldsAtV36Test {

    @Test
    fun config_renamesTheUserFieldDefinitions() {
        val config = JSONObject("""{ "name": "Mood", "custom_fields": [ { "name": "mood", "display_name": "Mood", "type": "TEXT" } ] }""")

        val rewritten = FieldsAtV36.config("journal", config)

        assertFalse(rewritten.has("custom_fields"))
        assertEquals("mood", rewritten.getJSONArray("extra_fields").getJSONObject(0).getString("name"))
        // The config read by the entries is left as it was
        assertTrue(config.has("custom_fields"))
    }

    @Test
    fun backup_movesTheUserValuesToExtra() {
        val backup = JSONObject().put("tool_instances", JSONArray().put(JSONObject()
            .put("id", "t1").put("tooltype", "journal").put("config_json", """{ "custom_fields": [] }""")
        )).put("tool_data", JSONArray().put(JSONObject()
            .put("id", "e1").put("tool_instance_id", "t1").put("tooltype", "journal").put("name", "Day")
            .put("data", """{ "content": "..." }""").put("custom_fields", """{ "mood": "calm" }""")
        ))

        FieldsAtV36.backup(backup)

        val row = backup.getJSONArray("tool_data").getJSONObject(0)
        assertFalse(row.has("custom_fields"))
        assertEquals("calm", JSONObject(row.getString("extra")).getString("mood"))
        assertEquals("Day", row.getString("name"))
        assertTrue(JSONObject(backup.getJSONArray("tool_instances").getJSONObject(0).getString("config_json")).has("extra_fields"))
    }

    /** An entry whose tool is missing stops the import before it wipes anything. */
    @Test(expected = IllegalStateException::class)
    fun backup_refusesAnEntryWithoutItsTool() {
        FieldsAtV36.backup(JSONObject().put("tool_data", JSONArray().put(JSONObject()
            .put("id", "e1").put("tool_instance_id", "gone").put("tooltype", "journal").put("data", "{}")
        )))
    }
}
