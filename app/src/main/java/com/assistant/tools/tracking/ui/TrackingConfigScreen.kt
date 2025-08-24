package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.tools.tracking.TrackingToolType
import org.json.JSONObject

/**
 * Configuration screen for Tracking tool type
 * Handles common fields + tracking-specific configuration
 */
@Composable
fun TrackingConfigScreen(
    zoneId: String,
    onSave: (config: String) -> Unit,
    onCancel: () -> Unit,
    existingConfig: String? = null,
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Mode detection
    val isEditing = existingConfig != null && existingToolId != null

    // Common tool configuration fields
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var trackingType by remember { mutableStateOf("") }
    var showValue by remember { mutableStateOf(true) }
    var itemMode by remember { mutableStateOf("free") }
    
    // Load configuration - existing config or default config for new tools
    LaunchedEffect(existingConfig) {
        val configToLoad = existingConfig ?: TrackingToolType.getDefaultConfig()
        
        try {
            val config = JSONObject(configToLoad)
            
            name = config.optString("name", "")
            description = config.optString("description", "")
            trackingType = config.optString("type", "")
            showValue = config.optBoolean("show_value", true)
            itemMode = config.optString("item_mode", "free")
            
        } catch (e: Exception) {
            // En cas d'erreur, utiliser les valeurs par défaut
        }
    }

    // Save configuration
    val saveConfig = {
        try {
            val config = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("management", "")
                put("config_validation", false)
                put("data_validation", false)
                put("display_mode", "")
                put("icon_name", "activity")
                put("type", trackingType)
                put("show_value", showValue)
                put("item_mode", itemMode)
                put("save_new_items", false)
                put("auto_switch", false)
            }
            
            onSave(config.toString())
        } catch (e: Exception) {
            // Handle error
        }
    }

    UI.Screen(type = ScreenType.MAIN) {
        UI.TopBar(
            type = TopBarType.DEFAULT,
            title = if (isEditing) "Modifier l'outil" else "Nouvel outil de suivi"
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Text(
                type = TextType.SUBTITLE,
                text = "Configuration générale"
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))

            // Name field
            UI.TextField(
                type = TextFieldType.STANDARD,
                value = name,
                onValueChange = { name = it },
                placeholder = "Nom de l'outil",
                modifier = Modifier.fillMaxWidth()
            )

            UI.Spacer(modifier = Modifier.height(8.dp))

            // Description field
            UI.TextField(
                type = TextFieldType.STANDARD,
                value = description,
                onValueChange = { description = it },
                placeholder = "Description de l'outil",
                modifier = Modifier.fillMaxWidth()
            )

            UI.Spacer(modifier = Modifier.height(16.dp))

            UI.Text(
                type = TextType.SUBTITLE,
                text = "Configuration du suivi"
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))

            // Type field
            UI.TextField(
                type = TextFieldType.STANDARD,
                value = trackingType,
                onValueChange = { trackingType = it },
                placeholder = "Type de données (numeric, text, scale, boolean, etc.)",
                modifier = Modifier.fillMaxWidth()
            )

            UI.Spacer(modifier = Modifier.height(8.dp))

            // Item mode field
            UI.TextField(
                type = TextFieldType.STANDARD,
                value = itemMode,
                onValueChange = { itemMode = it },
                placeholder = "Mode d'entrée (free, predefined, both)",
                modifier = Modifier.fillMaxWidth()
            )

            UI.Spacer(modifier = Modifier.height(16.dp))

            // Save button
            UI.Button(
                type = ButtonType.PRIMARY,
                onClick = saveConfig,
                enabled = name.isNotBlank() && trackingType.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Text(
                    type = TextType.LABEL,
                    text = if (isEditing) "Sauvegarder" else "Créer l'outil"
                )
            }
            
            // Delete button for edit mode
            if (isEditing && onDelete != null) {
                UI.Spacer(modifier = Modifier.height(8.dp))
                UI.Button(
                    type = ButtonType.SECONDARY,
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Text(
                        type = TextType.LABEL,
                        text = "Supprimer"
                    )
                }
            }
        }
    }
}