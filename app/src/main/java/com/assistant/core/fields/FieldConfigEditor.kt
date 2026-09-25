package com.assistant.core.fields

import com.assistant.core.ui.MutableStringListSaver
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import com.assistant.core.ui.FieldType as UIFieldType

/**
 * Dynamic configuration editor for custom field types.
 *
 * This composable renders the appropriate configuration UI based on the field type.
 * Each field type has different configuration requirements:
 *
 * - TEXT: length?
 * - NUMERIC: unit?, min?, max?, decimals?, step?
 * - SCALE: min (required), max (required), min_label?, max_label?, step?
 * - CHOICE: options (required, min 2, each {value, color?}), multiple?, ordered?, open?
 * - BOOLEAN: true_label?, false_label?
 * - RANGE: min?, max?, unit?, decimals?
 * - DATE: nothing
 * - TIME: format?
 * - DATETIME: time_format?
 * - DURATION: precision?, form?
 *
 * Note: default_value is supported at the root level in schemas but not exposed in UI for now.
 * The AI can set it directly if needed.
 *
 * @param fieldType The type of field being configured
 * @param config Current configuration map (can be null/empty)
 * @param onConfigChange Callback when configuration changes
 * @param context Android context for strings
 */
@Composable
fun FieldConfigEditor(
    fieldType: FieldType,
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section title
        UI.Text(
            text = s.shared("field_config_section_title"),
            type = TextType.SUBTITLE,
            fillMaxWidth = true
        )

        when (fieldType) {
            FieldType.TEXT -> {
                TextConfigEditor(config, onConfigChange, context)
            }

            FieldType.NUMERIC -> {
                NumericConfigEditor(config, onConfigChange, context)
            }

            FieldType.SCALE -> {
                ScaleConfigEditor(config, onConfigChange, context)
            }

            FieldType.CHOICE -> {
                ChoiceConfigEditor(config, onConfigChange, context)
            }

            FieldType.BOOLEAN -> {
                BooleanConfigEditor(config, onConfigChange, context)
            }

            FieldType.RANGE -> {
                RangeConfigEditor(config, onConfigChange, context)
            }

            // DATE has nothing to configure since its bounds went.
            FieldType.DATE -> {}

            FieldType.TIME -> {
                TimeConfigEditor(config, onConfigChange, context)
            }

            FieldType.DATETIME -> {
                DateTimeConfigEditor(config, onConfigChange, context)
            }

            FieldType.DURATION -> {
                DurationConfigEditor(config, onConfigChange, context)
            }
        }
    }
}

/**
 * Configuration editor for TEXT type.
 * Config: {length: "SHORT" | "MEDIUM" | "LONG" | "UNLIMITED"}
 */
@Composable
private fun TextConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    // Current length selection
    val currentLengthStr = config?.get("length") as? String
    val currentLength = TextLength.fromString(currentLengthStr)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormSelection(
            label = s.shared("field_config_text_length"),
            options = TextLength.getAllLengths().map { it.getDisplayName(context) },
            selected = currentLength.getDisplayName(context),
            onSelect = { selectedDisplayName ->
                // Find the TextLength that matches the selected display name
                val selectedLength = TextLength.getAllLengths()
                    .find { it.getDisplayName(context) == selectedDisplayName }
                    ?: TextLength.DEFAULT

                // Update config
                mutableConfig["length"] = selectedLength.name
                onConfigChange(mutableConfig)
            },
            required = false
        )

        // Show description of selected length
        UI.Text(
            text = currentLength.getDescription(context),
            type = TextType.CAPTION,
            fillMaxWidth = true
        )
    }
}

/**
 * Configuration editor for NUMERIC type.
 * Config: {unit?, min?, max?, decimals?, step?}
 */
