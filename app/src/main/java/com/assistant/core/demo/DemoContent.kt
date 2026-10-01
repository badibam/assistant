package com.assistant.core.demo

import com.assistant.core.database.entities.Zone
import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo as the app ships it (docs/design/demo.md): `assets/demo/structure.json` says what is
 * built, with symbolic ids and text keys and no text of its own; `assets/demo/texts-<lang>.json`
 * gives each key its text, in the phone's language. Pure: the service reads the files and hands
 * them here, and what comes back is written as it is.
 *
 * @property group The name of the home screen's zone group the demo's zones stand in
 * @property zones The demo's zones, each with its id prefixed by [PREFIX] and its place in the group
 */
class DemoContent(val group: String, val zones: List<Zone>) {

    companion object {

        /** What every id the demo writes starts with, and how a reinstall finds what to remove. */
        const val PREFIX = "demo-"

        /**
         * The demo read from [structure] and [texts], dated [now].
         *
         * @throws IllegalStateException when an id lacks the prefix or a text key has no text:
         *         a demo half named is not installed
         */
        fun read(structure: JSONObject, texts: JSONObject, now: Long): DemoContent {
            fun text(key: String): String =
                texts.optString(key).takeIf { it.isNotEmpty() } ?: error("Demo text '$key' missing")
            val group = text(structure.getString("zone_group"))
            val zones = structure.getJSONArray("zones").objects().map { zone ->
                val id = zone.getString("id")
                check(id.startsWith(PREFIX)) { "Demo zone id '$id' lacks the prefix $PREFIX" }
                Zone(
                    id = id,
                    name = text(zone.getString("name")),
                    description = text(zone.getString("description")),
                    icon_name = zone.getString("icon"),
                    display_mode = zone.getString("display_mode"),
                    grid_x = zone.getInt("grid_x"),
                    grid_y = zone.getInt("grid_y"),
                    created_at = now,
                    updated_at = now,
                    tool_groups = JSONArray(zone.getJSONArray("tool_groups").strings().map(::text)).toString(),
                    group = group
                )
            }
            return DemoContent(group, zones)
        }

        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    }
}
