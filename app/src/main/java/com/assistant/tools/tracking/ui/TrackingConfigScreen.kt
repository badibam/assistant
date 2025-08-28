package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.ui.ThemeIconManager
import com.assistant.tools.tracking.TrackingToolType
import com.assistant.core.utils.NumberFormatting
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
 * Helper function to safely get icon resource
 */
@Composable
private fun SafeIcon(
    context: android.content.Context,
    themeName: String,
    iconName: String,
    size: Dp
) {
    if (ThemeIconManager.iconExists(context, themeName, iconName)) {
        val iconResource = ThemeIconManager.getIconResource(context, themeName, iconName)
        UI.Icon(
            resourceId = iconResource,
            size = size,
            contentDescription = null
        )
    } else {
        UI.Text("❓", TextType.BODY)
    }
}

/**
 * Configuration screen for Tracking tool type
 * Uses UI_DECISIONS.md patterns with full functionality restored
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
    val isEditing = existingConfig != null && existingToolId != null
    
    // Helper function pour charger items depuis JSONArray
    fun loadItemsFromJSONArray(itemsArray: JSONArray): MutableList<TrackingItem> {
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
        return loadedItems
    }
    
    // Configuration state - Initialisation immédiate depuis config existante ou défaut
    val defaultConfig by remember { mutableStateOf(JSONObject(TrackingToolType.getDefaultConfig())) }
    
    var name by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("name") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("name")
        )
    }
    var description by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("description") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("description")
        )
    }
    var management by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("management") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("management")
        )
    }
    var configValidation by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("config_validation") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("config_validation")
        )
    }
    var dataValidation by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("data_validation") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("data_validation")
        )
    }
    var displayMode by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("display_mode") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("display_mode")
        )
    }
    var iconName by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("icon_name") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("icon_name")
        )
    }
    
    var trackingType by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optString("type") } catch (e: Exception) { null }
            } ?: defaultConfig.optString("type")
        )
    }
    var autoSwitch by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { 
                try { JSONObject(it).optBoolean("auto_switch") } catch (e: Exception) { null }
            } ?: defaultConfig.optBoolean("auto_switch")
        )
    }
    
    // Items management state - Initialisation immédiate
    var items: MutableList<TrackingItem> by remember(existingConfig) { 
        mutableStateOf(
            existingConfig?.let { config ->
                try {
                    val configJson = JSONObject(config)
                    val itemsArray = configJson.optJSONArray("items")
                    itemsArray?.let { loadItemsFromJSONArray(it) }
                } catch (e: Exception) { null }
            } ?: run {
                val defaultItemsArray = defaultConfig.optJSONArray("items")
                defaultItemsArray?.let { loadItemsFromJSONArray(it) } ?: mutableListOf()
            }
        )
    }
    
    // UI state for item dialog
    var showItemDialog by remember { mutableStateOf(false) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var editItemName by remember { mutableStateOf(String()) }
    var editItemDefaultValue by remember { mutableStateOf(String()) }
    var editItemUnit by remember { mutableStateOf(String()) }
    
    // État pour le sélecteur d'icônes
    var showIconSelector by remember { mutableStateOf(false) }
    
    // État pour la confirmation de changement de type
    var showTypeChangeWarning by remember { mutableStateOf(false) }
    var pendingTrackingType by remember { mutableStateOf<String?>(null) }
    
    // Chargement dynamique des icônes disponibles
    val availableIcons by remember { 
        mutableStateOf(ThemeIconManager.getAvailableIcons(context, "default"))
    }
    
    
    // Save function
    val handleSave = {
        // Validation des champs obligatoires
        val isValid = name.isNotBlank() && 
                     iconName.isNotBlank() && 
                     displayMode.isNotBlank() &&
                     management.isNotBlank() &&
                     configValidation.isNotBlank() &&
                     dataValidation.isNotBlank() &&
                     trackingType.isNotBlank()
        
        if (isValid) {
            val config = JSONObject().apply {
            put("name", name.trim())
            put("description", description.trim())
            put("management", management)
            put("config_validation", configValidation)
            put("data_validation", dataValidation)
            put("display_mode", displayMode)
            put("icon_name", iconName)
            put("type", trackingType)
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
    }
    
    // Confirmation dialogs
    if (showTypeChangeWarning) {
        UI.Dialog(
            type = DialogType.DANGER,
            onConfirm = {
                pendingTrackingType?.let { newType ->
                    trackingType = newType
                    items.clear()
                }
                showTypeChangeWarning = false
                pendingTrackingType = null
                
                // Cancel editing if in progress when type changes
                editingItemIndex = null
                editItemName = ""
            },
            onCancel = {
                showTypeChangeWarning = false
                pendingTrackingType = null
            }
        ) {
            UI.Text(
                "Vous avez ${items.size} élément(s) prédéfini(s). Changer le type supprimera tous les éléments existants. Continuer ?",
                TextType.BODY
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
                    required = true
                )
                
                UI.FormField(
                    label = "Description",
                    value = description,
                    onChange = { description = it }
                )
                
                // Sélection d'icône
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UI.Text("Icône:", TextType.LABEL)
                    
                    // Icône actuelle avec SafeIcon
                    SafeIcon(context, "default", iconName, 32.dp)
                    
                    UI.Button(
                        type = ButtonType.SECONDARY,
                        onClick = { showIconSelector = true }
                    ) {
                        UI.Text("Choisir", TextType.LABEL)
                    }
                }
                
                UI.FormSelection(
                    label = "Mode d'affichage",
                    options = listOf("ICON", "MINIMAL", "LINE", "CONDENSED", "EXTENDED", "SQUARE", "FULL"),
                    selected = displayMode,
                    onSelect = { displayMode = it },
                    required = true
                )
                
                UI.FormSelection(
                    label = "Gestion",
                    options = listOf("Manuel", "IA", "Collaboratif"),
                    selected = when(management) {
                        "manual" -> "Manuel"
                        "ai" -> "IA"
                        "collaborative" -> "Collaboratif"
                        else -> management
                    },
                    onSelect = { selectedLabel ->
                        management = when(selectedLabel) {
                            "Manuel" -> "manual"
                            "IA" -> "ai"
                            "Collaboratif" -> "collaborative"
                            else -> selectedLabel
                        }
                    },
                    required = true
                )
                
                UI.FormSelection(
                    label = "Validation config par IA",
                    options = listOf("Activée", "Désactivée"),
                    selected = when(configValidation) {
                        "enabled" -> "Activée"
                        "disabled" -> "Désactivée"
                        else -> configValidation
                    },
                    onSelect = { selectedLabel ->
                        configValidation = when(selectedLabel) {
                            "Activée" -> "enabled"
                            "Désactivée" -> "disabled"
                            else -> selectedLabel
                        }
                    },
                    required = true
                )
                
                UI.FormSelection(
                    label = "Validation données par IA",
                    options = listOf("Activée", "Désactivée"),
                    selected = when(dataValidation) {
                        "enabled" -> "Activée"
                        "disabled" -> "Désactivée"
                        else -> dataValidation
                    },
                    onSelect = { selectedLabel ->
                        dataValidation = when(selectedLabel) {
                            "Activée" -> "enabled"
                            "Désactivée" -> "disabled"
                            else -> selectedLabel
                        }
                    },
                    required = true
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
                    options = listOf(
                        "Numérique (numeric)",
                        "Texte (text)", 
                        "Échelle (scale)",
                        "Oui/Non (boolean)",
                        "Durée (duration)",
                        "Choix (choice)",
                        "Compteur (counter)"
                    ),
                    selected = when(trackingType) {
                        "numeric" -> "Numérique (numeric)"
                        "text" -> "Texte (text)"
                        "scale" -> "Échelle (scale)"
                        "boolean" -> "Oui/Non (boolean)"
                        "duration" -> "Durée (duration)"
                        "choice" -> "Choix (choice)"
                        "counter" -> "Compteur (counter)"
                        else -> trackingType
                    },
                    onSelect = { selectedLabel ->
                        val newType = when {
                            selectedLabel.contains("(numeric)") -> "numeric"
                            selectedLabel.contains("(text)") -> "text"
                            selectedLabel.contains("(scale)") -> "scale"
                            selectedLabel.contains("(boolean)") -> "boolean"
                            selectedLabel.contains("(duration)") -> "duration"
                            selectedLabel.contains("(choice)") -> "choice"
                            selectedLabel.contains("(counter)") -> "counter"
                            else -> selectedLabel
                        }
                        
                        if (trackingType != newType && items.isNotEmpty()) {
                            // Show warning if there are existing items
                            pendingTrackingType = newType
                            showTypeChangeWarning = true
                        } else {
                            // No items or same type, change directly
                            trackingType = newType
                        }
                        
                        // Cancel editing if in progress when type changes
                        if (trackingType != newType) {
                            editingItemIndex = null
                            editItemName = ""
                                    }
                    },
                    required = true
                )
                
                
                // Auto-switch (only for duration)
                if (trackingType == "duration") {
                    UI.FormSelection(
                        label = "Commutation auto",
                        options = listOf("Activée", "Désactivée"),
                        selected = when(autoSwitch) {
                            true -> "Activée"
                            false -> "Désactivée"
                        },
                        onSelect = { selectedLabel ->
                            autoSwitch = when(selectedLabel) {
                                "Activée" -> true
                                "Désactivée" -> false
                                else -> selectedLabel == "Activée"
                            }
                        }
                    )
                }
            }
        }
        
        // Card 3: Liste des items prédéfinis
        if (trackingType.isNotBlank()) {
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
                        UI.Button(
                            type = ButtonType.SECONDARY,
                            onClick = { 
                                // Ouvrir dialog pour nouvel item
                                editingItemIndex = null
                                editItemName = String()
                                editItemDefaultValue = String()
                                editItemUnit = String()
                                showItemDialog = true
                            }
                        ) {
                            UI.Text("+ Élément", TextType.LABEL)
                        }
                    }
                    
                    // En-tête du tableau (toujours affiché)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colonne ordre
                        Box(modifier = Modifier.width(60.dp)) {
                            UI.Text("Ordre", TextType.CAPTION)
                        }
                        
                        // Colonne nom
                        Box(modifier = Modifier.weight(0.4f).padding(horizontal = 8.dp)) {
                            UI.Text("Nom", TextType.CAPTION)
                        }
                        
                        // Colonnes spécifiques au type numeric
                        if (trackingType == "numeric") {
                            Box(modifier = Modifier.weight(0.25f).padding(horizontal = 8.dp)) {
                                UI.Text("Qté par défaut", TextType.CAPTION)
                            }
                            
                            Box(modifier = Modifier.weight(0.25f).padding(horizontal = 8.dp)) {
                                UI.Text("Unité", TextType.CAPTION)
                            }
                        }
                        
                        // Colonne actions
                        Box(modifier = Modifier.width(72.dp)) {
                            UI.Text("Actions", TextType.CAPTION)
                        }
                    }
                    
                    // Tableau items
                    if (items.isEmpty()) {
                        UI.Text("Aucun item défini", TextType.BODY)
                    }
                    
                    // Afficher les items s'il y en a
                    items.forEachIndexed { itemIndex, item ->
                        ItemRowReadonly(
                            item = item,
                            itemIndex = itemIndex,
                            trackingType = trackingType,
                            onEdit = { 
                                editingItemIndex = itemIndex
                                editItemName = item.name
                                editItemDefaultValue = item.properties["default_value"]?.toString() ?: String()
                                editItemUnit = item.properties["unit"]?.toString() ?: String()
                                showItemDialog = true
                            },
                            onMoveUp = {
                                if (itemIndex > 0) {
                                    val newItems = items.toMutableList()
                                    val temp = newItems[itemIndex]
                                    newItems[itemIndex] = newItems[itemIndex - 1]
                                    newItems[itemIndex - 1] = temp
                                    items = newItems
                                }
                            },
                            onMoveDown = {
                                if (itemIndex < items.size - 1) {
                                    val newItems = items.toMutableList()
                                    val temp = newItems[itemIndex]
                                    newItems[itemIndex] = newItems[itemIndex + 1]
                                    newItems[itemIndex + 1] = temp
                                    items = newItems
                                }
                            },
                            onDelete = {
                                val newItems = items.toMutableList()
                                newItems.removeAt(itemIndex)
                                items = newItems
                            }
                        )
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
                    type = ButtonType.SECONDARY,
                    onClick = onDelete
                ) {
                    UI.Text("Supprimer", TextType.LABEL)
                }
            }
        }
        
        
        // Item edit/create dialog
        if (showItemDialog) {
            val isCreating = editingItemIndex == null
            UI.Dialog(
                type = if (isCreating) DialogType.CREATE else DialogType.EDIT,
                onConfirm = {
                    if (editItemName.isNotBlank()) {
                        val properties = mutableMapOf<String, Any>()
                        if (editItemDefaultValue.isNotBlank()) {
                            val parsed = NumberFormatting.parseUserInput(editItemDefaultValue)
                            if (parsed != null) {
                                properties["default_value"] = parsed
                            }
                        }
                        if (editItemUnit.isNotBlank()) {
                            properties["unit"] = editItemUnit
                        }
                        
                        if (isCreating) {
                            val newItem = TrackingItem(editItemName, properties)
                            val newItems = items.toMutableList()
                            newItems.add(newItem)
                            items = newItems
                        } else {
                            editingItemIndex?.let { index ->
                                items[index] = TrackingItem(editItemName, properties)
                            }
                        }
                        showItemDialog = false
                        editItemName = String()
                        editItemDefaultValue = String()
                        editItemUnit = String()
                        editingItemIndex = null
                    }
                },
                onCancel = {
                    showItemDialog = false
                    editItemName = String()
                    editItemDefaultValue = String()
                    editItemUnit = String()
                    editingItemIndex = null
                }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UI.Text(
                        if (isCreating) "Créer un élément" else "Modifier l'élément",
                        TextType.SUBTITLE
                    )
                    
                    UI.FormField(
                        label = "Nom",
                        value = editItemName,
                        onChange = { editItemName = it },
                        required = true
                    )
                    
                    // Champs spécifiques selon le type de tracking
                    if (trackingType == "numeric") {
                        UI.FormField(
                            label = "Quantité par défaut",
                            value = editItemDefaultValue,
                            onChange = { editItemDefaultValue = it },
                            type = TextFieldType.NUMERIC,
                            fieldType = FieldType.NUMERIC
                        )
                        
                        UI.FormField(
                            label = "Unité",
                            value = editItemUnit,
                            onChange = { editItemUnit = it }
                        )
                    }
                }
            }
        }
        
        
        // Icon selector dialog  
        if (showIconSelector) {
            UI.Dialog(
                type = DialogType.SELECTION,
                onConfirm = {},
                onCancel = { showIconSelector = false }
            ) {
                Column {
                    UI.Text("Choisir une icône", TextType.SUBTITLE)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Grille d'icônes 3 par ligne
                    availableIcons.chunked(3).forEach { iconRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            iconRow.forEach { icon ->
                                // Case carrée avec icône + texte
                                UI.Button(
                                    type = if (iconName == icon.id) ButtonType.PRIMARY else ButtonType.SECONDARY,
                                    onClick = {
                                        iconName = icon.id
                                        showIconSelector = false
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        SafeIcon(context, "default", icon.id, 32.dp)
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        UI.Text(
                                            text = icon.displayName,
                                            type = TextType.CAPTION
                                        )
                                    }
                                }
                            }
                            
                            // Remplir les cases vides si moins de 3 icônes dans la ligne
                            repeat(3 - iconRow.size) {
                                Spacer(modifier = Modifier.size(80.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}


/**
 * Composable pour une ligne d'item readonly avec actions
 */
