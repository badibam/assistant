package com.assistant.core.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.ToolFields
import com.assistant.core.ui.selectors.FieldPick
import com.assistant.core.ui.selectors.FieldPicker
import com.assistant.core.imports.CellRead
import com.assistant.core.imports.Writing
import com.assistant.core.strings.Strings
import com.assistant.core.ui.CardType
import com.assistant.core.ui.DialogType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * An import into a tool (docs/DATA.md, « Import »): the declaration the detection proposes, one
 * line per column — its target, its type, its writing and an example read with it — every
 * ambiguity to settle and the lines that would be refused, all correctable before importing; then
 * what was done, line by line for what was refused.
 *
 * @param csv The file's text, read by the caller
 */
@Composable
fun ImportDialog(toolInstanceId: String, csv: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val zone = remember { AppConfigManager.getDateTimeConfig().getZoneId() }

    // The declaration as the service takes it, with the detection's notes beside each column
    var columns by rememberSaveable { mutableStateOf<String?>(null) }
    var lines by rememberSaveable { mutableStateOf(0) }
    var report by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fields by remember { mutableStateOf<Map<String, com.assistant.core.fields.FieldDefinition>>(emptyMap()) }

    LaunchedEffect(toolInstanceId) {
        fields = try {
            ToolFields.filterable(toolInstanceId, context, s).filterKeys { !it.startsWith("state.") && it != "created_at" && it != "updated_at" }
        } catch (e: IllegalStateException) { error = e.message; emptyMap() }
        if (columns != null) return@LaunchedEffect
        val result = coordinator.processUserAction("imports.detect", mapOf("tool_instance_id" to toolInstanceId, "csv" to csv))
        if (result.isSuccess) {
            columns = JSONArray(JsonUtils.toList(result.data?.get("columns") as List<*>)).toString()
            lines = (result.data?.get("lines") as? Number)?.toInt() ?: 0
        } else error = result.error
    }

    val done = report
    UI.Dialog(
        type = if (done != null) DialogType.INFO else DialogType.CONFIRM,
        onCancel = onDismiss,
        confirmEnabled = columns != null,
        onConfirm = {
            if (done != null) { onDismiss(); return@Dialog }
            val declared = JSONArray(columns!!)
            val clean = JSONArray((0 until declared.length()).map { i ->
                JSONObject(declared.getJSONObject(i).toString()).apply { remove("ambiguous"); remove("example"); remove("unreadable_lines") }
            })
            scope.launch {
                val result = coordinator.processUserAction("imports.apply", mapOf("tool_instance_id" to toolInstanceId, "csv" to csv, "columns" to JsonUtils.toList(clean)))
                if (!result.isSuccess) { error = result.error; return@launch }
                val refused = (result.data?.get("refused") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
                report = (listOf(s.shared("import_report").format(result.data?.get("created"), result.data?.get("updated"), refused.size)) +
                    refused.map { s.shared("import_report_line").format(it["line"], it["reason"]) }).joinToString("\n")
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UI.Text(s.shared("import_title"), TextType.TITLE)
            error?.let { UI.Text(it, TextType.ERROR) }
            when {
                done != null -> UI.Text(done, TextType.BODY)
                columns == null -> if (error == null) UI.LoadingIndicator()
                else -> {
                    UI.Text(s.shared("import_lines").format(lines), TextType.CAPTION)
                    val array = JSONArray(columns!!)
                    for (i in 0 until array.length()) {
                        ColumnEditor(array.getJSONObject(i), fields, zone, s) { changed ->
                            columns = JSONArray(columns!!).put(i, changed).toString()
                        }
                    }
                }
            }
        }
    }
}

/** One column: where it goes, its type and writing, an example read, what is left to settle. */
@Composable
private fun ColumnEditor(column: JSONObject, fields: Map<String, com.assistant.core.fields.FieldDefinition>, zone: java.time.ZoneId, s: com.assistant.core.strings.StringsContext, onChange: (JSONObject) -> Unit) {
    fun edit(change: JSONObject.() -> Unit) = onChange(JSONObject(column.toString()).apply(change))
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UI.Text(column.getString("column"), TextType.SUBTITLE)
            val targets = listOf("KEY", "FIELD", "NEW", "IGNORE").map { it to s.shared("import_target_${it.lowercase()}") }
            UI.FormSelection(
                label = s.shared("import_target"),
                options = targets.map { it.second },
                selected = targets.first { it.first == column.getString("target") }.second,
                onSelect = { label ->
                    val target = targets.first { it.second == label }.first
                    edit {
                        put("target", target)
                        if (target != "FIELD") remove("field")
                        if (target == "NEW" && !has("new_field")) put("new_field", JSONObject().put("name", "").put("display_name", column.getString("column")).put("type", "TEXT"))
                        if (target != "NEW") remove("new_field")
                        if (target == "KEY" || target == "IGNORE") remove("writing")
                    }
                },
                required = true
            )
            val type: FieldType? = when (column.getString("target")) {
                "FIELD" -> {
                    FieldPicker(
                        label = s.shared("import_field"),
                        fields = fields,
                        selected = column.optString("field").takeIf { it.isNotEmpty() }?.let { FieldPick.Path(it) },
                        onSelect = { pick -> edit { put("field", (pick as FieldPick.Path).path); remove("writing") } }
                    )
                    null
                }
                "NEW" -> {
                    val newField = column.getJSONObject("new_field")
                    val types = FieldType.entries.filter { Writing.of(it).isNotEmpty() }
                    UI.FormSelection(
                        label = s.shared("import_type"),
                        options = types.map { s.shared("field_type_${it.name.lowercase()}_display_name") },
                        selected = s.shared("field_type_${newField.getString("type").lowercase()}_display_name"),
                        onSelect = { label ->
                            val chosen = types.first { s.shared("field_type_${it.name.lowercase()}_display_name") == label }
                            edit {
                                put("new_field", JSONObject(newField.toString()).put("type", chosen.name).apply {
                                    when (chosen) {
                                        FieldType.NUMERIC -> put("config", JSONObject().put("decimals", 2))
                                        FieldType.SCALE -> put("config", JSONObject().put("min", 0).put("max", 10))
                                        FieldType.CHOICE -> {} // keeps the options the detection read
                                        else -> remove("config")
                                    }
                                })
                                put("writing", Writing.of(chosen).first().name)
                            }
                        },
                        required = true
                    )
                    FieldType.valueOf(newField.getString("type"))
                }
                else -> null
            }
            val writingType = type ?: column.optString("writing").takeIf { it.isNotEmpty() }?.let { Writing.valueOf(it).type }
            if (writingType != null && column.getString("target") != "KEY" && column.getString("target") != "IGNORE") {
                val writings = Writing.of(writingType)
                UI.FormSelection(
                    label = s.shared("import_writing"),
                    options = writings.map { s.shared("import_writing_${it.name.lowercase()}") },
                    selected = column.optString("writing").takeIf { it.isNotEmpty() }?.let { s.shared("import_writing_${it.lowercase()}") } ?: "",
                    onSelect = { label -> edit { put("writing", writings.first { s.shared("import_writing_${it.name.lowercase()}") == label }.name) } },
                    required = true
                )
            }
            // The example read with the writing chosen, "03/04/2026 → 2026-04-03"
            column.optJSONObject("example")?.optString("cell")?.takeIf { it.isNotEmpty() }?.let { cell ->
                val read = column.optString("writing").takeIf { it.isNotEmpty() }?.let { Writing.valueOf(it).read(cell, zone) }
                UI.Text("$cell → " + when (read) { is CellRead.Value -> read.value.toString(); is CellRead.Unreadable -> s.shared("import_unreadable"); null -> "?" }, TextType.CAPTION)
            }
            column.optJSONArray("ambiguous")?.takeIf { it.length() > 0 && column.optString("writing").isEmpty() }?.let {
                UI.Text(s.shared("import_ambiguous"), TextType.WARNING)
            }
            column.optJSONArray("unreadable_lines")?.takeIf { it.length() > 0 }?.let { lines ->
                UI.Text(s.shared("import_unreadable_lines").format((0 until lines.length()).joinToString(", ") { lines.get(it).toString() }), TextType.WARNING)
            }
        }
    }
}
