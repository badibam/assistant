package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class for numeric tracking items
 */
data class NumericTrackingItem(
    val name: String,
    val defaultValue: String = "",
    val unit: String = ""
)

/**
 * Configuration screen for Tracking tool type
 * Uses UI_DECISIONS.md patterns with 3 cards structure
 * Supports all tracking types but only numeric is functional for now
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
    val isEditing = existingConfig != null && existingToolId != null
    
    // Configuration state - Paramètres généraux
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var management by remember { mutableStateOf("Manuel") }
    var configValidation by remember { mutableStateOf("Désactivée") }
    var dataValidation by remember { mutableStateOf("Désactivée") }
    var displayMode by remember { mutableStateOf("MINIMAL") }
    var iconName by remember { mutableStateOf("📊") }
    
    // Configuration state - Paramètres spécifiques tracking
    var trackingType by remember { mutableStateOf("Numérique") }
    var showValue by remember { mutableStateOf("Oui") }
    var itemMode by remember { mutableStateOf("Libre") }
    var autoSwitch by remember { mutableStateOf("Désactivée") }
    
    // Items management state (numeric only)
    var items by remember { mutableStateOf(listOf<NumericTrackingItem>()) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Int?>(null) }
    
    // Load existing configuration
    LaunchedEffect(existingConfig) {
        existingConfig?.let { configJson ->
            try {
                val config = JSONObject(configJson)
                name = config.optString("name", "")
                description = config.optString("description", "")
                management = config.optString("management", "Manuel")
                configValidation = if (config.optBoolean("config_validation", false)) "Activée" else "Désactivée"
                dataValidation = if (config.optBoolean("data_validation", false)) "Activée" else "Désactivée"
                displayMode = config.optString("display_mode", "MINIMAL")
                iconName = config.optString("icon_name", "📊")
                trackingType = config.optString("type", "Numérique")
                showValue = if (config.optBoolean("show_value", true)) "Oui" else "Non"
                itemMode = config.optString("item_mode", "Libre")
                autoSwitch = if (config.optBoolean("auto_switch", false)) "Activée" else "Désactivée"
                
                // Load items (numeric only)
                if (trackingType == "Numérique") {
                    val itemsArray = config.optJSONArray("items")
                    if (itemsArray != null) {
                        val loadedItems = mutableListOf<NumericTrackingItem>()
                        for (i in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.getJSONObject(i)
                            loadedItems.add(
                                NumericTrackingItem(
                                    name = itemObj.getString("name"),
                                    defaultValue = itemObj.optString("default_value", ""),
                                    unit = itemObj.optString("unit", "")
                                )
                            )
                        }
                        items = loadedItems
                    }
                }
            } catch (e: Exception) {
                // Keep default values
            }
        }
    }
    
    // Save function
    val handleSave = {
        val config = JSONObject().apply {
            put("name", name.trim())
            put("description", description.trim())
            put("management", management)
            put("config_validation", configValidation == "Activée")
            put("data_validation", dataValidation == "Activée")
            put("display_mode", displayMode)
            put("icon_name", iconName)
            put("type", trackingType.lowercase())
            put("show_value", showValue == "Oui")
            put("item_mode", itemMode)
            put("auto_switch", autoSwitch == "Activée")
            
            // Items array (numeric only)
            if (trackingType == "Numérique") {
                val itemsArray = JSONArray()
                items.forEach { item ->
                    val itemObj = JSONObject().apply {
                        put("name", item.name)
                        put("default_value", item.defaultValue)
                        put("unit", item.unit)
                    }
                    itemsArray.put(itemObj)
                }
                put("items", itemsArray)
            }
        }
        onSave(config.toString())
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { itemIndex ->
        UI.Dialog(
            type = DialogType.DANGER,
            onConfirm = {
                items = items.filterIndexed { index, _ -> index != itemIndex }
                showDeleteDialog = null
            },
            onCancel = { showDeleteDialog = null }
        ) {
            UI.Text(
                "Êtes-vous sûr de vouloir supprimer l'item \"${items[itemIndex].name}\" ?",
                TextType.BODY
            )
        }
    }
    
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        UI.Text(
            text = if (isEditing) "Modifier Suivi" else "Créer Suivi",
            type = TextType.TITLE
        )
        
        // Card 1: Paramètres généraux
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text("Paramètres généraux", TextType.SUBTITLE)
                
                UI.FormField(
                    label = "Nom",
                    value = name,
                    onChange = { name = it },
                    validation = ValidationRule.REQUIRED
                )
                
                UI.FormField(
                    label = "Description",
                    value = description,
                    onChange = { description = it }
                )
                
                UI.FormSelection(
                    label = "Gestion",
                    options = listOf("Manuel", "IA", "Collaboratif"),
                    selected = management,
                    onSelect = { management = it }
                )
                
                UI.FormSelection(
                    label = "Validation config par IA",
                    options = listOf("Activée", "Désactivée"),
                    selected = configValidation,
                    onSelect = { configValidation = it }
                )
                
                UI.FormSelection(
                    label = "Validation données par IA",
                    options = listOf("Activée", "Désactivée"),
                    selected = dataValidation,
                    onSelect = { dataValidation = it }
                )
            }
        }
        
        // Card 2: Paramètres spécifiques tracking
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text("Paramètres spécifiques", TextType.SUBTITLE)
                
                UI.FormSelection(
                    label = "Type de tracking",
                    options = listOf("Numérique", "Durée", "Libre"),
                    selected = trackingType,
                    onSelect = { trackingType = it }
                )
                
                if (trackingType == "Numérique") {
                    // Configuration spécifique au numérique
                    UI.FormSelection(
                        label = "Mode des items",
                        options = listOf("Libre", "Prédéfini", "Mixte"),
                        selected = itemMode,
                        onSelect = { itemMode = it }
                    )
                    
                    UI.FormSelection(
                        label = "Afficher valeur",
                        options = listOf("Oui", "Non"),
                        selected = showValue,
                        onSelect = { showValue = it }
                    )
                } else if (trackingType == "Durée") {
                    // Configuration spécifique à la durée
                    UI.FormSelection(
                        label = "Commutation auto",
                        options = listOf("Activée", "Désactivée"),
                        selected = autoSwitch,
                        onSelect = { autoSwitch = it }
                    )
                    
                    UI.Text("Type durée non encore implémenté", TextType.BODY)
                } else {
                    // Type libre
                    UI.Text("Type libre non encore implémenté", TextType.BODY)
                }
            }
        }
        
        // Card 3: Liste des items (seulement pour numérique en mode Prédéfini ou Mixte)
        if (trackingType == "Numérique" && (itemMode == "Prédéfini" || itemMode == "Mixte")) {
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UI.Text("Items prédéfinis", TextType.SUBTITLE)
                        UI.AddButton {
                            items = items + NumericTrackingItem("Nouvel item", "", "")
                            editingItemIndex = items.size - 1
                        }
                    }
                    
                    if (items.isEmpty()) {
                        UI.Text("Aucun item défini", TextType.BODY)
                    } else {
                        // En-têtes du tableau
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UI.Text("Nom", TextType.LABEL, modifier = Modifier.weight(0.3f))
                            UI.Text("Valeur défaut", TextType.LABEL, modifier = Modifier.weight(0.25f))
                            UI.Text("Unité", TextType.LABEL, modifier = Modifier.weight(0.25f))
                            UI.Text("Actions", TextType.LABEL, modifier = Modifier.weight(0.2f))
                        }
                        
                        items.forEachIndexed { index, item ->
                            if (editingItemIndex == index) {
                                // Mode édition inline
                                var editName by remember { mutableStateOf(item.name) }
                                var editDefaultValue by remember { mutableStateOf(item.defaultValue) }
                                var editUnit by remember { mutableStateOf(item.unit) }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UI.TextField(
                                        type = TextFieldType.TEXT,
                                        value = editName,
                                        onChange = { editName = it },
                                        placeholder = "Nom"
                                    )
                                    
                                    UI.TextField(
                                        type = TextFieldType.NUMERIC,
                                        value = editDefaultValue,
                                        onChange = { editDefaultValue = it },
                                        placeholder = "0"
                                    )
                                    
                                    UI.TextField(
                                        type = TextFieldType.TEXT,
                                        value = editUnit,
                                        onChange = { editUnit = it },
                                        placeholder = "unité"
                                    )
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        UI.SaveButton {
                                            items = items.mapIndexed { i, oldItem ->
                                                if (i == index) {
                                                    NumericTrackingItem(editName.trim(), editDefaultValue.trim(), editUnit.trim())
                                                } else oldItem
                                            }
                                            editingItemIndex = null
                                        }
                                        
                                        UI.CancelButton {
                                            editingItemIndex = null
                                        }
                                    }
                                }
                            } else {
                                // Mode affichage normal
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UI.Text(item.name, TextType.BODY, modifier = Modifier.weight(0.3f))
                                    UI.Text(item.defaultValue.ifEmpty { "-" }, TextType.BODY, modifier = Modifier.weight(0.25f))
                                    UI.Text(item.unit.ifEmpty { "-" }, TextType.BODY, modifier = Modifier.weight(0.25f))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.weight(0.2f)
                                    ) {
                                        UI.EditButton { editingItemIndex = index }
                                        
                                        UI.DeleteButton { showDeleteDialog = index }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Actions
        UI.FormActions {
            UI.Button(
                type = ButtonType.PRIMARY,
                onClick = handleSave
            ) {
                UI.Text(if (isEditing) "Sauvegarder" else "Créer", TextType.LABEL)
            }
            
            UI.Button(
                type = ButtonType.SECONDARY,
                onClick = onCancel
            ) {
                UI.Text("Annuler", TextType.LABEL)
            }
            
            if (isEditing && onDelete != null) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    onClick = onDelete
                ) {
                    UI.Text("Supprimer", TextType.LABEL)
                }
            }
        }
    }
}