package com.assistant.tools.tracking.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.utils.NumberFormatting
import com.assistant.core.utils.DateUtils
import com.assistant.tools.tracking.timer.TimerManager
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generic section for predefined items with type-specific button behaviors
 * Supports all 7 tracking types with appropriate UX patterns
 */
@Composable
fun PredefinedItemsSection(
    config: JSONObject,
    trackingType: String,
    isLoading: Boolean,
    toolInstanceId: String,
    onQuickSave: (name: String, properties: Map<String, Any>) -> Unit,
    onOpenDialog: (name: String, properties: Map<String, Any>) -> Unit,
    onEntrySaved: () -> Unit = {},
    defaultTimestamp: Long = System.currentTimeMillis(),
    onDefaultTimestampChange: (Long) -> Unit = {}
) {
    // Parse predefined items from config
    val predefinedItems = remember(config) {
        val itemsArray = config.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<TrackingItem>()
        
        for (i in 0 until itemsArray.length()) {
            val itemJson = itemsArray.getJSONObject(i)
            val properties = mutableMapOf<String, Any>()
            
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
        
        items
    }
    
    // State for custom default timestamp
    var useCustomTimestamp by remember { mutableStateOf(false) }
    var customDate by remember { mutableStateOf("") }
    var customTime by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Initialize custom fields when toggle is first activated
    LaunchedEffect(useCustomTimestamp) {
        if (useCustomTimestamp && customDate.isBlank()) {
            val now = System.currentTimeMillis()
            customDate = DateUtils.formatDateForDisplay(now)
            customTime = DateUtils.formatTimeForDisplay(now)
        }
    }
    
    // Update defaultTimestamp when custom date/time changes
    LaunchedEffect(customDate, customTime, useCustomTimestamp) {
        android.util.Log.d("TIMESTAMP_DEBUG", "LaunchedEffect: customDate=$customDate, customTime=$customTime, useCustom=$useCustomTimestamp")
        if (useCustomTimestamp && customDate.isNotBlank() && customTime.isNotBlank()) {
            val newTimestamp = try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val dateTimeString = "$customDate $customTime"
                val parsed = dateFormat.parse(dateTimeString)?.time ?: System.currentTimeMillis()
                android.util.Log.d("TIMESTAMP_DEBUG", "Parsed custom timestamp: $parsed ($dateTimeString)")
                parsed
            } catch (e: Exception) {
                android.util.Log.e("TIMESTAMP_DEBUG", "Error parsing custom timestamp", e)
                System.currentTimeMillis()
            }
            android.util.Log.d("TIMESTAMP_DEBUG", "Calling onDefaultTimestampChange with: $newTimestamp")
            onDefaultTimestampChange(newTimestamp)
        } else {
            android.util.Log.d("TIMESTAMP_DEBUG", "Not updating timestamp - conditions not met")
        }
    }
    
    // Construct final timestamp to use for saving
    val finalTimestamp = if (useCustomTimestamp && customDate.isNotBlank() && customTime.isNotBlank()) {
        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateTimeString = "$customDate $customTime"
            dateFormat.parse(dateTimeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    } else {
        System.currentTimeMillis() // Always fresh timestamp when toggle is off
    }

    if (predefinedItems.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Custom timestamp controls section - all on one line
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Toggle for custom timestamp - disabled if timer running (only for timer type)
                val canUseCustomTimestamp = if (trackingType == "timer") {
                    val timerManager = remember { TimerManager.getInstance() }
                    val timerState by timerManager.timerState
                    !timerState.isActive
                } else {
                    true // Always enabled for non-timer types
                }
                
                UI.ToggleField(
                    label = "",
                    checked = useCustomTimestamp && canUseCustomTimestamp,
                    trueLabel = "Date personnalisée",
                    falseLabel = "Date = maintenant",
                    onCheckedChange = { checked ->
                        if (canUseCustomTimestamp) {
                            useCustomTimestamp = checked
                            if (!checked) {
                                // When disabling, clear custom fields - System.currentTimeMillis() will be used
                                customDate = ""
                                customTime = ""
                            }
                        }
                    }
                )
                
                // Date and time fields when custom timestamp is enabled and allowed
                if (useCustomTimestamp && canUseCustomTimestamp) {
                    // Date field
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Date",
                            value = customDate,
                            onChange = { customDate = it },
                            fieldType = FieldType.TEXT,
                            required = true,
                            readonly = true,
                            onClick = { showDatePicker = true }
                        )
                    }
                    
                    // Time field
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Heure",
                            value = customTime,
                            onChange = { customTime = it },
                            fieldType = FieldType.TEXT,
                            required = true,
                            readonly = true,
                            onClick = { showTimePicker = true }
                        )
                    }
                }
            }
            
            // Display items with type-specific layouts
            when (trackingType) {
                "numeric" -> {
                    NumericItemsLayout(
                        items = predefinedItems,
                        isLoading = isLoading,
                        onQuickSave = { name, properties ->
                            onQuickSave(name, properties)
                        },
                        onOpenDialog = { name, properties ->
                            onOpenDialog(name, properties)
                        },
                        customTimestamp = finalTimestamp
                    )
                }
                
                "boolean" -> {
                    BooleanItemsLayout(
                        items = predefinedItems,
                        config = config,
                        isLoading = isLoading,
                        onQuickSave = { name, properties ->
                            onQuickSave(name, properties)
                        },
                        onOpenDialog = { name, properties ->
                            onOpenDialog(name, properties)
                        },
                        customTimestamp = finalTimestamp
                    )
                }
                
                "counter" -> {
                    CounterItemsLayout(
                        items = predefinedItems,
                        config = config,
                        isLoading = isLoading,
                        onQuickSave = { name, properties ->
                            onQuickSave(name, properties)
                        },
                        onOpenDialog = { name, properties ->
                            onOpenDialog(name, properties)
                        },
                        customTimestamp = finalTimestamp
                    )
                }
                
                "timer" -> {
                    TimerItemsLayout(
                        items = predefinedItems,
                        isLoading = isLoading,
                        toolInstanceId = toolInstanceId,
                        useCustomTimestamp = useCustomTimestamp,
                        onQuickSave = { name, properties ->
                            onQuickSave(name, properties)
                        },
                        onOpenDialog = { name, properties ->
                            onOpenDialog(name, properties)
                        },
                        onEntrySaved = onEntrySaved,
                        customTimestamp = finalTimestamp
                    )
                }
                
                else -> {
                    // SCALE, TEXT, CHOICE - simple clickable buttons that open dialog
                    SimpleItemsLayout(
                        items = predefinedItems,
                        isLoading = isLoading,
                        onOpenDialog = { name, properties ->
                            onOpenDialog(name, properties)
                        },
                        customTimestamp = finalTimestamp
                    )
                }
            }
        }
    }
    
    // Date and time pickers
    if (showDatePicker) {
        UI.DatePicker(
            selectedDate = customDate,
            onDateSelected = { newDate ->
                customDate = newDate
            },
            onDismiss = {
                showDatePicker = false
            }
        )
    }
    
    if (showTimePicker) {
        UI.TimePicker(
            selectedTime = customTime,
            onTimeSelected = { newTime ->
                customTime = newTime
            },
            onDismiss = {
                showTimePicker = false
            }
        )
    }
}

