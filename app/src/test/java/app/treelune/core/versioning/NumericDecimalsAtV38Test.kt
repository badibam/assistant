package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the v38 rewrite: every stored NUMERIC says its decimals, 2 when it said nothing. */
class NumericDecimalsAtV38Test {

    @Test
    fun aUsersNumericFieldWithoutDecimalsTakesTwo() {
        val config = JSONObject("""{ "extra_fields": [
            { "name": "walk", "type": "NUMERIC", "config": { "unit": "km" } },
            { "name": "steps", "type": "NUMERIC" },
            { "name": "count", "type": "NUMERIC", "config": { "decimals": 0 } },
            { "name": "hours", "type": "RANGE", "config": { "unit": "h" } } ] }""")

        val fields = NumericDecimalsAtV38.config("journal", config).getJSONArray("extra_fields")

        assertEquals(2, fields.getJSONObject(0).getJSONObject("config").getInt("decimals"))
        assertEquals(2, fields.getJSONObject(1).getJSONObject("config").getInt("decimals"))
        assertEquals("a declared precision is kept", 0, fields.getJSONObject(2).getJSONObject("config").getInt("decimals"))
        assertEquals("a range too", 2, fields.getJSONObject(3).getJSONObject("config").getInt("decimals"))
    }

    @Test
    fun aNumericTrackingToolsValueTakesTwo() {
        val config = JSONObject("""{ "type": "numeric", "units": ["kg"] }""")

        assertEquals(2, NumericDecimalsAtV38.config("tracking", config).getJSONObject("value").getInt("decimals"))
    }
}
