package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the validation settings to their v69 form (docs/design/validation.md): three levels, each
 * guarding what it holds directly, where five switches of the app, two of each tool and one of
 * each session added up.
 *
 * - The app's validation_config keeps one key, validate_app, on when the app's settings or the
 *   zones were validated: what the app's level now guards.
 * - A zone is protected when one of its tools had its config validated: a tool's config is now
 *   guarded by its zone, and nothing that was protected stops being so.
 * - A tool loses validate_config, and management, which nothing read; validate_data stays.
 * - A session's require_validation becomes its three boxes, all on when it was.
 *
 * The app's former switches for the tools' data, their configs and the variables, which covered
 * the whole app, have no level left that covers the whole app: they go.
 *
 * Shared by the database migration and the backup import.
 */
object ValidationAtV69 {

    private const val TOOL_CONFIG = "validate_config"
    private const val MANAGEMENT = "management"

    /** [settings] of [category] at v69. */
    fun appSettings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.VALIDATION_CONFIG || settings.has("validate_app")) return settings
        val app = settings.optBoolean("validate_app_config_changes") || settings.optBoolean("validate_zone_config_changes")
        return JSONObject().put("validate_app", app)
    }

    /** Whether a tool's stored [config] asked for its config to be validated: its zone becomes protected. */
    fun protectsZone(config: JSONObject): Boolean = config.optBoolean(TOOL_CONFIG)

    /** A tool's stored [config] at v69. */
    fun toolConfig(config: JSONObject): JSONObject =
        JSONObject(config.toString()).apply { remove(TOOL_CONFIG); remove(MANAGEMENT) }

    /** Rewrites the backup document's zones, tools and sessions in place; its settings go through [appSettings]. */
    fun backup(data: JSONObject) {
        val protectedZones = mutableSetOf<String>()
        data.optJSONArray("tool_instances")?.let { tools ->
            for (i in 0 until tools.length()) {
                val tool = tools.getJSONObject(i)
                val config = JSONObject(tool.getString("config_json"))
                if (protectsZone(config)) protectedZones.add(tool.getString("zone_id"))
                tool.put("config_json", toolConfig(config).toString())
            }
        }
        data.optJSONArray("zones")?.let { zones ->
            for (i in 0 until zones.length()) {
                val zone = zones.getJSONObject(i)
                zone.put("validate", zone.getString("id") in protectedZones)
            }
        }
        data.optJSONArray("ai_sessions")?.let { sessions ->
            for (i in 0 until sessions.length()) {
                val session = sessions.getJSONObject(i)
                val on = session.optBoolean("require_validation")
                session.remove("require_validation")
                session.put("validate_app", on).put("validate_zones", on).put("validate_data", on)
            }
        }
    }
}
