package com.assistant.tools.tracking.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.utils.NumberFormatting

/**
 * Dialog modes for different usage contexts
 */
enum class TrackingDialogMode {
    FREE_INPUT,         // "Autre" - nom modifiable, quantité requise, checkbox raccourcis
    PREDEFINED_INPUT,   // Item prédéfini - nom readonly, quantité requise
    CREATE_CONFIG,      // Créer item config - nom modifiable, quantité par défaut optionnelle
    EDIT_CONFIG         // Modifier item config - nom modifiable, quantité par défaut optionnelle
}

/**
 * Reusable dialog for creating/editing tracking items
 * Currently supports numeric type only
 */
@Composable
fun TrackingItemDialog(
    isVisible: Boolean,
    trackingType: String,
    mode: TrackingDialogMode,
    initialName: String = "",
    initialUnit: String = "",
    initialDefaultValue: String = "",
    onConfirm: (name: String, unit: String, defaultValue: String, addToPredefined: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    // Dialog state
    var itemName by remember(isVisible) { mutableStateOf(initialName) }
    var itemUnit by remember(isVisible) { mutableStateOf(initialUnit) }
    var itemDefaultValue by remember(isVisible) { mutableStateOf(initialDefaultValue) }
    var addToPredefined by remember(isVisible) { mutableStateOf(false) }
    
    // Reset fields when dialog becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            itemName = initialName
            itemUnit = initialUnit
            itemDefaultValue = initialDefaultValue
        }
    }
    
    if (isVisible) {
        // Determine dialog properties based on mode
        val dialogType = when (mode) {
            TrackingDialogMode.FREE_INPUT, TrackingDialogMode.PREDEFINED_INPUT -> DialogType.CONFIRM
            TrackingDialogMode.CREATE_CONFIG -> DialogType.CREATE
            TrackingDialogMode.EDIT_CONFIG -> DialogType.EDIT
        }
        
        val dialogTitle = when (mode) {
            TrackingDialogMode.FREE_INPUT -> "Créer une entrée"
            TrackingDialogMode.PREDEFINED_INPUT -> "Modifier la quantité"
            TrackingDialogMode.CREATE_CONFIG -> "Créer un élément"
            TrackingDialogMode.EDIT_CONFIG -> "Modifier l'élément"
        }
        
        val isNameReadonly = mode == TrackingDialogMode.PREDEFINED_INPUT
        val isUnitReadonly = mode == TrackingDialogMode.PREDEFINED_INPUT
        val isValueRequired = mode in listOf(TrackingDialogMode.FREE_INPUT, TrackingDialogMode.PREDEFINED_INPUT)
        val showAddToPredefined = mode == TrackingDialogMode.FREE_INPUT
        val valueLabel = if (isValueRequired) "Quantité" else "Quantité par défaut"
        
        UI.Dialog(
            type = dialogType,
            onConfirm = {
                if (itemName.isNotBlank()) {
                    onConfirm(itemName.trim(), itemUnit.trim(), itemDefaultValue.trim(), addToPredefined)
                }
            },
            onCancel = onCancel
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(
                    text = dialogTitle,
                    type = TextType.SUBTITLE
                )
                
                // Nom (obligatoire pour tous les types)
                UI.FormField(
                    label = "Nom",
                    value = itemName,
                    onChange = { itemName = it },
                    required = true,
                    fieldType = FieldType.TEXT,
                    readonly = isNameReadonly
                )
                
                // Champs spécifiques pour numeric
                if (trackingType == "numeric") {
                    UI.FormField(
                        label = valueLabel,
                        value = itemDefaultValue,
                        onChange = { itemDefaultValue = it },
                        type = TextFieldType.NUMERIC,
                        fieldType = FieldType.NUMERIC,
                        required = isValueRequired
                    )
                    
                    UI.FormField(
                        label = "Unité",
                        value = itemUnit,
                        onChange = { itemUnit = it },
                        readonly = isUnitReadonly
                    )
                } else {
                    // Types non supportés pour le moment
                    UI.Text(
                        text = "Type '$trackingType' non supporté",
                        type = TextType.CAPTION
                    )
                }
                
                // Case à cocher "Ajouter aux raccourcis" si visible
                if (showAddToPredefined) {
                    UI.Checkbox(
                        checked = addToPredefined,
                        onCheckedChange = { checked -> addToPredefined = checked },
                        label = "Ajouter aux raccourcis"
                    )
                }
            }
        }
    }
}

/**
 * Data class for tracking item properties
 * Used to maintain consistency between config screen and input dialogs
 */
data class TrackingItem(
    val name: String,
    val properties: Map<String, Any> = emptyMap()
) {
    fun getProperty(key: String): String {
        return properties[key]?.toString() ?: ""
    }
    
    fun getUnit(): String = getProperty("unit")
    fun getDefaultValue(): String = getProperty("default_value")
    
    companion object {
        fun fromNumericDialog(name: String, unit: String, defaultValue: String): TrackingItem {
            val properties = mutableMapOf<String, Any>()
            
            if (unit.isNotBlank()) {
                properties["unit"] = unit
            }
            
            if (defaultValue.isNotBlank()) {
                val parsed = NumberFormatting.parseUserInput(defaultValue)
                if (parsed != null) {
                    properties["default_value"] = parsed
                }
            }
            
            return TrackingItem(name, properties)
        }
    }
}