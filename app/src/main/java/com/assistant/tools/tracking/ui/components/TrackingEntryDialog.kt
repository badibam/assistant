package com.assistant.tools.tracking.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.utils.DateUtils
import com.assistant.core.validation.SchemaValidator
import com.assistant.core.validation.ValidationResult
import com.assistant.core.strings.Strings
import org.json.JSONObject
import org.json.JSONArray

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
    initialName: String = "",
    initialData: Map<String, Any> = emptyMap(),
    initialTimestamp: Long = System.currentTimeMillis(),
    onConfirm: (name: String, dataJson: String, addToPredefined: Boolean, timestamp: Long) -> Unit,
    onCancel: () -> Unit
) {
    // State management
    var name by remember(isVisible) { mutableStateOf(initialName) }
    var timestamp by remember(isVisible, initialTimestamp) { mutableStateOf(initialTimestamp) }
    var addToPredefined by remember(isVisible) { mutableStateOf(false) }
    
    // Type-specific value states
    var numericQuantity by remember(isVisible) { 
        mutableStateOf(initialData["quantity"]?.toString() ?: initialData["default_quantity"]?.toString() ?: "") 
    }
    var numericUnit by remember(isVisible) { 
        mutableStateOf(initialData["unit"]?.toString() ?: "") 
    }
    
    var textValue by remember(isVisible) { 
        mutableStateOf(initialData["text"]?.toString() ?: "") 
    }
    
    var scaleRating by remember(isVisible) { 
        mutableStateOf((initialData["rating"] as? Number)?.toInt())
    }
    
    var choiceValue by remember(isVisible) { 
        mutableStateOf(initialData["selected_option"]?.toString() ?: "") 
    }
    
    var booleanValue by remember(isVisible) { 
        mutableStateOf(initialData["state"] as? Boolean ?: false) 
    }
    
    var counterIncrement by remember(isVisible) { 
        mutableStateOf(initialData["increment"]?.toString() ?: initialData["default_increment"]?.toString() ?: "1") 
    }
    
    // Timer: 3 champs séparés H/M/S
    val initialSeconds = (initialData["duration_seconds"] as? Number)?.toInt() ?: 0
    var timerHours by remember(isVisible) { 
        mutableStateOf((initialSeconds / 3600).toString()) 
    }
    var timerMinutes by remember(isVisible) { 
        mutableStateOf(((initialSeconds % 3600) / 60).toString()) 
    }
    var timerSeconds by remember(isVisible) { 
        mutableStateOf((initialSeconds % 60).toString()) 
    }

    // Date/time UI states
    var dateString by remember(isVisible) { 
        mutableStateOf(DateUtils.formatDateForDisplay(timestamp)) 
    }
    var timeString by remember(isVisible) { 
        mutableStateOf(DateUtils.formatTimeForDisplay(timestamp)) 
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Validation state
    var validationResult: ValidationResult by remember { mutableStateOf(ValidationResult.success()) }

    // Get Android context for string resources
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "tracking", context = context) }

    // ═══ Vraies valeurs centralisées pour tous les types (utilisées dans validateForm ET UI) ═══
    val realValues = remember(trackingType, config, initialData, s) {
        when (trackingType) {
            "scale" -> mapOf(
                "minValue" to ((initialData["min_value"] as? Number)?.toInt() 
                    ?: if (config.has("min")) config.getInt("min") else null),
                "maxValue" to ((initialData["max_value"] as? Number)?.toInt() 
                    ?: if (config.has("max")) config.getInt("max") else null),
                "minLabel" to ((initialData["min_label"] as? String)
                    ?: if (config.has("min_label")) config.getString("min_label") else null),
                "maxLabel" to ((initialData["max_label"] as? String)
                    ?: if (config.has("max_label")) config.getString("max_label") else null)
            )
            "boolean" -> mapOf(
                "trueLabel" to ((initialData["true_label"] as? String)
                    ?: if (config.has("true_label")) config.getString("true_label") else s.tool("config_default_true_label")),
                "falseLabel" to ((initialData["false_label"] as? String)
                    ?: if (config.has("false_label")) config.getString("false_label") else s.tool("config_default_false_label"))
            )
            "choice" -> mapOf(
                "options" to (config.optJSONArray("options")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                } ?: emptyList<String>())
            )
            else -> emptyMap()
        }
    }
    
    // State for error messages
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // State for validated data object (single source of truth)
    var dataObject by remember { mutableStateOf<Any>(emptyMap<String, Any>()) }

    // UI behavior based on context
    val isNameEditable = true // Name is always editable
    val showAddToPredefinedCheckbox = itemType == ItemType.FREE && actionType == ActionType.CREATE
    val dialogTitle = when (actionType) {
        ActionType.CREATE -> when (itemType) {
            ItemType.FREE -> s.tool("usage_dialog_create_entry")
            ItemType.PREDEFINED -> s.tool("usage_dialog_use_item")
            null -> s.tool("usage_dialog_create_entry")
        }
        ActionType.UPDATE -> s.tool("usage_dialog_edit_entry")
    }

    // Sync date/time with timestamp
    LaunchedEffect(dateString, timeString) {
        timestamp = DateUtils.combineDateTime(dateString, timeString)
    }

    // Reset validation errors when dialog opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            validationResult = ValidationResult.success()
        }
    }

    // Validation function
    val validateForm = {
        // Build data JSON according to tracking type
        val dataJson = when (trackingType) {
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
                val minValue = realValues["minValue"] as? Int
                val maxValue = realValues["maxValue"] as? Int  
                val minLabel = realValues["minLabel"] as? String
                val maxLabel = realValues["maxLabel"] as? String
                
                JSONObject().apply {
                    put("type", "scale")
                    put("rating", scaleRating)
                    if (minValue != null) put("min_value", minValue)
                    if (maxValue != null) put("max_value", maxValue)
                    if (minLabel != null) put("min_label", minLabel)
                    if (maxLabel != null) put("max_label", maxLabel)
                    val rangeText = if (minValue != null && maxValue != null) " ($minValue à $maxValue)" else ""
                    put("raw", "$scaleRating$rangeText")
                }.toString()
            }
            
            "choice" -> {
                val options = realValues["options"] as? List<String> ?: emptyList()
                
                JSONObject().apply {
                    put("type", "choice")
                    put("selected_option", choiceValue.trim())
                    put("available_options", JSONArray(options))
                    put("raw", choiceValue.trim())
                }.toString()
            }
            
            "boolean" -> {
                val trueLabel = realValues["trueLabel"] as? String ?: s.tool("config_default_true_label")
                val falseLabel = realValues["falseLabel"] as? String ?: s.tool("config_default_false_label")
                
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
            
            "timer" -> {
                // Convertir H/M/S vers secondes totales
                val h = timerHours.trim().toIntOrNull() ?: 0
                val m = timerMinutes.trim().toIntOrNull() ?: 0  
                val s = timerSeconds.trim().toIntOrNull() ?: 0
                val totalSeconds = h * 3600 + m * 60 + s
                
                // Format intelligent pour raw
                val rawText = buildString {
                    if (h > 0) append("${h}h ")
                    if (m > 0) append("${m}m ")
                    if (s > 0 || (h == 0 && m == 0)) append("${s}s")
                }.trim()
                
                JSONObject().apply {
                    put("type", "timer")
                    put("duration_seconds", totalSeconds)
                    put("raw", rawText)
                }.toString()
            }
            
            else -> "{}"
        }

        // Build complete TrackingData structure for validation
        // Parse dataJson to object for schema validation
        dataObject = com.assistant.tools.tracking.TrackingUtils.convertToValidationFormat(dataJson, trackingType)
        
        val entryData = mapOf(
            "id" to "temp-validation-id",
            "tool_instance_id" to toolInstanceId,
            "tooltype" to "tracking",
            "name" to name.trim(),
            "data" to dataObject, // Use parsed object, not JSON string
            "timestamp" to timestamp,
            "created_at" to System.currentTimeMillis(),
            "updated_at" to System.currentTimeMillis()
        )

        // Log data being validated
        android.util.Log.d("VALDEBUG", "=== VALIDATION START ===")
        android.util.Log.d("VALDEBUG", "trackingType: $trackingType")
        android.util.Log.d("VALDEBUG", "dataJson: $dataJson")
        android.util.Log.d("VALDEBUG", "dataObject: $dataObject")
        android.util.Log.d("VALDEBUG", "entryData: $entryData")
        
        val toolType = com.assistant.core.tools.ToolTypeManager.getToolType("tracking")
        if (toolType != null) {
            android.util.Log.d("VALDEBUG", "DataSchema being used: ${toolType.getDataSchema()?.take(200)}...")
            validationResult = SchemaValidator.validate(toolType, entryData, context, schemaType = "data")
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
                    // Use validated and transformed dataObject (single source of truth)
                    // This ensures Dialog and Service use exactly the same data format
                    val dataJson = JSONObject().apply {
                        val dataMap = dataObject as Map<String, Any>
                        for ((key, value) in dataMap) {
                            when (value) {
                                is List<*> -> put(key, JSONArray(value))
                                else -> put(key, value)
                            }
                        }
                    }.toString()
                    
                    android.util.Log.d("VALDEBUG", "=== CALLING PARENT ONCONFIRM ===")
                    android.util.Log.d("VALDEBUG", "Final name: '${name.trim()}'")
                    android.util.Log.d("VALDEBUG", "Final dataJson: $dataJson")
                    android.util.Log.d("VALDEBUG", "Final addToPredefined: $addToPredefined")
                    android.util.Log.d("VALDEBUG", "Final timestamp: $timestamp")
                    
                    onConfirm(name.trim(), dataJson, addToPredefined, timestamp)
                } else {
                    // DEBUG: Detailed error logging
                    android.util.Log.e("VALDEBUG", "=== VALIDATION FAILED ===")
                    android.util.Log.e("VALDEBUG", "Field data: $dataObject")
                    android.util.Log.e("VALDEBUG", "Error: ${validationResult.errorMessage}")
                    android.util.Log.e("VALDEBUG", "Tracking type: $trackingType")
                    android.util.Log.e("VALDEBUG", "Device: Android ${android.os.Build.VERSION.RELEASE}")
                    
                    // Set error message for toast display
                    errorMessage = validationResult.errorMessage ?: s.shared("message_validation_error_simple")
                }
            },
            onCancel = onCancel
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UI.Text(dialogTitle, TextType.SUBTITLE)
                
                // Name field
                UI.FormField(
                    label = s.shared("tools_config_label_name"),
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
                                    label = s.tool("usage_label_quantity"),
                                    value = numericQuantity,
                                    onChange = { numericQuantity = it },
                                    required = true,
                                    fieldType = FieldType.NUMERIC
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                UI.FormField(
                                    label = s.tool("usage_label_unit"),
                                    value = numericUnit,
                                    onChange = { numericUnit = it },
                                    fieldType = FieldType.TEXT
                                )
                            }
                        }
                    }
                    
                    "text" -> {
                        UI.FormField(
                            label = s.tool("usage_label_text"),
                            value = textValue,
                            onChange = { textValue = it },
                            required = true,
                            fieldType = FieldType.TEXT_MEDIUM
                        )
                    }
                    
                    "scale" -> {
                        // Utiliser les vraies valeurs centralisées
                        val minValue = realValues["minValue"] as? Int
                        val maxValue = realValues["maxValue"] as? Int
                        val minLabel = realValues["minLabel"] as? String
                        val maxLabel = realValues["maxLabel"] as? String
                        
                        android.util.Log.d("SCALE_DEBUG", "Dialog values: min=$minValue, max=$maxValue, minLabel='$minLabel', maxLabel='$maxLabel'")
                        
                        if (minValue != null && maxValue != null) {
                            // Initialiser à la valeur min si pas encore définie
                            val currentRating = scaleRating ?: minValue
                            if (scaleRating == null) {
                                scaleRating = currentRating
                            }
                            
                            UI.SliderField(
                                label = s.tool("usage_label_rating"),
                                value = currentRating,
                                onValueChange = { scaleRating = it },
                                range = minValue..maxValue,
                                minLabel = minLabel?.ifBlank { minValue.toString() } ?: minValue.toString(),
                                maxLabel = maxLabel?.ifBlank { maxValue.toString() } ?: maxValue.toString(),
                                required = true
                            )
                        } else {
                            // Pas de données min/max valides - afficher erreur
                            UI.Text(s.tool("usage_error_scale_config"), TextType.BODY)
                            android.util.Log.e("SCALE_DEBUG", "minValue ou maxValue null - impossible d'afficher le slider")
                        }
                    }
                    
                    "choice" -> {
                        val options = realValues["options"] as? List<String> ?: emptyList()
                        
                        // Show available options (readonly context)
                        if (options.isNotEmpty()) {
                            UI.Text(
                                text = s.tool("usage_available_options").format(options.joinToString(", ")),
                                type = TextType.CAPTION
                            )
                        }
                        
                        UI.FormSelection(
                            label = s.tool("usage_label_choice"),
                            options = options,
                            selected = choiceValue,
                            onSelect = { choiceValue = it },
                            required = true
                        )
                    }
                    
                    "boolean" -> {
                        val trueLabel = realValues["trueLabel"] as? String ?: s.tool("config_default_true_label")
                        val falseLabel = realValues["falseLabel"] as? String ?: s.tool("config_default_false_label")
                        
                        UI.ToggleField(
                            label = s.tool("usage_label_state"),
                            checked = booleanValue,
                            onCheckedChange = { booleanValue = it },
                            trueLabel = trueLabel,
                            falseLabel = falseLabel,
                            required = true
                        )
                    }
                    
                    "counter" -> {
                        UI.FormField(
                            label = s.tool("usage_label_increment"),
                            value = counterIncrement,
                            onChange = { counterIncrement = it },
                            required = true,
                            fieldType = FieldType.NUMERIC
                        )
                    }
                    
                    "timer" -> {
                        // 3 champs séparés pour H/M/S
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                UI.FormField(
                                    label = s.tool("usage_label_hours"),
                                    value = timerHours,
                                    onChange = { timerHours = it },
                                    fieldType = FieldType.NUMERIC
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                UI.FormField(
                                    label = s.tool("usage_label_minutes"), 
                                    value = timerMinutes,
                                    onChange = { timerMinutes = it },
                                    fieldType = FieldType.NUMERIC
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                UI.FormField(
                                    label = s.tool("usage_label_seconds"),
                                    value = timerSeconds,
                                    onChange = { timerSeconds = it },
                                    fieldType = FieldType.NUMERIC
                                )
                            }
                        }
                    }
                }
                
                // Date and time fields
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = s.shared("label_date"),
                            value = dateString,
                            onChange = { /* readonly, use picker */ },
                            readonly = true,
                            onClick = { showDatePicker = true },
                            fieldType = FieldType.TEXT
                        )
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = s.shared("label_time"),
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
                        label = s.tool("usage_add_to_shortcuts")
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
        
        // Show error toast when errorMessage is set
        errorMessage?.let { message ->
            val context = LocalContext.current
            LaunchedEffect(message) {
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_LONG
                ).show()
                errorMessage = null
            }
        }
    }
}