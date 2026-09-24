package com.assistant.core.validation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers the rule the "system_managed" mark states: inside an entry's data, a marked field is
 * the app's to produce, so what a caller sends under that name is dropped before the write.
 */
class SystemManagedFieldsTest {

    /** Shaped like a tracking data schema: raw marked, the value fields not, a root field marked too. */
    private val schema = """
        {
            "properties": {
                "tooltype": { "type": "string", "system_managed": true },
                "data": {
                    "type": "object",
                    "properties": {
                        "quantity": { "type": "number" },
                        "unit": { "type": "string", "system_managed": false },
                        "raw": { "type": "string", "system_managed": true }
                    }
                }
            }
        }
    """

    @Test
    fun theMarkedDataFields_areFound() {
        assertEquals(setOf("raw"), SystemManagedFields.inData(schema))
    }

    /** A raw sent by a caller is dropped; the value fields stay, and the input is left untouched. */
    @Test
    fun aMarkedFieldSent_isDropped() {
        val sent = JSONObject("""{ "quantity": 16, "unit": "cm", "raw": "a thousand" }""")

        val kept = SystemManagedFields.dropFromData(sent, schema)

        assertFalse(kept.has("raw"))
        assertEquals(16, kept.getInt("quantity"))
        assertEquals("cm", kept.getString("unit"))
        assertEquals("a thousand", sent.getString("raw"))
    }

    /** A root mark is not a data field: the service derives root fields itself. */
    @Test
    fun rootMarks_areNotDataFields() {
        assertFalse("tooltype" in SystemManagedFields.inData(schema))
    }

    @Test
    fun aSchemaWithoutDataProperties_marksNothing() {
        assertEquals(emptySet<String>(), SystemManagedFields.inData("""{ "properties": { "name": { "type": "string" } } }"""))
    }
}
