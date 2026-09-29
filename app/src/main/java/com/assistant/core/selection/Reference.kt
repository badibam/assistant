package com.assistant.core.selection

import org.json.JSONObject

/** What a reference designates, from the widest to the narrowest. */
enum class ReferenceKind {
    APP,
    ZONE,
    TOOL_INSTANCE,
    ENTRY
}

/**
 * A thing of the app, named by its kind and its id, never by its name: renaming the thing or
 * moving a tool to another zone leaves the reference valid, and whoever shows it reads the name
 * as it is now. A thing deleted since keeps its reference, and shows as deleted.
 *
 * Stored as `{"kind": "TOOL_INSTANCE", "id": "…"}`, the id absent for the app.
 *
 * @property id The thing's id; none for the app
 */
data class Reference(val kind: ReferenceKind, val id: String?) {

    init {
        require((kind == ReferenceKind.APP) == (id == null)) { "a reference to $kind ${if (id == null) "needs" else "takes no"} id" }
    }

    fun toJson(): JSONObject = JSONObject().put("kind", kind.name).apply { id?.let { put("id", it) } }

    companion object {
        fun fromJson(json: JSONObject) = Reference(
            ReferenceKind.valueOf(json.getString("kind")),
            json.optString("id").takeIf { it.isNotEmpty() }
        )
    }
}
