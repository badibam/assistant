package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.themes.base.ThemeIconManager
import com.assistant.ui.components.ToolIcon
import com.assistant.tools.tracking.TrackingToolType
import com.assistant.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class for tracking items
 */
data class TrackingItem(
    val name: String,
    val properties: MutableMap<String, Any> = mutableMapOf()
)

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
    
    // Configuration state - will be initialized from getDefaultConfig() or existing config
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var management by remember { mutableStateOf("") }
    var configValidation by remember { mutableStateOf(false) }
    var dataValidation by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf("") }
    var iconName by remember { mutableStateOf("activity") }
    
    // Tracking-specific state
    var trackingType by remember { mutableStateOf("") }
    var showValue by remember { mutableStateOf(false) }
    var itemMode by remember { mutableStateOf("") }
    var saveNewItems by remember { mutableStateOf(false) }
    var autoSwitch by remember { mutableStateOf(false) }
    
    // Items management state
    var items: MutableList<TrackingItem> by remember { 
        mutableStateOf(mutableListOf()) 
    }
    
    // UI state for adding items
    var showAddItem by remember { mutableStateOf(false) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var newItemName by remember { mutableStateOf("") }
    var editItemName by remember { mutableStateOf("") }
    var newItemProperties: MutableMap<String, Any> by remember { mutableStateOf(mutableMapOf<String, Any>()) }
    
    // État pour le sélecteur d'icônes
    var showIconSelector by remember { mutableStateOf(false) }
    
    // État pour la confirmation de changement de type
    var showTypeChangeWarning by remember { mutableStateOf(false) }
    var pendingTrackingType by remember { mutableStateOf<String?>(null) }
    
    // Liste des icônes disponibles (hardcodée pour l'instant)
    val availableIcons = listOf("activity", "trending-up")
    
    // TODO: Remplacer par ThemeIconManager.getAvailableIcons("default") quand implémenté
    
    // Load configuration - existing config or default config for new tools
    LaunchedEffect(existingConfig) {
        val configToLoad = existingConfig ?: TrackingToolType.getDefaultConfig()
        
        try {
            val config = JSONObject(configToLoad)
            
            // Common fields
            name = config.optString("name", "")
            description = config.optString("description", "")
            management = config.optString("management", "")
            configValidation = config.optBoolean("config_validation", false)
            dataValidation = config.optBoolean("data_validation", false)
            displayMode = config.optString("display_mode", "")
            iconName = config.optString("icon_name", "activity")
            
            // Tracking-specific fields
            trackingType = config.optString("type", "")
            showValue = config.optBoolean("show_value", false)
            itemMode = config.optString("item_mode", "")
            saveNewItems = config.optBoolean("save_new_items", false)
            autoSwitch = config.optBoolean("auto_switch", false)
            
            // Items
            val itemsArray = config.optJSONArray("items")
            if (itemsArray != null) {
                val loadedItems = mutableListOf<TrackingItem>()
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val itemName = itemObj.getString("name")
                    val properties = mutableMapOf<String, Any>()
                    
                    // Load all properties except name
                    itemObj.keys().forEach { key ->
                        if (key != "name") {
                            properties[key] = itemObj.get(key)
                        }
                    }
                    
                    loadedItems.add(TrackingItem(itemName, properties))
                }
                items = loadedItems
            }
            
        } catch (e: Exception) {
            // Fallback to empty state if JSON parsing fails
        }
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
            put("icon_name", iconName)
            
            // Tracking-specific fields
            put("type", trackingType)
            put("show_value", showValue)
            put("item_mode", itemMode)
            put("save_new_items", saveNewItems)
            put("auto_switch", autoSwitch)
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("name", item.name)
                        item.properties.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                }
            })
        }
        
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
                
                // Name field with icon
                UI.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UI.TextField(
                        type = TextFieldType.STANDARD,
                        semantic = "tool-name",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Nom de l'outil de suivi",
                        modifier = Modifier.weight(1f)
                    )
                    
                    UI.Button(
                        type = ButtonType.GHOST,
                        semantic = "icon-selector",
                        onClick = { showIconSelector = true }
                    ) {
                        val context = LocalContext.current
                        val iconResource = try {
                            ThemeIconManager.getIconResource(context, "default", iconName)
                        } catch (e: IllegalArgumentException) {
                            ThemeIconManager.getIconResource(context, "default", "activity")
                        }
                        
                        ToolIcon(
                            iconResource = iconResource,
                            size = 24.dp
                        )
                    }
                }
                
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
                        
                        listOf("Icône", "Minimal", "Ligne", "Condensé", "Étendu", "Complet").forEach { mode ->
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
                            "duration" to "Durée",
                            "choice" to "Choix",
                            "counter" to "Compteur"
                        ).forEach { (value, label) ->
                            UI.Button(
                                type = if (trackingType == value) ButtonType.PRIMARY else ButtonType.GHOST,
                                semantic = "tracking-type-$value",
                                onClick = { 
                                    if (trackingType != value && items.isNotEmpty()) {
                                        // Show warning if there are existing items
                                        pendingTrackingType = value
                                        showTypeChangeWarning = true
                                    } else {
                                        // No items or same type, change directly
                                        trackingType = value
                                    }
                                },
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
                
                // Show value option
                UI.Card(
                    type = CardType.SYSTEM,
                    semantic = "show-value-selection",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Column {
                        UI.Text(
                            text = "Affichage des valeurs",
                            type = TextType.SUBTITLE,
                            semantic = "field-label"
                        )
                        UI.Spacer(modifier = Modifier.height(8.dp))
                        
                        listOf(
                            true to "Afficher les champs de valeur",
                            false to "Masquer les champs de valeur"
                        ).forEach { (value, label) ->
                            UI.Button(
                                type = if (showValue == value) ButtonType.PRIMARY else ButtonType.GHOST,
                                semantic = "show-value-$value",
                                onClick = { showValue = value },
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
                
                // Save new items option (only if item mode allows free entry)
                if (itemMode == "free" || itemMode == "both") {
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "save-new-items-selection",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Column {
                            UI.Text(
                                text = "Sauvegarde des nouveaux items",
                                type = TextType.SUBTITLE,
                                semantic = "field-label"
                            )
                            UI.Spacer(modifier = Modifier.height(8.dp))
                            
                            listOf(
                                true to "Sauvegarder automatiquement les nouveaux items",
                                false to "Ne pas sauvegarder les nouveaux items"
                            ).forEach { (value, label) ->
                                UI.Button(
                                    type = if (saveNewItems == value) ButtonType.PRIMARY else ButtonType.GHOST,
                                    semantic = "save-new-items-$value",
                                    onClick = { saveNewItems = value },
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
        
        // Items management section (only if not free mode or if both mode)
        if (itemMode == "predefined" || itemMode == "both") {
            UI.Container(type = ContainerType.PRIMARY) {
                UI.Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UI.Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Text(
                            text = "Éléments prédéfinis",
                            type = TextType.TITLE,
                            semantic = "section-title"
                        )
                        
                        UI.Button(
                            type = ButtonType.SECONDARY,
                            semantic = "add-item",
                            onClick = { 
                                showAddItem = true
                                newItemName = ""
                                newItemProperties.clear()
                            },
                            enabled = true
                        ) {
                            UI.Text(
                                text = "+ Élément",
                                type = TextType.LABEL,
                                semantic = "button-label"
                            )
                        }
                    }
                    
                    // Items list or empty state
                    if (items.isEmpty()) {
                        UI.Card(
                            type = CardType.SYSTEM,
                            semantic = "no-items",
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UI.Text(
                                text = "Aucun élément défini. Cliquez sur '+ Élément' pour commencer.",
                                type = TextType.BODY,
                                semantic = "empty-state-text"
                            )
                        }
                    } else {
                        items.forEachIndexed { itemIndex, item ->
                            UI.Card(
                                type = CardType.SYSTEM,
                                semantic = "item-$itemIndex",
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                UI.Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UI.Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Item reorder buttons
                                        UI.Column {
                                            UI.Button(
                                                type = ButtonType.GHOST,
                                                semantic = "move-item-up-$itemIndex",
                                                onClick = { 
                                                    if (itemIndex > 0) {
                                                        val newItems = items.toMutableList()
                                                        val temp = newItems[itemIndex]
                                                        newItems[itemIndex] = newItems[itemIndex - 1]
                                                        newItems[itemIndex - 1] = temp
                                                        items = newItems
                                                    }
                                                },
                                                enabled = itemIndex > 0
                                            ) {
                                                UI.Text(
                                                    text = "↑",
                                                    type = TextType.CAPTION,
                                                    semantic = "button-label"
                                                )
                                            }
                                            
                                            UI.Button(
                                                type = ButtonType.GHOST,
                                                semantic = "move-item-down-$itemIndex",
                                                onClick = { 
                                                    if (itemIndex < items.size - 1) {
                                                        val newItems = items.toMutableList()
                                                        val temp = newItems[itemIndex]
                                                        newItems[itemIndex] = newItems[itemIndex + 1]
                                                        newItems[itemIndex + 1] = temp
                                                        items = newItems
                                                    }
                                                },
                                                enabled = itemIndex < items.size - 1
                                            ) {
                                                UI.Text(
                                                    text = "↓",
                                                    type = TextType.CAPTION,
                                                    semantic = "button-label"
                                                )
                                            }
                                        }
                                        
                                        // Edit button
                                        UI.Button(
                                            type = ButtonType.GHOST,
                                            semantic = "edit-item-$itemIndex",
                                            onClick = { 
                                                editingItemIndex = itemIndex
                                                editItemName = item.name
                                            },
                                            enabled = true
                                        ) {
                                            UI.Text(
                                                text = "✎",
                                                type = TextType.CAPTION,
                                                semantic = "button-label"
                                            )
                                        }
                                        
                                        UI.Column {
                                            UI.Text(
                                                text = item.name,
                                                type = TextType.SUBTITLE,
                                                semantic = "item-name"
                                            )
                                            if (item.properties.isNotEmpty()) {
                                                UI.Text(
                                                    text = buildItemPropertiesText(item.properties, trackingType),
                                                    type = TextType.CAPTION,
                                                    semantic = "item-properties"
                                                )
                                            }
                                        }
                                    }
                                    
                                    // Delete button
                                    UI.Button(
                                        type = ButtonType.DANGER,
                                        semantic = "delete-item-$itemIndex",
                                        onClick = { 
                                            val newItems = items.toMutableList()
                                            newItems.removeAt(itemIndex)
                                            items = newItems
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
                    }
                    
                    // Add item dialog
                    if (showAddItem) {
                        AddItemForm(
                            trackingType = trackingType,
                            itemName = newItemName,
                            onItemNameChange = { newItemName = it },
                            properties = newItemProperties,
                            onPropertiesChange = { newItemProperties = it },
                            onSave = {
                                if (newItemName.isNotBlank()) {
                                    val newItems = items.toMutableList()
                                    newItems.add(TrackingItem(newItemName, newItemProperties.toMutableMap()))
                                    items = newItems
                                    showAddItem = false
                                    newItemName = ""
                                    newItemProperties.clear()
                                }
                            },
                            onCancel = { 
                                showAddItem = false
                                newItemName = ""
                                newItemProperties.clear()
                            }
                        )
                    }
                    
                    // Edit item dialog
                    editingItemIndex?.let { index ->
                        UI.Card(
                            type = CardType.ZONE,
                            semantic = "edit-item-form",
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UI.Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UI.Text(
                                    text = "Modifier l'élément",
                                    type = TextType.SUBTITLE,
                                    semantic = "form-title"
                                )
                                
                                UI.TextField(
                                    type = TextFieldType.STANDARD,
                                    semantic = "edit-item-name",
                                    value = editItemName,
                                    onValueChange = { editItemName = it },
                                    placeholder = "Nom de l'élément",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                UI.Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UI.Button(
                                        type = ButtonType.PRIMARY,
                                        semantic = "save-edit-item",
                                        onClick = {
                                            if (editItemName.isNotBlank()) {
                                                val newItems = items.toMutableList()
                                                val oldItem = newItems[index]
                                                newItems[index] = oldItem.copy(name = editItemName)
                                                items = newItems
                                                editingItemIndex = null
                                                editItemName = ""
                                            }
                                        },
                                        enabled = editItemName.isNotBlank(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        UI.Text(
                                            text = "Sauvegarder",
                                            type = TextType.LABEL,
                                            semantic = "button-label"
                                        )
                                    }
                                    
                                    UI.Button(
                                        type = ButtonType.SECONDARY,
                                        semantic = "cancel-edit-item",
                                        onClick = {
                                            editingItemIndex = null
                                            editItemName = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        UI.Text(
                                            text = "Annuler",
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
        }
        
        // Action buttons
        UI.Container(type = ContainerType.PRIMARY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Save button
                UI.Button(
                    type = ButtonType.PRIMARY,
                    onClick = handleSave,
                    enabled = name.isNotBlank() && trackingType.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Text(
                        type = TextType.LABEL,
                        text = if (isEditing) "Sauvegarder les modifications" else "Créer l'outil de suivi"
                    )
                }
                
                // Cancel button
                UI.Button(
                    type = ButtonType.SECONDARY,
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Text(
                        type = TextType.LABEL,
                        text = "Annuler"
                    )
                }
                
                // Delete button for edit mode
                if (isEditing && onDelete != null) {
                    UI.Button(
                        type = ButtonType.DANGER,
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Text(
                            type = TextType.LABEL,
                            text = "Supprimer cet outil"
                        )
                    }
                }
            }
        }
        
        // Confirmation dialogs
        UI.ConfirmDialog(
            isVisible = showTypeChangeWarning,
            title = "Changer le type de données ?",
            message = "Vous avez ${items.size} élément(s) prédéfini(s). Changer le type supprimera tous les éléments existants. Continuer ?",
            confirmText = "Supprimer les éléments",
            cancelText = "Annuler",
            onConfirm = {
                pendingTrackingType?.let { newType ->
                    trackingType = newType
                    items.clear()
                }
                showTypeChangeWarning = false
                pendingTrackingType = null
            },
            onCancel = {
                showTypeChangeWarning = false
                pendingTrackingType = null
            }
        )
        
        // Dialogs and modals for additional functionality
        if (showAddItem || editingItemIndex != null) {
            AddItemForm(
                trackingType = trackingType,
                itemName = if (editingItemIndex != null) editItemName else newItemName,
                onItemNameChange = { name ->
                    if (editingItemIndex != null) editItemName = name else newItemName = name
                },
                properties = newItemProperties,
                onPropertiesChange = { newItemProperties = it },
                onSave = {
                    val itemName = if (editingItemIndex != null) editItemName else newItemName
                    
                    if (itemName.isNotBlank()) {
                        val newItem = TrackingItem(itemName, newItemProperties.toMutableMap())
                        
                        if (editingItemIndex != null) {
                            val index = editingItemIndex!!
                            if (index < items.size) {
                                items[index] = newItem
                            }
                            editingItemIndex = null
                            editItemName = ""
                        } else {
                            items.add(newItem)
                            newItemName = ""
                        }
                        
                        newItemProperties.clear()
                        showAddItem = false
                    }
                },
                onCancel = {
                    showAddItem = false
                    editingItemIndex = null
                    newItemName = ""
                    editItemName = ""
                    newItemProperties.clear()
                }
            )
        }
        
        // Icon selector
        if (showIconSelector) {
            UI.Card(
                type = CardType.ZONE,
                semantic = "icon-selector",
                modifier = Modifier.fillMaxWidth()
            ) {
                UI.Column {
                    UI.Text(
                        text = "Choisir une icône",
                        type = TextType.SUBTITLE,
                        semantic = "selector-title"
                    )
                    
                    UI.Spacer(modifier = Modifier.height(8.dp))
                    
                    UI.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableIcons.forEach { icon ->
                            UI.Button(
                                type = if (iconName == icon) ButtonType.PRIMARY else ButtonType.GHOST,
                                semantic = "icon-$icon",
                                onClick = { 
                                    iconName = icon
                                    showIconSelector = false
                                }
                            ) {
                                val context = LocalContext.current
                                val iconResource = try {
                                    ThemeIconManager.getIconResource(context, "default", icon)
                                } catch (e: IllegalArgumentException) {
                                    ThemeIconManager.getIconResource(context, "default", "activity")
                                }
                                
                                ToolIcon(
                                    iconResource = iconResource,
                                    size = 24.dp
                                )
                            }
                        }
                    }
                    
                    UI.Spacer(modifier = Modifier.height(8.dp))
                    
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        semantic = "close-icon-selector",
                        onClick = { showIconSelector = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Text(
                            text = "Fermer",
                            type = TextType.LABEL,
                            semantic = "button-label"
                        )
                    }
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
            
            // Type-specific fields - only numeric for now
            when (trackingType) {
                "numeric" -> NumericItemProperties(properties, onPropertiesChange)
                else -> {
                    // For other types, just show a message for now
                    UI.Card(
                        type = CardType.ZONE,
                        semantic = "item-info",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Text(
                            text = "Configuration des propriétés pour le type '$trackingType' à venir.",
                            type = TextType.CAPTION,
                            semantic = "info-text"
                        )
                    }
                }
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
    var defaultValue by remember { mutableStateOf(properties["default_value"]?.toString() ?: "") }
    
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
            placeholder = "Unité (kg, cm, €, etc.)",
            modifier = Modifier.fillMaxWidth()
        )
        
        UI.TextField(
            type = TextFieldType.NUMERIC,
            semantic = "item-default-value",
            value = defaultValue,
            onValueChange = { 
                defaultValue = it
                if (it.isNotBlank()) {
                    properties["default_value"] = it.toDoubleOrNull() ?: 0.0
                } else {
                    properties.remove("default_value")
                }
                onPropertiesChange(properties)
            },
            placeholder = "Valeur par défaut (optionnelle)",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Build display text for item properties based on tracking type
 */
private fun buildItemPropertiesText(properties: Map<String, Any>, trackingType: String): String {
    return when (trackingType) {
        "numeric" -> {
            val defaultValue = properties["default_value"]
            val unit = properties["unit"]?.toString()?.takeIf { it.isNotBlank() }
            
            when {
                defaultValue != null && unit != null -> "$defaultValue $unit"
                defaultValue != null -> defaultValue.toString()
                unit != null -> unit
                else -> ""
            }
        }
        else -> {
            // For other types, show all properties generically
            properties.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        }
    }
}