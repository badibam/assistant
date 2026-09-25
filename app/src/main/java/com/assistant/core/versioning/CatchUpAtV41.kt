package com.assistant.core.versioning

import com.assistant.core.ai.data.AutomationSettings
import com.assistant.core.ai.data.LegacyCatchUp
import org.json.JSONObject

/**
 * Brings an automation's catch-up settings to their v41 form (docs/design/config-fields.md,
 * decisions 14 and 15): the explicit choice "limited" or "unlimited" is stored, and a limited
 * window is a DURATION in milliseconds instead of a number of minutes. A scheduled automation
 * with no window had chosen "unlimited", since the screen refused to save it otherwise; an
 * automation without schedule has no catch-up settings at all.
 *
 * Shared by the database migration and the backup import.
 */
object CatchUpAtV41 {

    private const val MILLIS_PER_MINUTE = 60_000L

    /** The catch-up choice and window of an automation, scheduled or not, whose window was [windowMinutes]. */
    fun of(scheduled: Boolean, windowMinutes: Long?): Pair<String?, Long?> = when {
        !scheduled -> null to null
        windowMinutes == null -> AutomationSettings.UNLIMITED to null
        else -> AutomationSettings.LIMITED to windowMinutes * MILLIS_PER_MINUTE
    }

    /**
     * Rewrites the backup document's automations in place. A backup written before the catch-up
     * settings existed carries no window, and is read by LegacyCatchUp, as the v23 migration did.
     */
    fun backup(data: JSONObject) {
        val automations = data.optJSONArray("automations") ?: return
        for (i in 0 until automations.length()) {
            val automation = automations.getJSONObject(i)
            val hasWindow = automation.has("catch_up_window_minutes") && !automation.isNull("catch_up_window_minutes")
            val scheduled = !automation.isNull("schedule_json") && automation.optString("schedule_json").isNotEmpty()
            val (choice, window) = of(scheduled, if (hasWindow) automation.getLong("catch_up_window_minutes") else LegacyCatchUp.WINDOW_MINUTES)
            if (!hasWindow) automation.put("dismiss_older_instances", LegacyCatchUp.DISMISS_OLDER)
            automation.remove("catch_up_window_minutes")
            choice?.let { automation.put("catch_up", it) }
            window?.let { automation.put("catch_up_window", it) }
        }
    }
}
