package app.treelune.core.versioning

import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the chaining the backup import relies on: an entry written by an old version goes
 * through every step up to the current one, each step applied to the tooltypes it is meant for.
 * The single transformers have their own tests; this one checks that they are wired in at the
 * right version and that nothing between them undoes their work.
 */
class JsonTransformersTest {

    private val current = AppDatabase.VERSION

    /** A message entry from a v12 backup loses everything later versions dropped, and keeps its content. */
    @Test
    fun anOldMessageEntry_reachesTheCurrentShape() {
        val v12 = """
            {
                "data": { "schema_id": "messages_data", "title": "Water", "text": "Drink" },
                "executions": [ { "scheduled_time": 1700000000000, "status": "sent" } ],
                "transcription_metadata": { "text": { "model": "vosk", "segments_texts": ["Drink"] } }
            }
        """

        val migrated = JSONObject(JsonTransformers.transformToolData(v12, "messages", 12, current))

        assertFalse(migrated.has("executions"))
        assertFalse(migrated.has("transcription_metadata"))
        val data = migrated.getJSONObject("data")
        assertFalse(data.has("schema_id"))
        assertEquals("Water", data.getString("title"))
        assertEquals("Drink", data.getString("text"))
    }

    /** The message-only steps leave another tooltype's fields of the same name alone. */
    @Test
    fun theMessageSteps_touchMessagesOnly() {
        val entry = """{ "data": { "schema_id": "kept" }, "executions": [1] }"""

        val migrated = JSONObject(JsonTransformers.transformToolData(entry, "tracking", 13, current))

        assertEquals("kept", migrated.getJSONObject("data").getString("schema_id"))
        assertTrue(migrated.has("executions"))
    }

    /** The generic steps reach a tooltype that has no transformer of its own. */
    @Test
    fun theGenericSteps_reachEveryTooltype() {
        val entry = """{ "content": "Idea", "transcription_metadata": { "content": { "segments_texts": ["Idea"] } } }"""

        val migrated = JSONObject(JsonTransformers.transformToolData(entry, "notes", 12, current))

        assertFalse(migrated.has("transcription_metadata"))
        assertEquals("Idea", migrated.getString("content"))
    }

    /** A v12 → v13 step alone keeps the metadata and only drops its duplicated segment texts. */
    @Test
    fun theV12Step_dropsTheSegmentTextsOnly() {
        val entry = """{ "transcription_metadata": { "text": { "model": "vosk", "segments_texts": ["a"] } } }"""

        val migrated = JSONObject(JsonTransformers.transformToolData(entry, "journal", 12, 13))

        val field = migrated.getJSONObject("transcription_metadata").getJSONObject("text")
        assertFalse(field.has("segments_texts"))
        assertEquals("vosk", field.getString("model"))
    }

    /** A tool config from v24 gets the TEXT split, loses its dead date bounds and its former default icon. */
    @Test
    fun anOldToolConfig_reachesTheCurrentShape() {
        val v24 = """
            {
                "name": "Notes",
                "icon_name": "note",
                "custom_fields": [
                    { "name": "summary", "type": "TEXT_SHORT" },
                    { "name": "body", "type": "TEXT_LONG" },
                    { "name": "log", "type": "TEXT_UNLIMITED" },
                    { "name": "when", "type": "DATE", "config": { "min": "2020-01-01", "max": "2030-01-01" } },
                    { "name": "score", "type": "NUMERIC", "config": { "min": 0, "max": 10 } }
                ]
            }
        """

        val migrated = JSONObject(JsonTransformers.transformToolConfig(v24, "notes", 24, current))

        val fields = migrated.getJSONArray("custom_fields")
        listOf("SHORT", "LONG", "UNLIMITED").forEachIndexed { i, length ->
            val field = fields.getJSONObject(i)
            assertEquals("TEXT", field.getString("type"))
            assertEquals(length, field.getJSONObject("config").getString("length"))
        }
        assertFalse(fields.getJSONObject(3).has("config"))
        val score = fields.getJSONObject(4)
        assertEquals("NUMERIC", score.getString("type"))
        assertEquals(10, score.getJSONObject("config").getInt("max"))
        assertEquals("sticky-note", migrated.getString("icon_name"))
    }

    /** An entry already at the target version comes back as the very same string. */
    @Test
    fun theCurrentVersion_isReturnedUntouched() {
        val json = """{"b": 1,   "a": 2}"""

        assertSame(json, JsonTransformers.transformToolData(json, "messages", current, current))
        assertSame(json, JsonTransformers.transformToolConfig(json, "notes", current, current))
        assertSame(json, JsonTransformers.transformAppConfig(json, AppSettingCategories.FORMAT, current, current))
    }

    /** A step that cannot read the entry fails the import instead of passing the old format on. */
    @Test(expected = IllegalStateException::class)
    fun anUnreadableEntry_failsTheImport() {
        JsonTransformers.transformToolData("not json", "messages", 12, current)
    }

    /** The v31 → v32 rewrite of the AI limits keeps to its category: applied to another, it would empty it. */
    @Test
    fun theAILimitsRewrite_keepsToItsCategory() {
        val limits = """{ "chat_max_action_retries": 3, "chat_max_autonomous_roundtrips": 10, "automation_max_autonomous_roundtrips": 20 }"""
        val format = """{ "week_start_day": "sunday", "timezone_override": "Europe/Paris" }"""

        val migratedLimits = JSONObject(JsonTransformers.transformAppConfig(limits, AppSettingCategories.AI_LIMITS, 31, 32))
        val migratedFormat = JSONObject(JsonTransformers.transformAppConfig(format, AppSettingCategories.FORMAT, 31, 32))

        assertFalse(migratedLimits.has("chat_max_action_retries"))
        assertEquals(10, migratedLimits.getInt("chat_max_autonomous_roundtrips"))
        assertEquals("sunday", migratedFormat.getString("week_start_day"))
        assertEquals("Europe/Paris", migratedFormat.getString("timezone_override"))
    }

    /** The v10 schedule types, written with their full class name, come back under their short name. */
    @Test
    fun oldScheduleTypes_getTheirShortName() {
        val old = """{"pattern":{"type":"com.assistant.core.utils.SchedulePattern.WeeklySimple","days":[1]}}"""

        val fixed = JsonTransformers.fixSchedulePatternTypes(old)

        assertEquals("""{"pattern":{"type":"WeeklySimple","days":[1]}}""", fixed)
    }
}
