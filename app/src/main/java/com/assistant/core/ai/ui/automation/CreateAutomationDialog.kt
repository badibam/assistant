package com.assistant.core.ai.ui.automation

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
    var name by remember {
        mutableStateOf(
            if (isEditMode) automation?.get("name") as? String ?: ""
            else ""
        )
    }
    var selectedProvider by remember {
        mutableStateOf(
            if (isEditMode) automation?.get("provider_id") as? String
            else null
        )
    }
    var selectedGroup by remember {
        mutableStateOf(
            if (isEditMode) automation?.get("group") as? String
            else preSelectedGroup
        )
    }

    // Load zone tool_groups
    var zoneToolGroups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(zoneId) {
        try {
            val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
            if (result.status == CommandStatus.SUCCESS) {
                val zoneData = result.data?.get("zone") as? Map<*, *>
                val toolGroupsJson = zoneData?.get("tool_groups") as? String
                zoneToolGroups = if (toolGroupsJson != null) {
                    try {
                        val jsonArray = org.json.JSONArray(toolGroupsJson)
                        (0 until jsonArray.length()).map { jsonArray.getString(it) }
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
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
                    provider["isConfigured"] as? Boolean ?: false
                }

                // Auto-select first provider if available
                if (providers.isNotEmpty() && selectedProvider == null) {
                    selectedProvider = providers.first()["id"] as? String
                }
            }
        } catch (e: Exception) {
            LogManager.aiUI("Failed to load providers: ${e.message}", "ERROR", e)
            errorMessage = s.shared("error_provider_not_found").format("")
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

                        if (automationId == null || seedSessionId == null) {
                            errorMessage = s.shared("error_automation_not_found")
                            LogManager.aiUI("Invalid automation data for edit", "ERROR")
                            return@launch
                        }

                        // Update automation (name + provider + group)
                        val updateParams = mutableMapOf<String, Any>(
                            "automation_id" to automationId,
                            "name" to name,
                            "provider_id" to selectedProvider!!
                        )
                        // Add group if present (empty string means explicitly ungrouped)
                        if (selectedGroup != null) {
                            updateParams["group"] = selectedGroup!!
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
                                    "sessionId" to seedSessionId,
                                    "providerId" to selectedProvider!!
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
                                "providerId" to selectedProvider!!
                            )
                        )

                        if (createSessionResult.status != CommandStatus.SUCCESS) {
                            errorMessage = createSessionResult.error ?: s.shared("ai_error_create_session").format("")
                            LogManager.aiUI("Failed to create SEED session: ${createSessionResult.error}", "ERROR")
                            return@launch
                        }

                        val seedSessionId = createSessionResult.data?.get("sessionId") as? String
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
                        // Add group if present (empty string means explicitly ungrouped)
                        if (selectedGroup != null) {
                            createParams["group"] = selectedGroup!!
                        }

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

            // Name field
            UI.FormField(
                label = s.shared("label_name"),
                value = name,
                onChange = { name = it },
                fieldType = FieldType.TEXT,
                required = true
            )

            // Provider selection
            if (isLoadingProviders) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.CAPTION
                )
            } else if (providers.isEmpty()) {
                UI.Text(
                    text = s.shared("message_no_providers"),
                    type = TextType.CAPTION
                )
            } else {
                val providerNames = providers.map {
                    (it["displayName"] as? String) ?: (it["id"] as? String) ?: "Unknown"
                }
                val selectedProviderName = selectedProvider?.let { id ->
                    providers.find { (it["id"] as? String) == id }
                        ?.let { (it["displayName"] as? String) ?: id }
                } ?: providerNames.firstOrNull()

                UI.FormSelection(
                    label = s.shared("ai_provider_claude_display_name"),
                    options = providerNames,
                    selected = selectedProviderName ?: "",
                    onSelect = { selectedName ->
                        val index = providerNames.indexOf(selectedName)
                        if (index >= 0) {
                            selectedProvider = providers[index]["id"] as? String
                        }
                    },
                    required = true
                )
            }

            // Group selection
            com.assistant.core.ui.components.GroupSelector(
                availableGroups = zoneToolGroups,
                selectedGroup = selectedGroup,
                onGroupSelected = { selectedGroup = it },
                label = s.shared("label_group")
            )

            // Info text (changes based on mode)
            UI.Text(
                text = if (isEditMode)
                    "Modification du nom et du fournisseur de l'automation."
                else
                    "Après création, vous pourrez configurer le message et l'horaire de l'automation.",
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
