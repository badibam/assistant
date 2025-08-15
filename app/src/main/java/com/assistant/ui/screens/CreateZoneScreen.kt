package com.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.debug.DebugManager
import com.assistant.R

/**
 * Screen for creating a new zone
 * Form with name (required) and description (optional)
 */
@Composable
fun CreateZoneScreen(
    onCancel: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        DebugManager.debug("📝 CreateZoneScreen chargé")
    }
    
    UI.Screen(type = ScreenType.MAIN) {
        // Top bar avec navigation retour
        UI.TopBar(
            type = TopBarType.DEFAULT,
            title = stringResource(R.string.create_zone_title)
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Main form container
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name field (required)
                UI.Text(
                    text = stringResource(R.string.zone_name_label),
                    type = TextType.SUBTITLE,
                    semantic = "field-label"
                )
                
                UI.TextField(
                    type = TextFieldType.STANDARD,
                    value = name,
                    onValueChange = { name = it },
                    semantic = "zone-name-input",
                    placeholder = stringResource(R.string.zone_name_hint),
                    modifier = Modifier.fillMaxWidth()
                )
                
                UI.Spacer(modifier = Modifier.height(8.dp))
                
                // Description field (optional)
                UI.Text(
                    text = stringResource(R.string.zone_description_label),
                    type = TextType.SUBTITLE,
                    semantic = "field-label"
                )
                
                UI.TextField(
                    type = TextFieldType.MULTILINE,
                    value = description,
                    onValueChange = { description = it },
                    semantic = "zone-description-input",
                    placeholder = stringResource(R.string.zone_description_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                
                // Info text
                UI.Text(
                    text = stringResource(R.string.zone_help_text),
                    type = TextType.CAPTION,
                    semantic = "help-text"
                )
            }
        }
        
        UI.Spacer(modifier = Modifier.height(32.dp))
        
        // Action buttons
        UI.Container(type = ContainerType.FLOATING) {
            UI.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Button(
                    type = ButtonType.SECONDARY,
                    semantic = "cancel-button",
                    onClick = {
                        DebugManager.debugButtonClick("Annuler création zone")
                        onCancel()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    UI.Text(
                        text = stringResource(R.string.cancel),
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
                
                UI.Button(
                    type = if (name.isNotBlank()) ButtonType.PRIMARY else ButtonType.SECONDARY,
                    semantic = "create-button",
                    onClick = {
                        if (name.isNotBlank()) {
                            DebugManager.debugButtonClick("Créer zone: ${name.trim()}")
                            onCreate(
                                name.trim(),
                                if (description.isBlank()) null else description.trim()
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    UI.Text(
                        text = stringResource(R.string.create_zone_button),
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
            }
        }
    }
}