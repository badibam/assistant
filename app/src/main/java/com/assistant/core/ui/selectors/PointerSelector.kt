package com.assistant.core.ui.selectors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ai.enrichments.PointerKind
import com.assistant.core.ai.processing.FilterValues
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.navigation.DataNavigator
import com.assistant.core.navigation.data.NodeType
import com.assistant.core.navigation.data.SchemaNode
import com.assistant.core.strings.Strings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.components.PeriodRangeSelector
import com.assistant.core.ui.components.RelativePeriod
import com.assistant.core.ui.components.RelativePeriodRangeSelector
import com.assistant.core.ui.components.getPeriodEndTimestamp
import com.assistant.core.utils.LogManager

/** The selection across a rotation, as PointerSelection writes it. */
private val PointerSelectionSaver: Saver<PointerSelection, String> = Saver(
    save = { it.toJson() },
    restore = { PointerSelection.fromJson(it) }
)

/**
 * The POINTER selector, on one screen (docs/design/pointer.md).
 *
 * At the top, where the user is: the app, a zone, a tool, each step a way back up. In the middle,
 * the places one level down. At the bottom, once a zone or a tool is reached: the config and
 * the entries to attach, and for a tool the period, the value filters and the fields that narrow
 * its entries, with the sentence that says what will go.
 *
 * @param relative Whether the pointer is replayed later (an automation's starting message): its
 *   period is then relative, resolved at each run
 * @param onConfirm The pointer's stored form, its text written each time the message is read
 */
@Composable
fun PointerSelector(
    relative: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val navigator = remember { DataNavigator(context) }

    var selection by rememberSaveable(stateSaver = PointerSelectionSaver) { mutableStateOf(PointerSelection()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // Reloaded, never saved: the places one level down, and the fields of the tool reached
    var places by remember { mutableStateOf<List<SchemaNode>?>(null) }
    var fields by remember { mutableStateOf<Map<String, FieldDefinition>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selection.level, selection.zone?.id) {
        places = null
        try {
            places = when (selection.level) {
                PointerKind.APP -> navigator.getRootNodes().filter { it.type == NodeType.ZONE }
                PointerKind.ZONE -> navigator.getChildren("zones.${selection.zone!!.id}").filter { it.type == NodeType.TOOL }
                else -> emptyList()
            }
        } catch (e: Exception) {
            LogManager.ui("PointerSelector: places not loaded: ${e.message}", "ERROR", e)
            errorMessage = s.shared("error_loading_options")
            places = emptyList()
        }
    }

    LaunchedEffect(selection.tool?.id) {
        val toolId = selection.tool?.id
        fields = if (toolId == null) emptyMap() else try {
            FilterValues.filterableFields(toolId, context, s)
        } catch (e: Exception) {
            LogManager.ui("PointerSelector: fields of $toolId not loaded: ${e.message}", "ERROR", e)
            errorMessage = s.shared("error_loading_options")
            emptyMap()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { UI.Toast(context, it); errorMessage = null }
    }

    UI.Dialog(
        type = DialogType.CONFIRM,
        confirmEnabled = selection.complete,
        onCancel = onDismiss,
        onConfirm = {
            onConfirm(selection.pointer { getPeriodEndTimestamp(it) }.toJson().toString())
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UI.Text(text = s.shared("pointer_enrichment_selector_title"), type = TextType.TITLE, fillMaxWidth = true)

            Breadcrumb(selection) { level -> selection = selection.upTo(level) }

            Places(places, selection.level) { node ->
                val named = Named(node.path.substringAfter('.'), node.displayName, node.toolType)
                selection = if (node.type == NodeType.ZONE) selection.intoZone(named) else selection.intoTool(named)
            }

            if (selection.complete) {
                AttachPanel(
                    selection = selection,
                    fields = fields,
                    relative = relative,
                    onChange = { selection = it },
                    onOpenFilters = { showFilters = true }
                )
            }
        }
    }

    if (showFilters && selection.tool != null) {
        PointerFiltersDialog(
            toolInstanceId = selection.tool!!.id,
            fields = fields,
            filters = selection.filters,
            chosenFields = selection.fields,
            relative = relative,
            onDismiss = { showFilters = false },
            onConfirm = { filters, chosen ->
                selection = selection.copy(filters = filters, fields = chosen)
                showFilters = false
            }
        )
    }
}

/** App › zone › tool, each step taking the user back up to it. */
@Composable
private fun Breadcrumb(selection: PointerSelection, onUp: (PointerKind) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val steps = listOfNotNull(
        PointerKind.APP to s.shared("pointer_level_app"),
        selection.zone?.let { PointerKind.ZONE to it.name },
        selection.tool?.let { PointerKind.TOOL to it.name }
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, (level, name) ->
            if (i > 0) UI.Icon(iconName = "chevron-right", size = 20.dp)
            val last = i == steps.lastIndex
            Box(modifier = if (last) Modifier else Modifier.clickable { onUp(level) }) {
                UI.Text(text = name, type = if (last) TextType.SUBTITLE else TextType.BODY)
            }
        }
    }
}

/** The zones of the app, or the tools of a zone, to go down into. */
@Composable
private fun Places(places: List<SchemaNode>?, level: PointerKind, onSelect: (SchemaNode) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    if (level == PointerKind.TOOL) return
    when {
        places == null -> UI.LoadingIndicator()
        places.isEmpty() -> UI.Text(text = s.shared("scope_no_options"), type = TextType.BODY)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(
                text = s.shared(if (level == PointerKind.APP) "scope_select_zone" else "scope_select_tool"),
                type = TextType.SUBTITLE
            )
            places.forEach { node ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) { UI.Text(text = node.displayName, type = TextType.BODY) }
                    UI.ActionButton(action = ButtonAction.SELECT, onClick = { onSelect(node) })
                }
            }
        }
    }
}

