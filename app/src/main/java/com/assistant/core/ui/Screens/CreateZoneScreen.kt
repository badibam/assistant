package com.assistant.core.ui.Screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
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
    
    // Form state - persistent across orientation changes
    var name by rememberSaveable(existingZone) { mutableStateOf(existingZone?.name.orEmpty()) }
    var description by rememberSaveable(existingZone) { mutableStateOf(existingZone?.description.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(String()) } // Note: Zone entity doesn't have color field
    
    val isEditing = existingZone != null
    
    // Handle save logic
    val handleSave = {
        // Validation via SchemaValidator avant sauvegarde
        val zoneData = mapOf(
            "name" to name.trim(),
            "description" to description.trim()
        )
        
        try {
            val zoneProvider = com.assistant.core.schemas.ZoneSchemaProvider.create(context)
            val validation = com.assistant.core.validation.SchemaValidator.validate(
                zoneProvider,
                zoneData,
                context,
                useDataSchema = false
            )
            
            if (validation.isValid) {
                // Validation réussie, procéder à la sauvegarde
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
                // Validation échouée, afficher erreur via Toast
                Toast.makeText(context, validation.errorMessage, Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            // Erreur technique de validation
            Toast.makeText(context, "Erreur de validation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title - centré (pattern MainScreen)
        UI.Text(
            text = if (isEditing) "Modifier Zone" else "Créer Zone",
            type = TextType.TITLE,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        
        // Form fields
        UI.FormField(
            label = "Nom de la zone",
            value = name,
            onChange = { name = it },
            fieldType = FieldType.TEXT,
            required = true
        )
        
        UI.FormField(
            label = "Description",
            value = description,
            onChange = { description = it },
            fieldType = FieldType.TEXT_MEDIUM,
            required = false
        )
        
        // Actions
        UI.FormActions {
            UI.ActionButton(
                action = if (isEditing) ButtonAction.SAVE else ButtonAction.CREATE,
                onClick = handleSave
            )
            
            UI.ActionButton(
                action = ButtonAction.CANCEL,
                onClick = onCancel
            )
            
            if (isEditing && onDelete != null) {
                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    requireConfirmation = true,
                    confirmMessage = "Supprimer la zone \"${name.trim()}\" ? Cette action est irréversible.",
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val result = coordinator.processUserAction(
                                    "delete->zone",
                                    mapOf("zone_id" to existingZone!!.id)
                                )
                                onDelete?.invoke()
                            } catch (e: Exception) {
                                // TODO: Error handling
                            }
                        }
                    }
                )
            }
        }
        
    }
}