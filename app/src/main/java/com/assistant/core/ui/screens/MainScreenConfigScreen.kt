package com.assistant.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.mapData
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.commands.CommandStatus
import com.assistant.core.strings.Strings
import com.assistant.core.ui.components.GroupListEditor
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Configuration screen for MainScreen zone groups
 *
 * Allows user to:
 * - View current zone groups
 * - Add/delete/reorder zone groups
 * - Save changes to AppConfig (category MAIN_SCREEN)
 *
 * Uses GroupListEditor component for group management
 * Loads/saves via app_config.get_zone_groups and app_config.set_zone_groups operations
 */
@Composable
fun MainScreenConfigScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()

    // State for zone groups
    var zoneGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load zone groups on first composition
    LaunchedEffect(Unit) {
        LogManager.ui("Loading zone groups from app_config", "DEBUG")
        coordinator.executeWithLoading(
            operation = "app_config.get_zone_groups",
            params = emptyMap(),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            LogManager.ui("Result from get_zone_groups: ${result.data}", "DEBUG")
            // Extract zone_groups list from result (it's already a List<String>)
            val groups = (result.data?.get("zone_groups") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()
            LogManager.ui("Loaded ${groups.size} zone groups: $groups", "DEBUG")
            zoneGroups = groups
        }
    }

    // Save function
    val saveZoneGroups = {
        coroutineScope.launch {
            isSaving = true
            LogManager.ui("Attempting to save zone groups: $zoneGroups", "DEBUG")
            val result = coordinator.processUserAction(
                "app_config.set_zone_groups",
                mapOf("zone_groups" to zoneGroups)
            )
            isSaving = false

            if (result.status == CommandStatus.SUCCESS) {
                LogManager.ui("Zone groups saved successfully: $zoneGroups", "DEBUG")
                UI.Toast(context, s.shared("message_saved"), Duration.SHORT)
                onBack()
            } else {
                errorMessage = result.error ?: s.shared("error_operation_failed")
                LogManager.ui("Failed to save zone groups: ${result.error}", "ERROR")
            }
        }
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("label_main_screen_config"),
            subtitle = null,
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = { onBack() }
        )

        // Loading state or content
        if (isLoading) {
            UI.Text(
                text = s.shared("message_loading"),
                type = TextType.BODY,
                fillMaxWidth = true
            )
        } else {
            // Group editor card
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Description text
                    UI.Text(
                        text = s.shared("message_zone_groups_description"),
                        type = TextType.BODY
                    )

                    // Group list editor
                    GroupListEditor(
                        groups = zoneGroups,
                        onGroupsChange = { zoneGroups = it },
                        label = s.shared("label_zone_groups")
                    )
                }
            }

            // Form actions
            UI.FormActions {
                UI.ActionButton(
                    action = ButtonAction.SAVE,
                    enabled = !isSaving,
                    onClick = { saveZoneGroups() }
                )
                UI.ActionButton(
                    action = ButtonAction.CANCEL,
                    onClick = { onBack() }
                )
            }
        }
    }

    // Error handling with Toast
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}
