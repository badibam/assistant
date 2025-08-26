package com.assistant.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.database.entities.Zone
import com.assistant.core.coordinator.Coordinator
import kotlinx.coroutines.launch

/**
 * Screen for creating/editing a zone
 * Migrated to use new UI.* system
 */
@Composable
fun CreateZoneScreen(
    existingZone: Zone? = null,
    onCancel: () -> Unit = {},
    onCreate: ((String, String?, String?) -> Unit)? = null,
    onUpdate: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    
    // Form state - initialize with existing zone values if editing
    var name by remember { mutableStateOf(existingZone?.name ?: "") }
    var description by remember { mutableStateOf(existingZone?.description ?: "") }
    var color by remember { mutableStateOf("") } // Note: Zone entity doesn't have color field
    
    // Validation state
    var nameError by remember { mutableStateOf(false) }
    
    val isEditing = existingZone != null
    
    UI.Column {
        // Title
        UI.Text(
            text = if (isEditing) "Modifier Zone" else "Créer Zone",
            type = TextType.TITLE
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Form fields
        UI.Column {
            // Name field (required)
            UI.Text(
                text = "Nom de la zone",
                type = TextType.SUBTITLE
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            UI.TextField(
                type = TextFieldType.TEXT,
                value = name,
                onChange = { 
                    name = it
                    nameError = it.trim().isEmpty()
                },
                placeholder = "Nom de la zone"
            )
            
            if (nameError) {
                UI.Spacer(modifier = Modifier.height(4.dp))
                UI.Text(
                    text = "Le nom est obligatoire",
                    type = TextType.ERROR
                )
            }
            
            UI.Spacer(modifier = Modifier.height(16.dp))
            
            // Description field (optional)
            UI.Text(
                text = "Description",
                type = TextType.SUBTITLE
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            UI.TextField(
                type = TextFieldType.TEXT,
                value = description,
                onChange = { description = it },
                placeholder = "Description (optionnel)"
            )
            
            UI.Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            UI.Row {
                UI.Button(
                    type = ButtonType.CANCEL,
                    onClick = onCancel
                ) {
                    UI.Text(
                        text = "Annuler",
                        type = TextType.LABEL
                    )
                }
                
                UI.Spacer(modifier = Modifier.width(12.dp))
                
                UI.Button(
                    type = ButtonType.SAVE,
                    onClick = {
                        if (name.trim().isNotEmpty()) {
                            if (isEditing) {
                                // Handle update
                                coroutineScope.launch {
                                    // TODO: Implement update logic via coordinator
                                    onUpdate?.invoke()
                                }
                            } else {
                                // Handle create
                                onCreate?.invoke(name.trim(), description.takeIf { it.isNotBlank() }, null)
                            }
                        } else {
                            nameError = true
                        }
                    }
                ) {
                    UI.Text(
                        text = if (isEditing) "Sauvegarder" else "Créer",
                        type = TextType.LABEL
                    )
                }
            }
        }
    }
}