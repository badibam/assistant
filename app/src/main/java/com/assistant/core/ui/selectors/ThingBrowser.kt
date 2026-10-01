package com.assistant.core.ui.selectors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.LogManager

/**
 * One place one level down: where it leads, what situates it (a tool's zone, that it is a
 * variable), and in a zone the group it sits in, null outside the zone's groups.
 */
private data class Place(val path: ThingPath, val detail: String? = null, val group: String? = null)

/**
 * The one way to reach a thing of the app (the Chose brick, docs/BRICKS.md), for the pointer and
 * the REFERENCE field: the trail App › zone › tool › entry, each step a way back up, and the places
 * one level down, among those that lead to something [target] takes (references.choices). In a
 * zone, its tools then its variables, group by group as its screen shows them, those outside any
 * group last. An entry is searched by its label.
 *
 * It only moves [path]: what the place reached means, and whether it can be chosen, is the
 * caller's.
 */
@Composable
fun ThingBrowser(path: ThingPath, onPath: (ThingPath) -> Unit, target: ReferenceTarget) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var query by rememberSaveable(path.tool?.id) { mutableStateOf("") }
    // Reloaded, never saved: null while they are read
    var places by remember { mutableStateOf<List<Place>?>(null) }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    val searching = path.kind == ReferenceKind.TOOL_INSTANCE && ReferenceKind.ENTRY in target.kinds
    // Nothing is listed below a place when nothing deeper is taken (a tool, for the pointer)
    val deeper = target.kinds.any { it in path.kind.below }

    LaunchedEffect(path.zone?.id, path.tool?.id, path.variable?.id, path.entry?.id, query) {
        places = null
        groups = emptyList()
        failed = false
        if (!deeper) { places = emptyList(); return@LaunchedEffect }
        val result = Coordinator(context).processUserAction("references.choices", buildMap {
            put("kinds", target.kinds.map { it.name })
            if (target.toolInstances.isNotEmpty()) put("tool_instances", target.toolInstances)
            path.zone?.let { put("zone_id", it.id) }
            path.tool?.let { put("tool_instance_id", it.id) }
            if (query.isNotBlank()) put("query", query)
        })
        if (!result.isSuccess) {
            LogManager.ui("ThingBrowser: places under ${path.reference} not read: ${result.error}", "ERROR")
            failed = true
            places = emptyList()
            return@LaunchedEffect
        }
        fun rows(key: String) = (result.data?.get(key) as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
        groups = (result.data?.get("groups") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        fun named(row: Map<*, *>, tooltype: Boolean = false) =
            Named(row["id"] as String, row["name"] as? String ?: "", if (tooltype) row["tooltype"] as? String else null)
        places = rows("zones").map { Place(ThingPath(named(it))) } +
            rows("tool_instances").map { row ->
                // A tool listed at the app, without its zone reached first, says which it is in
                val zone = path.zone ?: Named(row["zone_id"] as String, row["zone_name"] as? String ?: "")
                Place(ThingPath(zone, named(row, tooltype = true)), (row["zone_name"] as? String).takeIf { path.zone == null }, row["group"] as? String)
            } +
            rows("variables").map { row -> Place(ThingPath(path.zone, variable = named(row)), s.shared("reference_kind_variable"), row["group"] as? String) } +
            rows("entries").map { Place(path.copy(entry = named(it))) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        Trail(path, s.shared("pointer_level_app")) { onPath(path.upTo(it)) }
        if (searching) {
            UI.FormField(label = s.shared("field_reference_search"), value = query, onChange = { query = it }, required = false)
        }
        val current = places
        when {
            failed -> UI.Text(text = s.shared("error_loading_options"), type = TextType.ERROR)
            !deeper -> Unit
            current == null -> UI.LoadingIndicator()
            current.isEmpty() -> UI.Text(text = s.shared("scope_no_options"), type = TextType.BODY)
            // Without groups, one list; with them, a title over each group holding something
            groups.isEmpty() -> current.forEach { PlaceRow(it, onPath) }
            else -> {
                (groups.map { it to it } + (null to s.shared("label_ungrouped"))).forEach { (group, title) ->
                    val inGroup = current.filter { it.group == group }
                    if (inGroup.isNotEmpty()) {
                        UI.Text(text = title, type = TextType.SUBTITLE)
                        inGroup.forEach { PlaceRow(it, onPath) }
                    }
                }
            }
        }
    }
}

/** A place, its detail under it, and the button that goes to it. */
@Composable
private fun PlaceRow(place: Place, onPath: (ThingPath) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(text = place.path.entry?.name ?: place.path.variable?.name ?: place.path.tool?.name ?: place.path.zone!!.name, type = TextType.BODY)
            place.detail?.let { UI.Text(text = it, type = TextType.CAPTION) }
        }
        UI.ActionButton(action = ButtonAction.SELECT, onClick = { onPath(place.path) })
    }
}

/** App › zone › tool or variable › entry, each step but the last taking the user back up to it. */
@Composable
private fun Trail(path: ThingPath, appName: String, onUp: (ReferenceKind) -> Unit) {
    val steps = listOfNotNull(
        ReferenceKind.APP to appName,
        path.zone?.let { ReferenceKind.ZONE to it.name },
        path.tool?.let { ReferenceKind.TOOL_INSTANCE to it.name },
        path.variable?.let { ReferenceKind.VARIABLE to it.name },
        path.entry?.let { ReferenceKind.ENTRY to it.name }
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        steps.forEachIndexed { i, (kind, name) ->
            if (i > 0) UI.Icon(iconName = "chevron-right", size = 20.dp)
            val last = i == steps.lastIndex
            Box(modifier = if (last) Modifier else Modifier.clickable { onUp(kind) }) {
                UI.Text(text = name, type = if (last) TextType.SUBTITLE else TextType.BODY)
            }
        }
    }
}

/**
 * The path to the tool [toolInstanceId], its zone and itself named as they are now; null when it
 * cannot be read (deleted since), which is logged.
 */
suspend fun toolPath(toolInstanceId: String, context: android.content.Context): ThingPath? {
    val result = Coordinator(context).processUserAction("references.choices", mapOf("kinds" to listOf(ReferenceKind.ENTRY.name), "tool_instances" to listOf(toolInstanceId)))
    val row = (result.data?.get("tool_instances") as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["id"] == toolInstanceId }
    if (!result.isSuccess || row == null) {
        LogManager.ui("toolPath: tool $toolInstanceId not found: ${result.error}", "ERROR")
        return null
    }
    return ThingPath(
        Named(row["zone_id"] as String, row["zone_name"] as? String ?: ""),
        Named(toolInstanceId, row["name"] as? String ?: "", row["tooltype"] as? String)
    )
}

/**
 * The fields of the tool [toolInstanceId] by path (ToolFields.filterable), none while it is null
 * or read; a read that fails is logged and said by a toast.
 */
@Composable
fun rememberToolFields(toolInstanceId: String?): Map<String, com.assistant.core.fields.FieldDefinition> {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var fields by remember { mutableStateOf<Map<String, com.assistant.core.fields.FieldDefinition>>(emptyMap()) }
    LaunchedEffect(toolInstanceId) {
        fields = if (toolInstanceId == null) emptyMap() else try {
            com.assistant.core.fields.ToolFields.filterable(toolInstanceId, context, s)
        } catch (e: Exception) {
            LogManager.ui("rememberToolFields: fields of $toolInstanceId not loaded: ${e.message}", "ERROR", e)
            UI.Toast(context, s.shared("error_loading_options"))
            emptyMap()
        }
    }
    return fields
}
