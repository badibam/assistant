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
 * AI Limits Settings Screen (STUB)
 *
 * Configuration screen for AI autonomous rounds limits
 *
 */
@Composable
fun AILimitsSettingsScreen(
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
            title = s.shared("settings_ai_limits"),
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

                // CHAT section
                UI.Text(
                    text = "Limites CHAT :",
                    type = TextType.SUBTITLE
                )

                val chatFeatures = listOf(
                    "• Max itérations queries consécutives",
                    "• Max tentatives actions échouées consécutives",
                    "• Max tentatives erreurs de format consécutives ",
                    "• Max rounds autonomes totaux",
                    "• Timeout inactivité avant éviction par automation"
                )

                chatFeatures.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // AUTOMATION section
                UI.Text(
                    text = "Limites AUTOMATION :",
                    type = TextType.SUBTITLE
                )

                val automationFeatures = listOf(
                    "• Max itérations queries consécutives",
                    "• Max tentatives actions échouées consécutives",
                    "• Max tentatives erreurs de format consécutives",
                    "• Max rounds autonomes totaux",
                    "• Watchdog timeout session"
                )

                automationFeatures.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Advanced section
                UI.Text(
                    text = "Paramètres tokens (avancé) :",
                    type = TextType.SUBTITLE
                )

                val advancedFeatures = listOf(
                    "• Max tokens par query",
                    "• Max tokens prompt"
                )

                advancedFeatures.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }
            }
        }
    }
}
