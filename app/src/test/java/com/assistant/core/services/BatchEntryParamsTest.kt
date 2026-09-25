package com.assistant.core.services

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers what an entry of a batch keeps on its way to the single create or update.
 *
 * A key this copy leaves out is lost without an error, and the batch still reports success.
 * A replay of the L1 prompt on the device created three entries with custom fields, was told
 * all three were created, and the database held none of their custom fields.
 */
class BatchEntryParamsTest {

    private val entry = JSONObject(
        """
        {
          "name": "Weighing",
          "timestamp": 1790200000000,
          "data": { "quantity": 81.5 },
          "extra": { "mood": "calm", "weighed_at": 1790200120000 }
        }
        """
    )

    /** A created entry keeps everything it names, custom fields included. */
    @Test
    fun aCreatedEntryKeepsItsCustomFields() {
        val params = BatchEntryParams.forCreate(entry, "tool-1")

        assertEquals("calm", params.getJSONObject("extra").getString("mood"))
        assertEquals(1790200120000L, params.getJSONObject("extra").getLong("weighed_at"))
        assertEquals(81.5, params.getJSONObject("data").getDouble("quantity"), 0.0)
        assertEquals("Weighing", params.getString("name"))
        assertEquals(1790200000000L, params.getLong("timestamp"))
    }

    /**
     * The tool instance comes from the command, named once for every entry. A tooltype or a
     * schema_id an entry carries is not passed on: the service reads both from the tool.
     */
    @Test
    fun aCreatedEntryTakesItsToolFromTheCommand() {
        val carrying = JSONObject(entry.toString()).put("tooltype", "notes").put("schema_id", "notes_data")
        val params = BatchEntryParams.forCreate(carrying, "tool-1")

        assertEquals("tool-1", params.getString("tool_instance_id"))
        assertFalse(params.has("tooltype"))
        assertFalse(params.has("schema_id"))
    }

    /** An updated entry keeps its custom fields too, and changes nothing it does not name. */
    @Test
    fun anUpdatedEntryKeepsItsCustomFieldsAndNothingElse() {
        val params = BatchEntryParams.forUpdate(JSONObject("""{ "extra": { "mood": "tired" } }"""), "entry-1")

        assertEquals("entry-1", params.getString("id"))
        assertEquals("tired", params.getJSONObject("extra").getString("mood"))
        assertFalse(params.has("data"))
        assertFalse(params.has("name"))
        assertFalse(params.has("timestamp"))
    }
}
