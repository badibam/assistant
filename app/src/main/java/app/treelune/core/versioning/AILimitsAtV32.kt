package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings stored ai_limits settings to their v32 form: the two roundtrip limits, nothing else.
 *
 * Before v32 a fresh install wrote a CHAT limit of 10 while the code read a missing key as no
 * limit, and the settings carried keys nothing read (token estimates, per-kind retry counts).
 * From v32 a CHAT is limited to 10 calls from the last user intervention, and both limits are
 * required when read. Every stored CHAT value is therefore set to 10 -- no screen could set it,
 * so what was there was a default, not a choice -- and the automation limit is kept, or set to
 * 20 when absent. This is the single place that says so, shared by the database migration and
 * the backup import.
 *
 * The values are written here as they were decided at v32, not read from AILimitsConfig, whose
 * defaults may change later without changing what this migration did.
 */
object AILimitsAtV32 {

    private const val KEY_CHAT = "chat_max_autonomous_roundtrips"
    private const val KEY_AUTOMATION = "automation_max_autonomous_roundtrips"

    /** @return the settings in their v32 form; [settings] is left untouched */
    fun rewrite(settings: JSONObject): JSONObject = JSONObject().apply {
        put(KEY_CHAT, 10)
        put(KEY_AUTOMATION, if (settings.has(KEY_AUTOMATION)) settings.getInt(KEY_AUTOMATION) else 20)
    }
}
