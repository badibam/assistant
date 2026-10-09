package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.secrets.SecretBox
import app.treelune.core.secrets.SecretSettings
import org.json.JSONObject

/**
 * Brings the secret settings to their v72 form: sealed (SecretSettings), where they were stored in
 * clear. The secrets of v72: every AI provider's API key, the relay's secret.
 *
 * Shared by the database migration and the backup import, which seals a backup's keys with this
 * phone's.
 */
object SecretsAtV72 {

    /** The config of an AI provider, its API key sealed. */
    fun providerConfig(config: JSONObject, box: SecretBox): JSONObject =
        SecretSettings.seal(listOf("api_key"), config, box)

    /** [settings] of [category], the relay's secret sealed. */
    fun appSettings(category: String, settings: JSONObject, box: SecretBox): JSONObject =
        if (category != AppSettingCategories.EXTERNAL_ACCESS) settings
        else SecretSettings.seal(listOf(AppSettings.RELAY_SECRET), settings, box)

    /** A backup's provider configs and settings, their secrets sealed. */
    fun backup(data: JSONObject, box: SecretBox) {
        data.optJSONArray("ai_provider_configs")?.let { configs ->
            for (i in 0 until configs.length()) {
                val config = configs.getJSONObject(i)
                config.put("config_json", providerConfig(JSONObject(config.getString("config_json")), box).toString())
            }
        }
        data.optJSONArray("app_settings_categories")?.let { categories ->
            for (i in 0 until categories.length()) {
                val category = categories.getJSONObject(i)
                category.put("settings", appSettings(category.getString("category"), JSONObject(category.getString("settings")), box).toString())
            }
        }
    }
}
