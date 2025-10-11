package com.assistant.core.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.ui.components.IconSelector
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import org.json.JSONObject

/**
 * Reusable general settings section for all tool types
 * Extracted from TrackingConfigScreen for generalization
 * 
 * @param config Complete JSON configuration of the tool
 * @param updateConfig Callback to update configuration
 * @param toolTypeName Tool type name to retrieve suggested icons
 */
@Composable
fun ToolGeneralConfigSection(
    config: JSONObject,
    updateConfig: (String, Any) -> Unit,
    toolTypeName: String
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    
    // Retrieve suggested icons from ToolType
    val suggestedIcons = remember(toolTypeName) {
        ToolTypeManager.getToolType(toolTypeName)?.getSuggestedIcons() ?: emptyList()
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
            UI.FormSelection(
                label = s.shared("tools_config_label_always_send"),
                options = listOf(s.shared("tools_config_option_yes"), s.shared("tools_config_option_no")),
                selected = if (alwaysSend) s.shared("tools_config_option_yes") else s.shared("tools_config_option_no"),
                onSelect = { selectedLabel ->
                    updateConfig("always_send", selectedLabel == s.shared("tools_config_option_yes"))
                },
                required = false
            )
        }
    }
}