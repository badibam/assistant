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
import com.assistant.core.strings.Strings
import com.assistant.tools.tracking.TrackingToolType
import com.assistant.core.utils.NumberFormatting
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.mapSingleData
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.tools.ui.ToolGeneralConfigSection
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

/**
 * Clears all type-specific configuration fields when changing tracking type
 * This prevents validation errors from leftover fields of the previous type
 */
private fun clearTypeSpecificFields(config: JSONObject) {
    // Scale-specific fields
    config.remove("min")
    config.remove("max")
    config.remove("min_label")
    config.remove("max_label")
    
    // Counter-specific fields
    config.remove("allow_decrement")
    
    // Boolean-specific fields  
    config.remove("true_label")
    config.remove("false_label")
    
    // Note: items is already cleared in the calling code
    // Note: choice-specific "options" would go here if it existed in schema
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
    
    // Strings context
    val s = remember { Strings.`for`(tool = "tracking", context = context) }
    
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
    
    // Derived states from config (only used ones)
    val trackingType by remember { derivedStateOf { config.optString("type", "") } }
    val items by remember { derivedStateOf { 
        val itemsArray = config.optJSONArray("items")
        itemsArray?.let { loadItemsFromJSONArray(it) } ?: mutableListOf()
    } }
    
    // Track original type for data deletion detection
    var originalType by remember { mutableStateOf("") }
    var initialConfigString by remember { mutableStateOf("") }
    
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
        
        if (!result.isSuccess) {
            throw RuntimeException("CONFIGDEBUG: DB call failed - status: ${result.status}, error: ${result.error}")
        }
        
        val toolInstanceData = result.mapSingleData("tool_instance") { it }
            ?: throw RuntimeException("CONFIGDEBUG: No tool_instance in result.data: ${result.data}")
            
        val configString = toolInstanceData["config_json"] as? String
            ?: throw RuntimeException("CONFIGDEBUG: No config_json in toolInstanceData: $toolInstanceData")
            
        android.util.Log.d("CONFIGDEBUG", "Config string from DB: $configString")
        val newConfig = JSONObject(configString)
        android.util.Log.d("CONFIGDEBUG", "New config items count: ${newConfig.optJSONArray("items")?.length() ?: 0}")
        
        // Capture original config and type before updating
        initialConfigString = configString
        originalType = newConfig.optString("type", "")
        android.util.Log.d("CONFIGDEBUG", "Original type captured: $originalType")
        
        config = newConfig
    }
    
    // UI state for item dialog
    var showItemDialog by remember { mutableStateOf(false) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var editItemName by remember { mutableStateOf(String()) }
    var editItemDefaultQuantity by remember { mutableStateOf(String()) }
    var editItemUnit by remember { mutableStateOf(String()) }
    
    
    // State for type change confirmation
    var showTypeChangeWarning by remember { mutableStateOf(false) }
    var pendingTrackingType by remember { mutableStateOf<String?>(null) }
    
    
    
    // State for error messages  
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // State for data deletion warning
    var showDataDeletionWarning by remember { mutableStateOf(false) }
    
    // State for scale change warning
    var showScaleChangeWarning by remember { mutableStateOf(false) }
    var scaleChangeDetails by remember { mutableStateOf<String?>(null) }
    
    // State for boolean labels change warning
    var showBooleanChangeWarning by remember { mutableStateOf(false) }
    var booleanChangeDetails by remember { mutableStateOf<String?>(null) }
    
    // State for choice options change warning  
    var showChoiceChangeWarning by remember { mutableStateOf(false) }
    var choiceChangeDetails by remember { mutableStateOf<String?>(null) }
    
    // Function to detect scale changes
    val detectScaleChanges = {
        if (isEditing && trackingType == "scale" && originalType == "scale") {
            val originalConfig = try { JSONObject(initialConfigString) } catch (e: Exception) { JSONObject() }
            val currentConfig = config
            
            val oldMin = originalConfig.optInt("min", 1)
            val oldMax = originalConfig.optInt("max", 10)
            val oldMinLabel = originalConfig.optString("min_label", "")
            val oldMaxLabel = originalConfig.optString("max_label", "")
            
            val newMin = currentConfig.optInt("min", 1)
            val newMax = currentConfig.optInt("max", 10)
            val newMinLabel = currentConfig.optString("min_label", "")
            val newMaxLabel = currentConfig.optString("max_label", "")
            
            val scaleChanged = oldMin != newMin || oldMax != newMax
            val labelsChanged = oldMinLabel != newMinLabel || oldMaxLabel != newMaxLabel
            
            if (scaleChanged || labelsChanged) {
                val oldScaleText = buildString {
                    append(oldMin)
                    if (oldMinLabel.isNotEmpty()) append(" ($oldMinLabel)")
                    append("->")
                    append(oldMax)
                    if (oldMaxLabel.isNotEmpty()) append(" ($oldMaxLabel)")
                }
                val newScaleText = buildString {
                    append(newMin)
                    if (newMinLabel.isNotEmpty()) append(" ($newMinLabel)")
                    append("->")
                    append(newMax)
                    if (newMaxLabel.isNotEmpty()) append(" ($newMaxLabel)")
                }
                scaleChangeDetails = s.tool("config_warning_scale_change_old_new").format(oldScaleText, newScaleText)
                true
            } else false
        } else false
    }

    // Function to detect boolean label changes
    val detectBooleanChanges = {
        if (isEditing && trackingType == "boolean" && originalType == "boolean") {
            val originalConfig = try { JSONObject(initialConfigString) } catch (e: Exception) { JSONObject() }
            val currentConfig = config
            
            val oldTrueLabel = originalConfig.optString("true_label", s.tool("config_default_true_label"))
            val oldFalseLabel = originalConfig.optString("false_label", s.tool("config_default_false_label"))
            val newTrueLabel = currentConfig.optString("true_label", s.tool("config_default_true_label"))
            val newFalseLabel = currentConfig.optString("false_label", s.tool("config_default_false_label"))
            
            if (oldTrueLabel != newTrueLabel || oldFalseLabel != newFalseLabel) {
                booleanChangeDetails = s.tool("config_warning_boolean_change_old").format(oldTrueLabel, oldFalseLabel) + "\n" + s.tool("config_warning_boolean_change_new").format(newTrueLabel, newFalseLabel)
                true
            } else false
        } else false
    }

    // Function to detect choice options changes  
    val detectChoiceChanges = {
        if (isEditing && trackingType == "choice" && originalType == "choice") {
            val originalConfig = try { JSONObject(initialConfigString) } catch (e: Exception) { JSONObject() }
            val currentConfig = config
            
            val oldOptions = originalConfig.optJSONArray("options")?.let { array ->
                (0 until array.length()).map { array.getString(it) }
            } ?: emptyList()
            val newOptions = currentConfig.optJSONArray("options")?.let { array ->
                (0 until array.length()).map { array.getString(it) }
            } ?: emptyList()
            
            if (oldOptions != newOptions) {
                val removedOptions = oldOptions.filter { it !in newOptions }
                val addedOptions = newOptions.filter { it !in oldOptions }
                
                val details = buildString {
                    if (removedOptions.isNotEmpty()) {
                        append(s.tool("config_warning_choice_options_removed").format(removedOptions.joinToString(", ") { "\"$it\"" }))
                    }
                    if (addedOptions.isNotEmpty()) {
                        if (removedOptions.isNotEmpty()) append("\n")
                        append(s.tool("config_warning_choice_options_added").format(addedOptions.joinToString(", ") { "\"$it\"" }))
                    }
                }
                choiceChangeDetails = details
                true
            } else false
        } else false
    }

    // Save function avec validation V3
    val handleSave = handleSave@{
        // Check if type changed and we're editing an existing tool
        if (isEditing && originalType.isNotEmpty() && originalType != trackingType) {
            // Show data deletion warning
            showDataDeletionWarning = true
            return@handleSave
        }
        
        // Check if scale parameters changed
        if (detectScaleChanges()) {
            showScaleChangeWarning = true
            return@handleSave
        }
        
        // Check if boolean labels changed
        if (detectBooleanChanges()) {
            showBooleanChangeWarning = true
            return@handleSave
        }
        
        // Check if choice options changed
        if (detectChoiceChanges()) {
            showChoiceChangeWarning = true
            return@handleSave
        }
        
        // Nettoyer la config avant validation
        val cleanConfig = cleanConfiguration(config)
        
        // Convertir JSONObject en Map pour ValidationHelper
        val configMap = cleanConfig.keys().asSequence().associateWith { key ->
            cleanConfig.get(key)
        }
        
        // Use unified ValidationHelper
        UI.ValidationHelper.validateAndSave(
            toolTypeName = "tracking",
            configData = configMap,
            context = context,
            schemaType = "config",
            onSuccess = onSave
        )
    }
    
    // Final save with data deletion
    val handleFinalSave = {
        android.util.Log.d("TrackingConfig", "=== FINAL SAVE STARTED ===")
        scope.launch {
            try {
                // Delete existing data first
                if (existingToolId != null) {
                    android.util.Log.d("TrackingConfig", "About to call delete_all_entries for tool: $existingToolId")
                    val deleteResult = coordinator.processUserAction(
                        "delete->tool_data", 
                        mapOf(
                            "tool_type" to "tracking",
                            "operation" to "delete_all_entries",
                            "tool_instance_id" to existingToolId
                        )
                    )
                    android.util.Log.d("TrackingConfig", "Delete result - status: ${deleteResult.status}, message: ${deleteResult.message}")
                    if (deleteResult.status != CommandStatus.SUCCESS) {
                        android.util.Log.w("TrackingConfig", "Failed to delete existing data: ${deleteResult.message}")
                    } else {
                        android.util.Log.d("TrackingConfig", "Data deletion successful")
                    }
                } else {
                    android.util.Log.d("TrackingConfig", "No existingToolId, skipping data deletion")
                }
                
                // Then proceed with normal save
                val cleanConfig = cleanConfiguration(config)
                val configMap = cleanConfig.keys().asSequence().associateWith { key ->
                    cleanConfig.get(key)
                }
                
                val toolType = ToolTypeManager.getToolType("tracking")
                if (toolType != null) {
                    val validation = SchemaValidator.validate(toolType, configMap, context, schemaType = "config")
                    
                    if (validation.isValid) {
                        onSave(cleanConfig.toString())
                    } else {
                        android.util.Log.e("TrackingConfigScreen", "Validation failed: ${validation.errorMessage}")
                        errorMessage = validation.errorMessage ?: s.shared("tools_config_error_validation")
                    }
                } else {
                    android.util.Log.e("TrackingConfigScreen", "ToolType tracking not found")
                    errorMessage = s.shared("tools_config_error_tooltype_not_found")
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackingConfig", "Error during final save", e)
                errorMessage = s.shared("tools_config_error_save")
            }
        }
    }
    
    // Confirmation dialogs
    if (showTypeChangeWarning) {
        UI.Dialog(
            type = DialogType.DANGER,
            onConfirm = {
                pendingTrackingType?.let { newType ->
                    // Clear all type-specific fields before setting new type
                    clearTypeSpecificFields(config)
                    
                    // Set new type and reset items
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
                s.tool("config_warning_type_change").format(items.size),
                TextType.BODY
            )
        }
    }
    
    // Data deletion warning dialog
    if (showDataDeletionWarning) {
        UI.Dialog(
            type = DialogType.DANGER,
            onConfirm = {
                showDataDeletionWarning = false
                handleFinalSave()
            },
            onCancel = {
                showDataDeletionWarning = false
            }
        ) {
            Column {
                UI.Text(
                    s.tool("config_warning_type_change_title"),
                    TextType.SUBTITLE
                )
                Spacer(modifier = Modifier.height(8.dp))
                UI.Text(
                    s.tool("config_warning_type_change_desc").format(originalType, trackingType),
                    TextType.BODY
                )
                UI.Text(
                    s.tool("config_warning_data_deletion"),
                    TextType.BODY
                )
                Spacer(modifier = Modifier.height(8.dp))
                UI.Text(
                    s.tool("config_warning_continue"),
                    TextType.BODY
                )
            }
        }
    }
    
    // Scale change warning dialog
    if (showScaleChangeWarning) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                showScaleChangeWarning = false
                handleFinalSave()
            },
            onCancel = {
                showScaleChangeWarning = false
            }
        ) {
            Column {
                UI.Text(
                    s.tool("config_warning_scale_change_title"),
                    TextType.SUBTITLE
                )
                Spacer(modifier = Modifier.height(8.dp))
                scaleChangeDetails?.let { details ->
                    UI.Text(
                        details,
                        TextType.BODY
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                UI.Text(
                    s.tool("config_warning_scale_change_desc"),
                    TextType.BODY
                )
                Spacer(modifier = Modifier.height(8.dp))
                UI.Text(
                    s.tool("config_warning_continue"),
                    TextType.BODY
                )
            }
        }
    }
    
    // Boolean labels change warning dialog
    if (showBooleanChangeWarning) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                showBooleanChangeWarning = false
                handleFinalSave()
            },
            onCancel = {
                showBooleanChangeWarning = false
            }
        ) {
            Column {
                UI.Text(
                    s.tool("config_warning_boolean_change_title"),
                    TextType.SUBTITLE
                )
                Spacer(modifier = Modifier.height(8.dp))
                booleanChangeDetails?.let { details ->
                    UI.Text(
                        details,
                        TextType.BODY
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                UI.Text(
                    s.tool("config_warning_boolean_change_desc"),
                    TextType.BODY
                )
                Spacer(modifier = Modifier.height(8.dp))
                UI.Text(
                    s.tool("config_warning_continue"),
                    TextType.BODY
                )
            }
        }
    }
    
    // Choice options change warning dialog
    if (showChoiceChangeWarning) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                showChoiceChangeWarning = false
                handleFinalSave()
            },
            onCancel = {
                showChoiceChangeWarning = false
            }
        ) {
            Column {
                UI.Text(
                    s.tool("config_warning_choice_change_title"),
                    TextType.SUBTITLE
                )
                Spacer(modifier = Modifier.height(8.dp))
                choiceChangeDetails?.let { details ->
                    UI.Text(
                        details,
                        TextType.BODY
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                UI.Text(
                    s.tool("config_warning_choice_desc"),
                    TextType.BODY
                )
                Spacer(modifier = Modifier.height(8.dp))
                UI.Text(
                    s.tool("config_warning_continue"),
                    TextType.BODY
                )
            }
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
            title = if (isEditing) s.tool("config_title_edit") else s.tool("config_title_create"),
            subtitle = null,
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onCancel,
            onRightClick = null
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Card 1: General parameters (reusable composable)
        ToolGeneralConfigSection(
            config = config,
            updateConfig = ::updateConfig,
            toolTypeName = "tracking"
        )
        
        // Card 2: Tracking-specific parameters
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(s.tool("config_section_specific_params"), TextType.SUBTITLE)
                
                UI.FormSelection(
                    label = s.tool("config_label_tracking_type"),
                    options = listOf(
                        s.tool("config_option_numeric"),
                        s.tool("config_option_text"), 
                        s.tool("config_option_scale"),
                        s.tool("config_option_boolean"),
                        s.tool("config_option_timer"),
                        s.tool("config_option_choice"),
                        s.tool("config_option_counter")
                    ),
                    selected = when(trackingType) {
                        "numeric" -> s.tool("config_option_numeric")
                        "text" -> s.tool("config_option_text")
                        "scale" -> s.tool("config_option_scale")
                        "boolean" -> s.tool("config_option_boolean")
                        "timer" -> s.tool("config_option_timer")
                        "choice" -> s.tool("config_option_choice")
                        "counter" -> s.tool("config_option_counter")
                        else -> trackingType
                    },
                    onSelect = { selectedLabel ->
                        val newType = when (selectedLabel) {
                            s.tool("config_option_numeric") -> "numeric"
                            s.tool("config_option_text") -> "text"
                            s.tool("config_option_scale") -> "scale"
                            s.tool("config_option_boolean") -> "boolean"
                            s.tool("config_option_timer") -> "timer"
                            s.tool("config_option_choice") -> "choice"
                            s.tool("config_option_counter") -> "counter"
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
                    updateConfig = ::updateConfig,
                    s = s
                )
            }
        }
        
        // Card 3: Predefined items list
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
                        UI.Text(s.tool("config_section_predefined_items"), TextType.SUBTITLE)
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
                            text = s.tool("config_message_no_items"),
                            type = TextType.CAPTION,
                            fillMaxWidth = true,
                            textAlign = TextAlign.Center
                        )
                    } else{
                        // Header row
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
                                UI.CenteredText(
                                    text = s.tool("config_header_name"),
                                    type = TextType.CAPTION
                                )
                            }

                            // Columns specific to numeric type
                            if (trackingType == "numeric") {
                                Box(
                                    modifier = Modifier.weight(2f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UI.CenteredText(
                                        text = s.tool("config_header_quantity"),
                                        type = TextType.CAPTION
                                    )
                                }

                                Box(
                                    modifier = Modifier.weight(2f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    UI.CenteredText(
                                        text = s.tool("config_header_unit"),
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
        UI.ToolConfigActions(
            isEditing = isEditing,
            onSave = handleSave,
            onCancel = onCancel,
            onDelete = onDelete
        )
        
        
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
                            // Convert string to number for schema validation
                            val quantity = editItemDefaultQuantity.toDoubleOrNull()
                            if (quantity != null) {
                                properties["default_quantity"] = quantity
                            }
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
                        if (isCreating) s.tool("config_dialog_create_item") else s.tool("config_dialog_edit_item"),
                        TextType.SUBTITLE
                    )
                    
                    UI.FormField(
                        label = s.shared("tools_config_label_name"),
                        value = editItemName,
                        onChange = { editItemName = it },
                        required = true
                    )
                    
                    // Specific fields according to tracking type
                    if (trackingType == "numeric") {
                        UI.FormField(
                            label = s.tool("config_label_default_quantity"),
                            value = editItemDefaultQuantity,
                            onChange = { editItemDefaultQuantity = it },
                            fieldType = FieldType.NUMERIC,
                            required = false
                        )
                        
                        UI.FormField(
                            label = s.tool("config_label_unit"),
                            value = editItemUnit,
                            onChange = { editItemUnit = it },
                            required = false
                        )
                    }
                }
            }
        }
        
        
    }
    
    // Show error toast when errorMessage is set
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
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
            UI.CenteredText(
                text = item.name,
                type = TextType.BODY
            )
        }
        
        // Specific fields (currently only for numeric)

        if (trackingType == "numeric") {
            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center
            ) {
                val defaultQuantity = item.properties["default_quantity"]
                UI.CenteredText(
                    text = defaultQuantity?.toString() ?: "-",
                    type = TextType.BODY
                )
            }

            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center
            ) {
                val unit = item.properties["unit"]?.toString()
                UI.CenteredText(
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
    updateConfig: (String, Any) -> Unit,
    s: com.assistant.core.strings.StringsContext
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
                            label = s.tool("config_label_min_value"),
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
                            label = s.tool("config_label_max_value"), 
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
                            label = s.tool("config_label_min_label"),
                            value = minLabel,
                            onChange = { updateConfig("min_label", it) },
                            fieldType = FieldType.TEXT
                        )
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = s.tool("config_label_max_label"),
                            value = maxLabel,
                            onChange = { updateConfig("max_label", it) },
                            fieldType = FieldType.TEXT
                        )
                    }
                }
            }
        }
        
        "boolean" -> {
            val trueLabel = config.optString("true_label", s.tool("config_default_true_label"))
            val falseLabel = config.optString("false_label", s.tool("config_default_false_label"))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = s.tool("config_label_true_label"),
                        value = trueLabel,
                        onChange = { updateConfig("true_label", it) },
                        fieldType = FieldType.TEXT
                    )
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = s.tool("config_label_false_label"),
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
                label = s.tool("config_label_available_options"),
                items = currentOptions,
                onItemsChanged = { newOptions ->
                    // Save all options (even empty ones) to allow editing
                    // Filtering will be done during final configuration save
                    val newArray = JSONArray()
                    newOptions.forEach { option ->
                        newArray.put(option) // Keep all elements, even empty ones
                    }
                    updateConfig("options", newArray)
                },
                placeholder = s.tool("config_default_placeholder"),
                required = true,
                minItems = 2 // Au moins 2 options pour un choix
            )
        }
        
        "counter" -> {
            val allowDecrement = config.optBoolean("allow_decrement", true)
            
            UI.FormSelection(
                label = s.tool("config_label_allow_decrement"),
                options = listOf(s.shared("tools_config_option_yes"), s.shared("tools_config_option_no")),
                selected = if (allowDecrement) s.shared("tools_config_option_yes") else s.shared("tools_config_option_no"),
                onSelect = { selected ->
                    updateConfig("allow_decrement", selected == s.shared("tools_config_option_yes"))
                }
            )
        }
        
        // NUMERIC, TEXT, TIMER don't have specific configuration parameters
        // Predefined items are sufficient for their configuration
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
    
    // Predefined items are always valid (name required in UI)
    
    return cleanConfig
}