package com.assistant.core.ai.enrichments

import org.json.JSONArray
import org.json.JSONObject

/** What a pointer designates, from the widest to the narrowest. */
enum class PointerKind {
    APP,
    ZONE,
    TOOL,
    ENTRY
}

/**
 * A pointer's target: its kind and the id of the thing, the most precise one only. A tool's zone
 * and an entry's tool are found when the pointer is read, as are the names: renaming a thing or
 * moving a tool to another zone leaves the pointer valid.
 *
 * @property id The thing's id; none for the app
 */
data class PointerTarget(val kind: PointerKind, val id: String?) {
    fun toJson(): JSONObject = JSONObject().put("kind", kind.name).apply { id?.let { put("id", it) } }

    companion object {
        fun fromJson(json: JSONObject) = PointerTarget(
            PointerKind.valueOf(json.getString("kind")),
            json.optString("id").takeIf { it.isNotEmpty() }
        )
    }
}

/**
 * A POINTER enrichment as stored in a message: what it designates, and what of it is attached.
 *
 * Two choices that do not depend on each other. [filters] and [fields] narrow the entries the
 * pointer designates, whether they are attached or only mentioned: a mention of filtered entries
 * hands the AI the query without running it.
 *
 * @property config Whether the target's config goes with the message, its schema with it
 * @property entries Whether the designated entries go with the message, their schema with them
 * @property filters The filters of tool_data.get, a period being one on timestamp. A value is in
 *   its stored form, or "NOW" or a relative period ("-7_DAY") that is resolved at each send: an
 *   automation's pointer rereads its period at every run
 * @property fields The fields of the entries to attach, all of them when null
 */
data class PointerConfig(
    val target: PointerTarget,
    val config: Boolean = false,
    val entries: Boolean = false,
    val filters: JSONArray = JSONArray(),
    val fields: List<String>? = null
) {
    /** Neither config nor entries: the AI is told of the target and reads what it wants. */
    val isMention: Boolean get() = !config && !entries

    fun toJson(): JSONObject = JSONObject().apply {
        put("target", target.toJson())
        put("attach", JSONObject().put("config", config).put("entries", entries))
        if (filters.length() > 0) put("filters", filters)
        fields?.let { put("fields", JSONArray(it)) }
    }

    companion object {
        fun fromJson(json: JSONObject): PointerConfig {
            val attach = json.optJSONObject("attach") ?: JSONObject()
            return PointerConfig(
                target = PointerTarget.fromJson(json.getJSONObject("target")),
                config = attach.optBoolean("config", false),
                entries = attach.optBoolean("entries", false),
                filters = json.optJSONArray("filters") ?: JSONArray(),
                fields = json.optJSONArray("fields")?.let { array -> (0 until array.length()).map { array.getString(it) } }
            )
        }

        fun fromJson(json: String): PointerConfig = fromJson(JSONObject(json))
    }
}
