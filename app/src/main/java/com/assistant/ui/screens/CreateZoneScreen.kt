package com.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.database.AppDatabase
import com.assistant.core.database.entities.Zone
import com.assistant.R
import kotlinx.coroutines.launch

/**
 * Screen for creating/editing a zone
 * Form with name (required), description and color (optional)
 */
@Composable
fun CreateZoneScreen(
    existingZone: Zone? = null,
    onCancel: () -> Unit,
    onCreate: (name: String, description: String?, color: String?) -> Unit = { _, _, _ -> },
    onUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    
    // Form state - initialize with existing zone values if editing
    var name by remember { mutableStateOf(existingZone?.name ?: "") }
    var description by remember { mutableStateOf(existingZone?.description ?: "") }
    var color by remember { mutableStateOf(existingZone?.color ?: "") }
    
    // Validation state
    var nameError by remember { mutableStateOf(false) }
    
    val isEditing = existingZone != null
    
    
    UI.Screen(type = ScreenType.MAIN) {
        // Titre dynamique selon mode création/édition
        UI.Text(
            text = stringResource(if (isEditing) R.string.zone_config_title else R.string.create_zone_title),
            type = TextType.TITLE,
            semantic = "screen-title"
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Formulaire
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Champ nom (obligatoire)
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "name-field",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = stringResource(R.string.zone_name_label),
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        UI.TextField(
                            type = TextFieldType.STANDARD,
                            value = name,
                            onValueChange = { 
                                name = it
                                nameError = it.trim().isEmpty()
                            },
                            placeholder = stringResource(R.string.zone_name_placeholder),
                            semantic = "name-input",
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (nameError) {
                            UI.Spacer(modifier = Modifier.height(4.dp))
                            UI.Text(
                                text = stringResource(R.string.zone_name_error),
                                type = TextType.CAPTION,
                                semantic = "error-message"
                            )
                        }
                    }
                }
                
                // Champ description (optionnel)
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "description-field",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = stringResource(R.string.zone_description_label),
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        UI.TextField(
                            type = TextFieldType.MULTILINE,
                            value = description,
                            onValueChange = { description = it },
                            placeholder = stringResource(R.string.zone_description_placeholder),
                            semantic = "description-input",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Champ couleur (optionnel)
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "color-field",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = stringResource(R.string.zone_color_label),
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        UI.TextField(
                            type = TextFieldType.STANDARD,
                            value = color,
                            onValueChange = { color = it },
                            placeholder = stringResource(R.string.zone_color_placeholder),
                            semantic = "color-input",
                            modifier = Modifier.fillMaxWidth()
                        )
                        UI.Spacer(modifier = Modifier.height(4.dp))
                        UI.Text(
                            text = stringResource(R.string.zone_color_hint),
                            type = TextType.CAPTION,
                            semantic = "field-hint"
                        )
                    }
                }
                
                // Informations de timestamps (seulement en mode édition)
                if (isEditing && existingZone != null) {
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "timestamps-info",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UI.Text(
                                text = stringResource(R.string.zone_timestamps_label),
                                type = TextType.SUBTITLE,
                                semantic = "field-label"
                            )
                            
                            UI.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                UI.Text(
                                    text = stringResource(R.string.zone_created_at),
                                    type = TextType.CAPTION,
                                    semantic = "created-label"
                                )
                                UI.Text(
                                    text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(existingZone.created_at)),
                                    type = TextType.CAPTION,
                                    semantic = "created-value"
                                )
                            }
                            
                            UI.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                UI.Text(
                                    text = stringResource(R.string.zone_updated_at),
                                    type = TextType.CAPTION,
                                    semantic = "updated-label"
                                )
                                UI.Text(
                                    text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(existingZone.updated_at)),
                                    type = TextType.CAPTION,
                                    semantic = "updated-value"
                                )
                            }
                        }
                    }
                } else {
                    // Info text pour création
                    UI.Text(
                        text = stringResource(R.string.zone_help_text),
                        type = TextType.CAPTION,
                        semantic = "help-text"
                    )
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(32.dp))
        
        // Boutons d'action
        UI.Container(type = ContainerType.FLOATING) {
            UI.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Button(
                    type = ButtonType.SECONDARY,
                    semantic = "cancel-button",
                    onClick = { onCancel() },
                    modifier = Modifier.weight(1f)
                ) {
                    UI.Text(
                        text = stringResource(R.string.cancel),
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
                
                UI.Button(
                    type = ButtonType.PRIMARY,
                    semantic = if (isEditing) "update-button" else "create-button",
                    onClick = {
                        val trimmedName = name.trim()
                        if (trimmedName.isNotEmpty()) {
                            if (isEditing && existingZone != null) {
                                // Mode édition
                                coroutineScope.launch {
                                    try {
                                        val updatedZone = existingZone.copy(
                                            name = trimmedName,
                                            description = description.trim().ifEmpty { null },
                                            color = color.trim().ifEmpty { null },
                                            updated_at = System.currentTimeMillis()
                                        )
                                        database.zoneDao().updateZone(updatedZone)
                                        onUpdate()
                                    } catch (e: Exception) {
                                        // TODO: Gestion d'erreur
                                    }
                                }
                            } else {
                                // Mode création
                                onCreate(
                                    trimmedName,
                                    description.trim().ifEmpty { null },
                                    color.trim().ifEmpty { null }
                                )
                            }
                        } else {
                            nameError = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    UI.Text(
                        text = stringResource(if (isEditing) R.string.update else R.string.create_zone_button),
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
            }
        }
    }
}