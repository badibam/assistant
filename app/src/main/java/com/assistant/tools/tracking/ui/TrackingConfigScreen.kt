package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.debug.DebugManager
import com.assistant.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data classes for flexible tracking configuration
 */
data class TrackingItem(
    val name: String,
    val properties: MutableMap<String, Any> = mutableMapOf()
)

data class TrackingGroup(
    val name: String,
    val items: MutableList<TrackingItem> = mutableListOf()
)

/**
 * Configuration screen for Tracking tool type
 * Handles common fields + tracking-specific configuration + groups/items management
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
    var autoSwitch by remember { mutableStateOf(true) }
    
    // Groups and items state
    var groups by remember { 
        mutableStateOf(mutableListOf(TrackingGroup("Default"))) 
    }
    
    // UI state for adding items/groups
    var showAddGroup by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf<Int?>(null) } // Index du groupe
    var newGroupName by remember { mutableStateOf("") }
    var newItemName by remember { mutableStateOf("") }
    var newItemProperties by remember { mutableStateOf(mutableMapOf<String, Any>()) }
    
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
            put("auto_switch", autoSwitch)
            put("groups", JSONArray().apply {
                groups.forEach { group ->
                    put(JSONObject().apply {
                        put("name", group.name)
                        put("items", JSONArray().apply {
                            group.items.forEach { item ->
                                put(JSONObject().apply {
                                    put("name", item.name)
                                    item.properties.forEach { (key, value) ->
                                        put(key, value)
                                    }
                                })
                            }
                        })
                    })
                }
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
                            "boolean" to "Oui/Non",
                            "duration" to "Durée"
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
                
                // Auto-switch (only for duration)
                if (trackingType == "duration") {
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "auto-switch-selection",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Column {
                            UI.Text(
                                text = "Basculement automatique",
                                type = TextType.SUBTITLE,
                                semantic = "field-label"
                            )
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            listOf(
                                true to "Activé (stoppe l'activité précédente)",
                                false to "Désactivé (tracking parallèle possible)"
                            ).forEach { (value, label) ->
                                UI.Button(
                                    type = if (autoSwitch == value) ButtonType.PRIMARY else ButtonType.GHOST,
                                    semantic = "auto-switch-$value",
                                    onClick = { autoSwitch = value },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    enabled = true
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
                }
            }
        }
        
        UI.Spacer(modifier = Modifier.height(24.dp))
        
        // Groups and Items management section
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UI.Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Text(
                        text = "Groupes et éléments",
                        type = TextType.TITLE,
                        semantic = "section-title"
                    )
                    
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        semantic = "add-group",
                        onClick = { showAddGroup = true },
                        enabled = true
                    ) {
                        UI.Text(
                            text = "+ Groupe",
                            type = TextType.LABEL,
                            semantic = "button-label"
                        )
                    }
                }
                
                // Groups list
                groups.forEachIndexed { groupIndex, group ->
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "group-$groupIndex",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Column {
                            // Group header
                            UI.Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                UI.Text(
                                    text = group.name,
                                    type = TextType.SUBTITLE,
                                    semantic = "group-name"
                                )
                                
                                UI.Row {
                                    UI.Button(
                                        type = ButtonType.GHOST,
                                        semantic = "add-item-$groupIndex",
                                        onClick = { 
                                            showAddItem = groupIndex
                                            newItemName = ""
                                            newItemProperties.clear()
                                        },
                                        enabled = true
                                    ) {
                                        UI.Text(
                                            text = "+ Item",
                                            type = TextType.CAPTION,
                                            semantic = "button-label"
                                        )
                                    }
                                    
                                    if (group.name != "Default") { // Can't delete default group
                                        UI.Button(
                                            type = ButtonType.DANGER,
                                            semantic = "delete-group-$groupIndex",
                                            onClick = { 
                                                groups.removeAt(groupIndex)
                                            },
                                            enabled = true
                                        ) {
                                            UI.Text(
                                                text = "×",
                                                type = TextType.CAPTION,
                                                semantic = "button-label"
                                            )
                                        }
                                    }
                                }
                            }
                            
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            // Items in group
                            group.items.forEachIndexed { itemIndex, item ->
                                UI.Card(
                                    type = CardType.ZONE,
                                    semantic = "item-$groupIndex-$itemIndex",
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UI.Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        UI.Column {
                                            UI.Text(
                                                text = item.name,
                                                type = TextType.BODY,
                                                semantic = "item-name"
                                            )
                                            if (item.properties.isNotEmpty()) {
                                                UI.Text(
                                                    text = item.properties.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                                                    type = TextType.CAPTION,
                                                    semantic = "item-properties"
                                                )
                                            }
                                        }
                                        
                                        UI.Button(
                                            type = ButtonType.DANGER,
                                            semantic = "delete-item-$groupIndex-$itemIndex",
                                            onClick = { 
                                                group.items.removeAt(itemIndex)
                                            },
                                            enabled = true
                                        ) {
                                            UI.Text(
                                                text = "×",
                                                type = TextType.CAPTION,
                                                semantic = "button-label"
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Show add item form if this group is selected
                            if (showAddItem == groupIndex) {
                                UI.Spacer(modifier = Modifier.height(8.dp))
                                AddItemForm(
                                    trackingType = trackingType,
                                    itemName = newItemName,
                                    onItemNameChange = { newItemName = it },
                                    properties = newItemProperties,
                                    onPropertiesChange = { newItemProperties = it },
                                    onSave = {
                                        if (newItemName.isNotBlank()) {
                                            group.items.add(TrackingItem(newItemName, newItemProperties.toMutableMap()))
                                            showAddItem = null
                                        }
                                    },
                                    onCancel = { showAddItem = null }
                                )
                            }
                        }
                    }
                }
                
                // Show add group form
                if (showAddGroup) {
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "add-group-form",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Column {
                            UI.Text(
                                text = "Nouveau groupe",
                                type = TextType.SUBTITLE,
                                semantic = "form-title"
                            )
                            
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            UI.TextField(
                                type = TextFieldType.STANDARD,
                                semantic = "new-group-name",
                                value = newGroupName,
                                onValueChange = { newGroupName = it },
                                placeholder = "Nom du groupe",
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            UI.Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UI.Button(
                                    type = ButtonType.SECONDARY,
                                    semantic = "cancel-group",
                                    onClick = { 
                                        showAddGroup = false
                                        newGroupName = ""
                                    },
                                    enabled = true
                                ) {
                                    UI.Text(
                                        text = "Annuler",
                                        type = TextType.LABEL,
                                        semantic = "button-label"
                                    )
                                }
                                
                                UI.Button(
                                    type = ButtonType.PRIMARY,
                                    semantic = "save-group",
                                    onClick = { 
                                        if (newGroupName.isNotBlank()) {
                                            groups.add(TrackingGroup(newGroupName))
                                            showAddGroup = false
                                            newGroupName = ""
                                        }
                                    },
                                    enabled = newGroupName.isNotBlank()
                                ) {
                                    UI.Text(
                                        text = "Ajouter",
                                        type = TextType.LABEL,
                                        semantic = "button-label"
                                    )
                                }
                            }
                        }
                    }
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

/**
 * Form for adding items with type-specific properties
 */
