package com.assistant.core.fields

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Editor for managing custom field definitions in tool configuration screens.
 *
 * This component provides a UI for:
 * - Displaying existing custom fields with their metadata
 * - Reordering fields (up/down)
 * - Editing field definitions
 * - Deleting fields (with confirmation)
 * - Adding new fields
 *
 * @param fields Current list of field definitions (ordered)
 * @param onFieldsChange Callback when the field list changes (create/update/delete/reorder)
 * @param context Android context for strings
 */
@Composable
fun CustomFieldsEditor(
    fields: List<FieldDefinition>,
    onFieldsChange: (List<FieldDefinition>) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)

    // State for showing the field definition dialog
    var showDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<FieldDefinition?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    // State for delete confirmation dialog
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section title
        UI.Text(
            text = s.shared("custom_fields_section_title"),
            type = TextType.SUBTITLE,
            fillMaxWidth = true
        )

        // List of existing fields
        if (fields.isNotEmpty()) {
            fields.forEachIndexed { index, field ->
                FieldDefinitionCard(
                    field = field,
                    index = index,
                    totalFields = fields.size,
                    onMoveUp = {
                        if (index > 0) {
                            val newFields = fields.toMutableList()
                            val temp = newFields[index]
                            newFields[index] = newFields[index - 1]
                            newFields[index - 1] = temp
                            onFieldsChange(newFields)
                        }
                    },
                    onMoveDown = {
                        if (index < fields.size - 1) {
                            val newFields = fields.toMutableList()
                            val temp = newFields[index]
                            newFields[index] = newFields[index + 1]
                            newFields[index + 1] = temp
                            onFieldsChange(newFields)
                        }
                    },
                    onEdit = {
                        editingField = field
                        editingIndex = index
                        showDialog = true
                    },
                    onDelete = {
                        deletingIndex = index
                        showDeleteConfirmation = true
                    },
                    context = context
                )
            }
        }

        // Add field button
        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                editingField = null
                editingIndex = null
                showDialog = true
            }
        )
    }

    // Field definition dialog (create or edit)
    if (showDialog) {
        FieldDefinitionDialog(
            existingField = editingField,
            existingFields = fields,
            onDismiss = {
                showDialog = false
                editingField = null
                editingIndex = null
            },
            onConfirm = { newField ->
                val newFields = fields.toMutableList()
                if (editingIndex != null) {
                    // Update existing field
                    newFields[editingIndex!!] = newField
                } else {
                    // Add new field
                    newFields.add(newField)
                }
                onFieldsChange(newFields)
                showDialog = false
                editingField = null
                editingIndex = null
            },
            context = context
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation && deletingIndex != null) {
        UI.ConfirmDialog(
            title = s.shared("custom_fields_delete_confirm_title"),
            message = s.shared("custom_fields_delete_confirm_message"),
            onConfirm = {
                val newFields = fields.toMutableList()
                newFields.removeAt(deletingIndex!!)
                onFieldsChange(newFields)
                showDeleteConfirmation = false
                deletingIndex = null
            },
            onDismiss = {
                showDeleteConfirmation = false
                deletingIndex = null
            }
        )
    }
}

/**
 * Card component for displaying a single field definition with action buttons.
 *
 * @param field The field definition to display
 * @param index The index of this field in the list
 * @param totalFields Total number of fields in the list
 * @param onMoveUp Callback to move field up
 * @param onMoveDown Callback to move field down
 * @param onEdit Callback to edit field
 * @param onDelete Callback to delete field
 * @param context Android context for strings
 */
@Composable
private fun FieldDefinitionCard(
    field: FieldDefinition,
    index: Int,
    totalFields: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Field display name (title)
            UI.Text(
                text = field.displayName,
                type = TextType.SUBTITLE,
                fillMaxWidth = true
            )

            // Field type
            val typeLabel = when (field.type) {
                FieldType.TEXT_UNLIMITED -> s.shared("field_type_text_unlimited")
                // Future types will be added here
            }
            UI.Text(
                text = typeLabel,
                type = TextType.LABEL,
                fillMaxWidth = true
            )

            // Description (truncated if long)
            if (!field.description.isNullOrEmpty()) {
                val truncatedDesc = if (field.description.length > 100) {
                    field.description.take(100) + "..."
                } else {
                    field.description
                }
                UI.Text(
                    text = truncatedDesc,
                    type = TextType.CAPTION,
                    fillMaxWidth = true
                )
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move up button (disabled if first)
                UI.ActionButton(
                    action = ButtonAction.UP,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onMoveUp,
                    enabled = index > 0
                )

                // Move down button (disabled if last)
                UI.ActionButton(
                    action = ButtonAction.DOWN,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onMoveDown,
                    enabled = index < totalFields - 1
                )

                Spacer(modifier = Modifier.weight(1f))

                // Edit button
                UI.ActionButton(
                    action = ButtonAction.EDIT,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onEdit
                )

                // Delete button
                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = onDelete
                )
            }
        }
    }
}

