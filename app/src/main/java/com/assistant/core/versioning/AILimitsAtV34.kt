package com.assistant.core.versioning

import org.json.JSONObject

/**
 * Brings stored ai_limits settings to their v34 form: the two data size thresholds join the two
 * roundtrip limits. Stored values are kept; a missing threshold gets the value decided at v34,
 * 15 000 characters for a CHAT and 100 000 for an AUTOMATION. The single place that says so,
 * shared by the database migration and the backup import.
 *
 * The values are written here as they were decided at v34, not read from AILimitsConfig, whose
 * defaults may change later without changing what this migration did.
 */
object AILimitsAtV34 {

    private const val KEY_CHAT_DATA = "chat_max_data_chars"
    private const val KEY_AUTOMATION_DATA = "automation_max_data_chars"

    /** @return the settings in their v34 form; [settings] is left untouched */
    fun rewrite(settings: JSONObject): JSONObject = JSONObject(settings.toString()).apply {
        if (!has(KEY_CHAT_DATA)) put(KEY_CHAT_DATA, 15_000)
        if (!has(KEY_AUTOMATION_DATA)) put(KEY_AUTOMATION_DATA, 100_000)
    }
}
