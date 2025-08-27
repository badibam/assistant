package com.assistant.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
 * Uses hybrid system: Compose layouts + UI.* visual components
 */
@Composable
fun CreateZoneScreen(
    existingZone: Zone? = null,
    onCancel: () -> Unit = {},
    onCreate: ((String, String?, String?) -> Unit)? = null,
    onUpdate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
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
    
    // Delete confirmation state
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val isEditing = existingZone != null
    
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title with delete button for editing
        if (isEditing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UI.Text(
                    text = "Modifier Zone",
                    type = TextType.TITLE
                )
                
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = {
                        showDeleteDialog = true
                    }
                ) {
                    UI.Text(
                        text = "Supprimer",
                        type = TextType.LABEL
                    )
                }
            }
        } else {
            UI.Text(
                text = "Créer Zone",
                type = TextType.TITLE
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Form fields
        Column {
            // Name field (required)
            UI.Text(
                text = "Nom de la zone",
                type = TextType.SUBTITLE
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
                Spacer(modifier = Modifier.height(4.dp))
                UI.Text(
                    text = "Le nom est obligatoire",
                    type = TextType.ERROR
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Description field (optional)
            UI.Text(
                text = "Description",
                type = TextType.SUBTITLE
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            UI.TextField(
                type = TextFieldType.TEXT,
                value = description,
                onChange = { description = it },
                placeholder = "Description (optionnel)"
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Button(
                    type = ButtonType.SECONDARY,
                    onClick = onCancel
                ) {
                    UI.Text(
                        text = "Annuler",
                        type = TextType.LABEL
                    )
                }
                
                UI.Button(
                    type = ButtonType.PRIMARY,
                    onClick = {
                        if (name.trim().isNotEmpty()) {
                            if (isEditing) {
                                // Handle update
                                coroutineScope.launch {
                                    try {
                                        val result = coordinator.processUserAction(
                                            "update->zone",
                                            mapOf(
                                                "zone_id" to existingZone!!.id,
                                                "name" to name.trim(),
                                                "description" to (description.takeIf { it.isNotBlank() } ?: "")
                                            )
                                        )
                                        onUpdate?.invoke()
                                    } catch (e: Exception) {
                                        // TODO: Error handling
                                    }
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
        
        // Delete confirmation dialog
        if (showDeleteDialog && isEditing) {
            UI.Dialog(
                type = DialogType.DANGER,
                onConfirm = {
                    coroutineScope.launch {
                        try {
                            val result = coordinator.processUserAction(
                                "delete->zone",
                                mapOf(
                                    "zone_id" to existingZone!!.id
                                )
                            )
                            showDeleteDialog = false
                            onDelete?.invoke()
                        } catch (e: Exception) {
                            // TODO: Error handling
                            showDeleteDialog = false
                        }
                    }
                },
                onCancel = {
                    showDeleteDialog = false
                }
            ) {
                UI.Text(
                    "Êtes-vous sûr de vouloir supprimer la zone \"${existingZone?.name}\" ? Cette action est irréversible.",
                    TextType.BODY
                )
            }
        }
    }
}