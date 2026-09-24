package com.assistant.core.ui.selectors.data

import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import org.json.JSONArray
import org.json.JSONObject

/**
 * ZoneScopeState as JSON, so the scope selector keeps where the user is across a rotation.
 *
 * The whole state is kept, the options loaded at each level included: they are short lists of
 * zones and tools, and keeping them spares reloading the navigation asynchronously on restore.
 */
object ZoneScopeStateJson {

    fun toJson(state: ZoneScopeState): String = JSONObject()
        .put("selection_chain", JSONArray(state.selectionChain.map { step ->
            JSONObject()
                .put("label", step.label)
                .put("selected_value", step.selectedValue)
                .put("selected_node", node(step.selectedNode))
        }))
        .put("selected_path", state.selectedPath)
        .put("current_options", nodes(state.currentOptions))
        .put("current_level", state.currentLevel)
        .put("options_by_level", JSONObject().apply {
            state.optionsByLevel.forEach { (level, options) -> put(level.toString(), nodes(options)) }
        })
        .put("selected_context", state.selectedContext.name)
        .put("selected_resources", JSONArray(state.selectedResources))
        .put("timestamp_selection", timestamps(state.timestampSelection))
        .put("is_complete", state.isComplete)
        .toString()

    fun fromJson(saved: String): ZoneScopeState {
        val json = JSONObject(saved)
        val chain = json.getJSONArray("selection_chain")
        val byLevel = json.getJSONObject("options_by_level")
        return ZoneScopeState(
            selectionChain = (0 until chain.length()).map { i ->
                val step = chain.getJSONObject(i)
                SelectionStep(
                    label = step.getString("label"),
                    selectedValue = step.getString("selected_value"),
                    selectedNode = node(step.getJSONObject("selected_node"))
                )
            },
            selectedPath = json.getString("selected_path"),
            currentOptions = nodes(json.getJSONArray("current_options")),
            currentLevel = json.getInt("current_level"),
            optionsByLevel = byLevel.keys().asSequence().associate { it.toInt() to nodes(byLevel.getJSONArray(it)) },
            selectedContext = PointerContext.valueOf(json.getString("selected_context")),
            selectedResources = json.getJSONArray("selected_resources").let { r -> (0 until r.length()).map { r.getString(it) } },
            timestampSelection = timestamps(json.getJSONObject("timestamp_selection")),
            isComplete = json.getBoolean("is_complete")
        )
    }

    private fun node(node: SchemaNode): JSONObject = JSONObject()
        .put("path", node.path)
        .put("display_name", node.displayName)
        .put("type", node.type.name)
        .put("has_children", node.hasChildren)
        .put("tool_type", node.toolType ?: JSONObject.NULL)
        .put("field_type", node.fieldType ?: JSONObject.NULL)

    private fun node(json: JSONObject) = SchemaNode(
        path = json.getString("path"),
        displayName = json.getString("display_name"),
        type = NodeType.valueOf(json.getString("type")),
        hasChildren = json.getBoolean("has_children"),
        toolType = json.optStringOrNull("tool_type"),
        fieldType = json.optStringOrNull("field_type")
    )

    private fun nodes(nodes: List<SchemaNode>) = JSONArray(nodes.map { node(it) })

    private fun nodes(array: JSONArray) = (0 until array.length()).map { node(array.getJSONObject(it)) }

    private fun timestamps(t: TimestampSelection): JSONObject = JSONObject()
        .put("min_period_type", t.minPeriodType?.name ?: JSONObject.NULL)
        .put("min_period", t.minPeriod?.let { period(it) } ?: JSONObject.NULL)
        .put("min_custom_date_time", t.minCustomDateTime ?: JSONObject.NULL)
        .put("min_is_now", t.minIsNow)
        .put("max_period_type", t.maxPeriodType?.name ?: JSONObject.NULL)
        .put("max_period", t.maxPeriod?.let { period(it) } ?: JSONObject.NULL)
        .put("max_custom_date_time", t.maxCustomDateTime ?: JSONObject.NULL)
        .put("max_is_now", t.maxIsNow)
        .put("min_relative_period", t.minRelativePeriod?.let { relative(it) } ?: JSONObject.NULL)
        .put("max_relative_period", t.maxRelativePeriod?.let { relative(it) } ?: JSONObject.NULL)

    private fun timestamps(json: JSONObject) = TimestampSelection(
        minPeriodType = json.optStringOrNull("min_period_type")?.let { PeriodType.valueOf(it) },
        minPeriod = json.optJSONObject("min_period")?.let { period(it) },
        minCustomDateTime = if (json.isNull("min_custom_date_time")) null else json.getLong("min_custom_date_time"),
        minIsNow = json.getBoolean("min_is_now"),
        maxPeriodType = json.optStringOrNull("max_period_type")?.let { PeriodType.valueOf(it) },
        maxPeriod = json.optJSONObject("max_period")?.let { period(it) },
        maxCustomDateTime = if (json.isNull("max_custom_date_time")) null else json.getLong("max_custom_date_time"),
        maxIsNow = json.getBoolean("max_is_now"),
        minRelativePeriod = json.optJSONObject("min_relative_period")?.let { relative(it) },
        maxRelativePeriod = json.optJSONObject("max_relative_period")?.let { relative(it) }
    )

    private fun period(p: Period) = JSONObject().put("timestamp", p.timestamp).put("type", p.type.name)
    private fun period(json: JSONObject) = Period(json.getLong("timestamp"), PeriodType.valueOf(json.getString("type")))

    private fun relative(p: RelativePeriod) = JSONObject().put("offset", p.offset).put("type", p.type.name)
    private fun relative(json: JSONObject) = RelativePeriod(json.getInt("offset"), PeriodType.valueOf(json.getString("type")))

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