@Composable
private fun NumericConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var unit by rememberSaveable { mutableStateOf(config?.get("unit")?.toString() ?: "") }
    var min by rememberSaveable { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by rememberSaveable { mutableStateOf(config?.get("max")?.toString() ?: "") }
    var decimals by rememberSaveable { mutableStateOf(config?.get("decimals")?.toString() ?: "0") }
    var step by rememberSaveable { mutableStateOf(config?.get("step")?.toString() ?: "") }

    // A number always says its decimals: a new one starts at 0, a whole number
    LaunchedEffect(Unit) {
        if (config?.get("decimals") == null) {
            mutableConfig["decimals"] = 0
            onConfigChange(mutableConfig)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormField(
            label = s.shared("field_config_unit"),
            value = unit,
            onChange = {
                unit = it
                if (it.isNotEmpty()) mutableConfig["unit"] = it else mutableConfig.remove("unit")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_min"),
            value = min,
            onChange = {
                min = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["min"] = v } ?: mutableConfig.remove("min")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_max"),
            value = max,
            onChange = {
                max = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["max"] = v } ?: mutableConfig.remove("max")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_decimals"),
            value = decimals,
            onChange = {
                decimals = it
                it.toIntOrNull()?.let { v -> mutableConfig["decimals"] = v } ?: mutableConfig.remove("decimals")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_step"),
            value = step,
            onChange = {
                step = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["step"] = v } ?: mutableConfig.remove("step")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )
    }
}

/**
 * Configuration editor for SCALE type.
 * Config: {min (required), max (required), min_label?, max_label?, step?}
 */
@Composable
private fun ScaleConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var min by rememberSaveable { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by rememberSaveable { mutableStateOf(config?.get("max")?.toString() ?: "") }
    var minLabel by rememberSaveable { mutableStateOf(config?.get("min_label")?.toString() ?: "") }
    var maxLabel by rememberSaveable { mutableStateOf(config?.get("max_label")?.toString() ?: "") }
    var step by rememberSaveable { mutableStateOf(config?.get("step")?.toString() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormField(
            label = s.shared("field_config_min"),
            value = min,
            onChange = {
                min = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["min"] = v } ?: mutableConfig.remove("min")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = true
        )

        UI.FormField(
            label = s.shared("field_config_max"),
            value = max,
            onChange = {
                max = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["max"] = v } ?: mutableConfig.remove("max")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = true
        )

        UI.FormField(
            label = s.shared("field_config_min_label"),
            value = minLabel,
            onChange = {
                minLabel = it
                if (it.isNotEmpty()) mutableConfig["min_label"] = it else mutableConfig.remove("min_label")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_max_label"),
            value = maxLabel,
            onChange = {
                maxLabel = it
                if (it.isNotEmpty()) mutableConfig["max_label"] = it else mutableConfig.remove("max_label")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_step"),
            value = step,
            onChange = {
                step = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["step"] = v } ?: mutableConfig.remove("step")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )
    }
}

/**
 * Configuration editor for CHOICE type.
 * Config: {options (required, min 2, each {value, color?}), multiple?, ordered?, open?}
 */
@Composable
private fun ChoiceConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }
    val settings = ChoiceSettings.fromConfig(config)

    var options by rememberSaveable(stateSaver = MutableStringListSaver) {
        mutableStateOf(settings.options.toMutableList().ifEmpty { mutableListOf("", "") })
    }
    // The option whose colors are laid out to choose from, if any
    var coloring by rememberSaveable { mutableStateOf<Int?>(null) }

    /** Writes the options and their colors back, dropping the colors of options gone or emptied. */
    fun publish(colors: Map<String, com.assistant.core.themes.TagColor> = ChoiceSettings.fromConfig(mutableConfig).colors) {
        val kept = options.filter { it.isNotEmpty() }
        mutableConfig["options"] = ChoiceSettings.storedOptions(kept, colors = colors.filterKeys { it in kept })
        onConfigChange(mutableConfig)
    }

    /** Sets one flag, clearing the ones a ranking excludes so the config stays valid. */
    fun setFlag(flag: String, on: Boolean) {
        if (on) mutableConfig[flag] = true else mutableConfig.remove(flag)
        if (on && flag == "ordered") {
            mutableConfig.remove("multiple")
            mutableConfig.remove("open")
        }
        if (on && flag != "ordered") mutableConfig.remove("ordered")
        onConfigChange(mutableConfig)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.ToggleField(
            label = s.shared("field_config_multiple"),
            checked = settings.shape == ChoiceShape.MULTIPLE,
            onCheckedChange = { setFlag("multiple", it) },
            required = false
        )

        UI.ToggleField(
            label = s.shared("field_config_ordered"),
            checked = settings.shape == ChoiceShape.ORDERED,
            onCheckedChange = { setFlag("ordered", it) },
            required = false
        )

        UI.ToggleField(
            label = s.shared("field_config_open"),
            checked = settings.open,
            onCheckedChange = { setFlag("open", it) },
            required = false
        )

        // Options list
        UI.Text(
            text = s.shared("field_config_options"),
            type = TextType.LABEL,
            fillMaxWidth = true
        )

        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = "",
                        value = option,
                        onChange = { newValue ->
                            // The color follows the option it belongs to through the edit
                            val colors = ChoiceSettings.fromConfig(mutableConfig).colors.toMutableMap()
                            colors.remove(option)?.let { if (newValue.isNotEmpty()) colors[newValue] = it }
                            options[index] = newValue
                            publish(colors)
                        },
                        fieldType = UIFieldType.TEXT,
                        required = true
                    )
                }

                // The option's color, or the button to give it one
                val color = settings.colors[option]
                if (color != null) {
                    Box(modifier = Modifier.clickable { coloring = if (coloring == index) null else index }) {
                        UI.TagSwatch(color)
                    }
                } else {
                    UI.ActionButton(
                        action = ButtonAction.SELECT,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        enabled = option.isNotEmpty(),
                        onClick = { coloring = if (coloring == index) null else index }
                    )
                }

                if (options.size > 2) {
                    UI.ActionButton(
                        action = ButtonAction.DELETE,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = {
                            // Create new list to trigger recomposition
                            options = options.toMutableList().apply { removeAt(index) }
                            coloring = null
                            publish()
                        }
                    )
                }
            }

            // The colors to choose from, and the way back to none
            if (coloring == index && option.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    com.assistant.core.themes.TagColor.entries.forEach { choice ->
                        Box(modifier = Modifier.clickable {
                            publish(ChoiceSettings.fromConfig(mutableConfig).colors + (option to choice))
                            coloring = null
                        }) {
                            UI.TagSwatch(choice)
                        }
                    }
                    UI.ActionButton(
                        action = ButtonAction.RESET,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = {
                            publish(ChoiceSettings.fromConfig(mutableConfig).colors - option)
                            coloring = null
                        }
                    )
                }
            }
        }

        // Add option button
        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.ICON,
            size = Size.S,
            onClick = {
                // Create new list to trigger recomposition
                options = (options + "").toMutableList()
                publish()
            }
        )
    }
}

