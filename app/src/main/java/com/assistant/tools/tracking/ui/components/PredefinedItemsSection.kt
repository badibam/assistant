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
    var customDate by remember { mutableStateOf(DateUtils.formatDateForDisplay(defaultTimestamp)) }
    var customTime by remember { mutableStateOf(DateUtils.formatTimeForDisplay(defaultTimestamp)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Update custom fields when defaultTimestamp changes
    LaunchedEffect(defaultTimestamp) {
        if (!useCustomTimestamp) {
            customDate = DateUtils.formatDateForDisplay(defaultTimestamp)
            customTime = DateUtils.formatTimeForDisplay(defaultTimestamp)
        }
    }
    
    // Construct final timestamp to use for saving
    val finalTimestamp = if (useCustomTimestamp) {
        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateTimeString = "$customDate $customTime"
            dateFormat.parse(dateTimeString)?.time ?: defaultTimestamp
        } catch (e: Exception) {
            defaultTimestamp
        }
    } else {
        defaultTimestamp
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
                // Toggle for custom timestamp
                UI.ToggleField(
                    label = "",
                    checked = useCustomTimestamp,
                    trueLabel = "Date personnalisée",
                    falseLabel = "Date = maintenant",
                    onCheckedChange = { checked ->
                        useCustomTimestamp = checked
                        if (checked) {
                            // When enabling custom timestamp, update callback immediately
                            val newTimestamp = try {
                                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                val dateTimeString = "$customDate $customTime"
                                dateFormat.parse(dateTimeString)?.time ?: defaultTimestamp
                            } catch (e: Exception) {
                                defaultTimestamp
                            }
                            onDefaultTimestampChange(newTimestamp)
                        } else {
                            // When disabling, reset to current time
                            val now = System.currentTimeMillis()
                            customDate = DateUtils.formatDateForDisplay(now)
                            customTime = DateUtils.formatTimeForDisplay(now)
                            onDefaultTimestampChange(now)
                        }
                    }
                )
                
                // Date and time fields when custom timestamp is enabled
                if (useCustomTimestamp) {
                    // Date field
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = "Date",
                            value = customDate,
                            onChange = { 
                                customDate = it
                                // Update callback when date changes
                                val newTimestamp = try {
                                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    val dateTimeString = "$customDate $customTime"
                                    dateFormat.parse(dateTimeString)?.time ?: defaultTimestamp
                                } catch (e: Exception) {
                                    defaultTimestamp
                                }
                                onDefaultTimestampChange(newTimestamp)
                            },
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
                            onChange = { 
                                customTime = it
                                // Update callback when time changes
                                val newTimestamp = try {
                                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    val dateTimeString = "$customDate $customTime"
                                    dateFormat.parse(dateTimeString)?.time ?: defaultTimestamp
                                } catch (e: Exception) {
                                    defaultTimestamp
                                }
                                onDefaultTimestampChange(newTimestamp)
                            },
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
                        onQuickSave = { name, properties ->
                            onQuickSave(name, properties)
                        },
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
                // Update callback when date changes
                val newTimestamp = try {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val dateTimeString = "$customDate $customTime"
                    dateFormat.parse(dateTimeString)?.time ?: defaultTimestamp
                } catch (e: Exception) {
                    defaultTimestamp
                }
                onDefaultTimestampChange(newTimestamp)
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
                // Update callback when time changes
                val newTimestamp = try {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val dateTimeString = "$customDate $customTime"
                    dateFormat.parse(dateTimeString)?.time ?: defaultTimestamp
                } catch (e: Exception) {
                    defaultTimestamp
                }
                onDefaultTimestampChange(newTimestamp)
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
    onQuickSave: (String, Map<String, Any>) -> Unit,
    customTimestamp: Long = System.currentTimeMillis()
) {
    val context = LocalContext.current
    val timerManager = remember { TimerManager.getInstance() }
    val timerState by timerManager.timerState
    
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
                                if (isActive) {
                                    // Arrêter le timer actuel
                                    timerManager.stopTimer { minutes, activityName ->
                                        if (minutes >= 1) {
                                            // Sauvegarder si >= 1 minute
                                            onQuickSave(activityName, mapOf(
                                                "activity" to activityName,
                                                "duration_minutes" to minutes
                                            ))
                                        } else {
                                            // Toast pour timer trop court
                                            android.widget.Toast.makeText(
                                                context,
                                                "Timer trop court, non enregistré",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } else {
                                    // Démarrer nouveau timer (auto-switch si besoin)
                                    timerManager.startTimer(item.name, toolInstanceId) { minutes, previousActivityName ->
                                        if (minutes >= 1) {
                                            // Sauvegarder le timer précédent si >= 1 minute
                                            onQuickSave(previousActivityName, mapOf(
                                                "activity" to previousActivityName,
                                                "duration_minutes" to minutes
                                            ))
                                        } else {
                                            // Toast pour timer trop court lors d'auto-switch
                                            android.widget.Toast.makeText(
                                                context,
                                                "Timer trop court, non enregistré",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
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