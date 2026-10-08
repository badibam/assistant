package app.treelune.core.ui.selectors

import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.json.JSONObject

/** A zone, a tool, a variable or an entry the browser went through: its id, its name as shown, a tool's type. */
data class Named(val id: String, val name: String, val tooltype: String? = null) {

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).apply { tooltype?.let { put("tooltype", it) } }

    companion object {
        fun fromJson(json: JSONObject) = Named(json.getString("id"), json.getString("name"), json.optString("tooltype").takeIf { it.isNotEmpty() })
    }
}

/**
 * Where the input of a thing stands (ThingBrowser): the app, a zone, a tool or a variable in it, an
 * entry of that tool. The thing designated is the deepest one reached.
 */
data class ThingPath(val zone: Named? = null, val tool: Named? = null, val entry: Named? = null, val variable: Named? = null) {

    init {
        require(tool == null || zone != null) { "a tool is reached through its zone" }
        require(entry == null || tool != null) { "an entry is reached through its tool" }
        require(variable == null || (zone != null && tool == null)) { "a variable is reached through its zone, beside its tools" }
    }

    /** The kind of the deepest thing reached. */
    val kind: ReferenceKind get() = when {
        entry != null -> ReferenceKind.ENTRY
        variable != null -> ReferenceKind.VARIABLE
        tool != null -> ReferenceKind.TOOL_INSTANCE
        zone != null -> ReferenceKind.ZONE
        else -> ReferenceKind.APP
    }

    /** The deepest thing reached, as a reference stores it. */
    val reference: Reference get() = when (kind) {
        ReferenceKind.APP -> Reference(ReferenceKind.APP, null)
        ReferenceKind.ZONE -> Reference(ReferenceKind.ZONE, zone!!.id)
        ReferenceKind.TOOL_INSTANCE -> Reference(ReferenceKind.TOOL_INSTANCE, tool!!.id)
        ReferenceKind.ENTRY -> Reference(ReferenceKind.ENTRY, entry!!.id)
        ReferenceKind.VARIABLE -> Reference(ReferenceKind.VARIABLE, variable!!.id)
    }

    /** Back up to [kind]: what lies below it is left. */
    fun upTo(kind: ReferenceKind): ThingPath = when (kind) {
        ReferenceKind.APP -> ThingPath()
        ReferenceKind.ZONE -> ThingPath(zone)
        ReferenceKind.TOOL_INSTANCE -> ThingPath(zone, tool)
        ReferenceKind.ENTRY, ReferenceKind.VARIABLE -> this
    }

    fun toJson(): String = JSONObject().apply {
        zone?.let { put("zone", it.toJson()) }
        tool?.let { put("tool", it.toJson()) }
        entry?.let { put("entry", it.toJson()) }
        variable?.let { put("variable", it.toJson()) }
    }.toString()

    companion object {
        fun fromJson(saved: String): ThingPath {
            val json = JSONObject(saved)
            fun named(key: String) = json.optJSONObject(key)?.let { Named.fromJson(it) }
            return ThingPath(named("zone"), named("tool"), named("entry"), named("variable"))
        }
    }
}
