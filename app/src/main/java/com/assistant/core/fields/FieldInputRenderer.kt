package com.assistant.core.fields

import com.assistant.core.utils.JsonUtils
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.assistant.core.utils.LogManager

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
 * @param required Whether a value must be given: an optional single choice can be emptied
 */
@Composable
fun FieldInput(
    fieldDef: FieldDefinition,
    value: Any?,
    onChange: (Any?) -> Unit,
    context: Context,
    modifier: Modifier = Modifier,
    required: Boolean = false
) {
    when (fieldDef.type) {
        com.assistant.core.fields.FieldType.TEXT -> {
            // Determine UI field type based on config.length
            val lengthStr = fieldDef.config?.get("length") as? String
            val length = TextLength.fromString(lengthStr)

            val uiFieldType = when (length) {
                TextLength.SHORT -> UIFieldType.TEXT
                TextLength.MEDIUM -> UIFieldType.TEXT_MEDIUM
                TextLength.LONG -> UIFieldType.TEXT_LONG
                TextLength.UNLIMITED -> UIFieldType.TEXT_UNLIMITED
            }

            UI.FormField(
                label = fieldDef.displayName,
                value = value?.toString() ?: "",
                onChange = { newValue -> onChange(if (newValue.isEmpty()) null else newValue) },
                fieldType = uiFieldType,
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
                // A whole number reads and writes as one: 16000, not 16000.0
                value = when {
                    value == null -> ""
                    decimals == 0 -> numValue.toLong().toString()
                    else -> numValue.toString()
                },
                onChange = { newValue ->
                    onChange(if (decimals == 0) newValue.toLongOrNull() else newValue.toDoubleOrNull())
                },
                fieldType = UIFieldType.NUMERIC,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.SCALE -> {
            val config = fieldDef.config
            val min = (config?.get("min") as? Number)?.toDouble() ?: 0.0
            val max = (config?.get("max") as? Number)?.toDouble() ?: 10.0
            val step = (config?.get("step") as? Number)?.toDouble() ?: 1.0
            val minLabel = config?.get("min_label") as? String ?: ""
            val maxLabel = config?.get("max_label") as? String ?: ""
            // A scale of whole numbers stores whole numbers: 7, not 7.0
            val wholeNumbers = com.assistant.core.ui.SliderSteps.decimals(min, step) == 0

            UI.SliderField(
                label = fieldDef.displayName,
                value = (value as? Number)?.toDouble() ?: min,
                onValueChange = { newValue -> onChange(if (wholeNumbers) newValue.toInt() else newValue) },
                min = min,
                max = max,
                step = step,
                minLabel = minLabel,
                maxLabel = maxLabel,
                required = false
            )
        }

        com.assistant.core.fields.FieldType.CHOICE -> {
            ChoiceInput(fieldDef, value, onChange, context, required)
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
            var showPicker by rememberSaveable { mutableStateOf(false) }
            val dateStr = value as? String ?: ""

            // Convert ISO 8601 to display format dd/MM/yyyy
            val displayDate = if (dateStr.isNotEmpty()) {
                val timestamp = DateUtils.parseIso8601Date(dateStr)
                if (timestamp == null) dateStr else DateUtils.formatDateForDisplay(timestamp)
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
                        // The picker gives back dd/MM/yyyy. Anything else is a bug upstream,
                        // and the field keeps what it had rather than recording today.
                        DateUtils.parseDateForFilter(newDateDisplay)?.let { timestamp ->
                            onChange(DateUtils.timestampToIso8601Date(timestamp))
                        }
                        showPicker = false
                    },
                    onDismiss = { showPicker = false }
                )
            }
        }

        com.assistant.core.fields.FieldType.TIME -> {
            var showPicker by rememberSaveable { mutableStateOf(false) }
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
            var showDatePicker by rememberSaveable { mutableStateOf(false) }
            var showTimePicker by rememberSaveable { mutableStateOf(false) }
            // Milliseconds, as the field's schema says. Nothing to parse, so nothing that can
            // fail to parse.
            val timestamp = (value as? Number)?.toLong()

            val (displayDate, displayTime) = if (timestamp != null) {
                Pair(
                    DateUtils.formatDateForDisplay(timestamp),
                    DateUtils.formatTimeForDisplay(timestamp)
                )
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
                        // Combine new date with existing time; an unreadable pair leaves the
                        // field as it was rather than recording the present moment.
                        DateUtils.combineDateTime(newDateDisplay, displayTime.ifEmpty { "00:00" })?.let {
                            onChange(it)
                        }
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
            }

            if (showTimePicker) {
                UI.TimePicker(
                    selectedTime = displayTime.ifEmpty { DateUtils.getCurrentTimeFormatted() },
                    onTimeSelected = { newTimeDisplay ->
                        // Same here: nothing is recorded unless both halves read.
                        DateUtils.combineDateTime(displayDate.ifEmpty { DateUtils.getTodayFormatted() }, newTimeDisplay)?.let {
                            onChange(it)
                        }
                        showTimePicker = false
                    },
                    onDismiss = { showTimePicker = false }
                )
            }
        }

        com.assistant.core.fields.FieldType.DURATION -> {
            DurationInput(fieldDef, value, onChange, context)
        }
    }
}

