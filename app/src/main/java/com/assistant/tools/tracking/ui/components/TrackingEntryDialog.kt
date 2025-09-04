package com.assistant.tools.tracking.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.utils.DateUtils
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import org.json.JSONObject

/**
 * Dialog modes for tracking entries
 */
enum class ItemType { FREE, PREDEFINED }
enum class ActionType { CREATE, UPDATE }

/**
 * TrackingEntryDialog - Clean rewrite for tracking data entry only
 * 
 * Purpose: Create/edit tracking entries (not predefined items configuration)
 * 
 * Use cases:
 * 1. Predefined item: Use predefined item (name + defaults pre-filled)
 * 2. Free entry: Create new entry (name + value from scratch)  
 * 3. History edit: Edit existing entry (all fields editable)
 */
@Composable
fun TrackingEntryDialog(
    isVisible: Boolean,
    trackingType: String,
    config: JSONObject,
    itemType: ItemType?, // PREDEFINED, FREE, or null for history
    actionType: ActionType, // CREATE or UPDATE
    toolInstanceId: String = "",
    zoneName: String = "",
    toolInstanceName: String = "",
    initialName: String = "",
    initialValue: Map<String, Any> = emptyMap(),
    initialRecordedAt: Long = System.currentTimeMillis(),
    onConfirm: (name: String, valueJson: String, addToPredefined: Boolean, recordedAt: Long) -> Unit,
    onCancel: () -> Unit
) {
    // State management
    var name by remember(isVisible) { mutableStateOf(initialName) }
    var recordedAt by remember(isVisible) { mutableStateOf(initialRecordedAt) }
    var addToPredefined by remember(isVisible) { mutableStateOf(false) }
    
    // Type-specific value states
    var numericQuantity by remember(isVisible) { 
        mutableStateOf(initialValue["quantity"]?.toString() ?: initialValue["default_quantity"]?.toString() ?: "") 
    }
    var numericUnit by remember(isVisible) { 
        mutableStateOf(initialValue["unit"]?.toString() ?: "") 
    }
    
    var textValue by remember(isVisible) { 
        mutableStateOf(initialValue["text"]?.toString() ?: "") 
    }
    
    var scaleRating by remember(isVisible) { 
        mutableStateOf((initialValue["rating"] as? Number)?.toInt() ?: 1) 
    }
    
    var choiceValue by remember(isVisible) { 
        mutableStateOf(initialValue["selected_option"]?.toString() ?: "") 
    }
    
    var booleanValue by remember(isVisible) { 
        mutableStateOf(initialValue["state"] as? Boolean ?: false) 
    }
    
    var counterIncrement by remember(isVisible) { 
        mutableStateOf(initialValue["increment"]?.toString() ?: initialValue["default_increment"]?.toString() ?: "1") 
    }
    
    var timerDuration by remember(isVisible) { 
        mutableStateOf(initialValue["duration"]?.toString() ?: "0") 
    }

    // Date/time UI states
    var dateString by remember(isVisible) { 
        mutableStateOf(DateUtils.formatDateForDisplay(recordedAt)) 
    }
    var timeString by remember(isVisible) { 
        mutableStateOf(DateUtils.formatTimeForDisplay(recordedAt)) 
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Validation state
    var validationResult: ValidationResult by remember { mutableStateOf(ValidationResult.success()) }

    // UI behavior based on context
    val isNameEditable = true // Name is always editable
    val showAddToPredefinedCheckbox = itemType == ItemType.FREE && actionType == ActionType.CREATE
    val dialogTitle = when (actionType) {
        ActionType.CREATE -> when (itemType) {
            ItemType.FREE -> "Créer une entrée"
            ItemType.PREDEFINED -> "Utiliser l'élément"
            null -> "Créer une entrée"
        }
        ActionType.UPDATE -> "Modifier l'entrée"
    }

    // Sync date/time with recordedAt
    LaunchedEffect(dateString, timeString) {
        recordedAt = DateUtils.combineDateTime(dateString, timeString)
    }

    // Reset validation errors when dialog opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            validationResult = ValidationResult.success()
        }
    }

    // Validation function
    val validateForm = {
        // Build value JSON according to tracking type
        val valueJson = when (trackingType) {
            "numeric" -> JSONObject().apply {
                put("type", "numeric")
                put("quantity", numericQuantity.trim()) // Keep as string per schema
                put("unit", numericUnit.trim())
                put("raw", "${numericQuantity.trim()}${if (numericUnit.isNotBlank()) " ${numericUnit.trim()}" else ""}")
            }.toString()
            
            "text" -> JSONObject().apply {
                put("type", "text")
                put("text", textValue.trim())
                put("raw", textValue.trim())
            }.toString()
            
            "scale" -> {
                val minValue = config.optInt("min", 1)
                val maxValue = config.optInt("max", 10)
                val minLabel = config.optString("min_label", "")
                val maxLabel = config.optString("max_label", "")
                
                JSONObject().apply {
                    put("type", "scale")
                    put("rating", scaleRating)
                    put("min_value", minValue)
                    put("max_value", maxValue)
                    put("min_label", minLabel)
                    put("max_label", maxLabel)
                    put("raw", "$scaleRating ($minValue à $maxValue)")
                }.toString()
            }
            
            "choice" -> JSONObject().apply {
                put("type", "choice")
                put("selected_option", choiceValue.trim())
                val options = config.optJSONArray("options")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                } ?: emptyList()
                put("available_options", options)
                put("raw", choiceValue.trim())
            }.toString()
            
            "boolean" -> {
                val trueLabel = config.optString("true_label", "Oui")
                val falseLabel = config.optString("false_label", "Non")
                
                JSONObject().apply {
                    put("type", "boolean")
                    put("state", booleanValue)
                    put("true_label", trueLabel)
                    put("false_label", falseLabel)
                    put("raw", if (booleanValue) trueLabel else falseLabel)
                }.toString()
            }
            
            "counter" -> JSONObject().apply {
                put("type", "counter")
                put("increment", counterIncrement.toIntOrNull() ?: 1)
                put("raw", counterIncrement.trim())
            }.toString()
            
            "timer" -> JSONObject().apply {
                put("type", "timer")
                put("activity", "") // Timer activity - could be derived from name
                put("duration_minutes", timerDuration.toIntOrNull() ?: 0)
                put("raw", "${timerDuration.trim()} min")
            }.toString()
            
            else -> "{}"
        }

        // Build complete TrackingData structure for validation
        // Parse valueJson to object for schema validation
        val valueObject = try {
            val jsonObj = JSONObject(valueJson)
            // Convert JSONObject to Map for validation
            val map = mutableMapOf<String, Any>()
            jsonObj.keys().forEach { key ->
                val value = jsonObj.get(key)
                // Convert quantity to number for numeric tracking types to match schema
                if (key == "quantity" && trackingType == "numeric" && value is String) {
                    try {
                        map[key] = value.toDouble()
                    } catch (e: NumberFormatException) {
                        map[key] = value
                    }
                } else {
                    map[key] = value
                }
            }
            map
        } catch (e: Exception) {
            emptyMap<String, Any>()
        }
        
        val entryData = mapOf(
            "id" to "temp-validation-id",
            "tool_instance_id" to toolInstanceId,
            "zone_name" to zoneName,
            "tool_instance_name" to toolInstanceName,
            "name" to name.trim(),
            "value" to valueObject, // Use parsed object, not JSON string
            "recorded_at" to recordedAt,
            "created_at" to System.currentTimeMillis(),
            "updated_at" to System.currentTimeMillis()
        )

        // Log data being validated
        android.util.Log.d("VALDEBUG", "=== VALIDATION START ===")
        android.util.Log.d("VALDEBUG", "trackingType: $trackingType")
        android.util.Log.d("VALDEBUG", "valueJson: $valueJson")
        android.util.Log.d("VALDEBUG", "valueObject: $valueObject")
        android.util.Log.d("VALDEBUG", "entryData: $entryData")
        
        val toolType = com.assistant.core.tools.ToolTypeManager.getToolType("tracking")
        if (toolType != null) {
            android.util.Log.d("VALDEBUG", "DataSchema being used: ${toolType.getDataSchema()?.take(200)}...")
            val dataSchema = toolType.getDataSchema() ?: throw IllegalStateException("Aucun schéma de données disponible")
            validationResult = SchemaValidator.validate(dataSchema, entryData)
        } else {
            validationResult = ValidationResult.error("Tool type 'tracking' not found")
        }
        
        // Log validation result for debugging
        android.util.Log.d("VALDEBUG", "TrackingEntryDialog validation result: isValid=${validationResult.isValid}")
        if (!validationResult.isValid) {
            android.util.Log.d("VALDEBUG", "TrackingEntryDialog validation error: ${validationResult.errorMessage}")
        }
        
        // Debug: Log additional details
        try {
            android.util.Log.d("VALDEBUG", "Using new SchemaValidator API")
        } catch (e: Exception) {
            android.util.Log.d("VALDEBUG", "Debug validation failed: ${e.message}")
        }
        android.util.Log.d("VALDEBUG", "=== VALIDATION END ===")
    }

    if (isVisible) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                android.util.Log.d("VALDEBUG", "=== ONCONFIRM CALLED ===")
                validateForm()
                android.util.Log.d("VALDEBUG", "After validation: isValid=${validationResult.isValid}")
                if (validationResult.isValid) {
                    // Build final value JSON
                    val valueJson = when (trackingType) {
                        "numeric" -> JSONObject().apply {
                            put("type", "numeric")
                            put("quantity", numericQuantity.trim()) // Keep as string per schema
                            put("unit", numericUnit.trim())
                            put("raw", "${numericQuantity.trim()}${if (numericUnit.isNotBlank()) " ${numericUnit.trim()}" else ""}")
                        }.toString()
                        
                        "text" -> JSONObject().apply {
                            put("type", "text")
                            put("text", textValue.trim())
                            put("raw", textValue.trim())
                        }.toString()
                        
                        "scale" -> {
                            val minValue = config.optInt("min", 1)
                            val maxValue = config.optInt("max", 10)
                            val minLabel = config.optString("min_label", "")
                            val maxLabel = config.optString("max_label", "")
                            
                            JSONObject().apply {
                                put("type", "scale")
                                put("rating", scaleRating)
                                put("min_value", minValue)
                                put("max_value", maxValue)
                                put("min_label", minLabel)
                                put("max_label", maxLabel)
                                put("raw", "$scaleRating ($minValue à $maxValue)")
                            }.toString()
                        }
                        
                        "choice" -> JSONObject().apply {
                            put("type", "choice")
                            put("selected_option", choiceValue.trim())
                            val options = config.optJSONArray("options")?.let { array ->
                                (0 until array.length()).map { array.getString(it) }
                            } ?: emptyList()
                            put("available_options", options)
                            put("raw", choiceValue.trim())
                        }.toString()
                        
                        "boolean" -> {
                            val trueLabel = config.optString("true_label", "Oui")
                            val falseLabel = config.optString("false_label", "Non")
                            
                            JSONObject().apply {
                                put("type", "boolean")
                                put("state", booleanValue)
                                put("true_label", trueLabel)
                                put("false_label", falseLabel)
                                put("raw", if (booleanValue) trueLabel else falseLabel)
                            }.toString()
                        }
                        
                        "counter" -> JSONObject().apply {
                            put("type", "counter")
                            put("increment", counterIncrement.toIntOrNull() ?: 1)
                            put("raw", counterIncrement.trim())
                        }.toString()
                        
                        "timer" -> JSONObject().apply {
                            put("type", "timer")
                            put("activity", "") // Timer activity - could be derived from name
                            put("duration_minutes", timerDuration.toIntOrNull() ?: 0)
                            put("raw", "${timerDuration.trim()} min")
                        }.toString()
                        
                        else -> "{}"
                    }
                    
                    android.util.Log.d("VALDEBUG", "=== CALLING PARENT ONCONFIRM ===")
                    android.util.Log.d("VALDEBUG", "Final name: '${name.trim()}'")
                    android.util.Log.d("VALDEBUG", "Final valueJson: $valueJson")
                    android.util.Log.d("VALDEBUG", "Final addToPredefined: $addToPredefined")
                    android.util.Log.d("VALDEBUG", "Final recordedAt: $recordedAt")
                    
                    onConfirm(name.trim(), valueJson, addToPredefined, recordedAt)
                }
            },
            onCancel = onCancel
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(dialogTitle, TextType.SUBTITLE)
                
                // Display validation errors if any
                if (!validationResult.isValid) {
                    UI.Text(
                        text = validationResult.errorMessage ?: "Erreur de validation",
                        type = TextType.BODY
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Name field
                UI.FormField(
                    label = "Nom",
                    value = name,
                    onChange = { name = it },
                    required = true,
                    readonly = !isNameEditable,
                    fieldType = FieldType.TEXT
                )
                
                // Type-specific value fields
                when (trackingType) {
                    "numeric" -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(2f)) {
                                UI.FormField(
                                    label = "Quantité",
                                    value = numericQuantity,
                                    onChange = { numericQuantity = it },
                                    required = true,
                                    fieldType = FieldType.NUMERIC
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                UI.FormField(
                                    label = "Unité",
                                    value = numericUnit,
                                    onChange = { numericUnit = it },
                                    fieldType = FieldType.TEXT
                                )
                            }
                        }
                    }
                    
                    "text" -> {
                        UI.FormField(
                            label = "Texte",
                            value = textValue,
                            onChange = { textValue = it },
                            required = true,
                            fieldType = FieldType.TEXT_MEDIUM
                        )
                    }
                    
                    "scale" -> {
                        val minValue = config.optInt("min", 1)
                        val maxValue = config.optInt("max", 10)
                        val minLabel = config.optString("min_label", "")
                        val maxLabel = config.optString("max_label", "")
                        
                        UI.SliderField(
                            label = "Note",
                            value = scaleRating,
                            onValueChange = { scaleRating = it },
                            range = minValue..maxValue,
                            minLabel = minLabel.ifBlank { minValue.toString() },
                            maxLabel = maxLabel.ifBlank { maxValue.toString() },
                            required = true
                        )
                    }
                    
                    "choice" -> {
                        val options = config.optJSONArray("options")?.let { array ->
                            (0 until array.length()).map { array.getString(it) }
                        } ?: emptyList()
                        
                        // Show available options (readonly context)
                        if (options.isNotEmpty()) {
                            UI.Text(
                                text = "Options: ${options.joinToString(", ")}",
                                type = TextType.CAPTION
                            )
                        }
                        
                        UI.FormSelection(
                            label = "Choix",
                            options = options,
                            selected = choiceValue,
                            onSelect = { choiceValue = it },
                            required = true
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
                            required = true
                        )
                    }
                    
                    "counter" -> {
                        UI.FormField(
                            label = "Incrément",
                            value = counterIncrement,
                            onChange = { counterIncrement = it },
                            required = true,
                            fieldType = FieldType.NUMERIC
                        )
                    }
                    
                    "timer" -> {
                        UI.FormField(
                            label = "Durée (minutes)",
                            value = timerDuration,
                            onChange = { timerDuration = it },
                            required = true,
                            fieldType = FieldType.NUMERIC
                        )
                    }
                }
                
                // Date and time fields
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Date",
                            value = dateString,
                            onChange = { /* readonly, use picker */ },
                            readonly = true,
                            onClick = { showDatePicker = true },
                            fieldType = FieldType.TEXT
                        )
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Heure",
                            value = timeString,
                            onChange = { /* readonly, use picker */ },
                            readonly = true,
                            onClick = { showTimePicker = true },
                            fieldType = FieldType.TEXT
                        )
                    }
                }
                
                // Add to predefined checkbox
                if (showAddToPredefinedCheckbox) {
                    UI.Checkbox(
                        checked = addToPredefined,
                        onCheckedChange = { addToPredefined = it },
                        label = "Ajouter aux raccourcis"
                    )
                }
            }
        }
        
        // Date picker dialog
        if (showDatePicker) {
            UI.DatePicker(
                selectedDate = dateString,
                onDateSelected = { newDate ->
                    dateString = newDate
                },
                onDismiss = { showDatePicker = false }
            )
        }
        
        // Time picker dialog  
        if (showTimePicker) {
            UI.TimePicker(
                selectedTime = timeString,
                onTimeSelected = { newTime ->
                    timeString = newTime
                },
                onDismiss = { showTimePicker = false }
            )
        }
    }
}