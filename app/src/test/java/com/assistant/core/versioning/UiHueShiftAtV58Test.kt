package com.assistant.core.versioning

import com.assistant.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A theme's palette family of v57 becomes the hue shift that turns the theme's colours nearest to it. */
class UiHueShiftAtV58Test {

    private fun migrated(theme: String, family: String) = UiHueShiftAtV58.settings(AppSettingCategories.UI,
        JSONObject().put("theme", theme).put("${theme}_palette", family).put("mode", "DARK").put("size_step", 1))

    @Test
    fun `each family becomes its shift, the family gone`() {
        for ((theme, family, shift) in listOf(Triple("default", "blue", 0), Triple("retro", "cream", 0), Triple("retro", "prune", 273))) {
            val out = migrated(theme, family)
            assertEquals(shift, out.getInt("hue_shift"))
            assertFalse(out.has("${theme}_palette"))
            assertEquals("DARK", out.getString("mode"))
            assertEquals(1, out.getInt("size_step"))
        }
    }

    @Test
    fun `another category, or settings already migrated, are left as they are`() {
        val format = JSONObject().put("timezone_override", "Europe/Paris")
        assertEquals(format.toString(), UiHueShiftAtV58.settings(AppSettingCategories.FORMAT, format).toString())
        val done = JSONObject().put("theme", "retro").put("mode", "SYSTEM").put("hue_shift", 40)
        assertEquals(done.toString(), UiHueShiftAtV58.settings(AppSettingCategories.UI, done).toString())
    }
}
