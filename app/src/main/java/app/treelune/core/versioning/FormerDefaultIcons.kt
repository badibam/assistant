package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Renames the two default icons the app gave its tools under names Lucide never had.
 *
 * Notes defaulted to "note" and messages to "notification". Neither is a Lucide icon, so both
 * showed as two letters, and since icon names are checked when stored, a config holding one
 * would be refused the day its icon changed. They become the Lucide icons the tooltypes now
 * default to. This is the single place that says so, shared by the database migration and the
 * backup import, which otherwise drift apart.
 *
 * It is history, not a rule: a name Lucide renamed is found through the icon index's former
 * names at every read, and needs nothing here.
 */
object FormerDefaultIcons {

    private val RENAMES = mapOf(
        "note" to "sticky-note",
        "notification" to "bell"
    )

    /**
     * Rename a tool config's icon if it is one of the former defaults.
     *
     * @return true when the config, modified in place, was renamed
     */
    fun rename(config: JSONObject): Boolean {
        val current = RENAMES[config.optString("icon_name")] ?: return false
        config.put("icon_name", current)
        return true
    }
}
