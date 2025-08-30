package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.tools.tracking.ui.components.TrackingItemDialog
import com.assistant.tools.tracking.ui.components.TrackingDialogMode
import com.assistant.tools.tracking.ui.components.TrackingItem
import com.assistant.core.utils.NumberFormatting
import com.assistant.core.validation.ValidationHelper
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.TrackingUtils
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
    onSave: (itemName: String, quantity: String, unit: String) -> Unit,
    onAddToPredefined: (itemName: String, unit: String, defaultValue: String) -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
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
                                onQuickSave = { quantity ->
                                    // Pass raw data to service - no JSON creation in UI
                                    onSave(item.name, quantity.toString(), item.getUnit())
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
            UI.ActionButton(
                action = ButtonAction.ADD,
                onClick = {
                    selectedItem = null
                    dialogMode = DialogMode.FREE_INPUT
                    showDialog = true
                }
            )
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
            initialDefaultQuantity = selectedItem?.getDefaultValue() ?: "",
            onConfirm = { name, unit, defaultQuantity, addToPredefined, date, time ->
                // Pass raw data to service - no JSON creation in UI
                // Note: date/time will be handled by TrackingService for new entries
                onSave(name, defaultQuantity, unit)
                
                // Add to predefined if checkbox was checked
                if (addToPredefined) {
                    onAddToPredefined(name, unit, defaultQuantity)
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
        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.ICON,
            size = Size.S,
            onClick = {
                android.util.Log.d("PredefinedItemButton", "Add click on ${item.name}, hasDefaultValue=$hasDefaultValue")
                if (hasDefaultValue) {
                    // Quick save with default quantity - no fallback, should be valid since it's predefined
                    val numericQuantity = NumberFormatting.parseUserInput(defaultValue)
                    if (numericQuantity != null) {
                        onQuickSave(numericQuantity)
                    }
                } else {
                    // Open dialog to input quantity
                    onOpenDialog()
                }
            }
        )
        
        // Edit button (always opens dialog for custom quantity)
        UI.ActionButton(
            action = ButtonAction.EDIT,
            display = ButtonDisplay.ICON,
            size = Size.S,
            onClick = {
                android.util.Log.d("PredefinedItemButton", "Edit click on ${item.name}")
                onOpenDialog()
            }
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

