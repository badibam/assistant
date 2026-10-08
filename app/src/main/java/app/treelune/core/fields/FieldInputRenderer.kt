package app.treelune.core.fields

import app.treelune.core.utils.JsonUtils
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.treelune.core.strings.Strings
import app.treelune.core.ui.UI
import app.treelune.core.ui.TextType
import app.treelune.core.ui.FieldType as UIFieldType
import app.treelune.core.utils.DateUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.selection.TimePoint
import app.treelune.core.ui.components.InstantPicker

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
        app.treelune.core.fields.FieldType.TEXT -> {
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
                required = required
            )
        }

        app.treelune.core.fields.FieldType.NUMERIC -> {
            val config = fieldDef.config

            // Extract config
            val unit = config?.get("unit") as? String
            val decimals = (config?.get("decimals") as? Number)?.toInt() ?: 0

            fun parse(text: String): Number? = if (decimals == 0) text.toLongOrNull() else text.toDoubleOrNull()
            // A number reads as it is written: 16000 and 0.5, never 16000.0
            fun format(number: Number?): String =
                number?.let { java.math.BigDecimal(it.toString()).stripTrailingZeros().toPlainString() } ?: ""

            // The text typed is kept as typed ("0.", "") while it is being written: rewritten from
            // the number it parses to, it could not be emptied nor take a decimal point. It is
            // replaced only when the value changes from outside.
            var text by rememberSaveable { mutableStateOf(format(value as? Number)) }
            if (parse(text)?.toDouble() != (value as? Number)?.toDouble()) text = format(value as? Number)

            // For now use FormField with NUMERIC type
            // TODO: Replace with dedicated NumericInput component with +/- buttons
            UI.FormField(
                label = fieldDef.displayName + (unit?.let { " ($it)" } ?: ""),
                value = text,
                onChange = { newValue ->
                    text = newValue
                    onChange(parse(newValue))
                },
                fieldType = UIFieldType.NUMERIC,
                required = required
            )
        }

        app.treelune.core.fields.FieldType.SCALE -> {
            val config = fieldDef.config
            val min = (config?.get("min") as? Number)?.toDouble() ?: 0.0
            val max = (config?.get("max") as? Number)?.toDouble() ?: 10.0
            val step = (config?.get("step") as? Number)?.toDouble() ?: 1.0
            val minLabel = config?.get("min_label") as? String ?: ""
            val maxLabel = config?.get("max_label") as? String ?: ""
            // A scale of whole numbers stores whole numbers: 7, not 7.0
            val wholeNumbers = app.treelune.core.ui.SliderSteps.decimals(min, step) == 0

            UI.SliderField(
                label = fieldDef.displayName,
                value = (value as? Number)?.toDouble(),
                onValueChange = { newValue -> onChange(newValue?.let { if (wholeNumbers) it.toInt() else it }) },
                min = min,
                max = max,
                step = step,
                minLabel = minLabel,
                maxLabel = maxLabel,
                required = required
            )
        }

        app.treelune.core.fields.FieldType.CHOICE -> {
            ChoiceInput(fieldDef, value, onChange, context, required)
        }

        app.treelune.core.fields.FieldType.BOOLEAN -> {
            val config = fieldDef.config
            UI.BooleanField(
                label = fieldDef.displayName,
                value = value as? Boolean,
                onValueChange = onChange,
                required = required,
                trueLabel = config?.get("true_label") as? String,
                falseLabel = config?.get("false_label") as? String
            )
        }

        app.treelune.core.fields.FieldType.RANGE -> {
            val s = Strings.`for`(context = context)
            val rangeValue = value as? Map<*, *>
            val unit = fieldDef.config?.get("unit") as? String
            val decimals = (fieldDef.config?.get("decimals") as? Number)?.toInt() ?: 0
            // A whole number reads and writes as one, as a NUMERIC does: 7, not 7.0
            fun text(bound: Any?): String = (bound as? Number)?.let { if (decimals == 0) it.toLong().toString() else it.toDouble().toString() } ?: ""
            fun number(text: String): Number? = if (decimals == 0) text.toLongOrNull() else text.toDoubleOrNull()
            // What is typed in each box, kept as typed: the range has a value only once both
            // bounds read as numbers, and none while one is empty or unreadable
            var startText by rememberSaveable { mutableStateOf(text(rangeValue?.get("start"))) }
            var endText by rememberSaveable { mutableStateOf(text(rangeValue?.get("end"))) }
            fun emit() {
                val start = number(startText)
                val end = number(endText)
                onChange(if (start != null && end != null) mapOf("start" to start, "end" to end) else null)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.FieldLabel(fieldDef.displayName, required)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S)
                ) {
                    // The range is marked as a whole, above: its two bounds bear no mark
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = s.shared("label_start") + (unit?.let { " ($it)" } ?: ""),
                            value = startText,
                            onChange = { startText = it; emit() },
                            fieldType = UIFieldType.NUMERIC,
                            required = false
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormField(
                            label = s.shared("label_end") + (unit?.let { " ($it)" } ?: ""),
                            value = endText,
                            onChange = { endText = it; emit() },
                            fieldType = UIFieldType.NUMERIC,
                            required = false
                        )
                    }
                }
            }
        }

        // A day or an instant, through the app's one date input: without a reference, a relative
        // choice or now is stored as the date it gives
        app.treelune.core.fields.FieldType.DATE, app.treelune.core.fields.FieldType.DATETIME -> {
            InstantPicker(
                label = fieldDef.displayName,
                value = value?.let { TimePoint.Fixed(it) },
                onChange = { point -> onChange((point as? TimePoint.Fixed)?.value) },
                precision = fieldDef.type,
                hasReference = false,
                required = required
            )
        }

        app.treelune.core.fields.FieldType.TIME -> {
            var showPicker by rememberSaveable { mutableStateOf(false) }
            val timeStr = value as? String ?: ""

            // ISO 8601 time is already HH:MM format, same as display format
            val displayTime = timeStr.ifEmpty { "" }

            // Use FormField that opens TimePicker on click
            Clearable(showClear = !required && value != null, onClear = { onChange(null) }) {
                UI.FormField(
                    label = fieldDef.displayName,
                    value = displayTime,
                    onChange = {}, // Read-only, use picker
                    fieldType = UIFieldType.TEXT,
                    required = required,
                    readonly = true,
                    onClick = { showPicker = true }
                )
            }

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

        app.treelune.core.fields.FieldType.DURATION -> {
            DurationInput(fieldDef, value, onChange, context, required)
        }

        app.treelune.core.fields.FieldType.REFERENCE -> {
            ReferenceInput(fieldDef, value, onChange, context, required)
        }
    }
}

