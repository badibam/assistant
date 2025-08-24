package com.assistant.tools.tracking.ui.inputs

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.themes.base.*
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
    
    val itemMode = config.optString("item_mode", "free")
    val showValue = config.optBoolean("show_value", true)

    UI.Container(type = ContainerType.PRIMARY) {
        UI.Text(
            type = TextType.SUBTITLE,
            text = "Saisie numérique"
        )

        UI.Spacer(modifier = Modifier.height(8.dp))

        // Item name input (always show for free mode)
        if (itemMode == "free" || itemMode == "both") {
            UI.TextField(
                type = TextFieldType.STANDARD,
                value = itemName,
                onValueChange = { itemName = it },
                placeholder = "Nom de l'élément",
                modifier = Modifier.fillMaxWidth()
            )
            
            UI.Spacer(modifier = Modifier.height(8.dp))
        }

        // Value input section
        if (showValue) {
            UI.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Value input
                UI.TextField(
                    type = TextFieldType.NUMERIC,
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "Valeur",
                    modifier = Modifier.weight(2f)
                )

                // Unit input
                UI.TextField(
                    type = TextFieldType.STANDARD,
                    value = unit,
                    onValueChange = { unit = it },
                    placeholder = "Unité",
                    modifier = Modifier.weight(1f)
                )
            }
            
            UI.Spacer(modifier = Modifier.height(8.dp))
        }

        // Save button
        UI.Button(
            type = ButtonType.PRIMARY,
            onClick = {
                if (itemName.isNotBlank() && (value.isNotBlank() || !showValue)) {
                    val numericValue = if (showValue) {
                        try {
                            value.toDoubleOrNull() ?: 0.0
                        } catch (e: Exception) {
                            0.0
                        }
                    } else {
                        1.0 // Default value when value field is hidden
                    }
                    
                    // Create JSON value with numeric data
                    val jsonValue = JSONObject().apply {
                        put("amount", numericValue)
                        put("unit", unit.takeIf { it.isNotBlank() } ?: "")
                        put("type", "numeric")
                        put("raw", if (showValue) "$value${unit.takeIf { it.isNotBlank() } ?: ""}" else itemName)
                    }
                    
                    onSave(jsonValue.toString(), itemName.takeIf { it.isNotBlank() })
                    
                    // Reset form
                    itemName = ""
                    value = ""
                    unit = ""
                }
            },
            enabled = !isLoading && itemName.isNotBlank() && (value.isNotBlank() || !showValue),
            modifier = Modifier.fillMaxWidth()
        ) {
            UI.Text(
                type = TextType.LABEL,
                text = if (isLoading) "Enregistrement..." else "Enregistrer"
            )
        }
    }
}