package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings stored ai_limits settings to their v64 form: the threshold of the tools sent always
 * joins them (docs/design/always-send.md). A stored value is kept; a missing one gets the value
 * decided at v64, 15 000 characters. The single place that says so, shared by the database
 * migration and the backup import.
 *
 * Written here as decided at v64, not read from AILimitsConfig, whose default may change later
 * without changing what this migration did.
 */
object AILimitsAtV64 {

    private const val KEY_ALWAYS_SEND = "always_send_max_chars"

    /** @return the settings in their v64 form; [settings] is left untouched */
    fun rewrite(settings: JSONObject): JSONObject = JSONObject(settings.toString()).apply {
        if (!has(KEY_ALWAYS_SEND)) put(KEY_ALWAYS_SEND, 15_000)
    }
}
