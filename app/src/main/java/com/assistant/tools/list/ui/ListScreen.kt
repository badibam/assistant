package com.assistant.tools.list.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.commands.CommandResult
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.defaultValues
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolConfigSettings
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.ButtonDisplay
import com.assistant.core.ui.ButtonType
import com.assistant.core.ui.Duration
import com.assistant.core.ui.FieldType
import com.assistant.core.ui.Size
import com.assistant.core.ui.TextType
import com.assistant.core.ui.UI
import com.assistant.core.utils.DataChangeEvent
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.JsonUtils
import com.assistant.tools.list.ListItem
import com.assistant.tools.list.ListItems
import com.assistant.tools.list.ListToolType
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The screen of a list: a field at the top to add an item by its name; the items left,
 * reordered by their handle; the checked ones below, greyed, in the order they were checked, and
 * a button to uncheck them all. A list set to remove what is checked deletes an item checked.
 *
 * Touching an item's name opens it, for its name and the list's own fields.
 */
@Composable
fun ListScreen(
    toolInstanceId: String,
    onNavigateBack: () -> Unit,
    onConfigureClick: () -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "list", context = context) }
    val scope = rememberCoroutineScope()

    // Reloaded, never saved: the tool's config and its items
    var config by remember { mutableStateOf<JSONObject?>(null) }
    var items by remember { mutableStateOf<List<ListItem>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var configVersion by remember { mutableIntStateOf(0) }
    var itemsVersion by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // What the user produced: the name being typed, the item open
    var typed by rememberSaveable { mutableStateOf("") }
    var openItemId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(toolInstanceId, configVersion) {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { loadFailed = true; errorMessage = result.error; return@LaunchedEffect }
        val tool = result.data?.get("tool_instance") as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        config = JsonUtils.toJSONObject(tool["config"] as Map<String, Any?>)
    }
    LaunchedEffect(toolInstanceId, itemsVersion) {
        val loaded = ListItems.load(coordinator, toolInstanceId)
        if (loaded == null) loadFailed = true else items = loaded
    }
    // An item written anywhere, by this screen, its tile or the AI, reloads the items; a config
    // saved reloads the header and the list's own fields
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) itemsVersion++
                is DataChangeEvent.ToolsChanged -> configVersion++
                else -> {}
            }
        }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    /** Runs a write, and says why when the service refuses it; the reload follows the change notice. */
    fun write(action: suspend () -> CommandResult, onDone: () -> Unit = {}) {
        scope.launch {
            val result = action()
            if (result.isSuccess) onDone() else errorMessage = result.error ?: s.shared("message_error_simple")
        }
    }

    val loadedConfig = config
    val loadedItems = items
    if (loadedConfig == null || loadedItems == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadFailed) UI.Text(s.shared("message_error_simple"), TextType.ERROR) else UI.LoadingIndicator()
        }
        return
    }

    val settings = ToolConfigSettings.read(ListToolType, loadedConfig, context)
    val removeWhenChecked = settings.boolean(ListToolType.REMOVE_WHEN_CHECKED)
    val fields: List<FieldDefinition> = loadedConfig.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
    val shown = ListItems.shown(loadedItems)
    val (checked, left) = shown.partition { it.isChecked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UI.PageHeader(
            title = settings.string("name")!!,
            subtitle = settings.string("description")?.takeIf { it.isNotBlank() },
            icon = settings.string("icon_name")!!,
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onNavigateBack,
            onRightClick = onConfigureClick
        )

        // Adding an item, at the top: its name; the list's fields take their default values
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                UI.FormField(
                    label = s.tool("add_item_label"),
                    value = typed,
                    onChange = { typed = it },
                    fieldType = FieldType.TEXT,
                    required = false
                )
            }
            UI.ActionButton(
                action = ButtonAction.ADD,
                display = ButtonDisplay.ICON,
                size = Size.S,
                enabled = typed.isNotBlank(),
                onClick = { write({ ListItems.add(coordinator, toolInstanceId, typed, fields.defaultValues()) }) { typed = "" } }
            )
        }

        if (shown.isEmpty()) UI.Text(s.tool("list_empty"), TextType.CAPTION)

        // The items left, in the manual order: the position written is the one the service
        // needs to put the item where it was dropped among them
        UI.ReorderableColumn(
            items = left,
            key = { it.id },
            spacing = 4.dp,
            onMove = { from, to ->
                write({ ListItems.move(coordinator, left[from], ListItems.positionForMove(left, from, to)) })
            }
        ) { _, item ->
            ItemRow(item, loadedConfig, onCheck = { write({ ListItems.setChecked(coordinator, item, it, removeWhenChecked) }) }, onOpen = { openItemId = item.id }) {
                DragHandle()
            }
        }

        // The checked items, greyed, in the order they were checked, parted from the others;
        // not reordered
        if (checked.isNotEmpty()) {
            if (left.isNotEmpty()) UI.Divider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                checked.forEach { item ->
                    ItemRow(item, loadedConfig, onCheck = { write({ ListItems.setChecked(coordinator, item, it, removeWhenChecked) }) }, onOpen = { openItemId = item.id })
                }
            }
        }

        // Unchecking them all, while some are checked: in a list that removes what is checked,
        // there never are
        if (checked.isNotEmpty()) {
            UI.Button(type = ButtonType.DEFAULT, onClick = { write({ ListItems.uncheckAll(coordinator, checked) }) }) {
                UI.Text(s.tool("action_uncheck_all"), TextType.LABEL)
            }
        }
    }

    // The item open is kept by its id and found again in the items loaded, so the dialog comes
    // back after a rotation once they are loaded; an item deleted meanwhile closes it
    val openItem = openItemId?.let { id -> loadedItems.find { it.id == id } }
    if (openItem != null) {
        ListItemDialog(
            item = openItem,
            fields = fields,
            onSave = { name, extra -> write({ ListItems.update(coordinator, openItem, name, extra) }) { openItemId = null } },
            onDelete = { write({ ListItems.delete(coordinator, openItem) }) { openItemId = null } },
            onCancel = { openItemId = null }
        )
    }
    LaunchedEffect(loadedItems, openItemId) {
        if (openItemId != null && loadedItems.none { it.id == openItemId }) openItemId = null
    }
}

/**
 * One item: its box, its name and, compact below it, the list's fields it shows; the name opens
 * it. A checked item is greyed.
 */
@Composable
private fun ItemRow(
    item: ListItem,
    config: JSONObject,
    onCheck: (Boolean) -> Unit,
    onOpen: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        UI.Checkbox(checked = item.isChecked, onCheckedChange = onCheck)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(vertical = 8.dp)
        ) {
            UI.Text(item.name, if (item.isChecked) TextType.CAPTION else TextType.BODY)
            com.assistant.core.fields.CustomFieldsDisplay(ListToolType, config, item.extra, com.assistant.core.fields.FieldsLayout.COMPACT, context)
        }
        trailing()
    }
}
