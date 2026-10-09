package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.secrets.SecretBox
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The secrets at v72: an API key and the relay's secret stored in clear come out sealed, in the database as in a backup. */
class SecretsAtV72Test {

    private val box = object : SecretBox {
        override fun seal(plain: String) = SecretBox.PREFIX + plain.reversed()
        override fun open(stored: String) = stored.removePrefix(SecretBox.PREFIX).reversed()
    }

    @Test
    fun aBackupInClear_comesOutSealed() {
        val data = JSONObject()
            .put("ai_provider_configs", JSONArray().put(JSONObject()
                .put("provider_id", "claude")
                .put("config_json", JSONObject().put("api_key", "sk-123").put("model", "m").toString())))
            .put("app_settings_categories", JSONArray()
                .put(JSONObject().put("category", AppSettingCategories.EXTERNAL_ACCESS)
                    .put("settings", JSONObject().put(AppSettings.RELAY_SECRET, "abc").put(AppSettings.RELAY_URL, "https://r.org").toString()))
                .put(JSONObject().put("category", AppSettingCategories.UI)
                    .put("settings", JSONObject().put(AppSettings.RELAY_SECRET, "not a secret here").toString())))

        SecretsAtV72.backup(data, box)

        val config = JSONObject(data.getJSONArray("ai_provider_configs").getJSONObject(0).getString("config_json"))
        assertEquals(SecretBox.PREFIX + "321-ks", config.getString("api_key"))
        assertEquals("m", config.getString("model"))
        val access = JSONObject(data.getJSONArray("app_settings_categories").getJSONObject(0).getString("settings"))
        assertEquals(SecretBox.PREFIX + "cba", access.getString(AppSettings.RELAY_SECRET))
        assertEquals("https://r.org", access.getString(AppSettings.RELAY_URL))
        // Only the external access holds the relay's secret
        val ui = JSONObject(data.getJSONArray("app_settings_categories").getJSONObject(1).getString("settings"))
        assertEquals("not a secret here", ui.getString(AppSettings.RELAY_SECRET))
        assertFalse(data.toString().contains("sk-123"))
    }
}
