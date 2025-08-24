package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Specialized input component for boolean tracking
 * Handles yes/no values with custom labels
 */
@Composable
fun BooleanTrackingInput(
    config: JSONObject,
    groups: JSONArray?,
    onSave: (value: Any, itemName: String?) -> Unit,
    isLoading: Boolean
) {
    var selectedItem by remember { mutableStateOf<String?>(null) }
    
    // Extract item mode
    val itemMode = config.optString("item_mode", "free")
    
    // Extract predefined items from groups
    val predefinedItems = remember(groups) {
        val items = mutableListOf<Triple<String, String, String>>() // name, trueLabel, falseLabel
        groups?.let { groupsArray ->
            for (i in 0 until groupsArray.length()) {
                val group = groupsArray.getJSONObject(i)
                val groupItems = group.optJSONArray("items")
                groupItems?.let { itemsArray ->
                    for (j in 0 until itemsArray.length()) {
                        val item = itemsArray.getJSONObject(j)
                        val itemName = item.optString("name", "")
                        val trueLabel = item.optString("true_label", "Oui") // TODO: Internationalization
                        val falseLabel = item.optString("false_label", "Non") // TODO: Internationalization
                        if (itemName.isNotBlank()) {
                            items.add(Triple(itemName, trueLabel, falseLabel))
                        }
                    }
                }
            }
        }
        items
    }
    
    val handleSave = { value: Boolean ->
        val itemName = selectedItem?.takeIf { it.isNotBlank() }
        onSave(mapOf("value" to value), itemName)
        // Reset form for free mode
        if (itemMode == "free") {
            selectedItem = null
        }
    }
    
    UI.Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Item selection (if predefined items exist and mode allows)
        if (predefinedItems.isNotEmpty() && itemMode != "free") {
            UI.Text(
                text = "Élément", // TODO: Internationalization
                type = TextType.SUBTITLE,
                semantic = "item-selection-label"
            )
            
            predefinedItems.forEach { (itemName, _, _) ->
                UI.Button(
                    type = if (selectedItem == itemName) ButtonType.PRIMARY else ButtonType.GHOST,
                    semantic = "select-item-$itemName",
                    onClick = { 
                        selectedItem = if (selectedItem == itemName) null else itemName 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    UI.Text(
                        text = itemName,
                        type = TextType.LABEL,
                        semantic = "item-name"
                    )
                }
            }
        }
        
        // Free text item name (for free mode or mixed mode)
        if (itemMode == "free" || itemMode == "both") {
            UI.TextField(
                type = TextFieldType.STANDARD,
                value = selectedItem ?: "",
                onValueChange = { selectedItem = it.takeIf { text -> text.isNotBlank() } },
                semantic = "free-item-name",
                placeholder = "Nom de l'élément (optionnel)", // TODO: Internationalization
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Boolean value buttons
        if (selectedItem != null || itemMode == "free") {
            // Get labels for current item
            val currentItem = predefinedItems.find { it.first == selectedItem }
            val trueLabel = currentItem?.second ?: "Oui" // TODO: Internationalization
            val falseLabel = currentItem?.third ?: "Non" // TODO: Internationalization
            
            UI.Text(
                text = "Valeur", // TODO: Internationalization
                type = TextType.SUBTITLE,
                semantic = "value-selection-label"
            )
            
            UI.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // True button
                UI.Button(
                    type = ButtonType.PRIMARY,
                    semantic = "save-true",
                    onClick = { handleSave(true) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        UI.LoadingIndicator(type = LoadingType.MINIMAL)
                    } else {
                        UI.Text(
                            text = trueLabel,
                            type = TextType.LABEL,
                            semantic = "true-label"
                        )
                    }
                }
                
                // False button
                UI.Button(
                    type = ButtonType.SECONDARY,
                    semantic = "save-false",
                    onClick = { handleSave(false) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        UI.LoadingIndicator(type = LoadingType.MINIMAL)
                    } else {
                        UI.Text(
                            text = falseLabel,
                            type = TextType.LABEL,
                            semantic = "false-label"
                        )
                    }
                }
            }
        }
        
        // Instruction text when no item is selected
        if (selectedItem == null && itemMode != "free") {
            UI.Text(
                text = "Sélectionnez un élément pour enregistrer une valeur", // TODO: Internationalization
                type = TextType.CAPTION,
                semantic = "instruction-text"
            )
        }
    }
}