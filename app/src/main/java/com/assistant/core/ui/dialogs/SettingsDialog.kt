package com.assistant.core.ui.dialogs

import androidx.compose.foundation.clickable
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
 * Settings option item
 */
data class SettingsOption(
    val id: String,
    val label: String,
    val description: String? = null
)

/**
 * SettingsDialog - Central settings menu
 *
 * Displays a list of available settings pages.
 * Click on an option navigates to the corresponding settings screen.
 *
 * Usage:
 * ```
 * if (showSettings) {
 *     SettingsDialog(
 *         onDismiss = { showSettings = false },
 *         onOptionSelected = { optionId ->
 *             when (optionId) {
 *                 "ai_providers" -> showAIProviders = true
 *                 // ... other options
 *             }
 *             showSettings = false
 *         }
 *     )
 * }
 * ```
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scrollState = rememberScrollState()

    // Available settings options
    val options = remember {
        listOf(
            SettingsOption(
                id = "history",
                label = s.shared("settings_history"),
                description = s.shared("settings_history_description")
            ),
            SettingsOption(
                id = "ai_providers",
                label = s.shared("settings_ai_providers"),
                description = s.shared("settings_ai_providers_description")
            ),
            SettingsOption(
                id = "transcription",
                label = s.shared("settings_transcription"),
                description = s.shared("settings_transcription_description")
            ),
            SettingsOption(
                id = "format",
                label = s.shared("settings_format"),
                description = s.shared("settings_format_description")
            ),
            SettingsOption(
                id = "ai_limits",
                label = s.shared("settings_ai_limits"),
                description = s.shared("settings_ai_limits_description")
            ),
            SettingsOption(
                id = "validation",
                label = s.shared("settings_validation"),
                description = s.shared("settings_validation_description")
            ),
            SettingsOption(
                id = "ui",
                label = s.shared("settings_ui"),
                description = s.shared("settings_ui_description")
            ),
            SettingsOption(
                id = "data",
                label = s.shared("settings_data"),
                description = s.shared("settings_data_description")
            ),
            SettingsOption(
                id = "logs",
                label = s.shared("settings_logs"),
                description = s.shared("settings_logs_description")
            )
        )
    }

    UI.Dialog(
        type = DialogType.INFO,
        onConfirm = onDismiss,
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dialog title
            UI.Text(
                text = s.shared("settings_title"),
                type = TextType.TITLE
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Settings options list
            options.forEach { option ->
                UI.Card(
                    type = CardType.DEFAULT,
                    size = Size.S
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option.id) }
                            .padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            UI.Text(
                                text = option.label,
                                type = TextType.SUBTITLE
                            )

                            option.description?.let { desc ->
                                UI.Text(
                                    text = desc,
                                    type = TextType.CAPTION
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
