package com.assistant.tools.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.assistant.core.ui.UI
import com.assistant.core.ui.*
import com.assistant.core.ui.ThemeIconManager
import com.assistant.tools.tracking.TrackingToolType
import com.assistant.core.utils.NumberFormatting
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.tools.ToolTypeManager
import kotlinx.coroutines.launch
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
 * Helper function to check if all fields of an item are empty
 */
private fun allFieldsEmpty(name: String, properties: Map<String, Any>): Boolean {
    // Check if name is empty or blank
    if (name.trim().isNotEmpty()) return false
    
    // Check if any property has non-empty value
    return properties.values.all { value ->
        when (value) {
            is String -> value.trim().isEmpty()
            is Number -> value.toDouble() == 0.0
            else -> value.toString().trim().isEmpty()
        }
    }
}


/**
 * Helper function to safely get icon resource
 */
@Composable
private fun SafeIcon(
    context: android.content.Context,
    themeName: String,
    iconName: String,
    size: Dp,
    tint: androidx.compose.ui.graphics.Color? = null,
    background: androidx.compose.ui.graphics.Color? = null
) {
    if (ThemeIconManager.iconExists(context, themeName, iconName)) {
        val iconResource = ThemeIconManager.getIconResource(context, themeName, iconName)
        UI.Icon(
            resourceId = iconResource,
            size = size,
            contentDescription = null,
            tint = tint,
            background = background
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
    existingToolId: String? = null,
    onDelete: (() -> Unit)? = null
) {
    // VALDEBUG: Screen startup debug
    android.util.Log.d("VALDEBUG", "TrackingConfigScreen opened - existingToolId=$existingToolId")
    println("===============================")
    println("TrackingConfigScreen called with existingToolId: $existingToolId")
    println("===============================")
    
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val isEditing = existingToolId != null
    
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
    
    // Single config state - source of truth
    var config by remember { mutableStateOf(JSONObject(TrackingToolType.getDefaultConfig())) }
    
    // Derived states from config
    val name by remember { derivedStateOf { config.optString("name", "") } }
    val description by remember { derivedStateOf { config.optString("description", "") } }
    val management by remember { derivedStateOf { config.optString("management", "") } }
    val configValidation by remember { derivedStateOf { config.optString("config_validation", "") } }
    val dataValidation by remember { derivedStateOf { config.optString("data_validation", "") } }
    val displayMode by remember { derivedStateOf { config.optString("display_mode", "") } }
    val iconName by remember { derivedStateOf { config.optString("icon_name", "") } }
    val trackingType by remember { derivedStateOf { config.optString("type", "") } }
    val items by remember { derivedStateOf { 
        val itemsArray = config.optJSONArray("items")
        itemsArray?.let { loadItemsFromJSONArray(it) } ?: mutableListOf()
    } }
    
    // Config update helpers
    fun updateConfig(key: String, value: Any) {
        config.put(key, value)
        config = JSONObject(config.toString()) // Force recomposition
    }
    
    fun updateItems(newItems: MutableList<TrackingItem>) {
        val itemsArray = JSONArray()
        newItems.forEach { item ->
            val itemObj = JSONObject().apply {
                put("name", item.name)
                item.properties.forEach { (key, value) ->
                    put(key, value)
                }
            }
            itemsArray.put(itemObj)
        }
        updateConfig("items", itemsArray)
    }
    
    // Load config: NO FALLBACKS - CRASH IF DB FAILS
    LaunchedEffect(existingToolId) {
        android.util.Log.d("CONFIGDEBUG", "LaunchedEffect triggered - existingToolId: $existingToolId")
        
        if (existingToolId == null) {
            android.util.Log.d("CONFIGDEBUG", "No existingToolId, using default config for creation")
            config = JSONObject(TrackingToolType.getDefaultConfig())
            return@LaunchedEffect
        }
        
        android.util.Log.d("CONFIGDEBUG", "Calling coordinator.processUserAction for toolId: $existingToolId")
        val result = coordinator.processUserAction(
            "get->tool_instance",
            mapOf("tool_instance_id" to existingToolId)
        )
        
        android.util.Log.d("CONFIGDEBUG", "Coordinator result - status: ${result.status}, data: ${result.data}")
        
        if (result.status != CommandStatus.SUCCESS) {
            throw RuntimeException("CONFIGDEBUG: DB call failed - status: ${result.status}, error: ${result.error}")
        }
        
        val toolInstanceData = result.data?.get("tool_instance") as? Map<String, Any>
            ?: throw RuntimeException("CONFIGDEBUG: No tool_instance in result.data: ${result.data}")
            
        val configString = toolInstanceData["config_json"] as? String
            ?: throw RuntimeException("CONFIGDEBUG: No config_json in toolInstanceData: $toolInstanceData")
            
        android.util.Log.d("CONFIGDEBUG", "Config string from DB: $configString")
        val newConfig = JSONObject(configString)
        android.util.Log.d("CONFIGDEBUG", "New config items count: ${newConfig.optJSONArray("items")?.length() ?: 0}")
        config = newConfig
    }
    
    // UI state for item dialog
    var showItemDialog by remember { mutableStateOf(false) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var editItemName by remember { mutableStateOf(String()) }
    var editItemDefaultQuantity by remember { mutableStateOf(String()) }
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
    
    
    // State for error messages
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Save function avec validation V3
    val handleSave = {
        // Nettoyer la config avant validation
        val cleanConfig = cleanConfiguration(config)
        
        // Convertir JSONObject en Map pour SchemaValidator V3
        val configMap = cleanConfig.keys().asSequence().associateWith { key ->
            cleanConfig.get(key)
        }
        
        // Utiliser SchemaValidator V3 avec le schéma de configuration tracking
        val toolType = ToolTypeManager.getToolType("tracking")
        if (toolType != null) {
            val validation = SchemaValidator.validate(toolType, configMap, context, useDataSchema = false)
            
            if (validation.isValid) {
                onSave(cleanConfig.toString())
            } else {
                android.util.Log.e("TrackingConfigScreen", "Validation failed: ${validation.errorMessage}")
                errorMessage = validation.errorMessage ?: "Erreur de validation"
            }
        } else {
            android.util.Log.e("TrackingConfigScreen", "ToolType tracking not found")
            errorMessage = "Type d'outil introuvable"
        }
    }
    
    // Confirmation dialogs
    if (showTypeChangeWarning) {
        UI.Dialog(
            type = DialogType.DANGER,
            onConfirm = {
                pendingTrackingType?.let { newType ->
                    updateConfig("type", newType)
                    updateConfig("items", JSONArray())
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
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back button
        UI.PageHeader(
            title = if (isEditing) "Modifier Suivi" else "Créer Suivi",
            subtitle = null,
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onCancel,
            onRightClick = null
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
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
                    onChange = { updateConfig("name", it) },
                    required = true
                )
                
                UI.FormField(
                    label = "Description",
                    value = description,
                    onChange = { updateConfig("description", it) },
                    fieldType = FieldType.TEXT_MEDIUM
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
                    
                    UI.ActionButton(
                        action = ButtonAction.SELECT,
                        onClick = { showIconSelector = true }
                    )
                }
                
                UI.FormSelection(
                    label = "Mode d'affichage",
                    options = listOf("ICON", "MINIMAL", "LINE", "CONDENSED", "EXTENDED", "SQUARE", "FULL"),
                    selected = displayMode,
                    onSelect = { updateConfig("display_mode", it) },
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
                        updateConfig("management", when(selectedLabel) {
                            "Manuel" -> "manual"
                            "IA" -> "ai"
                            "Collaboratif" -> "collaborative"
                            else -> selectedLabel
                        })
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
                        updateConfig("config_validation", when(selectedLabel) {
                            "Activée" -> "enabled"
                            "Désactivée" -> "disabled"
                            else -> selectedLabel
                        })
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
                        updateConfig("data_validation", when(selectedLabel) {
                            "Activée" -> "enabled"
                            "Désactivée" -> "disabled"
                            else -> selectedLabel
                        })
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
                        "Timer (timer)",
                        "Choix (choice)",
                        "Compteur (counter)"
                    ),
                    selected = when(trackingType) {
                        "numeric" -> "Numérique (numeric)"
                        "text" -> "Texte (text)"
                        "scale" -> "Échelle (scale)"
                        "boolean" -> "Oui/Non (boolean)"
                        "timer" -> "Timer (timer)"
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
                            selectedLabel.contains("(timer)") -> "timer"
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
                            updateConfig("type", newType)
                        }
                        
                        // Cancel editing if in progress when type changes
                        if (trackingType != newType) {
                            editingItemIndex = null
                            editItemName = ""
                                    }
                    },
                    required = true
                )
                
                // Type-specific parameters
                TypeSpecificParameters(
                    trackingType = trackingType,
                    config = config,
                    updateConfig = ::updateConfig
                )
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
                        UI.ActionButton(
                            action = ButtonAction.ADD,
                            onClick = { 
                                // Ouvrir dialog pour nouvel item
                                editingItemIndex = null
                                editItemName = String()
                                editItemDefaultQuantity = String()
                                editItemUnit = String()
                                showItemDialog = true
                            }
                        )
                    }

                    // Tableau items
                    if (items.isEmpty()) {
                        UI.Text(
                            text = "Aucun item défini",
                            type = TextType.CAPTION,
                            fillMaxWidth = true,
                            textAlign = TextAlign.Center
                        )
                    } else{
                        //Ligne d'en-tête
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Colonnes ordre
                            Box(
                                modifier = Modifier.weight(2f),
                                contentAlignment = Alignment.Center
                            ) {}

                            // Colonne nom
                            Box(
                                modifier = Modifier.weight(4f),
                                contentAlignment = Alignment.Center
                            ) {
                                UI.Text(
                                    text = "Nom",
                                    type = TextType.CAPTION
                                )
                            }

                            // Colonnes spécifiques au type numeric
                            if (trackingType == "numeric") {
                                Box(
                                    modifier = Modifier.weight(2f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UI.Text(
                                        text = "Qté",
                                        type = TextType.CAPTION
                                    )
                                }

                                Box(
                                    modifier = Modifier.weight(2f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UI.Text(
                                        text = "Unité",
                                        type = TextType.CAPTION
                                    )
                                }
                            }

                            // Colones modifier + supprimer
                            Box(
                                modifier = Modifier.weight(2f),
                                contentAlignment = Alignment.Center
                            ) {}
                        }

                        // Afficher les items
                        items.forEachIndexed { itemIndex, item ->
                            ItemRowReadonly(
                                item = item,
                                itemIndex = itemIndex,
                                trackingType = trackingType,
                                onEdit = {
                                    editingItemIndex = itemIndex
                                    editItemName = item.name
                                    editItemDefaultQuantity = item.properties["default_quantity"]?.toString() ?: String()
                                    editItemUnit = item.properties["unit"]?.toString() ?: String()
                                    showItemDialog = true
                                },
                                onMoveUp = {
                                    if (itemIndex > 0) {
                                        val newItems = items.toMutableList()
                                        val temp = newItems[itemIndex]
                                        newItems[itemIndex] = newItems[itemIndex - 1]
                                        newItems[itemIndex - 1] = temp
                                        updateItems(newItems)
                                    }
                                },
                                onMoveDown = {
                                    if (itemIndex < items.size - 1) {
                                        val newItems = items.toMutableList()
                                        val temp = newItems[itemIndex]
                                        newItems[itemIndex] = newItems[itemIndex + 1]
                                        newItems[itemIndex + 1] = temp
                                        updateItems(newItems)
                                    }
                                },
                                onDelete = {
                                    val newItems = items.toMutableList()
                                    newItems.removeAt(itemIndex)
                                    updateItems(newItems)
                                }
                            )
                        }
                    }

                    

                    

                }
            }
        }
        
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
                    onClick = onDelete
                )
            }
        }
        
        
        // Item edit/create dialog
        if (showItemDialog) {
            val isCreating = editingItemIndex == null
            UI.Dialog(
                type = if (isCreating) DialogType.CREATE else DialogType.EDIT,
                onConfirm = {
                    val properties = mutableMapOf<String, Any>()
                    
                    // Build properties according to tracking type
                    when (trackingType) {
                        "numeric" -> {
                            properties["default_quantity"] = editItemDefaultQuantity
                            properties["unit"] = editItemUnit
                        }
                        // For other types (text, choice, scale, etc.), no additional properties needed
                        // The item name is sufficient
                    }
                    
                    // Check if all fields are empty - if so, silently handle removal/non-addition
                    if (allFieldsEmpty(editItemName, properties)) {
                        if (!isCreating && editingItemIndex != null) {
                            // Editing mode: remove the item from the list
                            val newItems = items.toMutableList()
                            newItems.removeAt(editingItemIndex!!)
                            updateItems(newItems)
                        }
                        // Creating mode: simply don't add anything (silent)
                    } else {
                        // Normal case: add or update the item
                        if (isCreating) {
                            val newItem = TrackingItem(editItemName, properties)
                            val newItems = items.toMutableList()
                            newItems.add(newItem)
                            updateItems(newItems)
                        } else {
                            editingItemIndex?.let { index ->
                                val newItems = items.toMutableList()
                                newItems[index] = TrackingItem(editItemName, properties)
                                updateItems(newItems)
                            }
                        }
                    }
                    
                    showItemDialog = false
                    editItemName = String()
                    editItemDefaultQuantity = String()
                    editItemUnit = String()
                    editingItemIndex = null
                },
                onCancel = {
                    showItemDialog = false
                    editItemName = String()
                    editItemDefaultQuantity = String()
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
                            value = editItemDefaultQuantity,
                            onChange = { editItemDefaultQuantity = it },
                            fieldType = FieldType.NUMERIC,
                            required = false
                        )
                        
                        UI.FormField(
                            label = "Unité",
                            value = editItemUnit,
                            onChange = { editItemUnit = it },
                            required = false
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
                                        updateConfig("icon_name", icon.id)
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    
    // Show error toast when errorMessage is set
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_LONG
            ).show()
            errorMessage = null
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // UP button (weight=1f)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            UI.ActionButton(
                action = ButtonAction.UP,
                display = ButtonDisplay.ICON,
                size = Size.S,
                onClick = onMoveUp
            )
        }
        
        // DOWN button (weight=1f)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            UI.ActionButton(
                action = ButtonAction.DOWN,
                display = ButtonDisplay.ICON,
                size = Size.S,
                onClick = onMoveDown
            )
        }
        
        // Nom (weight=4f)
        Box(
            modifier = Modifier.weight(4f),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(
                text = item.name,
                type = TextType.BODY
            )
        }
        
        // Champs spécifiques (uniquement pour numeric actuellement)

        if (trackingType == "numeric") {
            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center
            ) {
                val defaultQuantity = item.properties["default_quantity"]
                UI.Text(
                    text = defaultQuantity?.toString() ?: "-",
                    type = TextType.BODY
                )
            }

            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center
            ) {
                val unit = item.properties["unit"]?.toString()
                UI.Text(
                    text = unit ?: "-",
                    type = TextType.BODY
                )
            }
        }

        // EDIT button (weight=1f)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            UI.ActionButton(
                action = ButtonAction.EDIT,
                display = ButtonDisplay.ICON,
                size = Size.S,
                onClick = onEdit
            )
        }
        
        // DELETE button (weight=1f)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                display = ButtonDisplay.ICON,
                size = Size.S,
                requireConfirmation = true,
                onClick = onDelete
            )
        }
    }
}

