package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.tools.tracking.ui.components.TrackingItemDialog
import com.assistant.tools.tracking.ui.components.TrackingDialogMode
import com.assistant.tools.tracking.ui.components.TrackingItem
import com.assistant.core.utils.NumberFormatting
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simplified numeric tracking input component
 * Features predefined items as buttons + "Other" button for free input
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumericTrackingInput(
    config: JSONObject,
    onSave: (valueJson: String, itemName: String) -> Unit,
    onAddToPredefined: (itemName: String, unit: String, defaultValue: String) -> Unit,
    isLoading: Boolean
) {
    // Parse predefined items from config
    val predefinedItems = remember(config) {
        val itemsArray = config.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<TrackingItem>()
        
        // Debug: log config content
        android.util.Log.d("NumericTrackingInput", "Config JSON: ${config.toString()}")
        android.util.Log.d("NumericTrackingInput", "Items array length: ${itemsArray.length()}")
        
        for (i in 0 until itemsArray.length()) {
            val itemJson = itemsArray.getJSONObject(i)
            val properties = mutableMapOf<String, Any>()
            
            android.util.Log.d("NumericTrackingInput", "Item $i: ${itemJson.toString()}")
            
            // Extract all properties except name
            itemJson.keys().forEach { key ->
                if (key != "name") {
                    properties[key] = itemJson.get(key)
                }
            }
            
            items.add(TrackingItem(
                name = itemJson.optString("name", ""),
                properties = properties
            ))
        }
        
        android.util.Log.d("NumericTrackingInput", "Parsed ${items.size} predefined items")
        items
    }
    
    // Dialog states
    var showDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf(DialogMode.FREE_INPUT) }
    var selectedItem by remember { mutableStateOf<TrackingItem?>(null) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Predefined items as clickable buttons
        if (predefinedItems.isNotEmpty()) {
            UI.Text("Raccourcis :", TextType.BODY)
            
            // Display items 2 per row
            predefinedItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            PredefinedItemButton(
                                item = item,
                                isLoading = isLoading,
                                onQuickSave = { value ->
                                    // Save directly with predefined values
                                    val jsonValue = JSONObject().apply {
                                        put("amount", value)
                                        put("unit", item.getUnit())
                                        put("type", "numeric")
                                        put("raw", formatDisplayValue(value, item.getUnit()))
                                    }
                                    onSave(jsonValue.toString(), item.name)
                                },
                                onOpenDialog = {
                                    selectedItem = item
                                    dialogMode = DialogMode.EDIT_QUANTITY
                                    showDialog = true
                                }
                            )
                        }
                    }
                    
                    // Fill remaining space if odd number of items
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // "Other" button for free input
        Box(modifier = Modifier.fillMaxWidth()) {
            UI.Button(
                type = ButtonType.SECONDARY,
                onClick = {
                    selectedItem = null
                    dialogMode = DialogMode.FREE_INPUT
                    showDialog = true
                }
            ) {
                UI.Text("Nouvelle entrée", TextType.LABEL)
            }
        }
        
        // Dialog for input
        TrackingItemDialog(
            isVisible = showDialog,
            trackingType = "numeric",
            mode = when (dialogMode) {
                DialogMode.FREE_INPUT -> TrackingDialogMode.FREE_INPUT
                DialogMode.EDIT_QUANTITY -> TrackingDialogMode.PREDEFINED_INPUT
            },
            initialName = selectedItem?.name ?: "",
            initialUnit = selectedItem?.getUnit() ?: "",
            initialDefaultValue = selectedItem?.getDefaultValue() ?: "",
            onConfirm = { name, unit, defaultValue, addToPredefined ->
                // Only save if we have a valid numeric value
                val numericValue = NumberFormatting.parseUserInput(defaultValue)
                if (numericValue != null) {
                    val jsonValue = JSONObject().apply {
                        put("amount", numericValue)
                        put("unit", unit)
                        put("type", "numeric")
                        put("raw", formatDisplayValue(numericValue, unit))
                    }
                    
                    onSave(jsonValue.toString(), name)
                } else {
                    // Invalid value - don't save, user should see validation error
                    android.util.Log.w("NumericTrackingInput", "Invalid numeric value: '$defaultValue'")
                }
                
                // Add to predefined if checkbox was checked
                if (addToPredefined) {
                    onAddToPredefined(name, unit, defaultValue)
                }
                
                showDialog = false
            },
            onCancel = {
                showDialog = false
                selectedItem = null
            }
        )
    }
}

/**
 * Button for predefined items with click/long-click behavior
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PredefinedItemButton(
    item: TrackingItem,
    isLoading: Boolean,
    onQuickSave: (Double) -> Unit,
    onOpenDialog: () -> Unit
) {
    val defaultValue = item.getDefaultValue()
    val hasDefaultValue = defaultValue.isNotBlank()
    
    // Display text for button
    val displayText = buildString {
        append(item.name)
        if (hasDefaultValue) {
            append(" (")
            append(defaultValue)
            if (item.getUnit().isNotBlank()) {
                append("\u00A0${item.getUnit()}") // Espace insécable
            }
            append(")")
        } else if (item.getUnit().isNotBlank()) {
            append("\u00A0(${item.getUnit()})")
        }
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Non-clickable label
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            UI.Text(displayText, TextType.BODY)
        }
        
        // Add button (quick save with default or open dialog)
        UI.AddButton(
            onClick = {
                android.util.Log.d("PredefinedItemButton", "Add click on ${item.name}, hasDefaultValue=$hasDefaultValue")
                if (hasDefaultValue) {
                    // Quick save with default value
                    val numericValue = NumberFormatting.parseUserInput(defaultValue) ?: 1.0
                    onQuickSave(numericValue)
                } else {
                    // Open dialog to input quantity
                    onOpenDialog()
                }
            },
            size = Size.S
        )
        
        // Edit button (always opens dialog for custom quantity)
        UI.EditButton(
            onClick = {
                android.util.Log.d("PredefinedItemButton", "Edit click on ${item.name}")
                onOpenDialog()
            },
            size = Size.S
        )
    }
}

/**
 * Dialog modes for different contexts
 */
private enum class DialogMode {
    FREE_INPUT,      // New item with modifiable name
    EDIT_QUANTITY    // Existing item, name readonly, focus on quantity
}

/**
 * Format display value for consistent presentation
 */
private fun formatDisplayValue(value: Double, unit: String): String {
    val formattedValue = if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        value.toString()
    }
    
    return if (unit.isNotBlank()) {
        "$formattedValue\u00A0$unit" // Espace insécable
    } else {
        formattedValue
    }
}