/**
 * What goes with the pointer: the two boxes, and for a tool what narrows its entries, then the
 * sentence that says what will go.
 */
@Composable
private fun AttachPanel(
    selection: PointerSelection,
    fields: Map<String, FieldDefinition>,
    relative: Boolean,
    onChange: (PointerSelection) -> Unit,
    onOpenFilters: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val isTool = selection.level == PointerKind.TOOL

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UI.Checkbox(checked = selection.config, onCheckedChange = { onChange(selection.copy(config = it)) }, label = s.shared("pointer_attach_config"))
            if (isTool) {
                UI.Checkbox(checked = selection.entries, onCheckedChange = { onChange(selection.copy(entries = it)) }, label = s.shared("pointer_attach_entries"))
            }
        }

        if (isTool) {
            UI.Text(text = s.shared("pointer_period"), type = TextType.SUBTITLE)
            PeriodEditor(selection.period, relative) { onChange(selection.copy(period = it)) }

            UI.Button(type = ButtonType.DEFAULT, onClick = onOpenFilters) {
                UI.Text(text = s.shared("pointer_filters_and_fields"), type = TextType.BODY)
            }
            for (i in 0 until selection.filters.length()) {
                UI.Text(text = PointerDescription.filter(selection.filters.getJSONObject(i), fields, s), type = TextType.CAPTION)
            }
            selection.fields?.let { kept ->
                UI.Text(text = s.shared("pointer_part_fields").format(kept.joinToString(", ") { fields[it]?.displayName ?: it }), type = TextType.CAPTION)
            }
        }

        UI.Text(text = PointerDescription.summary(selection, fields, s), type = TextType.BODY)
    }
}

/** The period of the entries: periods picked in a chat, relative ones for an automation. */
@Composable
internal fun PeriodEditor(period: TimestampSelection, relative: Boolean, onChange: (TimestampSelection) -> Unit) {
    if (relative) {
        RelativePeriodRangeSelector(
            startPeriodType = period.minPeriodType,
            endPeriodType = period.maxPeriodType,
            startRelativePeriod = period.minRelativePeriod,
            endRelativePeriod = period.maxRelativePeriod,
            startCustomDate = period.minCustomDateTime,
            endCustomDate = period.maxCustomDateTime,
            // A type picked starts at its current period; none means a date, picked next
            onStartTypeChange = { type ->
                onChange(
                    if (type != null) period.copy(minPeriodType = type, minRelativePeriod = RelativePeriod.now(type), minCustomDateTime = null)
                    else period.copy(minPeriodType = null, minRelativePeriod = null)
                )
            },
            onEndTypeChange = { type ->
                onChange(
                    if (type != null) period.copy(maxPeriodType = type, maxRelativePeriod = RelativePeriod.now(type), maxCustomDateTime = null)
                    else period.copy(maxPeriodType = null, maxRelativePeriod = null)
                )
            },
            onStartRelativePeriodChange = { onChange(period.copy(minRelativePeriod = it)) },
            onEndRelativePeriodChange = { onChange(period.copy(maxRelativePeriod = it)) },
            onStartCustomDateChange = { onChange(period.copy(minCustomDateTime = it)) },
            onEndCustomDateChange = { onChange(period.copy(maxCustomDateTime = it)) }
        )
    } else {
        PeriodRangeSelector(
            startPeriodType = period.minPeriodType,
            endPeriodType = period.maxPeriodType,
            startPeriod = period.minPeriod,
            endPeriod = period.maxPeriod,
            startCustomDate = period.minCustomDateTime,
            startIsNow = period.minIsNow,
            endCustomDate = period.maxCustomDateTime,
            endIsNow = period.maxIsNow,
            startRelativePeriod = null,
            endRelativePeriod = null,
            onStartTypeChange = { onChange(period.copy(minPeriodType = it)) },
            onEndTypeChange = { onChange(period.copy(maxPeriodType = it)) },
            // A period picked replaces a date, a date a period, and now either
            onStartPeriodChange = { p -> onChange(period.copy(minPeriodType = p?.type ?: period.minPeriodType, minPeriod = p, minCustomDateTime = null)) },
            onEndPeriodChange = { p -> onChange(period.copy(maxPeriodType = p?.type ?: period.maxPeriodType, maxPeriod = p, maxCustomDateTime = null)) },
            onStartCustomDateChange = { onChange(period.copy(minPeriodType = null, minPeriod = null, minCustomDateTime = it)) },
            onEndCustomDateChange = { onChange(period.copy(maxPeriodType = null, maxPeriod = null, maxCustomDateTime = it)) },
            onStartIsNowChange = { onChange(period.copy(minIsNow = it, minPeriodType = null, minPeriod = null, minCustomDateTime = null)) },
            onEndIsNowChange = { onChange(period.copy(maxIsNow = it, maxPeriodType = null, maxPeriod = null, maxCustomDateTime = null)) },
            useOnlyRelativeLabels = false,
            returnRelative = false,
            onStartRelativePeriodChange = null,
            onEndRelativePeriodChange = null
        )
    }
}
