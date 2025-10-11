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
 * Data Management Settings Screen (STUB)
 *
 * Configuration screen for data backup, export, and import
 *
 */
@Composable
fun DataSettingsScreen(
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
            title = s.shared("settings_data"),
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

                // Backup section
                UI.Text(
                    text = "Gestion des sauvegardes :",
                    type = TextType.SUBTITLE
                )

                val backupFeatures = listOf(
                    "• Auto-sauvegarde activée/désactivée",
                    "• Fréquence auto-sauvegarde (quotidien/hebdo/mensuel)",
                    "• Emplacement sauvegarde (local/cloud)",
                    "• Politique de rétention (garder N dernières sauvegardes)",
                    "• Déclenchement manuel de sauvegarde",
                    "• Restauration depuis sauvegarde"
                )

                backupFeatures.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Import section
                UI.Text(
                    text = "Import de données :",
                    type = TextType.SUBTITLE
                )

                val importFeatures = listOf(
                    "• Import depuis sauvegarde",
                )

                importFeatures.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Data reset section
                UI.Text(
                    text = "Réinitialisation :",
                    type = TextType.SUBTITLE
                )

                val managementFeatures = listOf(
                    "• Réinitialiser toutes les données (avec confirmation)"
                )

                managementFeatures.forEach { feature ->
                    UI.Text(
                        text = feature,
                        type = TextType.BODY
                    )
                }
            }
        }
    }
}
