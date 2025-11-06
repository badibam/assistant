package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings

/**
 * Reusable component for editing a list of group names
 *
 * Features:
 * - Add new group with validation (non-empty, no duplicates)
 * - Delete existing groups
 * - Reorder groups (move up/down)
 *
 * Usage:
 * ```kotlin
 * var groups by remember { mutableStateOf(listOf("Group 1", "Group 2")) }
 * GroupListEditor(
 *     groups = groups,
 *     onGroupsChange = { groups = it },
 *     label = "Tool Groups"
 * )
 * ```
 */
@Composable
fun GroupListEditor(
    groups: List<String>,
    onGroupsChange: (List<String>) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    var newGroupName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-clear error message after displaying
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            UI.Toast(context, errorMessage!!, Duration.SHORT)
            errorMessage = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label
        UI.Text(label, TextType.SUBTITLE)

        // List of existing groups
        if (groups.isEmpty()) {
            UI.Text(
                s.shared("message_no_tool_groups"),
                TextType.CAPTION
            )
        } else {
            groups.forEachIndexed { index, group ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Group name
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        UI.Text(group, TextType.BODY)
                    }

                    // Move up button (disabled for first item)
                    UI.ActionButton(
                        action = ButtonAction.UP,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        enabled = index > 0,
                        onClick = {
                            if (index > 0) {
                                val mutableGroups = groups.toMutableList()
                                val temp = mutableGroups[index]
                                mutableGroups[index] = mutableGroups[index - 1]
                                mutableGroups[index - 1] = temp
                                onGroupsChange(mutableGroups)
                            }
                        }
                    )

                    // Move down button (disabled for last item)
                    UI.ActionButton(
                        action = ButtonAction.DOWN,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        enabled = index < groups.size - 1,
                        onClick = {
                            if (index < groups.size - 1) {
                                val mutableGroups = groups.toMutableList()
                                val temp = mutableGroups[index]
                                mutableGroups[index] = mutableGroups[index + 1]
                                mutableGroups[index + 1] = temp
                                onGroupsChange(mutableGroups)
                            }
                        }
                    )

                    // Delete button
                    UI.ActionButton(
                        action = ButtonAction.DELETE,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = {
                            val mutableGroups = groups.toMutableList()
                            mutableGroups.removeAt(index)
                            onGroupsChange(mutableGroups)
                        }
                    )
                }
            }
        }

        // Add new group section
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                UI.FormField(
                    label = s.shared("label_group_name"),
                    value = newGroupName,
                    onChange = { newGroupName = it },
                    fieldType = FieldType.TEXT
                )
            }

            UI.ActionButton(
                action = ButtonAction.ADD,
                display = ButtonDisplay.ICON,
                size = Size.M,
                enabled = newGroupName.isNotBlank(),
                onClick = {
                    val trimmed = newGroupName.trim()
                    when {
                        trimmed.isEmpty() -> {
                            errorMessage = s.shared("service_error_tool_groups_empty_name")
                        }
                        groups.contains(trimmed) -> {
                            errorMessage = s.shared("service_error_tool_groups_duplicate").format(trimmed)
                        }
                        else -> {
                            onGroupsChange(groups + trimmed)
                            newGroupName = ""
                        }
                    }
                }
            )
        }
    }
}