/**
 * Build display text for item properties based on tracking type
 */
private fun buildItemPropertiesText(properties: Map<String, Any>, trackingType: String): String {
    return when (trackingType) {
        "numeric" -> {
            val defaultQuantity = properties["default_quantity"]
            val unit = properties["unit"]?.toString()?.takeIf { it.isNotBlank() }
            
            when {
                defaultQuantity != null && unit != null -> "$defaultQuantity $unit"
                defaultQuantity != null -> defaultQuantity.toString()
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

/**
 * Type-specific configuration parameters
 */
@Composable
private fun TypeSpecificParameters(
    trackingType: String,
    config: JSONObject,
    updateConfig: (String, Any) -> Unit
) {
    when (trackingType) {
        "scale" -> {
            // Use state variables instead of val to allow real-time updates
            var minValue by remember { mutableStateOf(config.optInt("min", 1).toString()) }
            var maxValue by remember { mutableStateOf(config.optInt("max", 10).toString()) }
            val minLabel = config.optString("min_label", "")
            val maxLabel = config.optString("max_label", "")
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Valeur minimale",
                            value = minValue,
                            onChange = { value ->
                                minValue = value
                                val intValue = value.toIntOrNull() ?: 1
                                updateConfig("min", intValue)
                            },
                            fieldType = FieldType.NUMERIC,
                            required = true
                        )
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Valeur maximale", 
                            value = maxValue,
                            onChange = { value ->
                                maxValue = value
                                val intValue = value.toIntOrNull() ?: 10
                                updateConfig("max", intValue)
                            },
                            fieldType = FieldType.NUMERIC,
                            required = true
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Label minimum",
                            value = minLabel,
                            onChange = { updateConfig("min_label", it) },
                            fieldType = FieldType.TEXT
                        )
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Label maximum",
                            value = maxLabel,
                            onChange = { updateConfig("max_label", it) },
                            fieldType = FieldType.TEXT
                        )
                    }
                }
            }
        }
        
        "boolean" -> {
            val trueLabel = config.optString("true_label", "Oui")
            val falseLabel = config.optString("false_label", "Non")
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = "Label \"Vrai\"",
                        value = trueLabel,
                        onChange = { updateConfig("true_label", it) },
                        fieldType = FieldType.TEXT
                    )
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = "Label \"Faux\"",
                        value = falseLabel,
                        onChange = { updateConfig("false_label", it) },
                        fieldType = FieldType.TEXT
                    )
                }
            }
        }
        
        "choice" -> {
            val optionsArray = config.optJSONArray("options") ?: JSONArray()
            val currentOptions = (0 until optionsArray.length()).map { 
                optionsArray.optString(it, "") 
            }.toMutableList()
            
            UI.DynamicList(
                label = "Options disponibles",
                items = currentOptions,
                onItemsChanged = { newOptions ->
                    // Sauvegarder toutes les options (même vides) pour permettre l'édition
                    // Le filtrage se fera lors de la sauvegarde finale de la configuration
                    val newArray = JSONArray()
                    newOptions.forEach { option ->
                        newArray.put(option) // Garder tous les éléments, même vides
                    }
                    updateConfig("options", newArray)
                },
                placeholder = "option",
                required = true,
                minItems = 2 // Au moins 2 options pour un choix
            )
        }
        
        "counter" -> {
            val allowDecrement = config.optBoolean("allow_decrement", true)
            
            UI.FormSelection(
                label = "Autoriser décrémentation",
                options = listOf("Oui", "Non"),
                selected = if (allowDecrement) "Oui" else "Non",
                onSelect = { selected ->
                    updateConfig("allow_decrement", selected == "Oui")
                }
            )
        }
        
        // NUMERIC, TEXT, TIMER n'ont pas de paramètres spécifiques de configuration
        // Les items prédéfinis suffisent pour leur configuration
    }
}

/**
 * Nettoie la configuration avant sauvegarde finale
 */
private fun cleanConfiguration(config: JSONObject): JSONObject {
    val cleanConfig = JSONObject(config.toString()) // Copie profonde
    
    // Nettoyer les options vides pour les types CHOICE
    if (cleanConfig.optString("type") == "choice") {
        android.util.Log.d("CONFIG_CLEAN", "Cleaning CHOICE config")
        val optionsArray = cleanConfig.optJSONArray("options")
        if (optionsArray != null) {
            android.util.Log.d("CONFIG_CLEAN", "Original options: $optionsArray")
            val cleanArray = JSONArray()
            for (i in 0 until optionsArray.length()) {
                val option = optionsArray.optString(i, "")
                val trimmedOption = option.trim()
                android.util.Log.d("CONFIG_CLEAN", "Option '$option' -> trimmed '$trimmedOption'")
                if (trimmedOption.isNotBlank()) {
                    cleanArray.put(trimmedOption)
                }
            }
            android.util.Log.d("CONFIG_CLEAN", "Clean options: $cleanArray")
            cleanConfig.put("options", cleanArray)
        }
    }
    
    // Les items prédéfinis sont toujours valides (nom obligatoire dans l'UI)
    
    return cleanConfig
}