package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.debug.DebugManager
import com.assistant.R
import org.json.JSONObject

/**
 * Configuration screen for Tracking tool type
 * Handles common fields + tracking-specific configuration
 */
@Composable
fun TrackingConfigScreen(
    zoneId: String,
    onSave: (config: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    // Configuration state
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var management by remember { mutableStateOf("Manuel") }
    var configValidation by remember { mutableStateOf(true) }
    var dataValidation by remember { mutableStateOf(true) }
    var displayMode by remember { mutableStateOf("Condensé") }
    
    // Tracking-specific state
    var trackingType by remember { mutableStateOf("numeric") }
    var showValue by remember { mutableStateOf(true) }
    var itemMode by remember { mutableStateOf("free") }
    var saveNewItems by remember { mutableStateOf(false) }
    var defaultUnit by remember { mutableStateOf("") }
    
    // Debug message
    LaunchedEffect(Unit) {
        DebugManager.debug("🔧 TrackingConfigScreen ouvert pour zone: $zoneId")
    }
    
    // Save function
    val handleSave = {
        val config = JSONObject().apply {
            // Common fields
            put("name", name)
            put("description", description)
            put("management", management)
            put("config_validation", configValidation)
            put("data_validation", dataValidation)
            put("display_mode", displayMode)
            
            // Tracking-specific fields
            put("type", trackingType)
            put("show_value", showValue)
            put("item_mode", itemMode)
            put("save_new_items", saveNewItems)
            put("default_unit", defaultUnit)
            put("min_value", null)
            put("max_value", null)
            put("groups", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "Default")
                    put("items", org.json.JSONArray())
                })
            })
        }
        
        DebugManager.debug("💾 Sauvegarde config tracking: ${config.toString(2)}")
        onSave(config.toString())
    }
    
    UI.Screen(type = ScreenType.MAIN) {
        // Top bar
        UI.TopBar(
            type = TopBarType.DEFAULT,
            title = "Configuration Suivi"
        )
        
        UI.Spacer(modifier = Modifier.height(16.dp))
        
        // Common configuration section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = "Configuration générale",
                    type = TextType.TITLE,
                    semantic = "section-title"
                )
                
                // Name field
                UI.TextField(
                    type = TextFieldType.STANDARD,
                    semantic = "tool-name",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nom de l'outil de suivi",
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Description field
                UI.TextField(
                    type = TextFieldType.MULTILINE,
                    semantic = "tool-description",
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "Description (optionnelle)",
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Management mode
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "management-selection",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = "Mode de gestion",
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        
                        listOf("Manuel", "IA", "Collaboratif").forEach { mode ->
                            UI.Button(
                                type = if (management == mode) ButtonType.PRIMARY else ButtonType.GHOST,
                                semantic = "management-$mode",
                                onClick = { management = mode },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                UI.Text(
                                    text = mode,
                                    type = TextType.LABEL,
                                    semantic = "button-label"
                                )
                            }
                        }
                    }
                }
                
                // Display mode
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "display-selection",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = "Mode d'affichage",
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        
                        listOf("Minimal", "Condensé", "Détaillé").forEach { mode ->
                            UI.Button(
                                type = if (displayMode == mode) ButtonType.PRIMARY else ButtonType.GHOST,
                                semantic = "display-$mode",
                                onClick = { displayMode = mode },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                UI.Text(
                                    text = mode,
                                    type = TextType.LABEL,
                                    semantic = "button-label"
                                )
                            }
                        }
                    }
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(24.dp))
        
        // Tracking-specific configuration section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Text(
                    text = "Configuration du suivi",
                    type = TextType.TITLE,
                    semantic = "section-title"
                )
                
                // Tracking type
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "tracking-type-selection",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = "Type de données",
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        
                        listOf(
                            "numeric" to "Numérique",
                            "text" to "Texte",
                            "scale" to "Échelle",
                            "boolean" to "Oui/Non"
                        ).forEach { (value, label) ->
                            UI.Button(
                                type = if (trackingType == value) ButtonType.PRIMARY else ButtonType.GHOST,
                                semantic = "tracking-type-$value",
                                onClick = { trackingType = value },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                UI.Text(
                                    text = label,
                                    type = TextType.LABEL,
                                    semantic = "button-label"
                                )
                            }
                        }
                    }
                }
                
                // Item mode
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "item-mode-selection",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = "Mode de saisie",
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        
                        listOf(
                            "free" to "Libre",
                            "predefined" to "Prédéfini",
                            "both" to "Mixte"
                        ).forEach { (value, label) ->
                            UI.Button(
                                type = if (itemMode == value) ButtonType.PRIMARY else ButtonType.GHOST,
                                semantic = "item-mode-$value",
                                onClick = { itemMode = value },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                UI.Text(
                                    text = label,
                                    type = TextType.LABEL,
                                    semantic = "button-label"
                                )
                            }
                        }
                    }
                }
                
                // Default unit (only for numeric)
                if (trackingType == "numeric") {
                    UI.TextField(
                        type = TextFieldType.STANDARD,
                        semantic = "default-unit",
                        value = defaultUnit,
                        onValueChange = { defaultUnit = it },
                        placeholder = "Unité par défaut (kg, cm, etc.)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                        DebugManager.debugButtonClick("Annuler config tracking")
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
                    type = ButtonType.PRIMARY,
                    semantic = "save-button",
                    onClick = {
                        DebugManager.debugButtonClick("Sauvegarder config tracking")
                        handleSave()
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    UI.Text(
                        text = stringResource(R.string.save),
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
            }
        }
    }
}