/**
 * Input for a DURATION field: one whole-number box per unit, from the hour down to the
 * precision when composed, a single box in the precision otherwise.
 *
 * The value stays milliseconds throughout. Emptying every box clears the field; a value finer
 * than the precision (a stopwatch's exact time) is kept as long as no box is touched.
 */
@Composable
private fun DurationInput(
    fieldDef: FieldDefinition,
    value: Any?,
    onChange: (Any?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val precision = DurationUnit.fromConfig(fieldDef.config)
    val units = when (DurationForm.fromConfig(fieldDef.config)) {
        DurationForm.COMPOSED -> Durations.composedUnits(precision)
        DurationForm.SINGLE -> listOf(precision)
    }
    val millis = (value as? Number)?.toLong()
    val amounts = millis?.let { Durations.split(it, units).toMap() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
            units.forEach { unit ->
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = unit.symbol(s),
                        value = amounts?.get(unit)?.toString() ?: "",
                        onChange = { text ->
                            val typed = text.toLongOrNull()?.coerceAtLeast(0)
                            val others = units.filter { it != unit }.associateWith { amounts?.get(it) ?: 0L }
                            onChange(
                                if (typed == null && others.values.all { it == 0L }) null
                                else Durations.join(others + (unit to (typed ?: 0L)))
                            )
                        },
                        fieldType = UIFieldType.NUMERIC,
                        required = false
                    )
                }
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
 * Supports two modes (priority order):
 * 1. Explicit definitions passed in by the caller
 * 2. ConfigBased: Load field definitions from tool instance config
 *
 * @param toolInstanceId Tool instance ID to load config from (ConfigBased mode)
 * @param customFieldsMetadata Field definitions supplied directly by the caller
 * @param values Current values map (fieldName -> value)
 * @param onValuesChange Callback when any value changes (receives updated full map)
 * @param context Android context for strings and formatting
 */
@Composable
fun CustomFieldsInput(
    toolInstanceId: String? = null,
    customFieldsMetadata: List<FieldDefinition>? = null,
    values: Map<String, Any?>,
    onValuesChange: (Map<String, Any?>) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)

    // Resolve field definitions from metadata source
    val resolvedFields = resolveFieldDefinitions(
        toolInstanceId = toolInstanceId,
        customFieldsMetadata = customFieldsMetadata,
        context = context
    )

    // Early return if no custom fields defined
    if (resolvedFields.isEmpty()) {
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
        resolvedFields.forEach { field ->
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
 * Supports two modes (priority order):
 * 1. Explicit definitions passed in by the caller
 * 2. ConfigBased: Load field definitions from tool instance config
 *
 * @param toolInstanceId Tool instance ID to load config from (ConfigBased mode)
 * @param customFieldsMetadata Field definitions supplied directly by the caller
 * @param values Current values map (fieldName -> value)
 * @param context Android context for strings and formatting
 */
@Composable
fun CustomFieldsDisplay(
    toolInstanceId: String? = null,
    customFieldsMetadata: List<FieldDefinition>? = null,
    values: Map<String, Any?>,
    context: Context
) {
    val s = Strings.`for`(context = context)

    // Resolve field definitions from metadata source
    val resolvedFields = resolveFieldDefinitions(
        toolInstanceId = toolInstanceId,
        customFieldsMetadata = customFieldsMetadata,
        context = context
    )

    // Filter fields to display: either has value OR alwaysVisible is true
    val fieldsToDisplay = resolvedFields.filter { field ->
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

                // Field value, drawn by its type
                FieldValue(field, values[field.name], context)
            }
        }
    }
}

/**
 * Resolves field definitions from the appropriate metadata source.
 *
 * Logic:
 * - If customFieldsMetadata is provided: use it directly
 * - Else if toolInstanceId is provided: load from config (ConfigBased mode)
 * - Else: return empty list
 *
 * @param toolInstanceId Tool instance ID to load config from
 * @param customFieldsMetadata Archived field definitions
 * @param context Android context
 * @return List of resolved field definitions
 */
@Composable
private fun resolveFieldDefinitions(
    toolInstanceId: String?,
    customFieldsMetadata: List<FieldDefinition>?,
    context: Context
): List<FieldDefinition> {
    // Priority 1: Use the definitions the caller passed in
    if (customFieldsMetadata != null) {
        return customFieldsMetadata
    }

    // Priority 2: Load from tool instance config (ConfigBased mode)
    if (toolInstanceId != null) {
        return loadFieldDefinitionsFromConfig(toolInstanceId, context)
    }

    // No metadata source provided
    return emptyList()
}

/**
 * Loads field definitions from a tool instance configuration.
 *
 * @param toolInstanceId The ID of the tool instance
 * @param context Android context
 * @return List of field definitions from the config, or empty list if not found
 */
@Composable
private fun loadFieldDefinitionsFromConfig(
    toolInstanceId: String,
    context: Context
): List<FieldDefinition> {
    var fields by remember(toolInstanceId) { mutableStateOf<List<FieldDefinition>>(emptyList()) }

    LaunchedEffect(toolInstanceId) {
        try {
            val coordinator = com.assistant.core.coordinator.Coordinator(context)
            val result = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))

            if (result.status == com.assistant.core.commands.CommandStatus.SUCCESS) {
                val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
                @Suppress("UNCHECKED_CAST")
                val configMap = toolInstance?.get("config") as? Map<String, Any?>

                if (configMap != null) {
                    val config = JsonUtils.toJSONObject(configMap)
                    val customFieldsArray = config.optJSONArray("extra_fields")

                    // Convert JSONArray to List<FieldDefinition>
                    fields = if (customFieldsArray != null) {
                        customFieldsArray.toFieldDefinitions()
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.ui("Failed to load custom fields from config: ${e.message}", "ERROR", e)
            fields = emptyList()
        }
    }

    return fields
}

/**
 * Input for a CHOICE field, by shape: a selection for one option, checkboxes for several, the
 * options with up and down buttons for a ranking. An open choice adds a box to type a value the
 * options do not have yet; the write that stores it adds it to them.
 */
@Composable
private fun ChoiceInput(
    fieldDef: FieldDefinition,
    value: Any?,
    onChange: (Any?) -> Unit,
    context: Context,
    required: Boolean
) {
    val s = Strings.`for`(context = context)
    val settings = ChoiceSettings.fromConfig(fieldDef.config)
    val selectedItems = (value as? List<*>)?.map { it.toString() } ?: emptyList()
    // Values given before, not yet in the options, stay on screen until the write adds them
    val options = settings.options + (if (settings.shape.isList) selectedItems else listOfNotNull(value?.toString()))
        .filter { it !in settings.options }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (settings.shape) {
            ChoiceShape.SINGLE -> {
                // Shown by their labels; an optional choice offers "none" first, which empties it
                val none = s.shared("field_choice_none")
                val byLabel = options.associateBy { settings.labelOf(it) }
                UI.FormSelection(
                    label = fieldDef.displayName,
                    options = (if (required) emptyList() else listOf(none)) + byLabel.keys,
                    selected = value?.toString()?.let { settings.labelOf(it) } ?: (if (required) "" else none),
                    onSelect = { label -> onChange(byLabel[label]) },
                    required = required
                )
            }

            ChoiceShape.MULTIPLE -> {
                UI.Text(
                    text = fieldDef.displayName,
                    type = TextType.LABEL,
                    fillMaxWidth = true
                )

                options.forEach { option ->
                    UI.Checkbox(
                        checked = option in selectedItems,
                        onCheckedChange = { checked ->
                            val newList = if (checked) selectedItems + option else selectedItems - option
                            onChange(if (newList.isEmpty()) null else newList)
                        },
                        label = settings.labelOf(option)
                    )
                }
            }

            ChoiceShape.ORDERED -> {
                UI.Text(
                    text = fieldDef.displayName,
                    type = TextType.LABEL,
                    fillMaxWidth = true
                )

                // Nothing ranked yet shows the options as the config lists them; a ranking stored
                // before an option was added shows that option last. The first move records the
                // whole order.
                val ranking = selectedItems + settings.options.filter { it !in selectedItems }
                ranking.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            UI.Text(
                                text = s.shared("field_choice_rank").format((index + 1).toString(), settings.labelOf(option)),
                                type = TextType.BODY
                            )
                        }
                        UI.ActionButton(
                            action = com.assistant.core.ui.ButtonAction.UP,
                            display = com.assistant.core.ui.ButtonDisplay.ICON,
                            size = com.assistant.core.ui.Size.S,
                            enabled = index > 0,
                            onClick = {
                                onChange(ranking.toMutableList().apply { add(index - 1, removeAt(index)) })
                            }
                        )
                        UI.ActionButton(
                            action = com.assistant.core.ui.ButtonAction.DOWN,
                            display = com.assistant.core.ui.ButtonDisplay.ICON,
                            size = com.assistant.core.ui.Size.S,
                            enabled = index < ranking.size - 1,
                            onClick = {
                                onChange(ranking.toMutableList().apply { add(index + 1, removeAt(index)) })
                            }
                        )
                    }
                }
            }
        }

        if (settings.open) {
            var typed by rememberSaveable { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = s.shared("field_choice_new_value"),
                        value = typed,
                        onChange = { typed = it },
                        fieldType = UIFieldType.TEXT,
                        required = false
                    )
                }
                UI.ActionButton(
                    action = com.assistant.core.ui.ButtonAction.ADD,
                    display = com.assistant.core.ui.ButtonDisplay.ICON,
                    size = com.assistant.core.ui.Size.S,
                    enabled = typed.isNotBlank(),
                    onClick = {
                        val added = typed.trim()
                        onChange(if (settings.shape.isList) (selectedItems + added).distinct() else added)
                        typed = ""
                    }
                )
            }
        }
    }
}
