package com.assistant.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.assistant.core.ui.*
import com.assistant.core.themes.ThemeIconManager
import com.assistant.core.strings.Strings


/**
 * Reusable icon selector
 * 
 * @param current Currently selected icon
 * @param suggested List of suggested icons (displayed first)
 * @param onChange Callback called when an icon is selected
 */
@Composable
fun IconSelector(
    current: String,
    suggested: List<String> = emptyList(),
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    
    // Loading available icons
    val allAvailableIcons by remember { 
        mutableStateOf(ThemeIconManager.getAvailableIcons(context, "default"))
    }
    
    
    // Strings context  
    val s = remember { Strings.`for`(context = context) }
    
    // Interface: current icon + SELECT button
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UI.Text(s.shared("tools_config_label_icon"), TextType.LABEL)
        
        // Current icon
        UI.Icon(iconName = current, size = 32.dp)
        
        UI.ActionButton(
            action = ButtonAction.SELECT,
            onClick = { showDialog = true }
        )
    }
    
    // Selection dialog
    if (showDialog) {
        UI.Dialog(
            type = DialogType.SELECTION,
            onConfirm = {},
            onCancel = { showDialog = false }
        ) {
            Column {
                UI.Text(s.shared("tools_config_dialog_choose_icon"), TextType.SUBTITLE)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Suggested icons section
                if (suggested.isNotEmpty()) {
                    UI.Text(s.shared("tools_config_dialog_suggested_icons"), TextType.LABEL)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Display suggestions
                    val suggestedIcons = suggested.mapNotNull { suggestedId ->
                        allAvailableIcons.find { it.id == suggestedId }
                    }
                    
                    suggestedIcons.chunked(3).forEach { iconRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            iconRow.forEach { icon ->
                                UI.Button(
                                    type = if (current == icon.id) ButtonType.PRIMARY else ButtonType.DEFAULT,
                                    onClick = {
                                        onChange(icon.id)
                                        showDialog = false
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        UI.Icon(iconName = icon.id, size = 32.dp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Translated name only
                                        val translatedName = s.shared("icon_${icon.id.replace("-", "_")}")
                                        UI.Text(
                                            text = translatedName,
                                            type = TextType.CAPTION,
                                            fillMaxWidth = true,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                            
                            // Fill row with empty spaces if needed
                            repeat(3 - iconRow.size) {
                                Spacer(modifier = Modifier.size(80.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Separator and "All" section
                    UI.Text(s.shared("tools_config_dialog_all_icons"), TextType.LABEL)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Grid of all icons 3 per row
                allAvailableIcons.chunked(3).forEach { iconRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        iconRow.forEach { icon ->
                            UI.Button(
                                type = if (current == icon.id) ButtonType.PRIMARY else ButtonType.DEFAULT,
                                onClick = {
                                    onChange(icon.id)
                                    showDialog = false
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    UI.Icon(iconName = icon.id, size = 32.dp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Nom traduit seulement
                                    val translatedName = s.shared("icon_${icon.id.replace("-", "_")}")
                                    UI.Text(
                                        text = translatedName,
                                        type = TextType.CAPTION,
                                        fillMaxWidth = true,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        
                        // Fill row with empty spaces if needed
                        repeat(3 - iconRow.size) {
                            Spacer(modifier = Modifier.size(80.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}