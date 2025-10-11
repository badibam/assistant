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
 * UI Settings Screen (STUB)
 *
 * Configuration screen for user interface appearance and behavior
 *
 * Features to be implemented:
 *
 * Theme Management:
 * - Theme selection (light/dark/auto-system)
 * - Custom theme variants (palettes)
 *
 * Uses AppConfigService with category "ui" (to be created)
 */
@Composable
fun UISettingsScreen(
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
            title = s.shared("settings_ui"),
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

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                UI.Text(
                    text = s.shared("settings_stub_description"),
                    type = TextType.BODY
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Theme section
                UI.Text(
                    text = "Gestion des thèmes :",
                    type = TextType.SUBTITLE
                )

                val themeFeatures = listOf(
                    "• Sélection thème (clair/sombre/auto-système)",
                    "• Variantes et palettes de thèmes personnalisées",
                )

                themeFeatures.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }
            }
        }
    }
}