/**
 * Layout for NUMERIC items: quick add button + edit button per item
 */
@Composable
private fun NumericItemsLayout(
    items: List<TrackingItem>,
    isLoading: Boolean,
    onQuickSave: (String, Map<String, Any>) -> Unit,
    onOpenDialog: (String, Map<String, Any>) -> Unit,
    customTimestamp: Long = System.currentTimeMillis()
) {
    items.forEach { item ->
        val defaultQuantity = item.getProperty("default_quantity")
        val hasDefaultQuantity = defaultQuantity.isNotBlank()
        
        val displayText = buildString {
            append(item.name)
            if (hasDefaultQuantity) {
                append(" (")
                append(defaultQuantity)
                val unit = item.getProperty("unit")
                if (unit.isNotBlank()) {
                    append("\u00A0${unit}")
                }
                append(")")
            } else {
                val unit = item.getProperty("unit")
                if (unit.isNotBlank()) {
                    append("\u00A0(${unit})")
                }
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                UI.Text(displayText, TextType.BODY)
            }
            
            // Quick add button
            UI.ActionButton(
                action = ButtonAction.ADD,
                display = ButtonDisplay.ICON,
                size = Size.S,
                enabled = !isLoading,
                onClick = {
                    if (hasDefaultQuantity) {
                        // Quick save with default quantity
                        val numericQuantity = NumberFormatting.parseUserInput(defaultQuantity)
                        if (numericQuantity != null) {
                            onQuickSave(item.name, mapOf(
                                "quantity" to defaultQuantity,
                                "unit" to item.getProperty("unit")
                            ))
                        }
                    } else {
                        // Open dialog
                        onOpenDialog(item.name, item.properties)
                    }
                }
            )
            
            // Edit button (always opens dialog)
            UI.ActionButton(
                action = ButtonAction.EDIT,
                display = ButtonDisplay.ICON,
                size = Size.S,
                enabled = !isLoading,
                onClick = {
                    onOpenDialog(item.name, item.properties)
                }
            )
        }
    }
}

