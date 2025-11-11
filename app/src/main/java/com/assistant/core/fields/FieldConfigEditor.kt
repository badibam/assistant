package com.assistant.core.fields

import android.content.Context
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
 * - TEXT_SHORT/TEXT_LONG/TEXT_UNLIMITED: No config required
 * - NUMERIC: unit?, min?, max?, decimals?, step?
 * - SCALE: min (required), max (required), min_label?, max_label?, step?
 * - CHOICE: options (required, min 2), multiple?
 * - BOOLEAN: true_label?, false_label?
 * - RANGE: min?, max?, unit?, decimals?
 * - DATE: min?, max?
 * - TIME: format?
 * - DATETIME: min?, max?, time_format?
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
            FieldType.TEXT_SHORT,
            FieldType.TEXT_LONG,
            FieldType.TEXT_UNLIMITED -> {
                // No configuration required for text types
                UI.Text(
                    text = s.shared("field_config_no_config_required"),
                    type = TextType.CAPTION,
                    fillMaxWidth = true
                )
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

            FieldType.DATE -> {
                DateConfigEditor(config, onConfigChange, context)
            }

            FieldType.TIME -> {
                TimeConfigEditor(config, onConfigChange, context)
            }

            FieldType.DATETIME -> {
                DateTimeConfigEditor(config, onConfigChange, context)
            }
        }
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

    var unit by remember { mutableStateOf(config?.get("unit")?.toString() ?: "") }
    var min by remember { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by remember { mutableStateOf(config?.get("max")?.toString() ?: "") }
    var decimals by remember { mutableStateOf(config?.get("decimals")?.toString() ?: "") }
    var step by remember { mutableStateOf(config?.get("step")?.toString() ?: "") }

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

    var min by remember { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by remember { mutableStateOf(config?.get("max")?.toString() ?: "") }
    var minLabel by remember { mutableStateOf(config?.get("min_label")?.toString() ?: "") }
    var maxLabel by remember { mutableStateOf(config?.get("max_label")?.toString() ?: "") }
    var step by remember { mutableStateOf(config?.get("step")?.toString() ?: "") }

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
 * Config: {options (required, min 2), multiple?}
 */
@Composable
private fun ChoiceConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var options by remember {
        mutableStateOf(
            (config?.get("options") as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList()
                ?: mutableListOf("", "")
        )
    }
    var multiple by remember { mutableStateOf(config?.get("multiple") as? Boolean ?: false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Multiple selection toggle
        UI.ToggleField(
            label = s.shared("field_config_multiple"),
            checked = multiple,
            onCheckedChange = {
                multiple = it
                mutableConfig["multiple"] = it
                onConfigChange(mutableConfig)
            },
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        label = "",
                        value = option,
                        onChange = { newValue ->
                            options[index] = newValue
                            mutableConfig["options"] = options.filter { it.isNotEmpty() }
                            onConfigChange(mutableConfig)
                        },
                        fieldType = UIFieldType.TEXT,
                        required = true
                    )
                }

                if (options.size > 2) {
                    UI.ActionButton(
                        action = ButtonAction.DELETE,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = {
                            options.removeAt(index)
                            mutableConfig["options"] = options.filter { it.isNotEmpty() }
                            onConfigChange(mutableConfig)
                        }
                    )
                }
            }
        }

        // Add option button
        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.S,
            onClick = {
                options.add("")
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

    var trueLabel by remember { mutableStateOf(config?.get("true_label")?.toString() ?: "") }
    var falseLabel by remember { mutableStateOf(config?.get("false_label")?.toString() ?: "") }

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

    var unit by remember { mutableStateOf(config?.get("unit")?.toString() ?: "") }
    var min by remember { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by remember { mutableStateOf(config?.get("max")?.toString() ?: "") }
    var decimals by remember { mutableStateOf(config?.get("decimals")?.toString() ?: "") }

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
 * Configuration editor for DATE type.
 * Config: {min?, max?}
 * Values are ISO 8601 date strings (YYYY-MM-DD)
 */
@Composable
private fun DateConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var min by remember { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by remember { mutableStateOf(config?.get("max")?.toString() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormField(
            label = s.shared("field_config_min"),
            value = min,
            onChange = {
                min = it
                if (it.isNotEmpty()) mutableConfig["min"] = it else mutableConfig.remove("min")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_max"),
            value = max,
            onChange = {
                max = it
                if (it.isNotEmpty()) mutableConfig["max"] = it else mutableConfig.remove("max")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
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

    var format by remember { mutableStateOf(config?.get("format")?.toString() ?: "") }

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
 * Config: {min?, max?, time_format?}
 * Values are ISO 8601 datetime strings (YYYY-MM-DDTHH:MM:SS)
 */
@Composable
private fun DateTimeConfigEditor(
    config: Map<String, Any>?,
    onConfigChange: (Map<String, Any>?) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val mutableConfig = remember(config) { config?.toMutableMap() ?: mutableMapOf() }

    var min by remember { mutableStateOf(config?.get("min")?.toString() ?: "") }
    var max by remember { mutableStateOf(config?.get("max")?.toString() ?: "") }
    var timeFormat by remember { mutableStateOf(config?.get("time_format")?.toString() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UI.FormField(
            label = s.shared("field_config_min"),
            value = min,
            onChange = {
                min = it
                if (it.isNotEmpty()) mutableConfig["min"] = it else mutableConfig.remove("min")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

        UI.FormField(
            label = s.shared("field_config_max"),
            value = max,
            onChange = {
                max = it
                if (it.isNotEmpty()) mutableConfig["max"] = it else mutableConfig.remove("max")
                onConfigChange(mutableConfig.ifEmpty { null })
            },
            fieldType = UIFieldType.TEXT,
            required = false
        )

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
}
