package com.assistant.core.ai.ui.automation

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * CreateAutomationDialog - Create or edit automation (name + provider)
 *
 * Flow CREATE (automation == null):
 * 1. User fills name + provider
 * 2. On confirm:
 *    - Create SEED session (empty message)
 *    - Create automation linking to SEED session
 *    - Return seedSessionId for navigation to SEED editor
 *
 * Flow EDIT (automation != null):
 * 1. Pre-fill name + provider from existing automation
 * 2. On confirm:
 *    - Update automation name + provider
 *    - Update SEED session provider if changed
 *    - Return existing seedSessionId
 *
 * Usage:
 * - CREATE: ZoneScreen automation section "Add" button
 * - EDIT: AIScreen SeedMode "Configure" button
 */
@Composable
fun CreateAutomationDialog(
    zoneId: String,
    zoneName: String,
    automation: Map<String, Any>? = null,  // null = CREATE mode, not null = EDIT mode
    preSelectedGroup: String? = null,      // Pre-selected group (can be changed by user)
    onDismiss: () -> Unit,
    onSuccess: (seedSessionId: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }

    val isEditMode = automation != null

    // Form states - initialize with existing values in EDIT mode
    // Name, provider and group, as the automation's declaration describes them (AutomationSettings)
    var settings by rememberSaveable(stateSaver = com.assistant.core.ui.JsonObjectSaver) {
        mutableStateOf(org.json.JSONObject().apply {
            if (isEditMode) {
                (automation?.get("name") as? String)?.let { put("name", it) }
                (automation?.get("provider_id") as? String)?.let { put("provider_id", it) }
                (automation?.get("group") as? String)?.let { put("group", it) }
            } else {
                preSelectedGroup?.let { put("group", it) }
            }
        })
    }
    val name = settings.optString("name")
    val selectedProvider = settings.optString("provider_id").ifEmpty { null }
    val selectedGroup = settings.optString("group").ifEmpty { null }
    val settingsNodes = remember {
        com.assistant.core.ai.data.AutomationSettings.nodes(context).filter { node ->
            node is com.assistant.core.fields.settings.SettingNode.Field && node.definition.name in setOf("name", "provider_id", "group")
        }
    }
    var selectedZoneId by rememberSaveable {
        mutableStateOf(
            if (isEditMode) automation?.get("zone_id") as? String ?: zoneId
            else zoneId
        )
    }

    // Load all zones for zone selection (EDIT mode)
    var availableZones by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val zonesResult = coordinator.processUserAction("zones.list", emptyMap())
            if (zonesResult.status == CommandStatus.SUCCESS) {
                val zones = zonesResult.data?.get("zones") as? List<*>
                availableZones = zones?.mapNotNull { zoneMap ->
                    val map = zoneMap as? Map<*, *>
                    val id = map?.get("id") as? String
                    val name = map?.get("name") as? String
                    if (id != null && name != null) Pair(id, name) else null
                } ?: emptyList()
            }
        } catch (e: Exception) {
            LogManager.aiUI("Failed to load zones: ${e.message}", "ERROR", e)
        }
    }

    // Load zone tool_groups (updates when selectedZoneId changes)
    var zoneToolGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(selectedZoneId) {
        try {
            val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to selectedZoneId))
            if (result.status == CommandStatus.SUCCESS) {
                val zoneData = result.data?.get("zone") as? Map<*, *>
                zoneToolGroups = (zoneData?.get("tool_groups") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
            }
        } catch (e: Exception) {
            LogManager.aiUI("Failed to load zone tool_groups: ${e.message}", "ERROR", e)
        }
    }

    // Available providers
    var providers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoadingProviders by remember { mutableStateOf(true) }

    // UI states
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    // Load available providers
    LaunchedEffect(Unit) {
        try {
            val result = coordinator.processUserAction("ai_provider_config.list", emptyMap())
            if (result.status == CommandStatus.SUCCESS) {
                @Suppress("UNCHECKED_CAST")
                val allProviders = result.data?.get("providers") as? List<Map<String, Any>> ?: emptyList()

                // Filter only configured providers
                providers = allProviders.filter { provider ->
                    provider["is_configured"] as? Boolean ?: false
                }

                // Auto-select first provider if available
                if (providers.isNotEmpty() && selectedProvider == null) {
                    settings = org.json.JSONObject(settings.toString()).put("provider_id", providers.first()["id"] as String)
                }
            }
        } catch (e: Exception) {
            LogManager.aiUI("Failed to load providers: ${e.message}", "ERROR", e)
            errorMessage = s.shared("error_load_failed")
        } finally {
            isLoadingProviders = false
        }
    }

    UI.Dialog(
        type = if (isEditMode) DialogType.EDIT else DialogType.CREATE,
        onConfirm = {
            if (name.isBlank()) {
                errorMessage = s.shared("ai_error_param_name_required")
                return@Dialog
            }
            if (selectedProvider == null) {
                errorMessage = s.shared("error_param_provider_id_required")
                return@Dialog
            }

            scope.launch {
                try {
                    isCreating = true

                    if (isEditMode) {
                        // EDIT MODE: Update existing automation
                        val automationId = automation?.get("id") as? String
                        val seedSessionId = automation?.get("seed_session_id") as? String
                        val currentProviderId = automation?.get("provider_id") as? String
                        val currentZoneId = automation?.get("zone_id") as? String

                        if (automationId == null || seedSessionId == null) {
                            errorMessage = s.shared("error_automation_not_found")
                            LogManager.aiUI("Invalid automation data for edit", "ERROR")
                            return@launch
                        }

                        // Update automation (name + provider + group + zone); no group is sent
                        // as null, which removes it
                        val updateParams = mutableMapOf<String, Any>(
                            "automation_id" to automationId,
                            "name" to name,
                            "provider_id" to selectedProvider!!,
                            "group" to (selectedGroup ?: org.json.JSONObject.NULL)
                        )
                        // Add zone_id if changed
                        if (selectedZoneId != currentZoneId) {
                            updateParams["zone_id"] = selectedZoneId
                        }

                        val updateResult = coordinator.processUserAction("automations.update", updateParams)

                        if (updateResult.status != CommandStatus.SUCCESS) {
                            errorMessage = updateResult.error ?: s.shared("error_automation_update_failed")
                            LogManager.aiUI("Failed to update automation: ${updateResult.error}", "ERROR")
                            return@launch
                        }

                        // If provider changed, update SEED session provider
                        if (selectedProvider != currentProviderId) {
                            val updateSessionResult = coordinator.processUserAction(
                                "ai_sessions.update_session",
                                mapOf(
                                    "session_id" to seedSessionId,
                                    "provider_id" to selectedProvider!!
                                )
                            )

                            if (updateSessionResult.status != CommandStatus.SUCCESS) {
                                LogManager.aiUI("Failed to update session provider: ${updateSessionResult.error}", "WARNING")
                                // Continue anyway - automation is updated
                            }
                        }

                        LogManager.aiUI("Updated automation: $automationId", "INFO")
                        onSuccess(seedSessionId)

                    } else {
                        // CREATE MODE: Original logic
                        // Step 1: Create SEED session
                        val sessionName = "$name (${zoneName})"
                        val createSessionResult = coordinator.processUserAction(
                            "ai_sessions.create_session",
                            mapOf(
                                "name" to sessionName,
                                "type" to "SEED",
                                "provider_id" to selectedProvider!!
                            )
                        )

                        if (createSessionResult.status != CommandStatus.SUCCESS) {
                            errorMessage = createSessionResult.error ?: s.shared("ai_error_create_session").format("")
                            LogManager.aiUI("Failed to create SEED session: ${createSessionResult.error}", "ERROR")
                            return@launch
                        }

                        val seedSessionId = createSessionResult.data?.get("session_id") as? String
                        if (seedSessionId == null) {
                            errorMessage = s.shared("ai_error_create_session").format("No session ID returned")
                            LogManager.aiUI("No session ID returned from create", "ERROR")
                            return@launch
                        }

                        LogManager.aiUI("Created SEED session: $seedSessionId", "DEBUG")

                        // Step 2: Create automation
                        val createParams = mutableMapOf<String, Any>(
                            "name" to name,
                            "zone_id" to zoneId,
                            "seed_session_id" to seedSessionId,
                            "provider_id" to selectedProvider!!,
                            "is_enabled" to true
                        )
                        selectedGroup?.let { createParams["group"] = it }

                        val createAutomationResult = coordinator.processUserAction("automations.create", createParams)

                        if (createAutomationResult.status != CommandStatus.SUCCESS) {
                            errorMessage = createAutomationResult.error ?: s.shared("service_error_automation").format("")
                            LogManager.aiUI("Failed to create automation: ${createAutomationResult.error}", "ERROR")
                            // TODO: Cleanup SEED session if automation creation fails
                            return@launch
                        }

                        val automationId = createAutomationResult.data?.get("automation_id") as? String
                        LogManager.aiUI("Created automation: $automationId", "INFO")

                        // Success - navigate to SEED editor
                        onSuccess(seedSessionId)
                    }
                } catch (e: Exception) {
                    errorMessage = s.shared("service_error_automation").format(e.message ?: "")
                    LogManager.aiUI("Exception ${if (isEditMode) "updating" else "creating"} automation: ${e.message}", "ERROR", e)
                } finally {
                    isCreating = false
                }
            }
        },
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title (changes based on mode)
            UI.Text(
                text = if (isEditMode)
                    s.shared("action_edit") + " " + s.shared("automation_display_name")
                else
                    s.shared("action_create") + " " + s.shared("automation_display_name"),
                type = TextType.TITLE
            )

            // Name, provider among the configured ones, group among the zone's
            com.assistant.core.fields.settings.SettingsForm(settingsNodes, settings, { settings = it }, context, mapOf(
                "provider_id" to object : com.assistant.core.fields.settings.SettingEditor {
                    @Composable
                    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                        when {
                            isLoadingProviders -> UI.Text(s.shared("message_loading"), TextType.CAPTION)
                            providers.isEmpty() -> UI.Text(s.shared("message_no_providers"), TextType.CAPTION)
                            else -> {
                                val names = providers.associate { (it["display_name"] as String) to (it["id"] as String) }
                                UI.FormSelection(
                                    label = s.shared("label_ai_provider"),
                                    options = names.keys.toList(),
                                    selected = names.entries.find { it.value == value }?.key ?: "",
                                    onSelect = { onChange(names[it]) },
                                    required = true
                                )
                            }
                        }
                    }
                },
                "group" to object : com.assistant.core.fields.settings.SettingEditor {
                    @Composable
                    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                        com.assistant.core.ui.components.GroupSelector(
                            availableGroups = zoneToolGroups,
                            selectedGroup = value as? String,
                            onGroupSelected = { onChange(it) },
                            label = s.shared("label_group")
                        )
                    }
                }
            ))

            // Zone selection (only in EDIT mode)
            if (isEditMode && availableZones.isNotEmpty()) {
                val currentZoneName = availableZones.find { it.first == selectedZoneId }?.second ?: ""
                UI.FormSelection(
                    label = s.shared("label_zone"),
                    options = availableZones.map { it.second },
                    selected = currentZoneName,
                    onSelect = { selectedZoneName ->
                        val newZoneId = availableZones.find { it.second == selectedZoneName }?.first
                        if (newZoneId != null) {
                            selectedZoneId = newZoneId
                            // Reset group selection when zone changes (groups are zone-specific)
                            settings = org.json.JSONObject(settings.toString()).apply { remove("group") }
                        }
                    },
                    required = false
                )
            }

            // Info text (changes based on mode)
            UI.Text(
                text = if (isEditMode) s.shared("automation_dialog_edit_hint") else s.shared("automation_dialog_create_hint"),
                type = TextType.CAPTION
            )

            // Loading indicator
            if (isCreating) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY
                )
            }
        }
    }

    // Error toast
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}
