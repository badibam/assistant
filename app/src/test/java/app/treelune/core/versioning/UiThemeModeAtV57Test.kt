package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The appearance of v56, one palette's id, becomes the theme, its family and the mode. */
class UiThemeModeAtV57Test {

    private fun migrated(appearance: String) = UiThemeModeAtV57.settings(AppSettingCategories.UI,
        JSONObject().put(UiAppearanceAtV55.KEY, appearance).put("size_step", 1).put("sounds", true))

    @Test
    fun `each former palette becomes its theme, family and mode`() {
        for ((former, expected) in mapOf(
            "default_dark" to listOf("default", "blue", "DARK"),
            "default_light" to listOf("default", "blue", "LIGHT"),
            "retro_dark" to listOf("retro", "prune", "DARK"),
            "retro_light" to listOf("retro", "prune", "LIGHT"),
            "retro_cream" to listOf("retro", "cream", "LIGHT")
        )) {
            val out = migrated(former)
            val (theme, family, mode) = expected
            assertEquals(theme, out.getString("theme"))
            assertEquals(family, out.getString("${theme}_palette"))
            assertEquals(mode, out.getString("mode"))
            assertFalse(out.has(UiAppearanceAtV55.KEY))
            assertEquals(1, out.getInt("size_step"))
        }
    }

    @Test
    fun `another category, or settings already migrated, are left as they are`() {
        val format = JSONObject().put("timezone_override", "Europe/Paris")
        assertEquals(format.toString(), UiThemeModeAtV57.settings(AppSettingCategories.FORMAT, format).toString())
        val done = JSONObject().put("theme", "retro").put("retro_palette", "cream").put("mode", "SYSTEM")
        assertEquals(done.toString(), UiThemeModeAtV57.settings(AppSettingCategories.UI, done).toString())
    }
}
