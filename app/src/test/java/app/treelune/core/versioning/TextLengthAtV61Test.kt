package app.treelune.core.versioning

import app.treelune.core.fields.FieldType
import app.treelune.core.fields.toFieldDefinitions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the v61 rewrite: the former text types become a TEXT with its length, and the tool reads again. */
class TextLengthAtV61Test {

    @Test
    fun theFormerTextTypesBecomeATextWithTheirLength() {
        val config = JSONObject("""{ "extra_fields": [
            { "name": "mood", "display_name": "Mood", "type": "TEXT_SHORT" },
            { "name": "dream", "display_name": "Dream", "type": "TEXT_LONG", "config": { "kept": true } },
            { "name": "story", "display_name": "Story", "type": "TEXT_UNLIMITED" },
            { "name": "tag", "display_name": "Tag", "type": "TEXT", "config": { "length": "MEDIUM" } } ] }""")

        val fields = TextLengthAtV61.config(config).getJSONArray("extra_fields")

        assertEquals("SHORT", fields.getJSONObject(0).getJSONObject("config").getString("length"))
        assertEquals("LONG", fields.getJSONObject(1).getJSONObject("config").getString("length"))
        assertEquals("other settings are kept", true, fields.getJSONObject(1).getJSONObject("config").getBoolean("kept"))
        assertEquals("UNLIMITED", fields.getJSONObject(2).getJSONObject("config").getString("length"))
        assertEquals("a current TEXT is left as it was", "MEDIUM", fields.getJSONObject(3).getJSONObject("config").getString("length"))
        for (i in 0 until fields.length()) {
            assertEquals("TEXT", fields.getJSONObject(i).getString("type"))
        }
    }

    @Test
    fun aRewrittenConfigsFieldsCanBeRead() {
        val config = JSONObject("""{ "extra_fields": [ { "name": "dream", "display_name": "Dream", "type": "TEXT_LONG" } ] }""")

        val fields = TextLengthAtV61.config(config).getJSONArray("extra_fields").toFieldDefinitions()

        assertEquals(FieldType.TEXT, fields.single().type)
    }
}
