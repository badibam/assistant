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
    onCancel: () -> Unit,
    existingConfig: String? = null,
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Mode detection
    val isEditing = existingConfig != null && existingToolId != null
    
    // Configuration state
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var management by remember { mutableStateOf("Manuel") }
    var configValidation by remember { mutableStateOf(true) }
    var dataValidation by remember { mutableStateOf(true) }
    var displayMode by remember { mutableStateOf("Icône") }
    var iconName by remember { mutableStateOf("activity") }
    
    // Tracking-specific state
    var trackingType by remember { mutableStateOf("numeric") }
    var showValue by remember { mutableStateOf(true) }
    var itemMode by remember { mutableStateOf("free") }
    var saveNewItems by remember { mutableStateOf(false) }
    var autoSwitch by remember { mutableStateOf(true) }
    
    // Groups and items state
    var groups: MutableList<TrackingGroup> by remember { 
        mutableStateOf(mutableListOf()) 
    }
    
    // UI state for adding items/groups
    var showAddGroup by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf<Int?>(null) } // Index du groupe
    var editingGroupIndex by remember { mutableStateOf<Int?>(null) } // Index du groupe en cours d'édition
    var newGroupName by remember { mutableStateOf("") }
    var editGroupName by remember { mutableStateOf("") }
    var newItemName by remember { mutableStateOf("") }
    var newItemProperties: MutableMap<String, Any> by remember { mutableStateOf(mutableMapOf<String, Any>()) }
    
    // État pour le sélecteur d'icônes
    var showIconSelector by remember { mutableStateOf(false) }
    
    // Liste des icônes disponibles (hardcodée pour l'instant)
    val availableIcons = listOf("activity", "trending-up")
    
    // TODO: Remplacer par ThemeIconManager.getAvailableIcons("default") quand implémenté
    
    // Load existing config if provided
    LaunchedEffect(existingConfig) {
        existingConfig?.let { configJson ->
            try {
                val config = JSONObject(configJson)
                
                // Common fields
                name = config.optString("name", "")
                description = config.optString("description", "")
                management = config.optString("management", "Manuel")
                configValidation = config.optBoolean("config_validation", true)
                dataValidation = config.optBoolean("data_validation", true)
                displayMode = config.optString("display_mode", "Icône")
                iconName = config.optString("icon_name").ifEmpty { "activity" }
                
                // Tracking-specific fields
                trackingType = config.optString("type", "numeric")
                showValue = config.optBoolean("show_value", true)
                itemMode = config.optString("item_mode", "free")
                saveNewItems = config.optBoolean("save_new_items", false)
                autoSwitch = config.optBoolean("auto_switch", true)
                
                // Groups and items
                val groupsArray = config.optJSONArray("groups")
                if (groupsArray != null) {
                    val loadedGroups = mutableListOf<TrackingGroup>()
                    for (i in 0 until groupsArray.length()) {
                        val groupObj = groupsArray.getJSONObject(i)
                        val groupName = groupObj.getString("name")
                        val itemsArray = groupObj.optJSONArray("items")
                        val items = mutableListOf<TrackingItem>()
                        
                        if (itemsArray != null) {
                            for (j in 0 until itemsArray.length()) {
                                val itemObj = itemsArray.getJSONObject(j)
                                val itemName = itemObj.getString("name")
                                val properties = mutableMapOf<String, Any>()
                                
                                // Load all properties except name
                                itemObj.keys().forEach { key ->
                                    if (key != "name") {
                                        properties[key] = itemObj.get(key)
                                    }
                                }
                                
                                items.add(TrackingItem(itemName, properties))
                            }
                        }
                        
                        loadedGroups.add(TrackingGroup(groupName, items))
                    }
                    groups = loadedGroups
                }
                
            } catch (e: Exception) {
            }
        } ?: run {
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
                
                // Groups list or empty state
                if (groups.isEmpty()) {
                    UI.Card(
                        type = CardType.SYSTEM,
                        semantic = "no-groups",
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Text(
                            text = "Aucun groupe défini. Cliquez sur '+ Groupe' pour commencer.",
                            type = TextType.BODY,
                            semantic = "empty-state-text"
                        )
                    }
                } else {
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
                                UI.Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Group reorder buttons
                                    UI.Column {
                                        UI.Button(
                                            type = ButtonType.GHOST,
                                            semantic = "move-group-up-$groupIndex",
                                            onClick = { 
                                                if (groupIndex > 0) {
                                                    val newGroups = groups.toMutableList()
                                                    val temp = newGroups[groupIndex]
                                                    newGroups[groupIndex] = newGroups[groupIndex - 1]
                                                    newGroups[groupIndex - 1] = temp
                                                    groups = newGroups
                                                }
                                            },
                                            enabled = groupIndex > 0
                                        ) {
                                            UI.Text(
                                                text = "↑",
                                                type = TextType.CAPTION,
                                                semantic = "button-label"
                                            )
                                        }
                                        
                                        UI.Button(
                                            type = ButtonType.GHOST,
                                            semantic = "move-group-down-$groupIndex",
                                            onClick = { 
                                                if (groupIndex < groups.size - 1) {
                                                    val newGroups = groups.toMutableList()
                                                    val temp = newGroups[groupIndex]
                                                    newGroups[groupIndex] = newGroups[groupIndex + 1]
                                                    newGroups[groupIndex + 1] = temp
                                                    groups = newGroups
                                                }
                                            },
                                            enabled = groupIndex < groups.size - 1
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
                                        semantic = "edit-group-$groupIndex",
                                        onClick = { 
                                            editingGroupIndex = groupIndex
                                            editGroupName = group.name
                                        },
                                        enabled = true
                                    ) {
                                        UI.Text(
                                            text = "✎",
                                            type = TextType.CAPTION,
                                            semantic = "button-label"
                                        )
                                    }
                                    
                                    UI.Text(
                                        text = group.name,
                                        type = TextType.SUBTITLE,
                                        semantic = "group-name"
                                    )
                                }
                                
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
                                    
                                    UI.Button(
                                        type = ButtonType.DANGER,
                                        semantic = "delete-group-$groupIndex",
                                        onClick = { 
                                            val newGroups = groups.toMutableList()
                                            newGroups.removeAt(groupIndex)
                                            groups = newGroups
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
                                        UI.Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Item reorder buttons
                                            UI.Column {
                                                UI.Button(
                                                    type = ButtonType.GHOST,
                                                    semantic = "move-item-up-$groupIndex-$itemIndex",
                                                    onClick = { 
                                                        if (itemIndex > 0) {
                                                            val newGroups = groups.toMutableList()
                                                            val currentGroup = newGroups[groupIndex]
                                                            val newItems = currentGroup.items.toMutableList()
                                                            val temp = newItems[itemIndex]
                                                            newItems[itemIndex] = newItems[itemIndex - 1]
                                                            newItems[itemIndex - 1] = temp
                                                            newGroups[groupIndex] = currentGroup.copy(items = newItems)
                                                            groups = newGroups
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
                                                    semantic = "move-item-down-$groupIndex-$itemIndex",
                                                    onClick = { 
                                                        if (itemIndex < group.items.size - 1) {
                                                            val newGroups = groups.toMutableList()
                                                            val currentGroup = newGroups[groupIndex]
                                                            val newItems = currentGroup.items.toMutableList()
                                                            val temp = newItems[itemIndex]
                                                            newItems[itemIndex] = newItems[itemIndex + 1]
                                                            newItems[itemIndex + 1] = temp
                                                            newGroups[groupIndex] = currentGroup.copy(items = newItems)
                                                            groups = newGroups
                                                        }
                                                    },
                                                    enabled = itemIndex < group.items.size - 1
                                                ) {
                                                    UI.Text(
                                                        text = "↓",
                                                        type = TextType.CAPTION,
                                                        semantic = "button-label"
                                                    )
                                                }
                                            }
                                            
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
                                        }
                                        
                                        UI.Button(
                                            type = ButtonType.DANGER,
                                            semantic = "delete-item-$groupIndex-$itemIndex",
                                            onClick = { 
                                                val newGroups = groups.toMutableList()
                                                val currentGroup = newGroups[groupIndex]
                                                val newItems = currentGroup.items.toMutableList()
                                                newItems.removeAt(itemIndex)
                                                newGroups[groupIndex] = currentGroup.copy(items = newItems)
                                                groups = newGroups
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
                            
                            // Show edit group form if this group is selected for editing
                            if (editingGroupIndex == groupIndex) {
                                UI.Spacer(modifier = Modifier.height(8.dp))
                                UI.Card(
                                    type = CardType.ZONE,
                                    semantic = "edit-group-form-$groupIndex",
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UI.Column {
                                        UI.Text(
                                            text = "Renommer le groupe",
                                            type = TextType.SUBTITLE,
                                            semantic = "form-title"
                                        )
                                        
                                        UI.Spacer(modifier = Modifier.height(8.dp))
                                        
                                        UI.TextField(
                                            type = TextFieldType.STANDARD,
                                            semantic = "edit-group-name",
                                            value = editGroupName,
                                            onValueChange = { editGroupName = it },
                                            placeholder = "Nom du groupe",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        UI.Spacer(modifier = Modifier.height(8.dp))
                                        
                                        UI.Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            UI.Button(
                                                type = ButtonType.SECONDARY,
                                                semantic = "cancel-edit-group",
                                                onClick = { 
                                                    editingGroupIndex = null
                                                    editGroupName = ""
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
                                                semantic = "save-edit-group",
                                                onClick = { 
                                                    if (editGroupName.isNotBlank()) {
                                                        val newGroups = groups.toMutableList()
                                                        newGroups[groupIndex] = group.copy(name = editGroupName)
                                                        groups = newGroups
                                                        editingGroupIndex = null
                                                        editGroupName = ""
                                                    }
                                                },
                                                enabled = editGroupName.isNotBlank() && editGroupName != group.name
                                            ) {
                                                UI.Text(
                                                    text = "Renommer",
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
            if (isEditing && onDelete != null) {
                // Mode édition : bouton supprimer en premier
                UI.Button(
                    type = ButtonType.DANGER,
                    semantic = "delete-button",
                    onClick = {
                        onDelete()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Text(
                        text = stringResource(R.string.delete),
                        type = TextType.LABEL,
                        semantic = "button-label"
                    )
                }
                
                UI.Spacer(modifier = Modifier.height(12.dp))
            }
            
            UI.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Button(
                    type = ButtonType.SECONDARY,
                    semantic = "cancel-button",
                    onClick = {
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
        
        // Dialogue sélecteur d'icônes
        UI.SelectionDialog(
            isVisible = showIconSelector,
            title = "Choisir une icône",
            items = availableIcons,
            selectedItem = iconName,
            onItemSelected = { selectedIconName ->
                iconName = selectedIconName
            },
            onDismiss = { showIconSelector = false }
        ) { availableIconName ->
            UI.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val context = LocalContext.current
                val iconResource = ThemeIconManager.getIconResource(context, "default", availableIconName)
                
                ToolIcon(
                    iconResource = iconResource,
                    size = 24.dp
                )
                
                UI.Text(
                    text = availableIconName,
                    type = TextType.BODY,
                    semantic = "icon-name"
                )
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
                "choice" -> ChoiceItemProperties(properties, onPropertiesChange)
                "counter" -> CounterItemProperties(properties, onPropertiesChange)
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
 * Properties form for text tracking items
 */
@Composable
private fun TextItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    // Text items don't need specific properties - just free text input
    
    UI.Card(
        type = CardType.ZONE,
        semantic = "text-item-info",
        modifier = Modifier.fillMaxWidth()
    ) {
        UI.Text(
            text = "Les éléments de type texte permettent la saisie libre sans contraintes. " +
                    "Seul le nom de l'élément est nécessaire.",
            type = TextType.CAPTION,
            semantic = "info-text"
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
    
    // Initialize default scale_size if not set
    LaunchedEffect(Unit) {
        if (properties["scale_size"] == null) {
            properties["scale_size"] = 5
            onPropertiesChange(properties)
        }
    }
    
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
    
    // Initialize default labels if not set
    LaunchedEffect(Unit) {
        if (properties["true_label"] == null) {
            properties["true_label"] = "Oui"
        }
        if (properties["false_label"] == null) {
            properties["false_label"] = "Non"
        }
        onPropertiesChange(properties)
    }
    
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

/**
 * Properties form for choice tracking items
 */
@Composable
private fun ChoiceItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    var options: MutableList<String> by remember { 
        mutableStateOf(
            (properties["options"] as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList() ?: mutableListOf()
        )
    }
    var defaultValue by remember { mutableStateOf(properties["default_value"]?.toString() ?: "") }
    var newOption by remember { mutableStateOf("") }
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Options list
        UI.Text(
            text = "Options disponibles",
            type = TextType.SUBTITLE,
            semantic = "options-label"
        )
        
        if (options.isEmpty()) {
            UI.Text(
                text = "Aucune option définie",
                type = TextType.CAPTION,
                semantic = "no-options"
            )
        } else {
            options.forEachIndexed { index, option ->
                UI.Card(
                    type = CardType.ZONE,
                    semantic = "option-$index",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UI.Text(
                            text = option,
                            type = TextType.BODY,
                            semantic = "option-text"
                        )
                        
                        UI.Button(
                            type = ButtonType.DANGER,
                            semantic = "delete-option-$index",
                            onClick = {
                                val newOptions = options.toMutableList()
                                newOptions.removeAt(index)
                                options = newOptions
                                properties["options"] = options.toList()
                                onPropertiesChange(properties)
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
        
        // Add new option
        UI.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UI.TextField(
                type = TextFieldType.STANDARD,
                semantic = "new-option",
                value = newOption,
                onValueChange = { newOption = it },
                placeholder = "Nouvelle option",
                modifier = Modifier.weight(1f)
            )
            
            UI.Button(
                type = ButtonType.SECONDARY,
                semantic = "add-option",
                onClick = {
                    if (newOption.isNotBlank() && !options.contains(newOption)) {
                        options.add(newOption)
                        properties["options"] = options.toList()
                        onPropertiesChange(properties)
                        newOption = ""
                    }
                },
                enabled = newOption.isNotBlank() && !options.contains(newOption)
            ) {
                UI.Text(
                    text = "Ajouter",
                    type = TextType.LABEL,
                    semantic = "button-label"
                )
            }
        }
        
        // Default value selection
        if (options.isNotEmpty()) {
            UI.Spacer(modifier = Modifier.height(8.dp))
            
            UI.Text(
                text = "Valeur par défaut (optionnelle)",
                type = TextType.SUBTITLE,
                semantic = "default-label"
            )
            
            // Add "None" option for no default
            val allOptions = listOf("" to "Aucune") + options.map { it to it }
            
            allOptions.forEach { (value, label) ->
                UI.Button(
                    type = if (defaultValue == value) ButtonType.PRIMARY else ButtonType.GHOST,
                    semantic = "default-$value",
                    onClick = {
                        defaultValue = value
                        if (value.isEmpty()) {
                            properties.remove("default_value")
                        } else {
                            properties["default_value"] = value
                        }
                        onPropertiesChange(properties)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
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

/**
 * Properties form for counter tracking items
 */
@Composable
private fun CounterItemProperties(
    properties: MutableMap<String, Any>,
    onPropertiesChange: (MutableMap<String, Any>) -> Unit
) {
    var step by remember { mutableStateOf(properties["step"]?.toString() ?: "1") }
    var unit by remember { mutableStateOf(properties["unit"]?.toString() ?: "") }
    
    // Initialize default step if not set
    LaunchedEffect(Unit) {
        if (properties["step"] == null) {
            properties["step"] = 1
            onPropertiesChange(properties)
        }
    }
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UI.TextField(
            type = TextFieldType.NUMERIC,
            semantic = "item-step",
            value = step,
            onValueChange = { 
                step = it
                properties["step"] = it.toIntOrNull() ?: 1
                onPropertiesChange(properties)
            },
            placeholder = "Pas d'incrémentation (défaut: 1)",
            modifier = Modifier.fillMaxWidth()
        )
        
        UI.TextField(
            type = TextFieldType.STANDARD,
            semantic = "item-unit",
            value = unit,
            onValueChange = { 
                unit = it
                if (it.isNotBlank()) {
                    properties["unit"] = it
                } else {
                    properties.remove("unit")
                }
                onPropertiesChange(properties)
            },
            placeholder = "Unité d'affichage (verres, pages, etc.)",
            modifier = Modifier.fillMaxWidth()
        )
    }
}