package com.assistant.core.ui.selectors

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.assistant.core.strings.Strings
import com.assistant.core.ui.UI
import com.assistant.core.ui.ButtonAction
import com.assistant.core.ui.CardType
import com.assistant.core.ui.TextType
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.coordinator.mapData
import com.assistant.core.commands.CommandStatus
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Duplicate Selector - Simplified selector for duplicating tools or automations
 *
 * Navigation flow:
 * 1. Zone selection
 * 2. Instance selection (filtered by type: tool/automation)
 *
 * @param type Type of element to duplicate (TOOL or AUTOMATION)
 * @param onDismiss Callback when dialog is dismissed
 * @param onConfirm Callback when selection is confirmed with (zoneId, instanceId)
 */
@Composable
fun DuplicateSelector(
    type: DuplicateType,
    onDismiss: () -> Unit,
    onConfirm: (zoneId: String, instanceId: String) -> Unit
) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }

    // Navigation state
    var currentStep by rememberSaveable { mutableStateOf(DuplicateStep.ZONE) }
    var selectedZoneId by rememberSaveable { mutableStateOf("") }
    var selectedZoneName by rememberSaveable { mutableStateOf("") }
    var selectedInstanceId by rememberSaveable { mutableStateOf<String?>(null) }

    // Data state
    var zones by remember { mutableStateOf<List<ZoneItem>>(emptyList()) }
    var instances by remember { mutableStateOf<List<InstanceItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load zones on mount
    LaunchedEffect(Unit) {
        coordinator.executeWithLoading(
            operation = "zones.list",
            params = emptyMap(),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            zones = result.mapData("zones") { map ->
                ZoneItem(
                    id = map["id"] as String,
                    name = map["name"] as String
                )
            }
            // Set selected zone name
            selectedZoneName = zones.find { it.id == selectedZoneId }?.name ?: ""
            LogManager.ui("DuplicateSelector: Loaded ${zones.size} zones", "DEBUG")
        }
    }

    // Load instances when zone is selected
    LaunchedEffect(selectedZoneId, currentStep) {
        if (currentStep == DuplicateStep.INSTANCE) {
            val (operation, dataKey) = when (type) {
                DuplicateType.TOOL -> "tools.list" to "tool_instances"
                DuplicateType.AUTOMATION -> "automations.list" to "automations"
            }

            coordinator.executeWithLoading(
                operation = operation,
                params = mapOf("zone_id" to selectedZoneId),
                onLoading = { isLoading = it },
                onError = { error -> errorMessage = error }
            )?.let { result ->
                instances = result.mapData(dataKey) { map ->
                    InstanceItem(
                        id = map["id"] as String,
                        name = map["name"] as String,
                        toolType = if (type == DuplicateType.TOOL) {
                            map["tooltype"] as? String
                        } else null
                    )
                }

                if (instances.isEmpty()) {
                    errorMessage = if (type == DuplicateType.TOOL) {
                        s.shared("duplicate_no_tools")
                    } else {
                        s.shared("duplicate_no_automations")
                    }
                }

                LogManager.ui("DuplicateSelector: Loaded ${instances.size} instances", "DEBUG")
            }
        }
    }

    // Error toast
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            UI.Toast(context, msg)
            errorMessage = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.L)
            ) {
                // Title
                UI.Text(
                    text = if (type == DuplicateType.TOOL) {
                        s.shared("duplicate_selector_title_tool")
                    } else {
                        s.shared("duplicate_selector_title_automation")
                    },
                    type = TextType.TITLE,
                    fillMaxWidth = true
                )

                // Breadcrumb
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The step being chosen is the last one of the path, after a chevron
                    if (currentStep == DuplicateStep.ZONE) {
                        UI.Icon(iconName = "chevron-right", size = 20.dp)
                        UI.Text(text = s.shared("duplicate_select_zone"), type = TextType.BODY)
                    } else {
                        UI.Text(text = selectedZoneName, type = TextType.BODY)
                        UI.Icon(iconName = "chevron-right", size = 20.dp)
                        UI.Text(text = s.shared("duplicate_select_instance"), type = TextType.BODY)
                    }
                }

                UI.Divider()

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                UI.LoadingIndicator()
                            }
                        }
                        currentStep == DuplicateStep.ZONE -> {
                            ZoneList(
                                zones = zones,
                                selectedZoneId = selectedZoneId,
                                onZoneSelected = { zone ->
                                    selectedZoneId = zone.id
                                    selectedZoneName = zone.name
                                    currentStep = DuplicateStep.INSTANCE
                                }
                            )
                        }
                        currentStep == DuplicateStep.INSTANCE -> {
                            if (instances.isEmpty() && !isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UI.Text(
                                        text = if (type == DuplicateType.TOOL) {
                                            s.shared("duplicate_no_tools")
                                        } else {
                                            s.shared("duplicate_no_automations")
                                        },
                                        type = TextType.BODY
                                    )
                                }
                            } else {
                                InstanceList(
                                    instances = instances,
                                    selectedInstanceId = selectedInstanceId,
                                    onInstanceSelected = { instance ->
                                        selectedInstanceId = instance.id
                                    }
                                )
                            }
                        }
                    }
                }

                UI.Divider()

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back/Cancel button
                    if (currentStep == DuplicateStep.INSTANCE) {
                        UI.ActionButton(
                            action = ButtonAction.BACK,
                            onClick = {
                                currentStep = DuplicateStep.ZONE
                                selectedInstanceId = null
                            }
                        )
                    } else {
                        UI.ActionButton(
                            action = ButtonAction.CANCEL,
                            onClick = onDismiss
                        )
                    }

                    // Confirm button
                    UI.ActionButton(
                        action = ButtonAction.CONFIRM,
                        enabled = selectedInstanceId != null,
                        onClick = {
                            selectedInstanceId?.let { instanceId ->
                                onConfirm(selectedZoneId, instanceId)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Zone list component
 */
@Composable
private fun ZoneList(
    zones: List<ZoneItem>,
    selectedZoneId: String,
    onZoneSelected: (ZoneItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.S)
    ) {
        items(zones) { zone ->
            UI.Card(type = CardType.DEFAULT, highlight = zone.id == selectedZoneId) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onZoneSelected(zone) }
                        .padding(UI.Space.L),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.Text(
                        text = zone.name,
                        type = TextType.BODY
                    )
                }
            }
        }
    }
}

/**
 * Instance list component
 */
@Composable
private fun InstanceList(
    instances: List<InstanceItem>,
    selectedInstanceId: String?,
    onInstanceSelected: (InstanceItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.S)
    ) {
        items(instances) { instance ->
            UI.Card(type = CardType.DEFAULT, highlight = instance.id == selectedInstanceId) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInstanceSelected(instance) }
                        .padding(UI.Space.L),
                    verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                ) {
                    UI.Text(
                        text = instance.name,
                        type = TextType.BODY
                    )
                    instance.toolType?.let { toolType ->
                        UI.Text(
                            text = toolType,
                            type = TextType.CAPTION
                        )
                    }
                }
            }
        }
    }
}

/**
 * Duplicate type enum
 */
enum class DuplicateType {
    TOOL,
    AUTOMATION
}

/**
 * Navigation steps
 */
private enum class DuplicateStep {
    ZONE,
    INSTANCE
}

/**
 * Data classes
 */
private data class ZoneItem(
    val id: String,
    val name: String
)

private data class InstanceItem(
    val id: String,
    val name: String,
    val toolType: String? = null
)
