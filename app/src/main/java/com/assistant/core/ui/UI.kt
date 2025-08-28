package com.assistant.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.assistant.core.database.entities.Zone
import com.assistant.core.database.entities.ToolInstance
import com.assistant.core.tools.ToolTypeManager
import com.assistant.themes.default.DefaultTheme
import org.json.JSONObject

/**
 * UI - API publique unifiée
 * UNIQUEMENT les composants VISUELS (thématisés)
 * 
 * LAYOUTS : utiliser Row/Column/Box/Spacer de Compose directement
 * VISUELS : utiliser UI.* pour thématisation
 * 
 * Principe : UI.* → délégation au thème actuel via CurrentTheme.current
 */
object UI {
    
    // =====================================
    // LAYOUTS : UTILISER COMPOSE DIRECTEMENT
    // =====================================
    // Row(..), Column(..), Box(..), Spacer(..) + modifiers Compose
    // PAS de wrappers - accès direct pour flexibilité maximale
    
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
    fun ActionButton(
        action: ButtonAction,
        display: ButtonDisplay = ButtonDisplay.LABEL,
        size: Size = Size.M,
        type: ButtonType? = null,  // Override optionnel du type par défaut
        enabled: Boolean = true,
        requireConfirmation: Boolean = false,  // Dialogue de confirmation automatique
        confirmMessage: String? = null,        // Message custom (null = message par défaut)
        onClick: () -> Unit
    ) = CurrentTheme.current.ActionButton(action, display, size, type, enabled, requireConfirmation, confirmMessage, onClick)
    
    @Composable
    fun TextField(
        fieldType: FieldType,
        state: ComponentState = ComponentState.NORMAL,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String
    ) = CurrentTheme.current.TextField(fieldType, state, value, onChange, placeholder)
    
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
    fun Icon(
        resourceId: Int,
        size: Dp = 24.dp,
        contentDescription: String? = null
    ) = CurrentTheme.current.Icon(resourceId, size, contentDescription)
    
    @Composable
    fun Dialog(
        type: DialogType,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = { },
        content: @Composable () -> Unit
    ) = CurrentTheme.current.Dialog(type, onConfirm, onCancel, content)
    
    // =====================================
    // FORMULAIRES UNIFIÉS
    // =====================================
    
    @Composable
    fun FormField(
        label: String,
        value: String,
        onChange: (String) -> Unit,
        fieldType: FieldType = FieldType.TEXT,
        required: Boolean = false,
        state: ComponentState = ComponentState.NORMAL,
        readonly: Boolean = false,
        onClick: (() -> Unit)? = null,
        contentDescription: String? = null
    ) = CurrentTheme.current.FormField(
        label = label,
        value = value, 
        onChange = onChange,
        fieldType = fieldType,
        state = state,
        readonly = readonly,
        onClick = onClick,
        contentDescription = contentDescription,
        required = required
    )
    
    @Composable
    fun FormSelection(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit,
        required: Boolean = false
    ) = CurrentTheme.current.FormSelection(
        label = label,
        options = options,
        selected = selected, 
        onSelect = onSelect,
        required = required
    )
    
    @Composable
    fun FormActions(
        content: @Composable RowScope.() -> Unit
    ) = CurrentTheme.current.FormActions(content)
    
    @Composable
    fun Checkbox(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String? = null
    ) = CurrentTheme.current.Checkbox(checked, onCheckedChange, label)
    
    // =====================================
    // BOUTONS AVEC ICÔNES AUTOMATIQUES
    // =====================================
    
    // Anciens boutons prédéfinis supprimés - utiliser UI.ActionButton à la place
    
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
    fun ToolCardHeader(
        tool: ToolInstance,
        context: android.content.Context
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône réelle
            val iconName = JSONObject(tool.config_json).optString("icon", "activity")
            val iconResourceId = ThemeIconManager.getIconResource(context, "default", iconName)
            Icon(iconResourceId, size = 24.dp)
            
            // Nom de l'instance
            val toolInstanceName = JSONObject(tool.config_json).optString("name", "Sans nom")
            Text(toolInstanceName, TextType.BODY)
        }
    }
    
    @Composable
    fun ToolCard(
        tool: ToolInstance,
        displayMode: DisplayMode,
        context: android.content.Context,
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
                    ToolCardHeader(tool, context)
                }
                DisplayMode.LINE -> {
                    Row {
                        // Icône + titre à gauche (partie fixe)
                        ToolCardHeader(tool, context)
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
                            ToolCardHeader(tool, context)
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