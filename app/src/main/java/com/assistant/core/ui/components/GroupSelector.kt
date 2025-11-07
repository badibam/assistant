package com.assistant.core.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.ui.UI
import com.assistant.core.strings.Strings

/**
 * Reusable component for selecting a group from available groups
 *
 * Features:
 * - Dropdown with all available groups + "No group" option
 * - Nullable selection (null = no group assigned)
 *
 * Usage:
 * ```kotlin
 * val availableGroups = listOf("Health", "Productivity", "Entertainment")
 * var selectedGroup by remember { mutableStateOf<String?>(null) }
 *
 * GroupSelector(
 *     availableGroups = availableGroups,
 *     selectedGroup = selectedGroup,
 *     onGroupSelected = { selectedGroup = it },
 *     label = "Group"
 * )
 * ```
 */
@Composable
fun GroupSelector(
    availableGroups: List<String>,
    selectedGroup: String?,
    onGroupSelected: (String?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Build options: "No group" + all available groups
    val noGroupLabel = s.shared("label_ungrouped")
    val options = buildList {
        add(noGroupLabel) // First option = no group (null)
        addAll(availableGroups)
    }

    // Find current selection label
    val selectedLabel = selectedGroup ?: noGroupLabel

    com.assistant.core.utils.LogManager.ui("GroupSelector - selectedGroup: '$selectedGroup', selectedLabel: '$selectedLabel', noGroupLabel: '$noGroupLabel'", "DEBUG")

    UI.FormSelection(
        label = label,
        options = options,
        selected = selectedLabel,
        onSelect = { selectedLabelClicked ->
            com.assistant.core.utils.LogManager.ui("GroupSelector - User selected: '$selectedLabelClicked', noGroupLabel: '$noGroupLabel'", "DEBUG")
            // If selected label is "No group" -> null, else actual group name
            val newGroup = if (selectedLabelClicked == noGroupLabel) null else selectedLabelClicked
            com.assistant.core.utils.LogManager.ui("GroupSelector - Calling onGroupSelected with: '$newGroup'", "DEBUG")
            onGroupSelected(newGroup)
        },
        required = false
    )
}