/**
 * Layout for BOOLEAN items: thumbs up/down buttons + edit button
 */
@Composable
private fun BooleanItemsLayout(
    items: List<TrackingItem>,
    config: JSONObject,
    isLoading: Boolean,
    onQuickSave: (String, Map<String, Any>) -> Unit,
    onOpenDialog: (String, Map<String, Any>) -> Unit,
    customTimestamp: Long = System.currentTimeMillis()
) {
    items.forEach { item ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                UI.Text(item.name, TextType.BODY)
            }
            
            // Get labels from config 
            val trueLabel = config.optString("true_label", "Oui")
            val falseLabel = config.optString("false_label", "Non")
            
            // Thumbs up button (true)
            UI.Button(
                type = ButtonType.DEFAULT,
                size = Size.S,
                onClick = {
                    onQuickSave(item.name, mapOf(
                        "state" to true,
                        "true_label" to trueLabel,
                        "false_label" to falseLabel
                    ))
                }
            ) {
                UI.Text("👍", TextType.BODY)
            }
            
            // Thumbs down button (false)
            UI.Button(
                type = ButtonType.DEFAULT,
                size = Size.S,
                onClick = {
                    onQuickSave(item.name, mapOf(
                        "state" to false,
                        "true_label" to trueLabel,
                        "false_label" to falseLabel
                    ))
                }
            ) {
                UI.Text("👎", TextType.BODY)
            }
            
            // Edit button
            UI.ActionButton(
                action = ButtonAction.EDIT,
                display = ButtonDisplay.ICON,
                size = Size.S,
                enabled = !isLoading,
                onClick = {
                    onOpenDialog(item.name, item.properties)
                }
            )
        }
    }
}

/**
 * Layout for COUNTER items: increment/decrement buttons + edit button
 */
@Composable
private fun CounterItemsLayout(
    items: List<TrackingItem>,
    config: JSONObject,
    isLoading: Boolean,
    onQuickSave: (String, Map<String, Any>) -> Unit,
    onOpenDialog: (String, Map<String, Any>) -> Unit,
    customTimestamp: Long = System.currentTimeMillis()
) {
    val allowDecrement = config.optBoolean("allow_decrement", true)
    
    items.forEach { item ->
        val increment = item.getProperty("increment").toIntOrNull() ?: 1
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                UI.Text(item.name, TextType.BODY)
            }
            
            // Increment button
            UI.Button(
                type = ButtonType.DEFAULT,
                size = Size.S,
                onClick = {
                    onQuickSave(item.name, mapOf("increment" to increment))
                }
            ) {
                UI.Text("+$increment", TextType.BODY)
            }
            
            // Decrement button (if allowed)
            if (allowDecrement) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.S,
                    onClick = {
                        onQuickSave(item.name, mapOf("increment" to -increment))
                    }
                ) {
                    UI.Text("-$increment", TextType.BODY)
                }
            }
            
            // Edit button
            UI.ActionButton(
                action = ButtonAction.EDIT,
                display = ButtonDisplay.ICON,
                size = Size.S,
                enabled = !isLoading,
                onClick = {
                    onOpenDialog(item.name, item.properties)
                }
            )
        }
    }
}

/**
 * Layout for TIMER items: activity buttons with auto-switch behavior
 */
