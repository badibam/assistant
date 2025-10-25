package com.assistant.core.ai.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.mapData
import com.assistant.core.coordinator.executeWithLoading
import com.assistant.core.ai.providers.AIProviderRegistry
import kotlinx.coroutines.launch

/**
 * Provider info from service
 */
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val isConfigured: Boolean,
    val isActive: Boolean
)

/**
 * AI Providers configuration screen
 *
 * Displays list of available AI providers with:
 * - Simple click on unconfigured → open config dialog
 * - Simple click on configured inactive → activate provider
 * - Simple click on active → no action
 * - Long click → edit configuration
 * - Configure button (icon) on the right → edit configuration (alternative to long click)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AIProvidersScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()

    // Load providers via command pattern
    var providers by remember { mutableStateOf<List<ProviderInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Configuration state - provider ID being configured
    var configuringProviderId by rememberSaveable { mutableStateOf<String?>(null) }

    // Load providers on first composition
    LaunchedEffect(Unit) {
        coordinator.executeWithLoading(
            operation = "ai_provider_config.list",
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            providers = result.mapData("providers") { map ->
                ProviderInfo(
                    id = map["id"] as String,
                    displayName = map["displayName"] as String,
                    isConfigured = map["isConfigured"] as Boolean,
                    isActive = map["isActive"] as Boolean
                )
            }

            // Auto-activate first configured provider if no active provider
            val hasActiveProvider = providers.any { it.isActive }
            val firstConfigured = providers.firstOrNull { it.isConfigured }

            if (!hasActiveProvider && firstConfigured != null) {
                coordinator.processUserAction(
                    "ai_provider_config.set_active",
                    mapOf("providerId" to firstConfigured.id)
                )
                // Reload to reflect the change
                coordinator.executeWithLoading(
                    operation = "ai_provider_config.list",
                    onLoading = { isLoading = it },
                    onError = { error -> errorMessage = error }
                )?.let { reloadResult ->
                    providers = reloadResult.mapData("providers") { map ->
                        ProviderInfo(
                            id = map["id"] as String,
                            displayName = map["displayName"] as String,
                            isConfigured = map["isConfigured"] as Boolean,
                            isActive = map["isActive"] as Boolean
                        )
                    }
                }
            }
        }
    }

    // Function to reload providers after operations
    val reloadProviders = {
        coroutineScope.launch {
            coordinator.executeWithLoading(
                operation = "ai_provider_config.list",
                onLoading = { isLoading = it },
                onError = { error -> errorMessage = error }
            )?.let { result ->
                providers = result.mapData("providers") { map ->
                    ProviderInfo(
                        id = map["id"] as String,
                        displayName = map["displayName"] as String,
                        isConfigured = map["isConfigured"] as Boolean,
                        isActive = map["isActive"] as Boolean
                    )
                }
            }
        }
    }

    // Show provider config screen if provider selected
    configuringProviderId?.let { providerId ->
        val registry = remember { AIProviderRegistry(context) }
        val provider = remember(providerId) { registry.getProvider(providerId) }

        provider?.let {
            // Get existing config if any
            var existingConfig by remember { mutableStateOf("{}") }
            var isLoadingConfig by remember { mutableStateOf(true) }

            LaunchedEffect(providerId) {
                coordinator.executeWithLoading(
                    operation = "ai_provider_config.get",
                    params = mapOf("providerId" to providerId),
                    onLoading = { },
                    onError = { /* Provider not configured yet, that's ok */ }
                )?.let { result ->
                    existingConfig = result.data?.get("config") as? String ?: "{}"
                }
                isLoadingConfig = false
            }

            // Render provider config screen after config is loaded
            if (!isLoadingConfig) {
                provider.getConfigScreen(
                    config = existingConfig,
                    onSave = { configJson ->
                        coroutineScope.launch {
                            val result = coordinator.processUserAction(
                                "ai_provider_config.set",
                                mapOf(
                                    "providerId" to providerId,
                                    "config" to configJson
                                )
                            )

                            if (result.status == CommandStatus.SUCCESS) {
                                configuringProviderId = null
                                reloadProviders()
                            } else {
                                errorMessage = result.error ?: s.shared("error_unknown")
                            }
                        }
                    },
                    onCancel = {
                        // Just close the config screen without saving
                        configuringProviderId = null
                    },
                    onReset = {
                        // Delete provider configuration from DB
                        coroutineScope.launch {
                            val result = coordinator.processUserAction(
                                "ai_provider_config.delete",
                                mapOf("providerId" to providerId)
                            )

                            if (result.status == CommandStatus.SUCCESS) {
                                configuringProviderId = null
                                reloadProviders()
                            } else {
                                errorMessage = result.error ?: s.shared("error_unknown")
                            }
                        }
                    }
                )
            } else {
                // Show loading while config is being fetched
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
        }

        return // Exit when showing config
    }

    // Main content: providers list
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("settings_ai_providers"),
            subtitle = null,
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = { }
        )

        // Error display (toast pattern)
        errorMessage?.let { error ->
            LaunchedEffect(error) {
                UI.Toast(context, error, Duration.LONG)
                errorMessage = null
            }
        }

        // Content based on loading state
        when {
            isLoading -> {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
            providers.isEmpty() -> {
                UI.Text(
                    text = s.shared("message_no_providers"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                // Providers list
                providers.forEach { provider ->
                    UI.Card(
                        type = CardType.DEFAULT,
                        size = Size.M
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            // Main content (clickable)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = {
                                            // Simple click behavior
                                            when {
                                                !provider.isConfigured -> {
                                                    // Not configured → open config
                                                    configuringProviderId = provider.id
                                                }
                                                !provider.isActive -> {
                                                    // Configured but inactive → activate
                                                    coroutineScope.launch {
                                                        coordinator.processUserAction(
                                                            "ai_provider_config.set_active",
                                                            mapOf("providerId" to provider.id)
                                                        )
                                                        reloadProviders()
                                                    }
                                                }
                                                // else: already active → do nothing
                                            }
                                        },
                                        onLongClick = {
                                            // Long click → edit config
                                            configuringProviderId = provider.id
                                        }
                                    )
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Provider name
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        // Active indicator
                                        val indicator = if (provider.isActive) "●" else "○"
                                        UI.Text(
                                            text = indicator,
                                            type = TextType.TITLE
                                        )

                                        UI.Text(
                                            text = provider.displayName,
                                            type = TextType.SUBTITLE
                                        )
                                    }

                                    // Status row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Configuration status
                                        val configStatus = if (provider.isConfigured) {
                                            s.shared("provider_status_configured")
                                        } else {
                                            s.shared("provider_status_not_configured")
                                        }

                                        UI.Text(
                                            text = configStatus,
                                            type = TextType.CAPTION
                                        )

                                        // Active status (only if configured)
                                        if (provider.isConfigured) {
                                            val activeStatus = if (provider.isActive) {
                                                s.shared("provider_status_active")
                                            } else {
                                                s.shared("provider_status_inactive")
                                            }

                                            UI.Text(
                                                text = activeStatus,
                                                type = TextType.CAPTION
                                            )
                                        }
                                    }
                                }
                            }

                            // Configure button (icon only)
                            UI.ActionButton(
                                action = ButtonAction.CONFIGURE,
                                display = ButtonDisplay.ICON,
                                size = Size.M,
                                onClick = {
                                    // Open config screen
                                    configuringProviderId = provider.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
