package com.assistant.core.ui.selectors

import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * What SelectionPicker holds while a selection of entries is built: where the user went (a zone,
 * a tool in it), by name for the browser, and what narrows the entries.
 *
 * A zone takes a period, applied to each of its tools; a tool a period, conditions on its entries
 * and a choice of fields, which are a tool's own: they have no sense across the tools of a zone.
 *
 * @property filters Conditions put on each entry (Conditions), the period apart
 * @property fields The fields kept, all of them when null
 */
data class SelectionDraft(
    val zone: Named? = null,
    val tool: Named? = null,
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

    /** Where the picker stands, for its browser. */
    val path: ThingPath get() = ThingPath(zone, tool)

    /** Whether the entries are narrowed, by a period or a condition. */
    val narrowed: Boolean get() = !period.isEmpty || filters.length() > 0

    /** Moved to [path] by the browser: going up or into another place leaves what they leave. */
    fun at(path: ThingPath): SelectionDraft = when {
        path.zone == null -> upTo(ReferenceKind.APP)
        path.tool == null -> if (path.zone == zone) upTo(ReferenceKind.ZONE) else intoZone(path.zone)
        path.tool == tool -> this
        else -> (if (path.zone == zone) this else intoZone(path.zone)).intoTool(path.tool)
    }

    /** Into [zone]: the period stays, which a zone takes as well. */
    fun intoZone(zone: Named): SelectionDraft = SelectionDraft(zone = zone, period = period)

    /** Into [tool]: the period stays; its conditions and fields, a tool's own, start empty. */
    fun intoTool(tool: Named): SelectionDraft = copy(tool = tool, filters = JSONArray(), fields = null)

    /** Back up to the app, or to the zone: what the level left offered goes with it. */
    fun upTo(level: ReferenceKind): SelectionDraft = when (level) {
        ReferenceKind.APP -> SelectionDraft()
        ReferenceKind.ZONE -> SelectionDraft(zone = zone, period = period)
        else -> this
    }

    /**
     * The selection of the core it stands for, on the zone or the tool reached.
     *
     * @throws IllegalStateException before a zone is reached
     */
    fun selection(): EntrySelection {
        val isTool = level == ReferenceKind.TOOL_INSTANCE
        return EntrySelection(
            target = when (level) {
                ReferenceKind.TOOL_INSTANCE -> Reference(ReferenceKind.TOOL_INSTANCE, tool!!.id)
                ReferenceKind.ZONE -> Reference(ReferenceKind.ZONE, zone!!.id)
                else -> throw IllegalStateException("a selection needs a zone or a tool")
            },
            period = period,
            filters = if (isTool) filters else JSONArray(),
            fields = fields.takeIf { isTool }
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        zone?.let { put("zone", it.toJson()) }
        tool?.let { put("tool", it.toJson()) }
        put("period", period.toJson())
        put("filters", filters)
        fields?.let { put("fields", JSONArray(it)) }
    }

    companion object {
        fun fromJson(json: JSONObject) = SelectionDraft(
            zone = json.optJSONObject("zone")?.let { Named.fromJson(it) },
            tool = json.optJSONObject("tool")?.let { Named.fromJson(it) },
            // The picker's own state, written by toJson: its dates read
            period = EntryPeriod.fromJson(json.getJSONObject("period")) { it },
            filters = json.getJSONArray("filters"),
            fields = json.optJSONArray("fields")?.let { a -> (0 until a.length()).map { a.getString(it) } }
        )

        /** A stored [selection] to edit again, the places of [path] named as they are now. */
        fun of(selection: EntrySelection, path: ThingPath) = SelectionDraft(
            zone = path.zone,
            tool = path.tool,
            period = selection.period,
            filters = selection.filters,
            fields = selection.fields
        )
    }
}
