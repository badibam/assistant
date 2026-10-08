package app.treelune.core.versioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * Covers the removal of the min/max bounds a DATE or DATETIME custom field used to carry.
 *
 * This runs once, on real configs, with no way back: the same code migrates the installed
 * database and an imported backup. What it must not do is touch a bound that still means
 * something, which is every other field type.
 */
class DateFieldBoundsTest {

    private fun config(json: String): JSONObject = JSONObject(json)

    /** The bounds of a DATETIME field go, since nothing ever applied them. */
    @Test
    fun aDateTimeFieldLosesItsBounds() {
        val c = config("""
            {"custom_fields": [{"name": "bedtime", "type": "DATETIME",
                "config": {"min": "2020-01-01T00:00:00", "max": "2030-01-01T00:00:00"}}]}
        """.trimIndent())

        assertEquals(2, DateFieldBounds.strip(c))
        assertFalse(c.getJSONArray("custom_fields").getJSONObject(0).has("config"))
    }

    /** A DATE field the same way. */
    @Test
    fun aDateFieldLosesItsBounds() {
        val c = config("""
            {"custom_fields": [{"name": "birthday", "type": "DATE",
                "config": {"min": "1900-01-01", "max": "2100-01-01"}}]}
        """.trimIndent())

        assertEquals(2, DateFieldBounds.strip(c))
        assertFalse(c.getJSONArray("custom_fields").getJSONObject(0).has("config"))
    }

    /** What the config still says survives: only the two bounds are named. */
    @Test
    fun theTimeFormatSurvives() {
        val c = config("""
            {"custom_fields": [{"name": "bedtime", "type": "DATETIME",
                "config": {"min": "2020-01-01T00:00:00", "time_format": "12h"}}]}
        """.trimIndent())

        assertEquals(1, DateFieldBounds.strip(c))
        val fieldConfig = c.getJSONArray("custom_fields").getJSONObject(0).getJSONObject("config")
        assertEquals("12h", fieldConfig.getString("time_format"))
        assertFalse(fieldConfig.has("min"))
    }

    /** A numeric field's bounds still constrain its values, and are left exactly as they were. */
    @Test
    fun aNumberFieldKeepsItsBounds() {
        val c = config("""
            {"custom_fields": [{"name": "weight", "type": "NUMBER",
                "config": {"min": 0, "max": 300}}]}
        """.trimIndent())

        assertEquals(0, DateFieldBounds.strip(c))
        val fieldConfig = c.getJSONArray("custom_fields").getJSONObject(0).getJSONObject("config")
        assertEquals(0, fieldConfig.getInt("min"))
        assertEquals(300, fieldConfig.getInt("max"))
    }

    /** A date field that never had a bound is left alone, config included. */
    @Test
    fun aDateFieldWithoutBoundsIsUntouched() {
        val c = config("""
            {"custom_fields": [{"name": "bedtime", "type": "DATETIME", "config": {"time_format": "24h"}}]}
        """.trimIndent())

        assertEquals(0, DateFieldBounds.strip(c))
        assertTrue(c.getJSONArray("custom_fields").getJSONObject(0).has("config"))
    }

    /** Most configs have no custom fields at all, and must come out of this untouched. */
    @Test
    fun aConfigWithoutCustomFieldsIsUntouched() {
        val source = """{"name": "Poids", "unit": "kg"}"""
        val c = config(source)

        assertEquals(0, DateFieldBounds.strip(c))
        assertEquals(JSONObject(source).toString(), c.toString())
    }

    /** Several fields in one config are all seen, and the count says how many bounds went. */
    @Test
    fun everyFieldInTheConfigIsSeen() {
        val c = config("""
            {"custom_fields": [
                {"name": "birthday", "type": "DATE", "config": {"min": "1900-01-01"}},
                {"name": "weight", "type": "NUMBER", "config": {"min": 0, "max": 300}},
                {"name": "bedtime", "type": "DATETIME", "config": {"max": "2030-01-01T00:00:00"}}
            ]}
        """.trimIndent())

        assertEquals(2, DateFieldBounds.strip(c))
        val fields = c.getJSONArray("custom_fields")
        assertFalse("birthday", fields.getJSONObject(0).has("config"))
        assertTrue("weight", fields.getJSONObject(1).has("config"))
        assertFalse("bedtime", fields.getJSONObject(2).has("config"))
    }
}
