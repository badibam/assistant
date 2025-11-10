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

/**
 * Renders a single custom field input component.
 *
 * This is the base component for editing individual custom fields. It wraps UI.FormField
 * with field definition metadata and handles type-specific rendering.
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
    // Convert custom FieldType to UI FieldType for rendering
    val uiFieldType = when (fieldDef.type) {
        com.assistant.core.fields.FieldType.TEXT_UNLIMITED -> UIFieldType.TEXT_UNLIMITED
        // Future field types will map to appropriate UI field types:
        // FieldType.TEXT -> UIFieldType.TEXT
        // FieldType.NUMERIC -> UIFieldType.NUMERIC
        // etc.
    }

    // Render the input field using UI.FormField
    UI.FormField(
        label = fieldDef.displayName,
        value = value?.toString() ?: "",
        onChange = { newValue ->
            // For TEXT_UNLIMITED, pass the string directly
            // Future types will need type-specific conversion
            onChange(if (newValue.isEmpty()) null else newValue)
        },
        fieldType = uiFieldType,
        required = false // Custom fields are always optional per specs
    )
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
