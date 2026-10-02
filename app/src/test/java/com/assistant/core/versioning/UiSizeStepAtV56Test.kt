package com.assistant.core.versioning

import com.assistant.core.config.AppSettings
import com.assistant.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every size step moves up by one, so that each keeps the size it gave: the former −1 is the new 0. */
class UiSizeStepAtV56Test {

    private fun step(old: Int) =
        UiSizeStepAtV56.settings(AppSettingCategories.UI, JSONObject().put(AppSettings.UI_SIZE_STEP, old)).getInt(AppSettings.UI_SIZE_STEP)

    @Test
    fun `each former step lands one up, inside the new range`() {
        for (old in -1..2) {
            assertEquals(old + 1, step(old))
            assert(step(old) in AppSettings.SIZE_STEP_RANGE)
        }
    }

    @Test
    fun `another category, or interface settings without a step, are left as they are`() {
        val format = JSONObject().put("timezone_override", "Europe/Paris")
        assertEquals(format.toString(), UiSizeStepAtV56.settings(AppSettingCategories.FORMAT, format).toString())
        val ui = JSONObject().put(UiAppearanceAtV55.KEY, "retro_light")
        assertEquals(ui.toString(), UiSizeStepAtV56.settings(AppSettingCategories.UI, ui).toString())
    }
}