/**
 * [content], a field's input, with a button emptying the field beside it when [showClear]: an
 * optional value chosen through a picker can otherwise never be taken back.
 */
@Composable
private fun Clearable(showClear: Boolean, onClear: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) { content() }
        if (showClear) {
            UI.ActionButton(
                action = app.treelune.core.ui.ButtonAction.DELETE,
                display = app.treelune.core.ui.ButtonDisplay.ICON,
                size = app.treelune.core.ui.Size.S,
                onClick = onClear
            )
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
    context: Context,
    required: Boolean
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
        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
    ) {
        UI.FieldLabel(fieldDef.displayName, required)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S)
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
 * a FieldInput for each one, a line between two, under no title of its own: the form around it
 * says what it is. It manages the global state of all field values.
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
 * @param newEntry Whether the entry is being created: each field's default value is then filled
 *   in once, when the fields are known, where no value is set; a value emptied after that stays
 *   empty
 */
@Composable
fun CustomFieldsInput(
    toolInstanceId: String? = null,
    customFieldsMetadata: List<FieldDefinition>? = null,
    values: Map<String, Any?>,
    onValuesChange: (Map<String, Any?>) -> Unit,
    context: Context,
    newEntry: Boolean = false
) {
    // Resolve field definitions from metadata source
    val resolvedFields = resolveFieldDefinitions(
        toolInstanceId = toolInstanceId,
        customFieldsMetadata = customFieldsMetadata,
        context = context
    )

    // Once only, a rotation included: the defaults are a suggestion, not a value held
    var defaultsApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resolvedFields) {
        if (!newEntry || defaultsApplied || resolvedFields.isEmpty()) return@LaunchedEffect
        defaultsApplied = true
        val missing = resolvedFields.defaultValues().filterKeys { it !in values }
        if (missing.isNotEmpty()) onValuesChange(values + missing)
    }

    // Early return if no custom fields defined
    if (resolvedFields.isEmpty()) {
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.S)
    ) {
        // Each field with its name small above its input, as in every form, a line between two
        resolvedFields.forEachIndexed { index, field ->
            if (index > 0) UI.Divider()
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

/** How a screen lays out the user's fields, by the room it has. */
enum class FieldsLayout {
    /** Each field on its own, its name above its value, full width: an entry's own page. */
    EXPANDED,
    /** One field per line, its name as large as a title, its value on the rest of the line: a summary. */
    LINE,
    /**
     * Two fields per line, each "Mood: Calm" with a small name, a long value wrapping within its
     * half: a list item, a card.
     */
    COMPACT
}

/**
 * The user's fields of an entry, the one way every tool shows them: those holding a value, and
 * those set to show always ("no value" when empty); their names beside their values unless the
 * tool's config says not to (show_field_labels). Nothing when no field is to be shown.
 *
 * @param toolType The tool's type, for its config's defaults
 * @param config The tool's config: its fields and whether they show their names
 * @param values The entry's values of the user's fields
 * @param layout Chosen by the screen, by the room it has
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomFieldsDisplay(
    toolType: app.treelune.core.tools.ToolTypeContract,
    config: org.json.JSONObject,
    values: Map<String, Any?>,
    layout: FieldsLayout,
    context: Context
) {
    val shown = shownCustomFields(config, values)
    if (shown.isEmpty()) return
    val showLabels = app.treelune.core.tools.ToolConfigSettings.read(toolType, config, context)
        .boolean(app.treelune.core.tools.ToolConfigSettings.SHOW_FIELD_LABELS)
    val s = Strings.`for`(context = context)

    when (layout) {
        FieldsLayout.EXPANDED -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            shown.forEach { field ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                    if (showLabels) UI.Text(text = field.displayName, type = TextType.SUBTITLE, fillMaxWidth = true)
                    FieldValue(field, values[field.name], context)
                }
            }
        }

        FieldsLayout.LINE -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
        ) {
            shown.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (showLabels) UI.Text(s.shared("field_label_inline").format(field.displayName), TextType.SUBTITLE)
                    Box(modifier = Modifier.weight(1f)) { FieldValue(field, values[field.name], context) }
                }
            }
        }

        // Two fields per row, the last one alone keeping half the width
        FieldsLayout.COMPACT -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
        ) {
            shown.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    row.forEach { field ->
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(UI.Space.XS),
                            verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                        ) {
                            if (showLabels) UI.Text(s.shared("field_label_inline").format(field.displayName), TextType.LABEL)
                            FieldValue(field, values[field.name], context)
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The user's fields CustomFieldsDisplay shows for [values]: those holding a value and those set
 * to show always. For a screen that draws something around them only when there are some.
 */
fun shownCustomFields(config: org.json.JSONObject, values: Map<String, Any?>): List<FieldDefinition> =
    (config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList())
        .filter { values[it.name] != null || it.alwaysVisible }

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
            val coordinator = app.treelune.core.coordinator.Coordinator(context)
            val result = coordinator.processUserAction("tools.get", mapOf(
                "tool_instance_id" to toolInstanceId
            ))

            if (result.status == app.treelune.core.commands.CommandStatus.SUCCESS) {
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
        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
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
                UI.FieldLabel(fieldDef.displayName, required)

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
                UI.FieldLabel(fieldDef.displayName, required)

                // Nothing ranked yet shows the options as the config lists them; a ranking stored
                // before an option was added shows that option last. The first move records the
                // whole order.
                val ranking = selectedItems + settings.options.filter { it !in selectedItems }
                UI.ReorderableColumn(
                    items = ranking,
                    onMove = { from, to -> onChange(ranking.toMutableList().apply { add(to, removeAt(from)) }) },
                    spacing = UI.Space.XS
                ) { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            UI.Text(
                                text = s.shared("field_choice_rank").format((index + 1).toString(), settings.labelOf(option)),
                                type = TextType.BODY
                            )
                        }
                        DragHandle()
                    }
                }
            }
        }

        if (settings.open) {
            var typed by rememberSaveable { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
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
                    action = app.treelune.core.ui.ButtonAction.ADD,
                    display = app.treelune.core.ui.ButtonDisplay.ICON,
                    size = app.treelune.core.ui.Size.S,
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
