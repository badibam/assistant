package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import com.assistant.core.utils.NumberFormatting
import org.json.JSONArray
import org.json.JSONObject

/**
 * Specialized input component for numeric tracking
 * Simple numeric input for tracking data
 */
@Composable
fun NumericTrackingInput(
    config: JSONObject,
    onSave: (value: Any, name: String?) -> Unit,
    isLoading: Boolean
) {
    // Input state
    var itemName by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var selectedPredefinedItem by remember { mutableStateOf<JSONObject?>(null) }
    
    val itemMode = config.optString("item_mode", "free")
    val showValue = config.optBoolean("show_value", true)
    
    // Parse predefined items
    val predefinedItems = remember(config) {
        val itemsArray = config.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<JSONObject>()
        for (i in 0 until itemsArray.length()) {
            items.add(itemsArray.getJSONObject(i))
        }
        items
    }
    
    // State for predefined items quick add (item name -> current value)
    val predefinedItemValues = remember(predefinedItems) { 
        mutableStateMapOf<String, String>().apply {
            // Initialize with default values from config
            predefinedItems.forEach { item ->
                val itemName = item.optString("name", "")
                val defaultValue = item.optString("default_value", "")
                if (itemName.isNotBlank()) {
                    this[itemName] = defaultValue
                }
            }
        }
    }

    UI.Container(type = ContainerType.PRIMARY) {
        UI.Text(
            type = TextType.SUBTITLE,
            text = "Saisie numérique"
        )

        UI.Spacer(modifier = Modifier.height(8.dp))

        // Unified interface based on mode
        when (itemMode) {
            "free" -> {
                // Only free input line
                UI.Text(
                    text = "Ajout libre :",
                    type = TextType.BODY,
                    semantic = "free-add-label"
                )
                UI.Spacer(modifier = Modifier.height(8.dp))
                
                FreeInputLine(
                    itemName = itemName,
                    onItemNameChange = { itemName = it },
                    value = value,
                    onValueChange = { value = it },
                    unit = unit,
                    onUnitChange = { unit = it },
                    showValue = showValue,
                    isLoading = isLoading,
                    onSave = { name, numValue, unitValue ->
                        val jsonValue = JSONObject().apply {
                            put("amount", numValue)
                            put("unit", unitValue)
                            put("type", "numeric")
                            put("raw", if (showValue) NumberFormatting.formatRawValue(numValue, value, unitValue) else name)
                        }
                        onSave(jsonValue.toString(), name)
                        
                        // Reset form
                        itemName = ""
                        value = ""
                        unit = ""
                    }
                )
            }
            
            "predefined" -> {
                // Only predefined items with quick add
                if (predefinedItems.isNotEmpty()) {
                    UI.Text(
                        text = "Ajout rapide :",
                        type = TextType.BODY,
                        semantic = "quick-add-label"
                    )
                    UI.Spacer(modifier = Modifier.height(8.dp))
                    
                    predefinedItems.forEach { item ->
                        val itemDisplayName = item.optString("name", "")
                        val itemUnit = item.optString("unit", "")
                        val currentValue = predefinedItemValues[itemDisplayName] ?: ""
                        
                        PredefinedItemLine(
                            itemName = itemDisplayName,
                            value = currentValue,
                            onValueChange = { newValue ->
                                predefinedItemValues[itemDisplayName] = newValue
                            },
                            unit = itemUnit,
                            showValue = showValue,
                            isLoading = isLoading,
                            onSave = {
                                val numericValue = if (showValue) {
                                    currentValue.toDoubleOrNull() ?: 0.0
                                } else {
                                    1.0
                                }
                                
                                val jsonValue = JSONObject().apply {
                                    put("amount", numericValue)
                                    put("unit", itemUnit)
                                    put("type", "numeric")
                                    put("raw", if (showValue) "$currentValue${if (itemUnit.isNotBlank()) " $itemUnit" else ""}" else itemDisplayName)
                                }
                                
                                onSave(jsonValue.toString(), itemDisplayName)
                                
                                // Reset to default value
                                val defaultValue = item.optString("default_value", "")
                                predefinedItemValues[itemDisplayName] = defaultValue
                            }
                        )
                    }
                } else {
                    UI.Text(
                        text = "Aucun élément prédéfini configuré",
                        type = TextType.CAPTION,
                        semantic = "no-predefined-items"
                    )
                }
            }
            
            "both" -> {
                // Free input line first
                UI.Text(
                    text = "Ajout libre :",
                    type = TextType.BODY,
                    semantic = "free-add-label"
                )
                UI.Spacer(modifier = Modifier.height(8.dp))
                
                FreeInputLine(
                    itemName = itemName,
                    onItemNameChange = { itemName = it },
                    value = value,
                    onValueChange = { value = it },
                    unit = unit,
                    onUnitChange = { unit = it },
                    showValue = showValue,
                    isLoading = isLoading,
                    onSave = { name, numValue, unitValue ->
                        val jsonValue = JSONObject().apply {
                            put("amount", numValue)
                            put("unit", unitValue)
                            put("type", "numeric")
                            put("raw", if (showValue) NumberFormatting.formatRawValue(numValue, value, unitValue) else name)
                        }
                        onSave(jsonValue.toString(), name)
                        
                        // Reset form
                        itemName = ""
                        value = ""
                        unit = ""
                    }
                )
                
                // Then predefined items if any
                if (predefinedItems.isNotEmpty()) {
                    UI.Spacer(modifier = Modifier.height(16.dp))
                    
                    UI.Text(
                        text = "Ou ajout rapide :",
                        type = TextType.CAPTION,
                        semantic = "quick-add-label"
                    )
                    UI.Spacer(modifier = Modifier.height(8.dp))
                    
                    predefinedItems.forEach { item ->
                        val itemDisplayName = item.optString("name", "")
                        val itemUnit = item.optString("unit", "")
                        val currentValue = predefinedItemValues[itemDisplayName] ?: ""
                        
                        PredefinedItemLine(
                            itemName = itemDisplayName,
                            value = currentValue,
                            onValueChange = { newValue ->
                                predefinedItemValues[itemDisplayName] = newValue
                            },
                            unit = itemUnit,
                            showValue = showValue,
                            isLoading = isLoading,
                            onSave = {
                                val numericValue = if (showValue) {
                                    currentValue.toDoubleOrNull() ?: 0.0
                                } else {
                                    1.0
                                }
                                
                                val jsonValue = JSONObject().apply {
                                    put("amount", numericValue)
                                    put("unit", itemUnit)
                                    put("type", "numeric")
                                    put("raw", if (showValue) "$currentValue${if (itemUnit.isNotBlank()) " $itemUnit" else ""}" else itemDisplayName)
                                }
                                
                                onSave(jsonValue.toString(), itemDisplayName)
                                
                                // Reset to default value
                                val defaultValue = item.optString("default_value", "")
                                predefinedItemValues[itemDisplayName] = defaultValue
                            }
                        )
                    }
                }
            }
        }

    }
}

