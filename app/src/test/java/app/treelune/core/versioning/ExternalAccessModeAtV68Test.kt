package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The external access settings at v68: a relay in use stays in use, anyone else goes through Tailscale. */
class ExternalAccessModeAtV68Test {

    private fun rewrite(settings: JSONObject) = ExternalAccessModeAtV68.rewrite(AppSettingCategories.EXTERNAL_ACCESS, settings)

    @Test
    fun aRelaySet_keepsTheRelay() {
        val v67 = JSONObject().put(AppSettings.RELAY_URL, "https://relay.example.org").put(AppSettings.RELAY_SECRET, "abc")
        val rewritten = rewrite(v67)
        assertEquals(AppSettings.ACCESS_MODE_RELAY, rewritten.getString(AppSettings.ACCESS_MODE))
        assertEquals("https://relay.example.org", rewritten.getString(AppSettings.RELAY_URL))
    }

    @Test
    fun noRelay_orHalfARelay_goesThroughTailscale() {
        assertEquals(AppSettings.ACCESS_MODE_TAILSCALE, rewrite(JSONObject()).getString(AppSettings.ACCESS_MODE))
        val addressOnly = JSONObject().put(AppSettings.RELAY_URL, "https://relay.example.org").put(AppSettings.RELAY_SECRET, " ")
        assertEquals(AppSettings.ACCESS_MODE_TAILSCALE, rewrite(addressOnly).getString(AppSettings.ACCESS_MODE))
    }

    @Test
    fun aModeAlreadyThere_andOtherCategories_stayAsTheyAre() {
        val set = JSONObject().put(AppSettings.ACCESS_MODE, AppSettings.ACCESS_MODE_RELAY)
        assertEquals(AppSettings.ACCESS_MODE_RELAY, rewrite(set).getString(AppSettings.ACCESS_MODE))
        assertFalse(ExternalAccessModeAtV68.rewrite(AppSettingCategories.UI, JSONObject()).has(AppSettings.ACCESS_MODE))
    }
}
