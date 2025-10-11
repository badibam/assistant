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
 * Format and Date Settings Screen (STUB)
 *
 * Configuration screen for temporal and display formats
 *
 * Features to be implemented:
 * - Week start day selection
 * - Day start hour
 * - Locale override (optional)
 * - Timezone selection
 * - Date format (dd/MM/yyyy, MM/dd/yyyy, etc.)
 * - Time format (12h/24h)
 *
 * Uses AppConfigService with category "format"
 */
@Composable
fun FormatSettingsScreen(
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
            title = s.shared("settings_format"),
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

                // Feature list
                val features = listOf(
                    "• Jour de début de semaine",
                    "• Heure de début de journée",
                    "• Override de locale (optionnel)",
                    "• Sélection de fuseau horaire",
                    "• Format d'affichage des dates (dd/MM/yyyy, MM/dd/yyyy, etc.)",
                    "• Format d'heure (12h/24h)"
                )

                features.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }
            }
        }
    }
}
