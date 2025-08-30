package com.assistant.tools.tracking.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.utils.DateUtils
import org.json.JSONObject

/**
 * Dialog modes using 3 orthogonal dimensions for clear logic
 */
enum class ItemType { FREE, PREDEFINED }
enum class InputType { ENTRY, CONFIG }
enum class ActionType { CREATE, UPDATE }

/**
 * Universal tracking dialog supporting all 7 tracking types
 * Replaces the old TrackingItemDialog with extended functionality
 */
@Composable
fun UniversalTrackingDialog(
    isVisible: Boolean,
    trackingType: String,
    config: JSONObject,
    itemType: ItemType?,  // null for history editing
    inputType: InputType,
    actionType: ActionType,
    initialName: String = "",
    initialProperties: Map<String, Any> = emptyMap(),
    initialDate: String = "",
    initialTime: String = "",
    onConfirm: (name: String, properties: Map<String, Any>, addToPredefined: Boolean, date: String, time: String) -> Unit,
    onCancel: () -> Unit
) {
    // Dialog state
    var itemName by remember(isVisible) { mutableStateOf(initialName) }
    var itemDate by remember(isVisible) { mutableStateOf(initialDate) }
    var itemTime by remember(isVisible) { mutableStateOf(initialTime) }
    var addToPredefined by remember(isVisible) { mutableStateOf(false) }
    
    // Type-specific states
    var numericQuantity by remember(isVisible) { mutableStateOf(initialProperties["quantity"]?.toString() ?: "") }
    var numericUnit by remember(isVisible) { mutableStateOf(initialProperties["unit"]?.toString() ?: "") }
    var scaleValue by remember(isVisible) { mutableStateOf(initialProperties["value"]?.toString() ?: "5") }
    var textValue by remember(isVisible) { mutableStateOf(initialProperties["text"]?.toString() ?: "") }
    var choiceValue by remember(isVisible) { mutableStateOf(initialProperties["selected_option"]?.toString() ?: "") }
    var booleanValue by remember(isVisible) { mutableStateOf(initialProperties["state"]?.toString()?.toBoolean() ?: false) }
    var counterIncrement by remember(isVisible) { mutableStateOf(initialProperties["increment"]?.toString() ?: "1") }
    var timerDuration by remember(isVisible) { mutableStateOf(initialProperties["duration_minutes"]?.toString() ?: "0") }
    
    // Date/time picker states
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Reset fields when dialog becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            android.util.Log.d("TRACKING_DEBUG", "UniversalTrackingDialog - opening dialog: trackingType=$trackingType, initialName='$initialName', initialProperties=$initialProperties")
            
            itemName = initialName
            itemDate = initialDate.ifEmpty { DateUtils.getTodayFormatted() }
            itemTime = initialTime.ifEmpty { DateUtils.getCurrentTimeFormatted() }
            addToPredefined = false
            
            // Reset type-specific values
            numericQuantity = initialProperties["quantity"]?.toString() ?: ""
            numericUnit = initialProperties["unit"]?.toString() ?: ""
            scaleValue = initialProperties["value"]?.toString() ?: "5"
            textValue = initialProperties["text"]?.toString() ?: ""
            choiceValue = initialProperties["selected_option"]?.toString() ?: ""
            booleanValue = initialProperties["state"]?.toString()?.toBoolean() ?: false
            counterIncrement = initialProperties["increment"]?.toString() ?: "1"
            timerDuration = initialProperties["duration_minutes"]?.toString() ?: "0"
            
            android.util.Log.d("TRACKING_DEBUG", "UniversalTrackingDialog - initialized timer values: timerDuration=$timerDuration from initialProperties")
        }
    }
    
    if (isVisible) {
        // Determine UI behavior
        val isNameEditable = itemType == ItemType.FREE
        val showDateTime = inputType == InputType.ENTRY
        val showAddToPredefined = itemType == ItemType.FREE && inputType == InputType.ENTRY
        val isValueRequired = inputType == InputType.ENTRY
        
        // Dialog properties
        val dialogType = when {
            inputType == InputType.CONFIG && actionType == ActionType.CREATE -> DialogType.CREATE
            inputType == InputType.CONFIG && actionType == ActionType.UPDATE -> DialogType.EDIT
            else -> DialogType.CONFIRM
        }
        
        val dialogTitle = when {
            inputType == InputType.CONFIG && actionType == ActionType.CREATE -> "Créer un élément"
            inputType == InputType.CONFIG && actionType == ActionType.UPDATE -> "Modifier l'élément"
            itemType == ItemType.FREE -> "Créer une entrée"
            else -> "Modifier l'entrée"
        }
        
        // Form validation - lightweight UI validation following patterns
        val isFormValid = remember(itemName, trackingType, numericQuantity, textValue, choiceValue) {
            when {
                itemName.trim().isEmpty() -> false
                trackingType == "numeric" && isValueRequired && numericQuantity.trim().isEmpty() -> false
                trackingType == "text" && isValueRequired && textValue.trim().isEmpty() -> false
                trackingType == "choice" && isValueRequired && choiceValue.trim().isEmpty() -> false
                else -> true
            }
        }
        
        UI.Dialog(
            type = dialogType,
            onConfirm = {
                if (isFormValid) {
                    // Build properties map based on type
                    val properties = when (trackingType) {
                        "numeric" -> mapOf(
                            "quantity" to numericQuantity.trim(),
                            "unit" to numericUnit.trim()
                        )
                        "scale" -> mapOf(
                            "value" to (scaleValue.toIntOrNull() ?: 5)
                        )
                        "text" -> mapOf(
                            "text" to textValue.trim()
                        )
                        "choice" -> mapOf(
                            "selected_option" to choiceValue.trim()
                        )
                        "boolean" -> mapOf(
                            "state" to booleanValue
                        )
                        "counter" -> mapOf(
                            "increment" to (counterIncrement.toIntOrNull() ?: 1)
                        )
                        "timer" -> {
                            android.util.Log.d("TRACKING_DEBUG", "UniversalTrackingDialog - timer properties: itemName='${itemName.trim()}', timerDuration=$timerDuration")
                            mapOf(
                                "activity" to itemName.trim(),
                                "duration_minutes" to (timerDuration.toIntOrNull() ?: 0)
                            )
                        }
                        else -> emptyMap()
                    }
                    
                    android.util.Log.d("TRACKING_DEBUG", "UniversalTrackingDialog - calling onConfirm with: name='${itemName.trim()}', properties=$properties, trackingType=$trackingType")
                    onConfirm(itemName.trim(), properties, addToPredefined, itemDate, itemTime)
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
                
                // 1. Date and time fields (first if present)
                if (showDateTime) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UI.FormField(
                            label = "Date",
                            value = itemDate,
                            onChange = { },
                            fieldType = FieldType.TEXT,
                            required = true,
                            readonly = true,
                            onClick = { showDatePicker = true }
                        )
                        
                        UI.FormField(
                            label = "Heure",
                            value = itemTime,
                            onChange = { },
                            fieldType = FieldType.TEXT,
                            required = true,
                            readonly = true,
                            onClick = { showTimePicker = true }
                        )
                    }
                }
                
                // 2. Name field
                UI.FormField(
                    label = "Nom",
                    value = itemName,
                    onChange = { itemName = it },
                    required = true,
                    fieldType = FieldType.TEXT,
                    readonly = !isNameEditable
                )
                
                // 3. Type-specific fields
                when (trackingType) {
                    "numeric" -> {
                        val valueLabel = if (isValueRequired) "Quantité" else "Quantité par défaut"
                        
                        UI.FormField(
                            label = valueLabel,
                            value = numericQuantity,
                            onChange = { numericQuantity = it },
                            fieldType = FieldType.NUMERIC,
                            required = isValueRequired
                        )
                        
                        UI.FormField(
                            label = "Unité",
                            value = numericUnit,
                            onChange = { numericUnit = it },
                            readonly = itemType == ItemType.PREDEFINED && inputType == InputType.ENTRY
                        )
                    }
                    
                    "scale" -> {
                        val minValue = config.optInt("min", 1)
                        val maxValue = config.optInt("max", 10)
                        val minLabel = config.optString("min_label", "")
                        val maxLabel = config.optString("max_label", "")
                        
                        UI.SliderField(
                            label = "Valeur",
                            value = scaleValue.toIntOrNull() ?: 5,
                            onValueChange = { scaleValue = it.toString() },
                            range = minValue..maxValue,
                            minLabel = minLabel,
                            maxLabel = maxLabel,
                            required = isValueRequired
                        )
                    }
                    
                    "text" -> {
                        UI.FormField(
                            label = "Texte",
                            value = textValue,
                            onChange = { textValue = it },
                            fieldType = FieldType.TEXT,
                            required = isValueRequired
                        )
                    }
                    
                    "choice" -> {
                        val options = config.optJSONArray("options")?.let { array ->
                            (0 until array.length()).map { array.getString(it) }
                        } ?: emptyList()
                        
                        UI.FormSelection(
                            label = "Option",
                            options = options,
                            selected = choiceValue,
                            onSelect = { choiceValue = it },
                            required = isValueRequired
                        )
                    }
                    
                    "boolean" -> {
                        val trueLabel = config.optString("true_label", "Oui")
                        val falseLabel = config.optString("false_label", "Non")
                        
                        UI.ToggleField(
                            label = "État",
                            checked = booleanValue,
                            onCheckedChange = { booleanValue = it },
                            trueLabel = trueLabel,
                            falseLabel = falseLabel,
                            required = isValueRequired
                        )
                    }
                    
                    "counter" -> {
                        UI.FormField(
                            label = "Incrément",
                            value = counterIncrement,
                            onChange = { value ->
                                counterIncrement = value
                            },
                            fieldType = FieldType.NUMERIC,
                            required = isValueRequired
                        )
                    }
                    
                    "timer" -> {
                        UI.FormField(
                            label = "Durée (minutes)",
                            value = timerDuration,
                            onChange = { value ->
                                timerDuration = value
                            },
                            fieldType = FieldType.NUMERIC,
                            required = isValueRequired
                        )
                    }
                }
                
                // 4. "Add to shortcuts" checkbox (last if present)
                if (showAddToPredefined) {
                    UI.Checkbox(
                        checked = addToPredefined,
                        onCheckedChange = { checked -> addToPredefined = checked },
                        label = "Ajouter aux raccourcis"
                    )
                }
            }
        }
        
        // Date picker dialog
        if (showDatePicker) {
            UI.DatePicker(
                selectedDate = itemDate,
                onDateSelected = { newDate ->
                    itemDate = newDate
                },
                onDismiss = {
                    showDatePicker = false
                }
            )
        }
        
        // Time picker dialog
        if (showTimePicker) {
            UI.TimePicker(
                selectedTime = itemTime,
                onTimeSelected = { newTime ->
                    itemTime = newTime
                },
                onDismiss = {
                    showTimePicker = false
                }
            )
        }
    }
}