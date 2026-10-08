package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The interface settings at v67: an install or a backup from before shows its grids as it did. */
class UiOneColumnAtV67Test {

    @Test
    fun theInterfaceSettings_gainOneColumn_off() {
        val v66 = JSONObject().put(AppSettings.UI_THEME, "retro").put(AppSettings.UI_SIZE_STEP, 1)
        val rewritten = UiOneColumnAtV67.rewrite(AppSettingCategories.UI, v66)
        assertFalse(rewritten.getBoolean(AppSettings.UI_ONE_COLUMN))
        assertEquals("retro", rewritten.getString(AppSettings.UI_THEME))
    }

    @Test
    fun aSettingAlreadyThere_andOtherCategories_stayAsTheyAre() {
        val set = JSONObject().put(AppSettings.UI_ONE_COLUMN, true)
        assertEquals(true, UiOneColumnAtV67.rewrite(AppSettingCategories.UI, set).getBoolean(AppSettings.UI_ONE_COLUMN))
        val format = JSONObject().put("x", 1)
        assertFalse(UiOneColumnAtV67.rewrite(AppSettingCategories.FORMAT, format).has(AppSettings.UI_ONE_COLUMN))
    }
}
