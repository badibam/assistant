package app.treelune.core.versioning

import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the groups to their v62 form, where a group held is null or one that exists
 * (Groups):
 * - a zone's group is one of the home screen's groups ("zone_groups" of the main_screen settings);
 * - the group of a tool (its config's "group"), an automation or a variable is one of its zone's
 *   tool groups.
 *
 * Any other is emptied. Nothing moves on screen: a group that does not exist was already shown
 * among the ungrouped, and the grid places were taken there. Shared by the database migration
 * and the backup import.
 */
object GroupsAtV62 {

    /** [group] if it is one of [groups], null otherwise. */
    fun kept(group: String?, groups: List<String>): String? = group?.takeIf { it in groups }

    /** The home screen's groups in the main_screen settings stored as [json]; none when absent. */
    fun zoneGroups(json: String?): List<String> =
        strings(json?.let { JSONObject(it).optJSONArray("zone_groups") })

    /** A zone's tool groups, stored as [json]; none when absent. */
    fun toolGroups(json: String?): List<String> =
        strings(json?.takeIf { it.isNotBlank() }?.let { JSONArray(it) })

    /** [config] of a tool whose zone has [groups], without a group that is not one of them. */
    fun toolConfig(config: JSONObject, groups: List<String>): JSONObject {
        val next = JSONObject(config.toString())
        if (next.has("group") && kept(text(next, "group"), groups) == null) next.remove("group")
        return next
    }

    /** Rewrites the backup document's zones, tools, automations and variables in place. */
    fun backup(data: JSONObject) {
        val settings = rows(data, "app_settings_categories")
        val homeGroups = zoneGroups(settings.firstOrNull { it.optString("category") == AppSettingCategories.MAIN_SCREEN }?.optString("settings"))

        val zoneToolGroups = mutableMapOf<String, List<String>>()
        for (zone in rows(data, "zones")) {
            keep(zone, homeGroups)
            zoneToolGroups[zone.getString("id")] = toolGroups(text(zone, "tool_groups"))
        }
        fun groupsOf(row: JSONObject) = zoneToolGroups[row.getString("zone_id")].orEmpty()

        for (tool in rows(data, "tool_instances")) {
            tool.put("config_json", toolConfig(JSONObject(tool.getString("config_json")), groupsOf(tool)).toString())
        }
        for (key in listOf("automations", "variables")) {
            for (row in rows(data, key)) keep(row, groupsOf(row))
        }
    }

    /**
     * [row]'s group kept if it is one of [groups], its key removed otherwise: the import reads a
     * JSON null with optString, which would hand back the text "null".
     */
    private fun keep(row: JSONObject, groups: List<String>) {
        val group = kept(text(row, "group"), groups)
        if (group == null) row.remove("group") else row.put("group", group)
    }

    /** The text at [key], null when it is absent, JSON null or empty. */
    private fun text(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotEmpty() }

    private fun strings(array: JSONArray?): List<String> =
        if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }

    private fun rows(data: JSONObject, key: String): List<JSONObject> =
        data.optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
}
