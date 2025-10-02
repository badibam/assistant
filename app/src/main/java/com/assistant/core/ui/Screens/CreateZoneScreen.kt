package com.assistant.core.ui.Screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
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
    val s = remember { Strings.`for`(context = context) }
    val coroutineScope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    
    // Form state - persistent across orientation changes
    var name by rememberSaveable(existingZone) { mutableStateOf(existingZone?.name.orEmpty()) }
    var description by rememberSaveable(existingZone) { mutableStateOf(existingZone?.description.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(String()) } // Note: Zone entity doesn't have color field
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val isEditing = existingZone != null
    
    // Handle save logic
    val handleSave = {
        // Validation via SchemaValidator avant sauvegarde
        val zoneData = mapOf(
            "name" to name.trim(),
            "description" to description.trim()
        )
        
        try {
            val schema = com.assistant.core.schemas.ZoneSchemaProvider.getSchema("zone_config", context)
            val validation = if (schema != null) {
                com.assistant.core.validation.SchemaValidator.validate(schema, zoneData, context)
            } else {
                com.assistant.core.validation.ValidationResult.error("Zone config schema not found")
            }
            
            if (validation.isValid) {
                // Validation successful, proceed to save
                if (isEditing) {
                // Handle update
                coroutineScope.launch {
                    try {
                        val result = coordinator.processUserAction(
                            "zones.update",
                            mapOf(
                                "zone_id" to existingZone!!.id,
                                "name" to name.trim(),
                                "description" to (description.takeIf { it.isNotBlank() } ?: "")
                            )
                        )
                        onUpdate?.invoke()
                    } catch (e: Exception) {
                        errorMessage = "Update error: ${e.message}"
                    }
                }
                } else {
                    // Handle create
                    onCreate?.invoke(name.trim(), description.takeIf { it.isNotBlank() }, null)
                }
            } else {
                // Validation failed, show error via Toast
                UI.Toast(context, validation.errorMessage ?: s.shared("message_validation_error_simple"), Duration.LONG)
            }
            
        } catch (e: Exception) {
            // Erreur technique de validation
            UI.Toast(context, s.shared("message_error").format(e.message ?: ""), Duration.LONG)
        }
    }
    
    
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title - centered (MainScreen pattern)
        UI.Text(
            text = if (isEditing) s.shared("action_edit_zone") else s.shared("action_create_zone"),
            type = TextType.TITLE,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        
        // Form fields
        UI.FormField(
            label = s.shared("label_zone_name"),
            value = name,
            onChange = { name = it },
            fieldType = FieldType.TEXT,
            required = true
        )
        
        UI.FormField(
            label = s.shared("label_description"),
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
                    confirmMessage = "${s.shared("message_delete_zone_confirmation").format(name.trim())} ${s.shared("message_irreversible_action")}",
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val result = coordinator.processUserAction(
                                    "zones.delete",
                                    mapOf("zone_id" to existingZone!!.id)
                                )
                                onDelete?.invoke()
                            } catch (e: Exception) {
                                errorMessage = "Delete error: ${e.message}"
                            }
                        }
                    }
                )
            }
        }
        
    }
    
    // Error handling with Toast
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }
}