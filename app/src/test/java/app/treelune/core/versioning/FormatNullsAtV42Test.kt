package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The format settings say "follow the phone" by an absence from v42; the other categories are untouched. */
class FormatNullsAtV42Test {

    @Test
    fun aNullOverrideBecomesAnAbsence() {
        val settings = FormatNullsAtV42.settings("format", JSONObject("""{"timezone_override":null,"locale_override":"fr-FR","day_start_hour":4}"""))

        assertFalse(settings.has("timezone_override"))
        assertEquals("fr-FR", settings.getString("locale_override"))
        assertEquals(4, settings.getInt("day_start_hour"))
    }

    @Test
    fun anotherCategoryIsLeftAsItIs() {
        val settings = JSONObject("""{"zone_groups":null}""")
        assertEquals(settings.toString(), FormatNullsAtV42.settings("main_screen", settings).toString())
    }
}
