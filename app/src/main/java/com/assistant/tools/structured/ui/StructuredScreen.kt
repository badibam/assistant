package com.assistant.tools.structured.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.commands.CommandResult
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.CustomFieldsDisplay
import com.assistant.core.fields.CustomFieldsInput
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldValue
import com.assistant.core.fields.FieldsLayout
import com.assistant.core.fields.ToolFields
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.CardType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.ui.selectors.PointerDescription
import com.assistant.core.ui.selectors.PointerFiltersDialog
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.JsonUtils
import com.assistant.tools.structured.StructuredToolType
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One sheet: its id, its name, the user's fields it holds. */
data class Sheet(val id: String, val name: String, val extra: Map<String, Any?>) {
    companion object {
        fun of(map: Map<*, *>) = Sheet(
            id = map["id"] as String,
            name = map["name"] as? String ?: "",
            extra = (map["extra"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()
        )
    }
}

/** The key the table is sorted by: the name, or one of the user's fields. */
private const val BY_NAME = "name"

/**
 * The screen of structured data (docs/design/missing-tools.md, « Données structurées »): a table
 * of the sheets — the name, then the first fields of the config, sorted by touching a column's
 * header — and the screen of one sheet, all its fields, a swipe or a button leading to its
 * neighbours. One filter header serves both: search by name, filters, sort, folded into one line
 * that sums them up with the position. It lasts while the tool is visited, rotation included.
 *
 * A sheet is changed on its own screen: « Modifier » turns it all into inputs, « Enregistrer »
 * writes it at once, and no swipe meanwhile. « + » opens an empty sheet being written, nothing
 * stored before « Enregistrer ». A sheet changed that leaves the filter stays shown until left.
 */
@Composable
fun StructuredScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "structured", context = context) }
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<JSONObject?>(null) }
    var sheets by remember { mutableStateOf<List<Sheet>?>(null) }
    var filterable by remember { mutableStateOf<Map<String, FieldDefinition>>(emptyMap()) }
    var configVersion by remember { mutableIntStateOf(0) }
    var sheetsVersion by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // The filter header, kept for the visit
    var search by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf("[]") }
    var sortKey by rememberSaveable { mutableStateOf(BY_NAME) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var headerOpen by rememberSaveable { mutableStateOf(false) }
    var editingFilters by rememberSaveable { mutableStateOf(false) }

    // The sheet open: its id, "" for a new one; whether it is being written, and the draft
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftExtra by rememberSaveable(stateSaver = com.assistant.core.ui.FieldValuesSaver) { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Sheets changed while open that the filter may no longer keep, shown until the sheet is left
    var kept by remember { mutableStateOf<Map<String, Sheet>>(emptyMap()) }
    // A file picked to import, its text; too large to be kept across a rotation, it is picked again
    var importing by remember { mutableStateOf<String?>(null) }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: java.io.IOException) {
                errorMessage = s.shared("import_error_file").format(e.message ?: ""); null
            }
        }
    }

    LaunchedEffect(toolInstanceId, configVersion) {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        @Suppress("UNCHECKED_CAST")
        config = JsonUtils.toJSONObject((result.data?.get("tool_instance") as Map<*, *>)["config"] as Map<String, Any?>)
        // The fields not read are said: filters and sorting would otherwise offer nothing in silence
        filterable = try { ToolFields.filterable(toolInstanceId, context, s) } catch (e: IllegalStateException) { errorMessage = e.message; emptyMap() }
    }
    LaunchedEffect(toolInstanceId, sheetsVersion, search, filters) {
        val all = JSONArray(filters)
        if (search.isNotBlank()) all.put(JSONObject().put("field", "name").put("op", "contains").put("value", search.trim()))
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId, "filters" to JsonUtils.toList(all)))
        if (!result.isSuccess) { errorMessage = result.error; return@LaunchedEffect }
        sheets = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { Sheet.of(it) }
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) sheetsVersion++
                is DataChangeEvent.ToolsChanged -> configVersion++
                else -> {}
            }
        }
    }
    errorMessage?.let { message ->
        LaunchedEffect(message) { UI.Toast(context, message, Duration.LONG); errorMessage = null }
    }

    fun write(action: suspend () -> CommandResult, onDone: (CommandResult) -> Unit = {}) {
        scope.launch {
            val result = action()
            if (result.isSuccess) onDone(result) else errorMessage = result.error ?: s.shared("message_error_simple")
        }
    }

    val loadedConfig = config
    val loadedSheets = sheets
    if (loadedConfig == null || loadedSheets == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { UI.LoadingIndicator() }
        return
    }
    val settings = ToolConfigSettings.read(StructuredToolType, loadedConfig, context)
    val fields: List<FieldDefinition> = loadedConfig.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
    val columns = fields.take((settings.number(StructuredToolType.TABLE_COLUMNS)?.toInt() ?: 2).coerceAtLeast(0))

    // What is shown: the filtered sheets, and those kept while open, in the order asked
    val shown = (loadedSheets + kept.values.filter { k -> loadedSheets.none { it.id == k.id } })
        .let { list -> sorted(list, sortKey, ascending) }
        .map { sheet -> kept[sheet.id]?.takeIf { loadedSheets.none { it.id == sheet.id } } ?: sheet }
    val openSheet = openId?.takeIf { it.isNotEmpty() }?.let { id -> shown.find { it.id == id } }
    val position = openSheet?.let { shown.indexOf(it) }

    fun open(sheet: Sheet?) {
        openId = sheet?.id ?: ""
        editing = sheet == null
        draftName = sheet?.name ?: ""
        draftExtra = sheet?.extra ?: fields.associate { it.name to it.defaultValue }
    }
    fun close() { openId = null; editing = false; kept = emptyMap() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UI.PageHeader(
            title = if (openId == null) settings.string("name")!! else if (openId == "") s.tool("new_sheet") else openSheet?.name ?: "",
            subtitle = settings.string("description")?.takeIf { it.isNotBlank() && openId == null },
            icon = settings.string("icon_name"),
            leftButton = ButtonAction.BACK,
            rightButton = if (openId == null) ButtonAction.CONFIGURE else null,
            onLeftClick = { if (openId != null) close() else onNavigateBack() },
            onRightClick = onConfigureClick
        )

        // The filter header: one line summing it up, the whole of it when opened
        if (!editing) {
            val summary = listOfNotNull(
                search.takeIf { it.isNotBlank() }?.let { "« $it »" },
                *JSONArray(filters).let { a -> (0 until a.length()).map { PointerDescription.filter(a.getJSONObject(it), filterable, s) } }.toTypedArray(),
                "${if (sortKey == BY_NAME) s.shared("label_name") else fields.find { it.name == sortKey }?.displayName ?: sortKey} ${if (ascending) "↑" else "↓"}",
                if (position != null) s.tool("position").format(position + 1, shown.size) else s.tool("count").format(shown.size)
            ).joinToString(" · ")
            UI.Card(type = CardType.SECTION_HEADER) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().clickable { headerOpen = !headerOpen }) {
                        UI.Text(summary, TextType.CAPTION)
                    }
                    if (headerOpen) {
                        UI.FormField(label = s.tool("search"), value = search, onChange = { search = it }, fieldType = FieldType.SEARCH, required = false)
                        UI.Button(type = ButtonType.DEFAULT, onClick = { editingFilters = true }) { UI.Text(s.tool("filters"), TextType.LABEL) }
                        val sortOptions = listOf(BY_NAME to s.shared("label_name")) + fields.map { it.name to it.displayName }
                        UI.FormSelection(
                            label = s.tool("sort_by").format(""),
                            options = sortOptions.map { it.second },
                            selected = sortOptions.firstOrNull { it.first == sortKey }?.second ?: sortOptions.first().second,
                            onSelect = { label -> sortKey = sortOptions.first { it.second == label }.first },
                            required = false
                        )
                    }
                }
            }
        }

        if (openId == null) {
            SheetsTable(shown, columns, sortKey, ascending, loadedSheets.isEmpty() && search.isBlank() && JSONArray(filters).length() == 0, s, context,
                onSort = { key -> if (key == sortKey) ascending = !ascending else { sortKey = key; ascending = true } },
                onOpen = { open(it) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                UI.ActionButton(action = ButtonAction.ADD, onClick = { open(null) })
                UI.Button(type = ButtonType.SECONDARY, onClick = { pickFile.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) {
                    UI.Text(s.shared("import_action"), TextType.LABEL)
                }
            }
        } else if (editing) {
            UI.FormField(label = s.shared("label_name"), value = draftName, onChange = { draftName = it }, fieldType = FieldType.TEXT, required = true)
            CustomFieldsInput(customFieldsMetadata = fields, values = draftExtra, onValuesChange = { draftExtra = it }, context = context, newEntry = openId == "")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UI.Button(type = ButtonType.PRIMARY, state = if (draftName.isNotBlank()) com.assistant.core.ui.ComponentState.NORMAL else com.assistant.core.ui.ComponentState.DISABLED, onClick = {
                    val extra = draftExtra.filterValues { it != null }
                    val params = mutableMapOf<String, Any>("tool_instance_id" to toolInstanceId, "name" to draftName.trim(), "data" to emptyMap<String, Any>())
                    if (openId == "") {
                        if (extra.isNotEmpty()) params["extra"] = extra
                        write({ coordinator.processUserAction("tool_data.create", params) }) { result ->
                            val id = result.data?.get("id") as String
                            kept = kept + (id to Sheet(id, draftName.trim(), extra))
                            openId = id
                            editing = false
                        }
                    } else {
                        val id = openId!!
                        // A field emptied is sent as null, which removes it
                        params["id"] = id
                        params["extra"] = fields.associate { it.name to draftExtra[it.name] }
                        write({ coordinator.processUserAction("tool_data.update", params) }) {
                            kept = kept + (id to Sheet(id, draftName.trim(), extra))
                            editing = false
                        }
                    }
                }) { UI.Text(s.shared("action_save"), TextType.LABEL) }
                UI.Button(type = ButtonType.SECONDARY, onClick = { if (openId == "") close() else editing = false }) { UI.Text(s.shared("action_cancel"), TextType.LABEL) }
            }
        } else if (openSheet != null && position != null) {
            SheetView(openSheet, loadedConfig, context,
                onPrevious = if (position > 0) ({ open(shown[position - 1]) }) else null,
                onNext = if (position < shown.size - 1) ({ open(shown[position + 1]) }) else null)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                UI.Button(type = ButtonType.PRIMARY, onClick = { open(openSheet); editing = true }) { UI.Text(s.tool("action_edit"), TextType.LABEL) }
                UI.ActionButton(action = ButtonAction.DELETE, onClick = { confirmDelete = true })
            }
        }
    }

    // A sheet deleted meanwhile closes it
    LaunchedEffect(loadedSheets, openId) {
        if (!editing && openId?.isNotEmpty() == true && shown.none { it.id == openId }) close()
    }

    if (confirmDelete && openSheet != null) {
        UI.Dialog(type = DialogType.DANGER, onCancel = { confirmDelete = false }, onConfirm = {
            confirmDelete = false
            write({ coordinator.processUserAction("tool_data.delete", mapOf("tool_instance_id" to toolInstanceId, "id" to openSheet.id)) }) { close() }
        }) { UI.Text(s.tool("delete_confirm").format(openSheet.name), TextType.BODY) }
    }
    importing?.let { csv ->
        com.assistant.core.ui.imports.ImportDialog(toolInstanceId = toolInstanceId, csv = csv, onDismiss = { importing = null })
    }
    if (editingFilters) {
        PointerFiltersDialog(
            toolInstanceId = toolInstanceId,
            fields = filterable.filterKeys { it != "name" },
            filters = JSONArray(filters),
            chosenFields = null,
            reference = null,
            onDismiss = { editingFilters = false },
            onConfirm = { chosen, _ -> filters = chosen.toString(); editingFilters = false }
        )
    }
}

