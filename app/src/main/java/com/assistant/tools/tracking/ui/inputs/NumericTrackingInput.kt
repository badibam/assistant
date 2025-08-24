package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Specialized input component for numeric tracking
 * Displays groups with predefined items and free text entry
 */
@Composable
fun NumericTrackingInput(
    config: JSONObject,
    groups: JSONArray?,
    onSave: (value: Any, name: String?) -> Unit,
    isLoading: Boolean
) {
    // Free text entry state
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var freeItemName by remember { mutableStateOf("") }
    var freeValue by remember { mutableStateOf("") }
    var freeUnit by remember { mutableStateOf("") }
    var shouldSaveNewItem by remember { mutableStateOf(config.optBoolean("save_new_items", false)) }
    
    // Predefined items state - store values for each item
    val itemValues = remember { mutableStateMapOf<String, String>() }
    
    // Parse groups and items
    val groupsData = remember(groups) {
        val result = mutableListOf<Pair<String, List<JSONObject>>>()
        groups?.let { groupsArray ->
            for (i in 0 until groupsArray.length()) {
                val group = groupsArray.getJSONObject(i)
                val groupName = group.optString("name", "")
                val groupItems = group.optJSONArray("items")
                val items = mutableListOf<JSONObject>()
                
                groupItems?.let { itemsArray ->
                    for (j in 0 until itemsArray.length()) {
                        val item = itemsArray.getJSONObject(j)
                        items.add(item)
                    }
                }
                
                if (groupName.isNotBlank()) {
                    result.add(groupName to items)
                }
            }
        }
        result
    }
    
    // Initialize default values for predefined items
    LaunchedEffect(groupsData) {
        groupsData.forEach { (_, items) ->
            items.forEach { item ->
                val itemName = item.optString("name", "")
                val defaultValue = item.optDouble("default_value", Double.NaN)
                if (itemName.isNotBlank() && !defaultValue.isNaN()) {
                    itemValues[itemName] = defaultValue.toString()
                }
            }
        }
    }
    
    // Helper function to save an entry
    val saveEntry = { value: String, unit: String, itemName: String ->
        val numericValue = value.toDoubleOrNull()
        if (numericValue != null) {
            val valueData = if (unit.isNotBlank()) {
                mapOf("value" to numericValue, "unit" to unit)
            } else {
                mapOf("value" to numericValue)
            }
            onSave(valueData, itemName)
        }
    }
    
    // Extract item mode from config
    val itemMode = config.optString("item_mode", "free")
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Free text entry line (show if mode is "free" or "both")
        if (itemMode == "free" || itemMode == "both") {
            UI.Card(type = CardType.DATA_ENTRY) {
            UI.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = "Nouvelle entrée libre",
                    type = TextType.SUBTITLE,
                    semantic = "free-entry-title"
                )
                
                // Group selector + Item name on same line
                UI.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Group dropdown (simplified as text field for now)
                    UI.TextField(
                        type = TextFieldType.STANDARD,
                        value = selectedGroup ?: "",
                        onValueChange = { selectedGroup = it.takeIf { text -> text.isNotBlank() } },
                        semantic = "group-selector",
                        placeholder = "Groupe",
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Item name
                    UI.TextField(
                        type = TextFieldType.STANDARD,
                        value = freeItemName,
                        onValueChange = { freeItemName = it },
                        semantic = "free-item-name",
                        placeholder = "Nom de l'item",
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Value + Unit + Save checkbox on same line
                UI.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UI.TextField(
                        type = TextFieldType.NUMERIC,
                        value = freeValue,
                        onValueChange = { freeValue = it },
                        semantic = "free-value",
                        placeholder = "Valeur",
                        modifier = Modifier.weight(1f)
                    )
                    
                    UI.TextField(
                        type = TextFieldType.STANDARD,
                        value = freeUnit,
                        onValueChange = { freeUnit = it },
                        semantic = "free-unit",
                        placeholder = "Unité",
                        modifier = Modifier.weight(0.7f)
                    )
                    
                    // Checkbox for "Ajouter"
                    UI.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // TODO: Add checkbox component when available
                        UI.Button(
                            type = if (shouldSaveNewItem) ButtonType.PRIMARY else ButtonType.GHOST,
                            semantic = "save-new-items-toggle",
                            onClick = { shouldSaveNewItem = !shouldSaveNewItem },
                            modifier = Modifier.width(60.dp)
                        ) {
                            UI.Text(
                                text = if (shouldSaveNewItem) "✓" else "○",
                                type = TextType.LABEL,
                                semantic = "checkbox-state"
                            )
                        }
                        
                        UI.Text(
                            text = "Ajouter",
                            type = TextType.CAPTION,
                            semantic = "save-new-items-label"
                        )
                    }
                    
                    // Save button for free entry
                    UI.Button(
                        type = ButtonType.PRIMARY,
                        semantic = "save-free-entry",
                        onClick = { 
                            if (freeItemName.isNotBlank()) {
                                saveEntry(freeValue, freeUnit, freeItemName)
                                // Reset form
                                freeItemName = ""
                                freeValue = ""
                                freeUnit = ""
                            }
                        },
                        enabled = !isLoading && freeItemName.isNotBlank() && freeValue.toDoubleOrNull() != null,
                        modifier = Modifier.width(40.dp)
                    ) {
                        UI.Text(
                            text = "+",
                            type = TextType.LABEL,
                            semantic = "save-free-label"
                        )
                    }
                }
            }
        }
        }
        
        // Groups sections with predefined items (show if mode is "predefined" or "both")
        if (itemMode == "predefined" || itemMode == "both") {
            groupsData.forEach { (groupName, items) ->
            UI.Card(type = CardType.DATA_ENTRY) {
                UI.Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Group title
                    UI.Text(
                        text = groupName,
                        type = TextType.SUBTITLE,
                        semantic = "group-title-$groupName"
                    )
                    
                    // Items in this group
                    items.forEach { item ->
                        val itemName = item.optString("name", "")
                        val showValue = item.optBoolean("show_value", true)
                        val unit = item.optString("unit", "")
                        val currentValue = itemValues[itemName] ?: ""
                        
                        if (itemName.isNotBlank()) {
                            UI.Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Item name - takes available space
                                UI.Text(
                                    text = itemName,
                                    type = TextType.BODY,
                                    semantic = "item-name-$itemName",
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Value field (if show_value is true) - proportional width
                                if (showValue) {
                                    UI.TextField(
                                        type = TextFieldType.NUMERIC,
                                        value = currentValue,
                                        onValueChange = { itemValues[itemName] = it },
                                        semantic = "item-value-$itemName",
                                        placeholder = "Valeur",
                                        modifier = Modifier.weight(0.6f)
                                    )
                                    
                                    // Unit display (readonly) - proportional width
                                    UI.Text(
                                        text = unit.ifBlank { "" },
                                        type = TextType.BODY,
                                        semantic = "item-unit-$itemName",
                                        modifier = Modifier.weight(0.3f)
                                    )
                                } else {
                                    // Spacer to maintain button alignment when no value field
                                    UI.Spacer(modifier = Modifier.weight(0.9f))
                                }
                                
                                // Add button - fixed minimal width
                                UI.Button(
                                    type = ButtonType.PRIMARY,
                                    semantic = "add-item-$itemName",
                                    onClick = { 
                                        val valueToSave = if (showValue) currentValue else "1" // Default to 1 for boolean-like items
                                        saveEntry(valueToSave, unit, itemName)
                                    },
                                    enabled = !isLoading && (!showValue || currentValue.toDoubleOrNull() != null),
                                    modifier = Modifier.width(40.dp)
                                ) {
                                    UI.Text(
                                        text = "+",
                                        type = TextType.LABEL,
                                        semantic = "add-label"
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
}