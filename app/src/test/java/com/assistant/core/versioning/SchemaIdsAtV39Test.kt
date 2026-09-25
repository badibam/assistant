package com.assistant.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Covers the v39 rewrite: a tool config loses its schema ids, and keeps everything else. */
class SchemaIdsAtV39Test {

    @Test
    fun theSchemaIdsGoAndTheRestStays() {
        val config = SchemaIdsAtV39.config(JSONObject(
            """{ "name": "Weight", "schema_id": "tracking_config_numeric", "data_schema_id": "tracking_data_numeric", "type": "numeric" }"""
        ))

        assertFalse(config.has("schema_id"))
        assertFalse(config.has("data_schema_id"))
        assertEquals("Weight", config.getString("name"))
        assertEquals("numeric", config.getString("type"))
    }
}