/**
 * Dialog for creating or editing a custom field definition.
 *
 * @param existingField Field to edit (null for creating new field)
 * @param existingFields List of all existing fields (for name collision detection)
 * @param onDismiss Callback when dialog is dismissed
 * @param onConfirm Callback when field is confirmed (receives the new/updated field)
 * @param context Android context for strings
 */
@Composable
fun FieldDefinitionDialog(
    existingField: FieldDefinition?,
    existingFields: List<FieldDefinition>,
    onDismiss: () -> Unit,
    onConfirm: (FieldDefinition) -> Unit,
    context: Context
) {
    val s = Strings.`for`(context = context)
    val isEditing = existingField != null

    // Form state
    var displayName by remember { mutableStateOf(existingField?.displayName ?: "") }
    var description by remember { mutableStateOf(existingField?.description ?: "") }
    var fieldType by remember { mutableStateOf(existingField?.type ?: FieldType.TEXT_UNLIMITED) }
    var alwaysVisible by remember { mutableStateOf(existingField?.alwaysVisible ?: false) }

    // Generated name preview (for transparency)
    val generatedName = remember(displayName) {
        if (displayName.isNotEmpty()) {
            // Filter out the field being edited to avoid false collision detection
            val otherFields = if (isEditing) {
                existingFields.filter { it.name != existingField?.name }
            } else {
                existingFields
            }
            FieldNameGenerator.generateName(displayName, otherFields)
        } else {
            ""
        }
    }

    // Validation
    val isValid = displayName.isNotBlank()

    // Error message state for toast
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Show error toast
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            UI.Toast(context, errorMessage!!, Duration.SHORT)
            errorMessage = null
        }
    }

    UI.Dialog(
        type = if (isEditing) DialogType.EDIT else DialogType.CREATE,
        onConfirm = {
            if (isValid) {
                // Validate field definition
                val otherFields = if (isEditing) {
                    existingFields.filter { it.name != existingField?.name }
                } else {
                    existingFields
                }

                val fieldDef = FieldDefinition(
                    name = existingField?.name ?: generatedName, // Keep existing name if editing
                    displayName = displayName.trim(),
                    description = description.trim().ifEmpty { null },
                    type = fieldType,
                    alwaysVisible = alwaysVisible,
                    config = null // V1: always null for TEXT_UNLIMITED
                )

                // Validate with FieldConfigValidator
                val validation = FieldConfigValidator.validate(fieldDef, otherFields)
                if (validation.isValid) {
                    onConfirm(fieldDef)
                } else {
                    errorMessage = validation.errorMessage
                }
            }
        },
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dialog title
            UI.Text(
                text = if (isEditing) {
                    s.shared("custom_fields_edit")
                } else {
                    s.shared("custom_fields_create")
                },
                type = TextType.TITLE,
                fillMaxWidth = true
            )

            // Display name field (required)
            UI.FormField(
                label = s.shared("custom_fields_display_name"),
                value = displayName,
                onChange = { displayName = it },
                fieldType = com.assistant.core.ui.FieldType.TEXT,
                required = true
            )

            // Generated name preview (read-only, shown for transparency)
            if (generatedName.isNotEmpty()) {
                UI.Text(
                    text = s.shared("custom_fields_generated_name").format(generatedName),
                    type = TextType.CAPTION,
                    fillMaxWidth = true
                )
            }

            // Description field (optional)
            UI.FormField(
                label = s.shared("custom_fields_description"),
                value = description,
                onChange = { description = it },
                fieldType = com.assistant.core.ui.FieldType.TEXT_MEDIUM,
                required = false
            )

            // Type selection (only TEXT_UNLIMITED for V1)
            // In edit mode, type is immutable (read-only)
            if (isEditing) {
                // Show type as read-only in edit mode
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UI.Text(
                        text = s.shared("custom_fields_type"),
                        type = TextType.LABEL,
                        fillMaxWidth = true
                    )
                    UI.Text(
                        text = s.shared("field_type_text_unlimited"),
                        type = TextType.BODY,
                        fillMaxWidth = true
                    )
                }
            } else {
                // Show type as selection in create mode (only one option for V1)
                UI.FormSelection(
                    label = s.shared("custom_fields_type"),
                    options = listOf(s.shared("field_type_text_unlimited")),
                    selected = s.shared("field_type_text_unlimited"),
                    onSelect = { /* No-op, only one option */ },
                    required = true
                )
            }

            // Always visible toggle
            UI.ToggleField(
                label = s.shared("custom_fields_always_visible"),
                checked = alwaysVisible,
                onCheckedChange = { alwaysVisible = it },
                required = false
            )
        }
    }
}