/**
 * Configuration editor for BOOLEAN type.
 * Config: {true_label?, false_label?}
 */
@Composable
private fun BooleanConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var trueLabel by rememberSaveable { mutableStateOf(config?.get("true_label")?.toString() ?: "") }
    var falseLabel by rememberSaveable { mutableStateOf(config?.get("false_label")?.toString() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormField(
            label = s.shared("field_config_true_label"),
            value = trueLabel,
            onChange = {
                trueLabel = it
                if (it.isNotEmpty()) mutableConfig["true_label"] = it else mutableConfig.remove("true_label")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_false_label"),
            value = falseLabel,
            onChange = {
                falseLabel = it
                if (it.isNotEmpty()) mutableConfig["false_label"] = it else mutableConfig.remove("false_label")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )
    }
}

/**
 * Configuration editor for RANGE type.
 * Config: {min?, max?, unit?, decimals?}
 */
@Composable
private fun RangeConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var unit by rememberSaveable { mutableStateOf(config?.get("unit")?.toString() ?: "") }
    var min by rememberSaveable { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by rememberSaveable { mutableStateOf(config?.get("max")?.toString() ?: "") }
    var decimals by rememberSaveable { mutableStateOf(config?.get("decimals")?.toString() ?: "0") }

    // A range always says its decimals: a new one starts at 0, whole numbers
    LaunchedEffect(Unit) {
        if (config?.get("decimals") == null) {
            mutableConfig["decimals"] = 0
            onConfigChange(mutableConfig)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormField(
            label = s.shared("field_config_unit"),
            value = unit,
            onChange = {
                unit = it
                if (it.isNotEmpty()) mutableConfig["unit"] = it else mutableConfig.remove("unit")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_min"),
            value = min,
            onChange = {
                min = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["min"] = v } ?: mutableConfig.remove("min")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_max"),
            value = max,
            onChange = {
                max = it
                it.toDoubleOrNull()?.let { v -> mutableConfig["max"] = v } ?: mutableConfig.remove("max")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_decimals"),
            value = decimals,
            onChange = {
                decimals = it
                it.toIntOrNull()?.let { v -> mutableConfig["decimals"] = v } ?: mutableConfig.remove("decimals")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.NUMERIC,
            required = false
        )
    }
}

/**
 * Configuration editor for TIME type.
 * Config: {format?}
 * Format is always 24h (HH:MM) in storage
 */
@Composable
private fun TimeConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var format by rememberSaveable { mutableStateOf(config?.get("format")?.toString() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormField(
            label = s.shared("field_config_time_format"),
            value = format,
            onChange = {
                format = it
                if (it.isNotEmpty()) mutableConfig["format"] = it else mutableConfig.remove("format")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )
    }
}

/**
 * Configuration editor for DATETIME type.
 * Config: {time_format?}
 * Values are timestamps in milliseconds
 */
@Composable
private fun DateTimeConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var timeFormat by rememberSaveable { mutableStateOf(config?.get("time_format")?.toString() ?: "") }

    UI.FormField(
        label = s.shared("field_config_time_format"),
        value = timeFormat,
        onChange = {
            timeFormat = it
            if (it.isNotEmpty()) mutableConfig["time_format"] = it else mutableConfig.remove("time_format")
            onConfigChange(mutableConfig.ifEmpty { null })
        },
        fieldType = UIFieldType.TEXT,
        required = false
    )
}

/**
 * Configuration editor for DURATION type.
 * Config: {precision?, form?}
 * Values are milliseconds whatever the choice: both only change what is entered and shown.
 */
@Composable
private fun DurationConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    val precision = DurationUnit.fromConfig(config)
    val form = DurationForm.fromConfig(config)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormSelection(
            label = s.shared("field_config_duration_precision"),
            options = DurationUnit.entries.map { it.displayName(s) },
            selected = precision.displayName(s),
            onSelect = { selected ->
                DurationUnit.entries.find { it.displayName(s) == selected }?.let {
                    mutableConfig["precision"] = it.name
                    onConfigChange(mutableConfig)
                }
            },
            required = false
        )

        UI.FormSelection(
            label = s.shared("field_config_duration_form"),
            options = DurationForm.entries.map { it.displayName(s) },
            selected = form.displayName(s),
            onSelect = { selected ->
                DurationForm.entries.find { it.displayName(s) == selected }?.let {
                    mutableConfig["form"] = it.name
                    onConfigChange(mutableConfig)
                }
            },
            required = false
        )
    }
}
