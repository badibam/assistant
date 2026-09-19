package com.assistant.core.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings

/**
 * Validation Settings Screen (STUB)
 *
 * Configuration screen for AI action validation requirements
 *
 * Features to be implemented:
 * - validateAppConfigChanges: require validation for app config modifications (default: false)
 * - validateZoneConfigChanges: require validation for all zone config modifications (default: false)
 * - validateToolConfigChanges: require validation for all tool config modifications (default: false)
 * - validateToolDataChanges: require validation for all tool data modifications (default: false)
 *
 * Validation Hierarchy:
 * The validation system uses OR logic across levels:
 * app > zone > tool > session > AI request
 * If ANY level requires validation, the AI will request user validation.
 *
 * Example:
 * - App level validateToolDataChanges = false
 * - Zone "Santé" validateToolDataChanges = true
 * - Tool "Poids" validateData = false
 * → AI will still request validation because zone level requires it
 *
 * Notes:
 * - Session level validation is configured per session (not in app config)
 * - AI request level validation is controlled by AI via validationRequest field
 * - Zone-specific validation overrides are configured in zone settings
 * - Tool-specific validation overrides are configured in tool settings
 *
 * Uses AppConfigService with category "validation_config"
 */
@Composable
fun ValidationSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scrollState = rememberScrollState()

    // Main content using hybrid system: Compose layouts + UI.* components
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back button
        UI.PageHeader(
            title = s.shared("settings_validation"),
            subtitle = null,
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = null
        )

        // Stub content
        UI.Card(
            type = CardType.DEFAULT,
            size = Size.M
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Coming soon message
                UI.Text(
                    text = s.shared("settings_stub_coming_soon"),
                    type = TextType.TITLE
                )
            }
        }
    }
}
