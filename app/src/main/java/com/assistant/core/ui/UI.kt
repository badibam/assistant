package com.assistant.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.themes.default.DefaultTheme
import org.json.JSONObject

/**
 * UI - API publique unifiée
 * UNIQUEMENT les éléments convenus dans UI_DECISIONS.md
 * 
 * Principe : UI.* → délégation au thème actuel via CurrentTheme.current
 */
object UI {
    
    // =====================================
    // LAYOUT
    // =====================================
    
    @Composable
    fun Column(
        spacing: Dp? = null,
        content: @Composable ColumnScope.() -> Unit
    ) = CurrentTheme.current.Column(spacing, content)
    
    @Composable
    fun Row(
        spacing: Dp? = null,
        content: @Composable RowScope.() -> Unit
    ) = CurrentTheme.current.Row(spacing, content)
    
    @Composable
    fun Box(content: @Composable BoxScope.() -> Unit) = 
        CurrentTheme.current.Box(content)
    
    @Composable
    fun Spacer(modifier: Modifier) = 
        CurrentTheme.current.Spacer(modifier)
    
    // =====================================
    // INTERACTIVE
    // =====================================
    
    @Composable
    fun Button(
        type: ButtonType,
        size: Size = Size.M,
        state: ComponentState = ComponentState.NORMAL,
        onClick: () -> Unit,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Button(type, size, state, onClick, content)
    
    @Composable
    fun TextField(
        type: TextFieldType,
        state: ComponentState = ComponentState.NORMAL,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String
    ) = CurrentTheme.current.TextField(type, state, value, onChange, placeholder)
    
    // =====================================
    // DISPLAY
    // =====================================
    
    @Composable
    fun Text(
        text: String,
        type: TextType
    ) = CurrentTheme.current.Text(text, type)
    
    @Composable
    fun Card(
        type: CardType,
        size: Size = Size.M,
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Card(type, size, content)
    
    // =====================================
    // FEEDBACK SYSTEM
    // =====================================
    
    @Composable
    fun Toast(
        type: FeedbackType,
        message: String,
        duration: Duration = Duration.SHORT
    ) = CurrentTheme.current.Toast(type, message, duration)
    
    @Composable
    fun Snackbar(
        type: FeedbackType,
        message: String,
        action: String? = null,
        onAction: (() -> Unit)? = null
    ) = CurrentTheme.current.Snackbar(type, message, action, onAction)
    
    // =====================================
    // SYSTEM
    // =====================================
    
    @Composable
    fun LoadingIndicator(size: Size = Size.M) = 
        CurrentTheme.current.LoadingIndicator(size)
    
    @Composable
    fun Dialog(
        type: DialogType,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = { },
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Dialog(type, onConfirm, onCancel, content)
    
    // =====================================
    // COMPOSANTS SPÉCIALISÉS
    // =====================================
    
    @Composable
    fun ZoneCard(
        zone: Zone,
        onClick: () -> Unit,
        onLongClick: () -> Unit = { }
    ) {
        // Container thématisé + contenu standard avec UI.*
        CurrentTheme.current.ZoneCardContainer(onClick = onClick, onLongClick = onLongClick) {
            Column {
                Text(zone.name, TextType.TITLE)
                zone.description?.let { desc ->
                    Text(desc, TextType.BODY)
                }
            }
        }
    }
    
    @Composable
    fun ToolCard(
        tool: ToolInstance,
        displayMode: DisplayMode,
        onClick: () -> Unit,
        onLongClick: () -> Unit = { }
    ) {
        // Contenu défini au niveau core + tool types avec UI.*
        CurrentTheme.current.ToolCardContainer(
            displayMode = displayMode,
            onClick = onClick, 
            onLongClick = onLongClick
        ) {
            when (displayMode) {
                DisplayMode.ICON -> {
                    // TODO: Icône seule via tool type
                    Text("T", TextType.BODY) // Placeholder
                }
                DisplayMode.MINIMAL -> {
                    Row {
                        Text("T", TextType.BODY) // TODO: Icône
                        val toolInstanceName = try {
                            JSONObject(tool.config_json).optString("name")
                        } catch (e: Exception) {
                            ""
                        }
                        Text(toolInstanceName, TextType.BODY)
                    }
                }
                DisplayMode.LINE -> {
                    Row {
                        // Icône + titre à gauche (partie fixe)
                        Row {
                            Text("T", TextType.BODY) // TODO: Icône via tool type
                            val toolInstanceName = try {
                                JSONObject(tool.config_json).optString("name")
                            } catch (e: Exception) {
                                ""
                            }
                            Text(toolInstanceName, TextType.BODY)
                        }
                        // Zone libre à droite définie par tool type
                        Box {
                            // TODO: Contenu libre LINE défini par tool type
                        }
                    }
                }
                DisplayMode.CONDENSED, DisplayMode.EXTENDED, DisplayMode.SQUARE, DisplayMode.FULL -> {
                    Column {
                        Row {
                            // Icône + titre à gauche (partie fixe)
                            Row {
                                Text("T", TextType.BODY) // TODO: Icône via tool type
                                val toolInstanceName = try {
                                JSONObject(tool.config_json).optString("name")
                            } catch (e: Exception) {
                                ""
                            }
                            Text(toolInstanceName, TextType.BODY)
                            }
                            // Zone libre en haut à droite définie par tool type
                            Box {
                                // TODO: Contenu libre haut défini par tool type selon mode
                            }
                        }
                        // Zone libre en-dessous définie par tool type
                        Box {
                            // TODO: Contenu libre bas défini par tool type selon mode
                        }
                    }
                }
            }
        }
    }
}