@Composable
private fun AddItemForm(
    trackingType: String,
    itemName: String,
    onItemNameChange: (String) -> Unit,
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    UI.Card(
        type = CardType.ZONE,
        semantic = "add-item-form",
        modifier = Modifier.fillMaxWidth()
    ) {
        UI.Column {
            UI.Text(
                text = "Nouvel élément",
                type = TextType.SUBTITLE,
                semantic = "form-title"
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            // Item name field
            UI.TextField(
                type = TextFieldType.STANDARD,
                semantic = "new-item-name",
                value = itemName,
                onValueChange = onItemNameChange,
                placeholder = "Nom de l'élément",
                modifier = Modifier.fillMaxWidth()
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            // Type-specific fields
            when (trackingType) {
                "numeric" -> NumericItemProperties(properties, onPropertiesChange)
                "text" -> TextItemProperties(properties, onPropertiesChange)
                "scale" -> ScaleItemProperties(properties, onPropertiesChange)
                "boolean" -> BooleanItemProperties(properties, onPropertiesChange)
                "duration" -> DurationItemProperties(properties, onPropertiesChange)
            }
            
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            UI.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Button(
                    type = ButtonType.SECONDARY,
                    semantic = "cancel-item",
                    onClick = onCancel,
                    enabled = true
                ) {
                    UI.Text(
                        text = "Annuler",
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
                
                UI.Button(
                    type = ButtonType.PRIMARY,
                    semantic = "save-item",
                    onClick = onSave,
                    enabled = itemName.isNotBlank()
                ) {
                    UI.Text(
                        text = "Ajouter",
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
            }
        }
    }
}

/**
 * Properties form for numeric tracking items
 */
@Composable
private fun NumericItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    var unit by remember { mutableStateOf(properties["unit"]?.toString() ?: "") }
    var defaultAmount by remember { mutableStateOf(properties["default_amount"]?.toString() ?: "") }
    var minValue by remember { mutableStateOf(properties["min_value"]?.toString() ?: "") }
    var maxValue by remember { mutableStateOf(properties["max_value"]?.toString() ?: "") }
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UI.TextField(
            type = TextFieldType.STANDARD,
            semantic = "item-unit",
            value = unit,
            onValueChange = { 
                unit = it
                properties["unit"] = it
                onPropertiesChange(properties)
            },
            placeholder = "Unité (kg, cm, etc.)",
            modifier = Modifier.fillMaxWidth()
        )
        
        UI.TextField(
            type = TextFieldType.NUMERIC,
            semantic = "item-default-amount",
            value = defaultAmount,
            onValueChange = { 
                defaultAmount = it
                properties["default_amount"] = it.toDoubleOrNull() ?: 0.0
                onPropertiesChange(properties)
            },
            placeholder = "Valeur par défaut",
            modifier = Modifier.fillMaxWidth()
        )
        
        UI.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UI.TextField(
                type = TextFieldType.NUMERIC,
                semantic = "item-min-value",
                value = minValue,
                onValueChange = { 
                    minValue = it
                    if (it.toDoubleOrNull() != null) properties["min_value"] = it.toDoubleOrNull()!! else properties.remove("min_value")
                    onPropertiesChange(properties)
                },
                placeholder = "Min",
                modifier = Modifier.weight(1f)
            )
            
            UI.TextField(
                type = TextFieldType.NUMERIC,
                semantic = "item-max-value",
                value = maxValue,
                onValueChange = { 
                    maxValue = it
                    if (it.toDoubleOrNull() != null) properties["max_value"] = it.toDoubleOrNull()!! else properties.remove("max_value")
                    onPropertiesChange(properties)
                },
                placeholder = "Max",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Properties form for text tracking items
 */
@Composable
private fun TextItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    var defaultText by remember { mutableStateOf(properties["default_text"]?.toString() ?: "") }
    var maxLength by remember { mutableStateOf(properties["max_length"]?.toString() ?: "") }
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UI.TextField(
            type = TextFieldType.STANDARD,
            semantic = "item-default-text",
            value = defaultText,
            onValueChange = { 
                defaultText = it
                properties["default_text"] = it
                onPropertiesChange(properties)
            },
            placeholder = "Texte par défaut",
            modifier = Modifier.fillMaxWidth()
        )
        
        UI.TextField(
            type = TextFieldType.NUMERIC,
            semantic = "item-max-length",
            value = maxLength,
            onValueChange = { 
                maxLength = it
                if (it.toIntOrNull() != null) properties["max_length"] = it.toIntOrNull()!! else properties.remove("max_length")
                onPropertiesChange(properties)
            },
            placeholder = "Longueur maximale",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Properties form for scale tracking items
 */
@Composable
private fun ScaleItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    var scaleSize by remember { mutableStateOf(properties["scale_size"]?.toString() ?: "5") }
    var minLabel by remember { mutableStateOf(properties["min_label"]?.toString() ?: "") }
    var maxLabel by remember { mutableStateOf(properties["max_label"]?.toString() ?: "") }
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UI.TextField(
            type = TextFieldType.NUMERIC,
            semantic = "item-scale-size",
            value = scaleSize,
            onValueChange = { 
                scaleSize = it
                properties["scale_size"] = it.toIntOrNull() ?: 5
                onPropertiesChange(properties)
            },
            placeholder = "Taille de l'échelle (ex: 5, 10)",
            modifier = Modifier.fillMaxWidth()
        )
        
        UI.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UI.TextField(
                type = TextFieldType.STANDARD,
                semantic = "item-min-label",
                value = minLabel,
                onValueChange = { 
                    minLabel = it
                    properties["min_label"] = it
                    onPropertiesChange(properties)
                },
                placeholder = "Label minimum",
                modifier = Modifier.weight(1f)
            )
            
            UI.TextField(
                type = TextFieldType.STANDARD,
                semantic = "item-max-label",
                value = maxLabel,
                onValueChange = { 
                    maxLabel = it
                    properties["max_label"] = it
                    onPropertiesChange(properties)
                },
                placeholder = "Label maximum",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Properties form for boolean tracking items
 */
@Composable
private fun BooleanItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    var trueLabel by remember { mutableStateOf(properties["true_label"]?.toString() ?: "Oui") }
    var falseLabel by remember { mutableStateOf(properties["false_label"]?.toString() ?: "Non") }
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UI.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UI.TextField(
                type = TextFieldType.STANDARD,
                semantic = "item-true-label",
                value = trueLabel,
                onValueChange = { 
                    trueLabel = it
                    properties["true_label"] = it
                    onPropertiesChange(properties)
                },
                placeholder = "Label pour Vrai",
                modifier = Modifier.weight(1f)
            )
            
            UI.TextField(
                type = TextFieldType.STANDARD,
                semantic = "item-false-label",
                value = falseLabel,
                onValueChange = { 
                    falseLabel = it
                    properties["false_label"] = it
                    onPropertiesChange(properties)
                },
                placeholder = "Label pour Faux",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Properties form for duration tracking items (activities)
 */
@Composable
private fun DurationItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    // Duration items don't need specific properties - just the activity name
    // The activity name is handled by the parent form
    
    UI.Card(
        type = CardType.ZONE,
        semantic = "duration-item-info",
        modifier = Modifier.fillMaxWidth()
    ) {
        UI.Text(
            text = "Les activités de durée n'ont pas de propriétés spécifiques. " +
                    "Seul le nom de l'activité est nécessaire pour le tracking temporel.",
            type = TextType.CAPTION,
            semantic = "info-text"
        )
    }
}