@Composable
private fun TimerItemsLayout(
    items: List<TrackingItem>,
    isLoading: Boolean,
    toolInstanceId: String,
    useCustomTimestamp: Boolean,
    onQuickSave: (String, Map<String, Any>) -> Unit,
    onOpenDialog: (String, Map<String, Any>) -> Unit,
    onEntrySaved: () -> Unit,
    customTimestamp: Long = System.currentTimeMillis()
) {
    val context = LocalContext.current
    val timerManager = remember { TimerManager.getInstance() }
    val timerState by timerManager.timerState
    val coordinator = remember { Coordinator(context) }
    
    // Fonction pour créer une entrée timer avec durée = 0
    val createTimerEntry: (String) -> String = { name ->
        val timerValueJson = JSONObject().apply {
            put("type", "timer")
            put("duration_seconds", 0)
            put("raw", "0s")
        }.toString()
        
        val params = mutableMapOf<String, Any>(
            "toolInstanceId" to toolInstanceId,
            "tooltype" to "tracking", 
            "timestamp" to System.currentTimeMillis(),
            "name" to name,
            "data" to JSONObject(timerValueJson)
        )
        
        try {
            val result = runBlocking { coordinator.processUserAction("create->tool_data", params) }
            if (result.status == CommandStatus.SUCCESS) {
                result.data?.get("id") as? String ?: "error_no_id"
            } else {
                "error_${System.currentTimeMillis()}"
            }
        } catch (e: Exception) {
            "error_${System.currentTimeMillis()}"
        }
    }
    
    // Fonction pour mettre à jour une entrée timer avec la durée finale
    val updateTimerEntry: (String, Int) -> Unit = { entryId, durationSeconds ->
        val timerValueJson = JSONObject().apply {
            put("type", "timer")
            put("duration_seconds", durationSeconds)
            val h = durationSeconds / 3600
            val m = (durationSeconds % 3600) / 60
            val s = durationSeconds % 60
            val rawText = buildString {
                if (h > 0) append("${h}h ")
                if (m > 0) append("${m}m ")
                if (s > 0 || (h == 0 && m == 0)) append("${s}s")
            }.trim()
            put("raw", rawText)
        }.toString()
        
        val params = mutableMapOf<String, Any>(
            "id" to entryId,
            "data" to JSONObject(timerValueJson)
            // On n'inclut pas "name" pour garder le nom existant
        )
        
        try {
            val result = runBlocking { coordinator.processUserAction("update->tool_data", params) }
            if (result.status == CommandStatus.SUCCESS) {
                // Refresh de l'historique après mise à jour réussie
                onEntrySaved()
            } else {
                // Afficher toast d'erreur
                android.widget.Toast.makeText(
                    context,
                    "Erreur: ${result.error ?: "Impossible de mettre à jour l'entrée"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                android.util.Log.e("TIMER", "Failed to update timer entry: ${result.error}")
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Erreur: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            android.util.Log.e("TIMER", "Exception updating timer entry", e)
        }
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Barre de timer active
        UI.Text(timerState.formatDisplayText(), TextType.CAPTION)
        
        // Activity buttons
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        val isActive = timerManager.isActivityActive(item.name)
                        
                        UI.Button(
                            type = if (isActive) ButtonType.PRIMARY else ButtonType.DEFAULT,
                            onClick = {
                                if (useCustomTimestamp) {
                                    // Mode date personnalisée : ouvrir dialogue pour saisie manuelle
                                    onOpenDialog(item.name, item.properties)
                                } else {
                                    // Mode temps réel : comportement timer normal
                                    if (isActive) {
                                        // Arrêter le timer actuel
                                        timerManager.stopCurrentTimer { entryId, seconds ->
                                            // Mettre à jour l'entrée existante avec la durée finale
                                            updateTimerEntry(entryId, seconds)
                                        }
                                    } else {
                                        // Démarrer nouveau timer (auto-switch si besoin)
                                        timerManager.startTimer(
                                            activityName = item.name,
                                            toolInstanceId = toolInstanceId,
                                            onPreviousTimerUpdate = { entryId, seconds ->
                                                // Mettre à jour le timer précédent avec la durée finale
                                                updateTimerEntry(entryId, seconds)
                                            },
                                            onCreateNewEntry = { activityName ->
                                                // Créer immédiatement l'entrée avec durée = 0
                                                createTimerEntry(activityName)
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            UI.Text(item.name, TextType.LABEL)
                        }
                    }
                }
                
                // Fill remaining space if odd number
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Layout for SCALE, TEXT, CHOICE items: simple clickable buttons
 */
@Composable
private fun SimpleItemsLayout(
    items: List<TrackingItem>,
    isLoading: Boolean,
    onOpenDialog: (String, Map<String, Any>) -> Unit,
    customTimestamp: Long = System.currentTimeMillis()
) {
    items.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowItems.forEach { item ->
                Box(modifier = Modifier.weight(1f)) {
                    UI.Button(
                        type = ButtonType.DEFAULT,
                        onClick = {
                            onOpenDialog(item.name, item.properties)
                        }
                    ) {
                        UI.Text(item.name, TextType.LABEL)
                    }
                }
            }
            
            // Fill remaining space if odd number
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Data class for tracking item properties (from original NumericTrackingInput)
 */
data class TrackingItem(
    val name: String,
    val properties: Map<String, Any> = emptyMap()
) {
    fun getProperty(key: String): String {
        return properties[key]?.toString() ?: ""
    }
}