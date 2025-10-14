package com.assistant.core.ai.ui.automation

import androidx.compose.foundation.layout.*
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
 * CreateAutomationDialog - Create new automation
 *
 * Flow:
 * 1. User fills name + provider
 * 2. On confirm:
 *    - Create SEED session (empty message)
 *    - Create automation linking to SEED session
 *    - Return seedSessionId for navigation to SEED editor
 *
 * Usage: ZoneScreen automation section "Add" button
 */
@Composable
fun CreateAutomationDialog(
    zoneId: String,
    zoneName: String,
    onDismiss: () -> Unit,
    onSuccess: (seedSessionId: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }

    // Form states
    var name by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<String?>(null) }

    // Available providers
    var providers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoadingProviders by remember { mutableStateOf(true) }

    // UI states
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    // Load available providers
    LaunchedEffect(Unit) {
        try {
            val result = coordinator.processUserAction("ai_provider_configs.list", emptyMap())
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
        type = DialogType.CREATE,
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

                    // Step 1: Create SEED session
                    val sessionName = "$name (${zoneName})"
                    val createSessionResult = coordinator.processUserAction(
                        "ai_sessions.create",
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
                    val createAutomationResult = coordinator.processUserAction(
                        "automations.create",
                        mapOf(
                            "name" to name,
                            "zone_id" to zoneId,
                            "seed_session_id" to seedSessionId,
                            "provider_id" to selectedProvider!!,
                            "is_enabled" to true
                        )
                    )

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
                } catch (e: Exception) {
                    errorMessage = s.shared("service_error_automation").format(e.message ?: "")
                    LogManager.aiUI("Exception creating automation: ${e.message}", "ERROR", e)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            UI.Text(
                text = s.shared("action_create") + " " + s.shared("automation_display_name"),
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

            // Info text
            UI.Text(
                text = "Après création, vous pourrez configurer le message et l'horaire de l'automation.",
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
