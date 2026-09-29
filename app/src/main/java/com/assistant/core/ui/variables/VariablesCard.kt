package com.assistant.core.ui.variables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.formatValue
import com.assistant.core.strings.Strings
import com.assistant.core.ui.CardType
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier

/** A variable as a zone lists it (variables.list). */
data class VariableRow(val id: String, val name: String, val group: String?, val definition: Map<*, *>, val toolsRead: Set<String>) {
    companion object {
        fun of(map: Map<*, *>) = VariableRow(
            id = map["id"] as String,
            name = map["name"] as String,
            group = (map["group"] as? String)?.takeIf { it.isNotEmpty() },
            definition = map["definition"] as? Map<*, *> ?: emptyMap<String, Any>(),
            toolsRead = (map["tools_read"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet()
        )
    }
}

/**
 * The variables of a zone's group in one compact card, one per line: its name and its value now,
 * or why it has none. The values are read again when an entry of a tool they read changes, or a
 * variable does. Touching a line opens the variable.
 */
@Composable
fun VariablesCard(variables: List<VariableRow>, onOpen: (String) -> Unit) {
    if (variables.isEmpty()) return
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var shown by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var version by remember { mutableStateOf(0) }

    val toolsRead = variables.flatMap { it.toolsRead }.toSet()
    LaunchedEffect(toolsRead) {
        DataChangeNotifier.changes.collect { event ->
            val read = event is DataChangeEvent.ToolDataChanged &&
                (event.toolInstanceId in toolsRead || com.assistant.core.services.VariableService.ANY_TOOL in toolsRead)
            if (read || event is DataChangeEvent.VariablesChanged) version++
        }
    }
    LaunchedEffect(variables, version) {
        val coordinator = Coordinator(context)
        shown = variables.associate { variable ->
            val result = coordinator.processUserAction("variables.evaluate", mapOf(
                "variable_id" to variable.id, "at" to listOf(System.currentTimeMillis())
            ))
            variable.id to if (!result.isSuccess) (result.error ?: s.shared("field_reference_unreadable")) else {
                val field = (result.data?.get("field") as? Map<*, *>)?.let { fieldOf(variable.name, it) }
                val row = (result.data?.get("values") as? List<*>)?.firstOrNull() as? Map<*, *>
                when {
                    row?.get("failure") != null -> s.shared("variable_no_value").format(((row["failure"] as Map<*, *>)["message"] as? String) ?: "")
                    field != null && row?.get("value") != null -> field.formatValue(row["value"], context)
                    else -> s.shared("field_reference_unreadable")
                }
            }
        }
    }

    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            variables.forEach { variable ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(variable.id) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.Text(text = variable.name, type = TextType.LABEL)
                    UI.Text(text = "·", type = TextType.CAPTION)
                    UI.Text(text = shown[variable.id] ?: s.shared("tools_loading"), type = TextType.BODY)
                }
            }
        }
    }
}

/** A variable's field as the service gives it, `{type, config}`. */
fun fieldOf(name: String, json: Map<*, *>): FieldDefinition? {
    val type = FieldType.entries.firstOrNull { it.name == json["type"] } ?: return null
    @Suppress("UNCHECKED_CAST")
    return FieldDefinition(name, name, null, type, false, (json["config"] as? Map<String, Any>))
}