/**
 * Free input line component with unified layout
 */
@Composable
private fun FreeInputLine(
    itemName: String,
    onItemNameChange: (String) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    showValue: Boolean,
    isLoading: Boolean,
    onSave: (name: String, numValue: Double, unitValue: String) -> Unit
) {
    UI.Card(
        type = CardType.SYSTEM,
        semantic = "free-input-line",
        modifier = Modifier.fillMaxWidth()
    ) {
        UI.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Item name (editable)
            UI.TextField(
                type = TextFieldType.STANDARD,
                value = itemName,
                onValueChange = onItemNameChange,
                placeholder = "Nom",
                modifier = Modifier.weight(1f)
            )
            
            if (showValue) {
                // Value field (editable)
                UI.TextField(
                    type = TextFieldType.NUMERIC,
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "Valeur",
                    modifier = Modifier.weight(1f)
                )
                
                // Unit field (editable)
                UI.TextField(
                    type = TextFieldType.STANDARD,
                    value = unit,
                    onValueChange = onUnitChange,
                    placeholder = "Unité",
                    modifier = Modifier.weight(0.8f)
                )
            }
            
            // Add button
            UI.Button(
                type = ButtonType.PRIMARY,
                semantic = "add-free-item",
                enabled = !isLoading && itemName.isNotBlank() && NumberFormatting.isValidNumericInput(value),
                onClick = {
                    val numericValue = NumberFormatting.parseUserInput(value)
                    if (numericValue != null) {
                        onSave(itemName, numericValue, unit)
                    }
                    // If null, nothing happens - button shouldn't be enabled anyway
                }
            ) {
                UI.Text(
                    text = "+",
                    type = TextType.LABEL,
                    semantic = "add-icon"
                )
            }
        }
    }
}

/**
 * Predefined item line component with unified layout
 */
@Composable
private fun PredefinedItemLine(
    itemName: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    showValue: Boolean,
    isLoading: Boolean,
    onSave: () -> Unit
) {
    UI.Card(
        type = CardType.SYSTEM,
        semantic = "predefined-item-line-$itemName",
        modifier = Modifier.fillMaxWidth()
    ) {
        UI.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Item name (readonly)
            UI.Text(
                text = itemName,
                type = TextType.BODY,
                semantic = "item-name",
                modifier = Modifier.weight(1f)
            )
            
            if (showValue) {
                // Value field (editable)
                UI.TextField(
                    type = TextFieldType.NUMERIC,
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "Valeur",
                    modifier = Modifier.weight(1f)
                )
                
                // Unit (readonly)
                if (unit.isNotBlank()) {
                    UI.Text(
                        text = unit,
                        type = TextType.CAPTION,
                        semantic = "item-unit",
                        modifier = Modifier.weight(0.8f)
                    )
                } else {
                    // Empty space to maintain alignment
                    UI.Spacer(modifier = Modifier.weight(0.8f))
                }
            }
            
            // Add button
            UI.Button(
                type = ButtonType.PRIMARY,
                semantic = "add-predefined-$itemName",
                enabled = !isLoading && (!showValue || NumberFormatting.isValidNumericInput(value)),
                onClick = onSave
            ) {
                UI.Text(
                    text = "+",
                    type = TextType.LABEL,
                    semantic = "add-icon"
                )
            }
        }
    }
}