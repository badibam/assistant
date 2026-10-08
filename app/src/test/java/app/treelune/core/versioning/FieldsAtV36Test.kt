package app.treelune.core.versioning

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

    /** The same JSON object, whatever the order of its keys. */
    private fun assertJson(expected: String, actual: JSONObject) =
        assertEquals(app.treelune.core.utils.JsonUtils.toMap(JSONObject(expected)), app.treelune.core.utils.JsonUtils.toMap(actual))

    @Test
    fun config_renamesTheUserFieldDefinitions() {
        val config = JSONObject("""{ "name": "Mood", "custom_fields": [ { "name": "mood", "display_name": "Mood", "type": "TEXT" } ] }""")

        val rewritten = FieldsAtV36.config("journal", config, emptyList())

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

    @Test
    fun aNote_keepsItsTextAndMovesItsPositionToState() {
        val note = FieldsAtV36.entry(
            "notes",
            FieldsAtV36.Entry("Note", JSONObject("""{ "content": "Buy bread", "position": 2 }"""), null, null),
            JSONObject()
        )

        assertEquals(null, note.name)
        assertJson("""{"content":"Buy bread"}""", note.data)
        assertEquals(2, note.state!!.getInt("position"))
    }

    @Test
    fun aMessage_movesWhatTheAppWritesToState_andKeepsWhatItSays() {
        val occurrence = FieldsAtV36.entry(
            "messages",
            FieldsAtV36.Entry(
                "Reminder",
                JSONObject("""{ "status": "sent", "title": "Today", "common_title": "Pills", "priority": "high",
                    "notification_sent": true, "read": false, "archived": false, "triggered_by": "SCHEDULE" }"""),
                null, null
            ),
            JSONObject()
        )

        assertEquals(setOf("title", "common_title", "priority"), occurrence.data.keys().asSequence().toSet())
        assertEquals(setOf("status", "notification_sent", "read", "archived", "triggered_by"), occurrence.state!!.keys().asSequence().toSet())
        assertEquals("Reminder", occurrence.name)
    }

    private fun tracking(data: String, config: String) =
        FieldsAtV36.entry("tracking", FieldsAtV36.Entry("x", JSONObject(data), null, null), JSONObject(config)).data

    /** Each tracking type keeps its value alone, and what it copied from the config goes. */
    @Test
    fun aTrackingEntry_keepsItsValueAlone() {
        assertJson("""{"value":72.5,"unit":"kg"}""", tracking("""{ "type": "numeric", "quantity": 72.5, "unit": "kg", "raw": "72.5 kg" }""", """{ "type": "numeric" }"""))
        assertJson("""{"value":7}""", tracking("""{ "type": "scale", "rating": 7, "min_value": 1, "max_value": 10, "raw": "7/10" }""", """{ "type": "scale" }"""))
        assertJson("""{"value":true}""", tracking("""{ "type": "boolean", "state": true, "true_label": "Yes", "false_label": "No" }""", """{ "type": "boolean" }"""))
        assertJson("""{"value":"Red"}""", tracking("""{ "type": "choice", "selected_option": "Red", "available_options": ["Red", "Blue"] }""", """{ "type": "choice" }"""))
        assertJson("""{"value":-1}""", tracking("""{ "type": "counter", "increment": -1 }""", """{ "type": "counter" }"""))
        assertJson("""{"value":"Fine"}""", tracking("""{ "type": "text", "text": "Fine" }""", """{ "type": "text" }"""))
    }

    @Test
    fun aTimer_turnsItsSecondsIntoMilliseconds() {
        assertEquals(5_100_000L, tracking("""{ "type": "timer", "duration_seconds": 5100 }""", """{ "type": "timer" }""").getLong("value"))
    }

    /** A numeric entry without a unit stays without one: none is invented. */
    @Test
    fun aNumericEntryWithoutUnit_getsNone() {
        assertFalse(tracking("""{ "type": "numeric", "quantity": 3, "unit": "" }""", """{ "type": "numeric" }""").has("unit"))
    }

    @Test
    fun aNumericConfig_declaresTheUnitsOfItsShortcutsThenOfItsEntries() {
        val config = FieldsAtV36.config(
            "tracking",
            JSONObject("""{ "type": "numeric", "items": [ { "name": "Apple", "default_quantity": 150, "unit": "g" }, { "name": "Water" } ] }"""),
            listOf(JSONObject("""{ "quantity": 1, "unit": "kg" }"""), JSONObject("""{ "quantity": 2, "unit": "g" }"""))
        )

        assertEquals("""["g","kg"]""", config.getJSONArray("units").toString())
        assertJson("""{"name":"Apple","value":150,"unit":"g"}""", config.getJSONArray("items").getJSONObject(0))
        assertJson("""{"name":"Water"}""", config.getJSONArray("items").getJSONObject(1))
    }

    @Test
    fun aScaleConfig_movesItsBoundsUnderValue() {
        val config = FieldsAtV36.config("tracking", JSONObject("""{ "type": "scale", "min": -5, "max": 5, "min_label": "Bad", "max_label": "", "items": [ { "name": "Mood" } ] }"""), emptyList())

        assertJson("""{"min":-5,"max":5,"min_label":"Bad"}""", config.getJSONObject("value"))
        assertFalse(config.has("min"))
    }

    /** The labels belong to the field: the first shortcut's pair becomes the tool's. */
    @Test
    fun aBooleanConfig_takesTheLabelsOfItsFirstShortcut() {
        val config = FieldsAtV36.config("tracking", JSONObject("""{ "type": "boolean", "items": [
            { "name": "Pill", "true_label": "Taken", "false_label": "Missed" },
            { "name": "Walk", "true_label": "Done", "false_label": "Not done" } ] }"""), emptyList())

        assertJson("""{"true_label":"Taken","false_label":"Missed"}""", config.getJSONObject("value"))
        assertJson("""{"name":"Walk"}""", config.getJSONArray("items").getJSONObject(1))
    }

    @Test
    fun aCounterConfig_keepsItsStepsAsShortcutValues() {
        val config = FieldsAtV36.config("tracking", JSONObject("""{ "type": "counter", "allow_decrement": false, "items": [ { "name": "Glass", "default_increment": 2 } ] }"""), emptyList())

        assertJson("""{"name":"Glass","value":2}""", config.getJSONArray("items").getJSONObject(0))
        assertFalse(config.getBoolean("allow_decrement"))
    }
}
