package com.assistant.core.fields

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.ui.UI
import com.assistant.core.ui.TextType
import com.assistant.core.ui.FieldType as UIFieldType
import com.assistant.core.utils.DateUtils

/**
 * Renders a single custom field input component.
 *
 * This is the base component for editing individual custom fields.
 * Handles type-specific rendering for all supported field types.
 *
 * @param fieldDef The field definition containing metadata (type, display name, config)
 * @param value The current value (can be null)
 * @param onChange Callback when the value changes
 * @param context Android context for strings and formatting
 * @param modifier Optional modifier for the composable
 */
@Composable
fun FieldInput(
    fieldDef: FieldDefinition,
    value: Any?,
    onChange: (Any?) -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    when (fieldDef.type) {
        com.assistant.core.fields.FieldType.TEXT_SHORT -> {
            UI.FormField(
                label = fieldDef.displayName,
                value = value?.toString() ?: "",
                onChange = { newValue -> onChange(if (newValue.isEmpty()) null else newValue) },
                fieldType = UIFieldType.TEXT,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.TEXT_LONG -> {
            UI.FormField(
                label = fieldDef.displayName,
                value = value?.toString() ?: "",
                onChange = { newValue -> onChange(if (newValue.isEmpty()) null else newValue) },
                fieldType = UIFieldType.TEXT_LONG,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.TEXT_UNLIMITED -> {
            UI.FormField(
                label = fieldDef.displayName,
                value = value?.toString() ?: "",
                onChange = { newValue -> onChange(if (newValue.isEmpty()) null else newValue) },
                fieldType = UIFieldType.TEXT_UNLIMITED,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.NUMERIC -> {
            val numValue = (value as? Number)?.toDouble() ?: 0.0
            val config = fieldDef.config

            // Extract config
            val unit = config?.get("unit") as? String
            val min = (config?.get("min") as? Number)?.toDouble()
            val max = (config?.get("max") as? Number)?.toDouble()
            val decimals = (config?.get("decimals") as? Number)?.toInt() ?: 0
            val step = (config?.get("step") as? Number)?.toDouble()

            // For now use FormField with NUMERIC type
            // TODO: Replace with dedicated NumericInput component with +/- buttons
            UI.FormField(
                label = fieldDef.displayName + (unit?.let { " ($it)" } ?: ""),
                value = if (value != null) numValue.toString() else "",
                onChange = { newValue ->
                    onChange(newValue.toDoubleOrNull())
                },
                fieldType = UIFieldType.NUMERIC,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.SCALE -> {
            val numValue = (value as? Number)?.toInt() ?: run {
                val config = fieldDef.config
                ((config?.get("min") as? Number)?.toInt() ?: 0)
            }

            val config = fieldDef.config
            val min = (config?.get("min") as? Number)?.toInt() ?: 0
            val max = (config?.get("max") as? Number)?.toInt() ?: 10
            val minLabel = config?.get("min_label") as? String ?: ""
            val maxLabel = config?.get("max_label") as? String ?: ""

            // Use SliderField (will need step support added)
            UI.SliderField(
                label = fieldDef.displayName,
                value = numValue,
                onValueChange = { newValue -> onChange(newValue) },
                range = min..max,
                minLabel = minLabel,
                maxLabel = maxLabel,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.CHOICE -> {
            val config = fieldDef.config
            val options = (config?.get("options") as? List<*>)?.map { it.toString() } ?: emptyList()
            val multiple = (config?.get("multiple") as? Boolean) ?: false

            if (multiple) {
                // Multiple choice - checkboxes list
                // TODO: Replace with dedicated MultiSelect component
                val selectedItems = (value as? List<*>)?.map { it.toString() } ?: emptyList()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UI.Text(
                        text = fieldDef.displayName,
                        type = TextType.LABEL,
                        fillMaxWidth = true
                    )

                    options.forEach { option ->
                        val isSelected = selectedItems.contains(option)
                        UI.Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                val newList = if (checked) {
                                    selectedItems + option
                                } else {
                                    selectedItems - option
                                }
                                onChange(if (newList.isEmpty()) null else newList)
                            },
                            label = option
                        )
                    }
                }
            } else {
                // Single choice - dropdown
                val selectedValue = value?.toString() ?: ""
                UI.FormSelection(
                    label = fieldDef.displayName,
                    options = options,
                    selected = selectedValue,
                    onSelect = { newValue -> onChange(if (newValue.isEmpty()) null else newValue) },
                    required = false
                )
            }
        }

        com.assistant.core.fields.FieldType.BOOLEAN -> {
            val boolValue = (value as? Boolean) ?: false
            val config = fieldDef.config

            val s = Strings.`for`(context = context)
            // Use user-provided labels if exist, otherwise use default translated labels
            val trueLabel = config?.get("true_label") as? String ?: s.shared("label_yes")
            val falseLabel = config?.get("false_label") as? String ?: s.shared("label_no")

            UI.ToggleField(
                label = fieldDef.displayName,
                checked = boolValue,
                onCheckedChange = { newValue -> onChange(newValue) },
                trueLabel = trueLabel,
                falseLabel = falseLabel,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.RANGE -> {
            val rangeValue = value as? Map<*, *>
            val startValue = (rangeValue?.get("start") as? Number)?.toDouble() ?: 0.0
            val endValue = (rangeValue?.get("end") as? Number)?.toDouble() ?: 0.0

            val config = fieldDef.config
            val unit = config?.get("unit") as? String

            // Two numeric inputs (start and end)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = fieldDef.displayName,
                    type = TextType.LABEL,
                    fillMaxWidth = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start field
                    Box(modifier = Modifier.weight(1f)) {
                        val s = Strings.`for`(context = context)
                        UI.FormField(
                            label = s.shared("label_start") + (unit?.let { " ($it)" } ?: ""),
                            value = startValue.toString(),
                            onChange = { newValue ->
                                val newStart = newValue.toDoubleOrNull() ?: 0.0
                                onChange(mapOf("start" to newStart, "end" to endValue))
                            },
                            fieldType = UIFieldType.NUMERIC,
                            required = false
                        )
                    }

                    // End field
                    Box(modifier = Modifier.weight(1f)) {
                        val s = Strings.`for`(context = context)
                        UI.FormField(
                            label = s.shared("label_end") + (unit?.let { " ($it)" } ?: ""),
                            value = endValue.toString(),
                            onChange = { newValue ->
                                val newEnd = newValue.toDoubleOrNull() ?: 0.0
                                onChange(mapOf("start" to startValue, "end" to newEnd))
                            },
                            fieldType = UIFieldType.NUMERIC,
                            required = false
                        )
                    }
                }
            }
        }

        com.assistant.core.fields.FieldType.DATE -> {
            var showPicker by remember { mutableStateOf(false) }
            val dateStr = value as? String ?: ""

            // Convert ISO 8601 to display format dd/MM/yyyy
            val displayDate = if (dateStr.isNotEmpty()) {
                val timestamp = DateUtils.parseIso8601Date(dateStr)
                DateUtils.formatDateForDisplay(timestamp)
            } else {
                ""
            }

            // Use FormField that opens DatePicker on click
            UI.FormField(
                label = fieldDef.displayName,
                value = displayDate,
                onChange = {}, // Read-only, use picker
                fieldType = UIFieldType.TEXT,
                required = false,
                readonly = true,
                onClick = { showPicker = true }
            )

            if (showPicker) {
                UI.DatePicker(
                    selectedDate = displayDate.ifEmpty { DateUtils.getTodayFormatted() },
                    onDateSelected = { newDateDisplay ->
                        // Convert display format to ISO 8601
                        val timestamp = DateUtils.parseDateForFilter(newDateDisplay)
                        val isoDate = DateUtils.timestampToIso8601Date(timestamp)
                        onChange(isoDate)
                        showPicker = false
                    },
                    onDismiss = { showPicker = false }
                )
            }
        }

        com.assistant.core.fields.FieldType.TIME -> {
            var showPicker by remember { mutableStateOf(false) }
            val timeStr = value as? String ?: ""

            // ISO 8601 time is already HH:MM format, same as display format
            val displayTime = timeStr.ifEmpty { "" }

            // Use FormField that opens TimePicker on click
            UI.FormField(
                label = fieldDef.displayName,
                value = displayTime,
                onChange = {}, // Read-only, use picker
                fieldType = UIFieldType.TEXT,
                required = false,
                readonly = true,
                onClick = { showPicker = true }
            )

            if (showPicker) {
                UI.TimePicker(
                    selectedTime = displayTime.ifEmpty { DateUtils.getCurrentTimeFormatted() },
                    onTimeSelected = { newTimeDisplay ->
                        // Display format is already HH:MM, same as ISO 8601
                        onChange(newTimeDisplay)
                        showPicker = false
                    },
                    onDismiss = { showPicker = false }
                )
            }
        }

        com.assistant.core.fields.FieldType.DATETIME -> {
            var showDatePicker by remember { mutableStateOf(false) }
            var showTimePicker by remember { mutableStateOf(false) }
            val dateTimeStr = value as? String ?: ""

            // Parse ISO 8601 datetime to date and time parts
            val (displayDate, displayTime) = if (dateTimeStr.isNotEmpty()) {
                val timestamp = DateUtils.parseIso8601DateTime(dateTimeStr)
                val date = DateUtils.formatDateForDisplay(timestamp)
                val time = DateUtils.formatTimeForDisplay(timestamp)
                Pair(date, time)
            } else {
                Pair("", "")
            }

            // Combined DatePicker and TimePicker
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UI.Text(
                    text = fieldDef.displayName,
                    type = TextType.LABEL,
                    fillMaxWidth = true
                )

                // Date field
                UI.FormField(
                    label = "",
                    value = displayDate,
                    onChange = {},
                    fieldType = UIFieldType.TEXT,
                    required = false,
                    readonly = true,
                    onClick = { showDatePicker = true }
                )

                // Time field
                UI.FormField(
                    label = "",
                    value = displayTime,
                    onChange = {},
                    fieldType = UIFieldType.TEXT,
                    required = false,
                    readonly = true,
                    onClick = { showTimePicker = true }
                )
            }

            if (showDatePicker) {
                UI.DatePicker(
                    selectedDate = displayDate.ifEmpty { DateUtils.getTodayFormatted() },
                    onDateSelected = { newDateDisplay ->
                        // Combine new date with existing time
                        val combinedTimestamp = DateUtils.combineDateTime(newDateDisplay, displayTime.ifEmpty { "00:00" })
                        val isoDateTime = DateUtils.timestampToIso8601DateTime(combinedTimestamp)
                        onChange(isoDateTime)
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
            }

            if (showTimePicker) {
                UI.TimePicker(
                    selectedTime = displayTime.ifEmpty { DateUtils.getCurrentTimeFormatted() },
                    onTimeSelected = { newTimeDisplay ->
                        // Combine existing date with new time
                        val combinedTimestamp = DateUtils.combineDateTime(displayDate.ifEmpty { DateUtils.getTodayFormatted() }, newTimeDisplay)
                        val isoDateTime = DateUtils.timestampToIso8601DateTime(combinedTimestamp)
                        onChange(isoDateTime)
                        showTimePicker = false
                    },
                    onDismiss = { showTimePicker = false }
                )
            }
        }
    }
}

/**
 * Renders all custom fields in edit mode.
 *
 * This high-level component iterates over all field definitions and renders
 * a FieldInput for each one. It manages the global state of all field values.
 *
 * @param fields List of field definitions to render
 * @param values Current values map (fieldName -> value)
 * @param onValuesChange Callback when any value changes (receives updated full map)
 * @param context Android context for strings and formatting
 */
@Composable
fun CustomFieldsInput(
    fields: List<FieldDefinition>,
    values: Map<String, Any?>,
    onValuesChange: (Map<String, Any?>) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)

    // Early return if no custom fields defined
    if (fields.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section title
        UI.Text(
            text = s.shared("custom_fields_section_title"),
            type = TextType.SUBTITLE,
            fillMaxWidth = true
        )

        // Render each field
        fields.forEach { field ->
            FieldInput(
                fieldDef = field,
                value = values[field.name],
                onChange = { newValue ->
                    // Update the values map with the new value
                    val updatedValues = values.toMutableMap()
                    if (newValue == null) {
                        updatedValues.remove(field.name)
                    } else {
                        updatedValues[field.name] = newValue
                    }
                    onValuesChange(updatedValues)
                },
                context = context
            )
        }
    }
}

/**
 * Renders all custom fields in read-only display mode.
 *
 * This component displays the formatted values of custom fields. Fields with no value
 * are either hidden or shown with "No value" text depending on the alwaysVisible flag.
 *
 * @param fields List of field definitions to display
 * @param values Current values map (fieldName -> value)
 * @param context Android context for strings and formatting
 */
@Composable
fun CustomFieldsDisplay(
    fields: List<FieldDefinition>,
    values: Map<String, Any?>,
    context: Context
) {
    val s = Strings.`for`(context = context)

    // Filter fields to display: either has value OR alwaysVisible is true
    val fieldsToDisplay = fields.filter { field ->
        values[field.name] != null || field.alwaysVisible
    }

    // Early return if no fields to display
    if (fieldsToDisplay.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Display each field with its own title
        fieldsToDisplay.forEach { field ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Field title (display name as subtitle)
                UI.Text(
                    text = field.displayName,
                    type = TextType.SUBTITLE,
                    fillMaxWidth = true
                )

                // Field value (formatted according to type)
                val formattedValue = field.formatValue(values[field.name], context)
                UI.Text(
                    text = formattedValue,
                    type = TextType.BODY,
                    fillMaxWidth = true
                )
            }
        }
    }
}
