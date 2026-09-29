package com.assistant.core.ui.selectors

import com.assistant.core.ai.enrichments.PointerConfig
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject

/** A zone or a tool the selector went through: its id, its name as shown, a tool's type. */
data class Named(val id: String, val name: String, val tooltype: String? = null)

/**
 * What the pointer selector holds while a pointer is built: where the user went (a zone, a tool
 * in it), what is attached, and what narrows the tool's entries.
 *
 * The target is the deepest place reached. A zone offers its config and the entries of its tools
 * over a period; a tool its config and its entries, with a period, value filters and a choice of
 * fields, which narrow the entries whether they are attached or only mentioned. Value filters and
 * fields are a tool's own: they have no sense across the tools of a zone.
 *
 * @property filters The value filters, as tool_data.get takes them; the period apart
 * @property fields The fields of the entries to attach, all of them when null
 */
data class PointerSelection(
    val zone: Named? = null,
    val tool: Named? = null,
    val config: Boolean = false,
    val entries: Boolean = false,
    val period: EntryPeriod = EntryPeriod(),
    val filters: JSONArray = JSONArray(),
    val fields: List<String>? = null
) {
    /** The kind of the deepest place reached: the app until a zone is chosen. */
    val level: ReferenceKind get() = when {
        tool != null -> ReferenceKind.TOOL_INSTANCE
        zone != null -> ReferenceKind.ZONE
        else -> ReferenceKind.APP
    }

    /** A pointer can be made once a zone or a tool is reached. */
    val complete: Boolean get() = level == ReferenceKind.ZONE || level == ReferenceKind.TOOL_INSTANCE

    /** Whether the entries are narrowed, by a period or a value filter. */
    val narrowed: Boolean get() = !period.isEmpty || filters.length() > 0

    /** Into [zone]: the boxes and the period stay, which a zone offers as well. */
    fun intoZone(zone: Named): PointerSelection = PointerSelection(zone = zone, config = config, entries = entries, period = period)

    /** Into [tool]: the period stays; its filters and fields, a tool's own, start empty. */
    fun intoTool(tool: Named): PointerSelection = copy(tool = tool, filters = JSONArray(), fields = null)

    /** Back up to the app, or to the zone: what the level left offered goes with it. */
    fun upTo(level: ReferenceKind): PointerSelection = when (level) {
        ReferenceKind.APP -> PointerSelection()
        ReferenceKind.ZONE -> PointerSelection(zone = zone, config = config, entries = entries, period = period)
        else -> this
    }

    /** The pointer to store: a selection of the core, its period apart from the value filters. */
    fun pointer(): PointerConfig {
        val target = when (level) {
            ReferenceKind.TOOL_INSTANCE -> Reference(ReferenceKind.TOOL_INSTANCE, tool!!.id)
            ReferenceKind.ZONE -> Reference(ReferenceKind.ZONE, zone!!.id)
            else -> throw IllegalStateException("a pointer needs a zone or a tool")
        }
        val isTool = level == ReferenceKind.TOOL_INSTANCE
        return PointerConfig(
            selection = EntrySelection(
                target = target,
                period = period,
                filters = if (isTool) filters else JSONArray(),
                fields = fields.takeIf { isTool }
            ),
            config = config,
            entries = entries
        )
    }

    fun toJson(): String = JSONObject().apply {
        zone?.let { put("zone", named(it)) }
        tool?.let { put("tool", named(it)) }
        put("config", config)
        put("entries", entries)
        put("period", period.toJson())
        put("filters", filters)
        fields?.let { put("fields", JSONArray(it)) }
    }.toString()

    companion object {
        private fun named(n: Named) = JSONObject().put("id", n.id).put("name", n.name).apply { n.tooltype?.let { put("tooltype", it) } }
        private fun named(json: JSONObject) = Named(json.getString("id"), json.getString("name"), json.optString("tooltype").takeIf { it.isNotEmpty() })

        fun fromJson(saved: String): PointerSelection {
            val json = JSONObject(saved)
            return PointerSelection(
                zone = json.optJSONObject("zone")?.let { named(it) },
                tool = json.optJSONObject("tool")?.let { named(it) },
                config = json.getBoolean("config"),
                entries = json.getBoolean("entries"),
                // The selector's own state, written by toJson: its dates read
                period = EntryPeriod.fromJson(json.getJSONObject("period")) { it },
                filters = json.getJSONArray("filters"),
                fields = json.optJSONArray("fields")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            )
        }
    }
}