/** [sheets] ordered by [key], a missing value last whichever the direction. */
private fun sorted(sheets: List<Sheet>, key: String, ascending: Boolean): List<Sheet> {
    fun valueOf(sheet: Sheet): Any? = if (key == BY_NAME) sheet.name else sheet.extra[key]
    val present = sheets.filter { valueOf(it) != null }
    val comparator = Comparator<Sheet> { a, b ->
        val x = valueOf(a); val y = valueOf(b)
        if (x is Number && y is Number) x.toDouble().compareTo(y.toDouble())
        else x.toString().lowercase().compareTo(y.toString().lowercase())
    }
    val ordered = present.sortedWith(if (ascending) comparator else comparator.reversed())
    return ordered + sheets.filter { valueOf(it) == null }
}

/** The table: the names of its columns once, in the header, then one line of values per sheet. */
@Composable
private fun SheetsTable(
    sheets: List<Sheet>,
    columns: List<FieldDefinition>,
    sortKey: String,
    ascending: Boolean,
    empty: Boolean,
    s: com.assistant.core.strings.StringsContext,
    context: android.content.Context,
    onSort: (String) -> Unit,
    onOpen: (Sheet) -> Unit
) {
    if (sheets.isEmpty()) {
        UI.Text(s.tool(if (empty) "table_empty" else "no_match"), TextType.CAPTION)
        return
    }
    val keys = listOf(BY_NAME to s.shared("label_name")) + columns.map { it.name to it.displayName }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { (key, label) ->
            Box(modifier = Modifier.weight(1f).clickable { onSort(key) }) {
                UI.Text(label + if (key == sortKey) (if (ascending) " ↑" else " ↓") else "", TextType.LABEL)
            }
        }
    }
    UI.Divider()
    sheets.forEach { sheet ->
        Row(modifier = Modifier.fillMaxWidth().clickable { onOpen(sheet) }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) { UI.Text(sheet.name, TextType.BODY) }
            columns.forEach { field ->
                Box(modifier = Modifier.weight(1f)) {
                    sheet.extra[field.name]?.let { FieldValue(field, it, context) }
                }
            }
        }
    }
}

/** One sheet, every field in full; a swipe or the buttons lead to its neighbours. */
@Composable
private fun SheetView(sheet: Sheet, config: JSONObject, context: android.content.Context, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    val s = remember { Strings.`for`(tool = "structured", context = context) }
    var dragged by remember(sheet.id) { mutableStateOf(0f) }
    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(sheet.id, onPrevious, onNext) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    when {
                        dragged > 120f -> onPrevious?.invoke()
                        dragged < -120f -> onNext?.invoke()
                    }
                    dragged = 0f
                },
                onHorizontalDrag = { _, amount -> dragged += amount }
            )
        },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CustomFieldsDisplay(StructuredToolType, config, sheet.extra, FieldsLayout.EXPANDED, context)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UI.ActionButton(action = ButtonAction.LEFT, display = ButtonDisplay.ICON, size = Size.S, enabled = onPrevious != null, onClick = { onPrevious?.invoke() })
            UI.ActionButton(action = ButtonAction.RIGHT, display = ButtonDisplay.ICON, size = Size.S, enabled = onNext != null, onClick = { onNext?.invoke() })
        }
    }
}