@Composable
private fun ItemRowReadonly(
    item: TrackingItem,
    itemIndex: Int,
    trackingType: String,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Up/Down buttons côte à côte
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.width(60.dp)
        ) {
            UI.UpButton(
                size = Size.XS,
                onClick = onMoveUp
            )
            UI.DownButton(
                size = Size.XS,
                onClick = onMoveDown
            )
        }
        
        // Name column
        Box(
            modifier = Modifier.weight(0.4f).padding(horizontal = 8.dp)
        ) {
            UI.Text(item.name, TextType.BODY)
        }
        
        // Value columns for numeric type
        if (trackingType == "numeric") {
            Box(
                modifier = Modifier.weight(0.25f).padding(horizontal = 8.dp)
            ) {
                val defaultValue = item.properties["default_value"]
                UI.Text(defaultValue?.toString() ?: "-", TextType.BODY)
            }
            
            Box(
                modifier = Modifier.weight(0.25f).padding(horizontal = 8.dp)
            ) {
                val unit = item.properties["unit"]?.toString()
                UI.Text(unit ?: "-", TextType.BODY)
            }
        }
        
        // Action buttons
        UI.EditButton(
            size = Size.XS,
            onClick = onEdit
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        UI.DeleteButton(
            size = Size.XS,
            onClick = onDelete
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