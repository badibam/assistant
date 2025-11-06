package com.assistant.core.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.ui.components.IconSelector
import com.assistant.core.ui.components.GroupSelector
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.commands.CommandStatus
import org.json.JSONObject
import org.json.JSONArray

/**
 * Reusable general settings section for all tool types
 * Extracted from TrackingConfigScreen for generalization
 *
 * @param config Complete JSON configuration of the tool
 * @param updateConfig Callback to update configuration
 * @param toolTypeName Tool type name to retrieve suggested icons
 * @param zoneId Zone ID to fetch available tool groups
 */
@Composable
fun ToolGeneralConfigSection(
    config: JSONObject,
    updateConfig: (String, Any) -> Unit,
    toolTypeName: String,
    zoneId: String,
    initialGroup: String? = null  // Pre-selected group for new tool creation
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }

    // Retrieve suggested icons from ToolType
    val suggestedIcons = remember(toolTypeName) {
        ToolTypeManager.getToolType(toolTypeName)?.getSuggestedIcons() ?: emptyList()
    }

    // Load available tool groups from zone
    var availableGroups by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(zoneId) {
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
        if (result.status == CommandStatus.SUCCESS) {
            val zone = result.data?.get("zone") as? Map<*, *>
            val toolGroupsJson = zone?.get("tool_groups") as? String
            if (toolGroupsJson != null) {
                try {
                    val jsonArray = JSONArray(toolGroupsJson)
                    availableGroups = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                } catch (e: Exception) {
                    availableGroups = emptyList()
                }
            } else {
                availableGroups = emptyList()
            }
        }
    }

    // Initialize group with initialGroup if config doesn't have one (only once on first composition)
    LaunchedEffect(initialGroup) {
        if (initialGroup != null && config.optString("group", "").isBlank()) {
            updateConfig("group", initialGroup)
        }
    }

    // Extract values from config
    val name = config.optString("name", "")
    val description = config.optString("description", "")
    val iconName = config.optString("icon_name", "")
    val displayMode = config.optString("display_mode", "")
    val management = config.optString("management", "")
    val validateConfig = config.optBoolean("validateConfig", false)
    val validateData = config.optBoolean("validateData", false)
    val alwaysSend = config.optBoolean("always_send", false)
    val group = config.optString("group", "").takeIf { it.isNotBlank() }

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UI.Text(s.shared("tools_config_section_general_params"), TextType.SUBTITLE)
            
            // 1. Name (required)
            UI.FormField(
                label = s.shared("tools_config_label_name"),
                value = name,
                onChange = { updateConfig("name", it) },
                required = true
            )
            
            // 2. Description (optional)
            UI.FormField(
                label = s.shared("tools_config_label_description"),
                value = description,
                onChange = { updateConfig("description", it) },
                fieldType = FieldType.TEXT_MEDIUM
            )
            
            // 3. Icon selector with suggestions
            IconSelector(
                current = iconName,
                suggested = suggestedIcons,
                onChange = { updateConfig("icon_name", it) }
            )
            
            // 4. Display mode (required)
            UI.FormSelection(
                label = s.shared("tools_config_label_display_mode"),
                options = listOf(
                    s.shared("tools_config_display_icon"), 
                    s.shared("tools_config_display_minimal"), 
                    s.shared("tools_config_display_line"), 
                    s.shared("tools_config_display_condensed"), 
                    s.shared("tools_config_display_extended"), 
                    s.shared("tools_config_display_square"), 
                    s.shared("tools_config_display_full")
                ),
                selected = when(displayMode) {
                    "ICON" -> s.shared("tools_config_display_icon")
                    "MINIMAL" -> s.shared("tools_config_display_minimal")
                    "LINE" -> s.shared("tools_config_display_line")
                    "CONDENSED" -> s.shared("tools_config_display_condensed")
                    "EXTENDED" -> s.shared("tools_config_display_extended")
                    "SQUARE" -> s.shared("tools_config_display_square")
                    "FULL" -> s.shared("tools_config_display_full")
                    else -> displayMode
                },
                onSelect = { selectedLabel ->
                    updateConfig("display_mode", when(selectedLabel) {
                        s.shared("tools_config_display_icon") -> "ICON"
                        s.shared("tools_config_display_minimal") -> "MINIMAL"
                        s.shared("tools_config_display_line") -> "LINE"
                        s.shared("tools_config_display_condensed") -> "CONDENSED"
                        s.shared("tools_config_display_extended") -> "EXTENDED"
                        s.shared("tools_config_display_square") -> "SQUARE"
                        s.shared("tools_config_display_full") -> "FULL"
                        else -> selectedLabel
                    })
                },
                required = true
            )
            
            // 5. Management (required)
            UI.FormSelection(
                label = s.shared("tools_config_label_management"),
                options = listOf(s.shared("tools_config_option_manual"), s.shared("tools_config_option_ai")),
                selected = when(management) {
                    "manual" -> s.shared("tools_config_option_manual")
                    "ai" -> s.shared("tools_config_option_ai")
                    else -> management
                },
                onSelect = { selectedLabel ->
                    updateConfig("management", when(selectedLabel) {
                        s.shared("tools_config_option_manual") -> "manual"
                        s.shared("tools_config_option_ai") -> "ai"
                        else -> selectedLabel
                    })
                },
                required = true
            )
            
            // 6. AI config validation (required)
            UI.FormSelection(
                label = s.shared("tools_config_label_config_validation"),
                options = listOf(s.shared("tools_config_option_enabled"), s.shared("tools_config_option_disabled")),
                selected = if (validateConfig) s.shared("tools_config_option_enabled") else s.shared("tools_config_option_disabled"),
                onSelect = { selectedLabel ->
                    updateConfig("validateConfig", selectedLabel == s.shared("tools_config_option_enabled"))
                },
                required = true
            )
            
            // 7. AI data validation (required)
            UI.FormSelection(
                label = s.shared("tools_config_label_data_validation"),
                options = listOf(s.shared("tools_config_option_enabled"), s.shared("tools_config_option_disabled")),
                selected = if (validateData) s.shared("tools_config_option_enabled") else s.shared("tools_config_option_disabled"),
                onSelect = { selectedLabel ->
                    updateConfig("validateData", selectedLabel == s.shared("tools_config_option_enabled"))
                },
                required = true
            )

            // 8. Always send to AI (optional - Level 2)
            UI.ToggleField(
                label = s.shared("tools_config_label_always_send"),
                checked = alwaysSend,
                onCheckedChange = { updateConfig("always_send", it) },
                trueLabel = s.shared("tools_config_option_yes"),
                falseLabel = s.shared("tools_config_option_no"),
                required = false
            )

            // 9. Group selection (optional)
            GroupSelector(
                availableGroups = availableGroups,
                selectedGroup = group,
                onGroupSelected = { newGroup ->
                    if (newGroup != null) {
                        updateConfig("group", newGroup)
                    } else {
                        // Remove group field from config
                        config.remove("group")
                    }
                },
                label = s.shared("label_group")
            )
        }
    }
}