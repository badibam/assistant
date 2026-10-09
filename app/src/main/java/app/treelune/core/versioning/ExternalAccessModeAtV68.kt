package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the external access settings to their v68 form: they gain the access mode
 * (docs/design/funnel-access.md). A relay set, address and secret, keeps the relay; otherwise the
 * app's own Tailscale node, as for a new install.
 *
 * Shared by the database migration and the backup import (JsonTransformers.transformAppConfig).
 */
object ExternalAccessModeAtV68 {

    /** [settings] of [category], with the access mode. */
    fun rewrite(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.EXTERNAL_ACCESS) return settings
        val out = JSONObject(settings.toString())
        if (out.has(AppSettings.ACCESS_MODE)) return out
        val relaySet = out.optString(AppSettings.RELAY_URL).isNotBlank() && out.optString(AppSettings.RELAY_SECRET).isNotBlank()
        out.put(AppSettings.ACCESS_MODE, if (relaySet) AppSettings.ACCESS_MODE_RELAY else AppSettings.ACCESS_MODE_TAILSCALE)
        return out
    }
}
